package com.ruoyi.wexinAuto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ai.DeepSeekAiService;
import com.ruoyi.wexinAuto.analysis.FixedOfferMarker;
import com.ruoyi.wexinAuto.analysis.WeChatLlmClassifier;
import com.ruoyi.wexinAuto.config.WeChatGatewayProperties;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.msg.WeChatMessageHandler;
import com.ruoyi.wexinAuto.msg.WeChatMessageParser;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import java.text.SimpleDateFormat;

/**
 * 微信消息网关服务。
 * <ul>
 *   <li>fetch(...) —— 主动拉取(按时间+可选会话)，消息统一入库(wechat_message)，
 *       未分类消息按会话归并交给 AI 分类后回写，返回含分类结果的消息列表；</li>
 *   <li>start/stop/status —— 实时监听：每条消息先入库，同一会话积累到 aiTriggerCount 条
 *       或超过 aiFlushMinutes 分钟未分类时，批量调起 AI 分类（数量/时间窗口均在数据字典
 *       wechat_analysis_config 配置），分类结果回写数据库并逐条回调 Handler；</li>
 *   <li>消息只保留最近 msgRetentionDays 天，由定时任务定期清理。</li>
 * </ul>
 */
@Service
@Slf4j
public class WeChatGatewayService {

    /** 消息分析参数数据字典类型（sys_dict_type.dict_type） */
    private static final String ANALYSIS_DICT_TYPE = "wechat_analysis_config";

    /** 单次从库查询的最大行数(3天窗口，足够覆盖) */
    private static final int DB_QUERY_MAX = 50000;

    /** 定时回收未分类消息的单批上限 */
    private static final int RECOVER_BATCH = 3000;

    /** 同会话 AI 分类失败后的冷却时间(ms)，避免网络故障时反复打接口 */
    private static final long FAIL_COOLDOWN_MS = 3 * 60 * 1000L;

    private final WeChatGatewayProperties props;
    private final WeChatMessageHandler handler;
    private final WeChatLlmClassifier llm;
    private final WeChatMessageStore store;
    private final CrmOfferMapper offerMapper;
    private final DeepSeekAiService deepSeekAiService;

    private final Object listenLock = new Object();
    private Process listenProcess;
    private Thread listenThread;
    private volatile boolean listening = false;
    private final AtomicLong listenCount = new AtomicLong(0);
    private final AtomicLong filteredCount = new AtomicLong(0);

    /** 监听路径：每个会话最近 N 条已分类消息（供 AI 上下文与固定标记使用） */
    private final Map<String, Deque<WeChatMessage>> listenHistory = new ConcurrentHashMap<>();

    /** 待分类缓冲：每个会话积累未分类消息，满足数量/时间窗口触发 AI 分类 */
    private final Map<String, ChatBuffer> buffers = new ConcurrentHashMap<>();
    private final Set<String> bufferedKeys = ConcurrentHashMap.newKeySet();

    /** 同会话 AI 分类失败时间戳(冷却用) */
    private final Map<String, Long> lastFailAt = new ConcurrentHashMap<>();

    // ---------- 定时轮询拉取状态 ----------
    /** dump 进程互斥锁（轮询/主动拉取/手动拉取共用一个 Python 进程槽） */
    private final Object dumpLock = new Object();
    /** 是否正在执行 dump（防止轮询与接口调用并发起进程） */
    private final AtomicBoolean pollRunning = new AtomicBoolean(false);
    /** 上次轮询完成时间(ms)，用于计算间隔 */
    private volatile long lastPollFinishedAt = 0L;
    /** 上次拉取到的最大 sort_seq(ms)，作为增量水位；0=尚未初始化(首轮拉 pollInitBackSec 历史) */
    private volatile long lastPollMaxSeqMs = 0L;
    /** 最近一轮 dump 拉到的原始条数(含被忽略名单过滤掉的) */
    private volatile long lastPollFetched = 0L;

    // ---------- INQ/OFFER 自动录入状态 ----------
    /** 录入任务互斥锁（定时扫描与手动触发共用，防止并发重入） */
    private final AtomicBoolean entryRunning = new AtomicBoolean(false);
    /** 上次录入完成时间(ms)，用于按字典 offerEntryScanSec 控制扫描间隔 */
    private volatile long lastEntryFinishedAt = 0L;
    /** 最近一轮录入处理的原始条数 */
    private volatile int lastEntryProcessed = 0;

    /** AI 分类线程池（异步，不阻塞监听读行） */
    private final ExecutorService classifyPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "wechat-classify");
        t.setDaemon(true);
        return t;
    });

    private final ObjectMapper jackson = new ObjectMapper();

    /** 会话缓冲：pending=未分类消息；lastFlushAt=上次触发分类时间。 */
    private static final class ChatBuffer {
        final List<WeChatMessage> pending = new ArrayList<>();
        volatile long lastFlushAt = System.currentTimeMillis();

        ChatBuffer() {
        }
    }

    @Autowired
    public WeChatGatewayService(WeChatGatewayProperties props, WeChatMessageHandler handler,
                                WeChatLlmClassifier llm, WeChatMessageStore store,
                                CrmOfferMapper offerMapper, DeepSeekAiService deepSeekAiService) {
        this.props = props;
        this.handler = handler;
        this.llm = llm;
        this.store = store;
        this.offerMapper = offerMapper;
        this.deepSeekAiService = deepSeekAiService;
    }

    // ------------------------------------------------------------------
    // 主动拉取：按时间(到秒,闭区间) + 可选群/好友名
    // ------------------------------------------------------------------
    public List<WeChatMessage> fetch(String since, String chat, boolean media, Integer limit, Integer status) {
        // 先增量同步最新消息(继承轮询水位；指定 chat 时只同步该会话)，随后从库里查询
        dumpIncremental(chat, null, media);
        // 从库里读(带历史分类结果)，对未分类的做 AI 归并分类并回写
        List<WeChatMessage> rows = store.query(since, chat, status, DB_QUERY_MAX);
        classifySequence(rows, true);
        return applyOutputFilters(rows, limit);
    }

    // ------------------------------------------------------------------
    // 定时轮询拉取：服务启动后按 pollIntervalSec 定时 dump 增量入库(status=0)
    // ------------------------------------------------------------------
    /** 手动触发一次拉取（可选指定群/好友名，只拉该会话）；定时任务也调用本方法(chat=null)。 */
    public Map<String, Object> pollOnce(String chat) {
        return pollOnce(chat, null, false);
    }

    /** 手动触发拉取并节流（兼容旧调用）。 */
    public Map<String, Object> pollOnce(String chat, boolean force) {
        return pollOnce(chat, null, force);
    }

    /**
     * 拉取入口。
     *
     * @param chat  群名/好友名，可选；null=所有会话增量
     * @param since 手动指定拉取起点(格式 yyyy-MM-dd HH:mm:ss，含该秒)，可选；null=使用轮询水位
     * @param force true=忽略 pollIntervalSec 间隔直接拉（手动接口）；false=按间隔节流（定时任务）
     */
    public Map<String, Object> pollOnce(String chat, String since, boolean force) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!analysisBool("pollEnabled", true)) {
            out.put("message", "轮询拉取已关闭(字典 pollEnabled=false)");
            out.put("enabled", false);
            out.put("polled", false);
            return out;
        }
        long intervalMs = Math.max(1, analysisInt("pollIntervalSec", 10)) * 1000L;
        long now = System.currentTimeMillis();
        if (!force && lastPollFinishedAt > 0 && now - lastPollFinishedAt < intervalMs) {
            out.put("message", "距上次拉取不足间隔(pollIntervalSec)，跳过");
            out.put("polled", false);
            out.put("running", pollRunning.get());
            out.put("lastRunAt", fmtTime(lastPollFinishedAt));
            return out;
        }
        boolean ok = dumpIncremental(chat, since, false);
        out.put("message", ok ? "拉取完成" : "拉取被跳过(上一轮仍在执行)");
        out.put("polled", ok);
        out.put("since", StringUtils.defaultString(since));
        out.put("running", pollRunning.get());
        out.put("fetched", lastPollFetched);
        out.put("lastRunAt", fmtTime(lastPollFinishedAt));
        return out;
    }

    /** 手动触发一次分类：可选指定群/好友名，只处理该会话最早未分类的 classifyBatchSize 条；不填=全部会话。 */
    public Map<String, Object> classifyOnce(String chat) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 先同步拉一次最新(指定 chat 只拉该会话)，保证测试数据是最新的
        dumpIncremental(chat, null, false);
        int batch = Math.max(1, Math.min(100, analysisInt("classifyBatchSize", 20)));
        List<WeChatMessage> rows = store.query(null, chat, 0, batch);
        out.put("chat", StringUtils.defaultString(chat));
        if (rows.isEmpty()) {
            out.put("message", "没有未分类消息(status=0)");
            out.put("classified", 0);
            return out;
        }
        classifySequence(rows, true);
        long done = 0;
        for (WeChatMessage m : rows) {
            if (StringUtils.isNotBlank(m.getMsgCategory())) {
                done++;
            }
        }
        out.put("message", "分类完成,共处理 " + rows.size() + " 条");
        out.put("classified", done);
        out.put("total", rows.size());
        return out;
    }

    /** 轮询拉取/分类参数状态（便于 Postman 观察配置生效情况）。 */
    public Map<String, Object> pollStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", analysisBool("pollEnabled", true));
        m.put("intervalSec", analysisInt("pollIntervalSec", 10));
        m.put("backSec", analysisInt("pollBackSec", 120));
        m.put("initBackSec", analysisInt("pollInitBackSec", 259200));
        m.put("limit", analysisInt("pollLimit", 5000));
        m.put("ignoreChats", analysisStr("pollIgnoreChats", ""));
        m.put("classifyTriggerCount", analysisInt("classifyTriggerCount", 5));
        m.put("classifyMaxIdleSec", analysisInt("classifyMaxIdleSec", 180));
        m.put("classifyBatchSize", analysisInt("classifyBatchSize", 20));
        m.put("running", pollRunning.get());
        m.put("lastRunAt", lastPollFinishedAt == 0 ? null : fmtTime(lastPollFinishedAt));
        m.put("lastFetched", lastPollFetched);
        m.put("watermark", lastPollMaxSeqMs == 0 ? null : fmtTime(lastPollMaxSeqMs));
        Map<String, Object> st = store.unclassifiedStats();
        m.put("unclassifiedCnt", st == null ? 0 : st.get("cnt"));
        m.put("earliestUnclassified", st == null ? null : st.get("earliest"));
        return m;
    }

    /**
     * 增量拉取入库。
     * since 为空时：dump --since <水位-冗余回看>，即只拉新增；
     * since 非空时(如 2026-10-17 00:00:00)：dump --since <指定时间>，重新拉该时刻之后的消息，
     * 已入库的(唯一键 username+local_id 冲突)由 SQL 直接跳过，不重复处理、不覆盖已有分类。
     * 解析出的消息先过 pollIgnoreChats 名称关键词过滤，再入库(status=0)。
     *
     * @return false 表示上一轮 dump 仍在执行、本次被跳过
     */
    private boolean dumpIncremental(String chat, String since, boolean media) {
        synchronized (dumpLock) {
            if (pollRunning.get()) {
                log.info("dump 仍在执行中，本次跳过(chat={})", StringUtils.defaultString(chat));
                return false;
            }
            pollRunning.set(true);
            try {
                long nowMs = System.currentTimeMillis();
                long initBackMs = Math.max(60, analysisInt("pollInitBackSec", 259200)) * 1000L;
                long backMs = Math.max(0, analysisInt("pollBackSec", 120)) * 1000L;
                long sinceMs = (lastPollMaxSeqMs > 0 ? lastPollMaxSeqMs : nowMs - initBackMs) - backMs;
                List<String> cmd = new ArrayList<>();
                cmd.add(props.getPython());
                cmd.add(props.getScript());
                cmd.add("dump");
                cmd.add("--since");
                cmd.add(StringUtils.isNotBlank(since) ? since.trim() : fmtTime(sinceMs));
                if (StringUtils.isNotBlank(since)) {
                    log.info("手动指定 since 拉取: {}", since.trim());
                }
                if (StringUtils.isNotBlank(chat)) {
                    cmd.add("--chat");
                    cmd.add(chat);
                }
                if (media) {
                    cmd.add("--media");
                    cmd.add(props.getMediaDir());
                }
                cmd.add("--limit");
                cmd.add(String.valueOf(Math.max(1, analysisInt("pollLimit", 5000))));
                List<WeChatMessage> raw = runProcess(cmd, true);
                if (raw == null) {
                    raw = new ArrayList<>();
                }
                long maxSeq = lastPollMaxSeqMs;
                for (WeChatMessage m : raw) {
                    maxSeq = Math.max(maxSeq, m.getSortSeq());
                }
                lastPollMaxSeqMs = maxSeq;
                long kept = 0;
                if (!raw.isEmpty()) {
                    List<WeChatMessage> normal = new ArrayList<>();
                    List<WeChatMessage> black = new ArrayList<>();
                    splitIgnoreChats(raw, normal, black);
                    if (!black.isEmpty()) {
                        log.info("黑名单消息 {} 条转存 wechat_message_black(不处理)", black.size());
                        store.saveBlackAll(black);
                    }
                    if (!normal.isEmpty()) {
                        store.saveAll(normal);
                        kept = normal.size();
                    }
                }
                lastPollFetched = raw.size();
                lastPollFinishedAt = System.currentTimeMillis();
                log.info("轮询拉取完成: 拉到 {} 条, 入库 {} 条, 水位 {}", raw.size(), kept, fmtTime(lastPollMaxSeqMs));
                return true;
            } finally {
                pollRunning.set(false);
            }
        }
    }

    /**
     * pollIgnoreChats 黑名单拆分：名称包含任一关键词(不区分大小写)的消息 → black(转存
     * wechat_message_black，只落库不做任何处理)；其余 → normal(正常入库参与分类)。
     */
    private void splitIgnoreChats(List<WeChatMessage> raw, List<WeChatMessage> normal, List<WeChatMessage> black) {
        String cfg = analysisStr("pollIgnoreChats", "");
        List<String> keys = new ArrayList<>();
        if (StringUtils.isNotBlank(cfg)) {
            for (String k : cfg.split(",")) {
                if (StringUtils.isNotBlank(k)) {
                    keys.add(k.trim().toLowerCase());
                }
            }
        }
        if (keys.isEmpty()) {
            normal.addAll(raw);
            return;
        }
        for (WeChatMessage m : raw) {
            String name = StringUtils.trimToEmpty(m.getName()).toLowerCase();
            boolean hit = false;
            for (String k : keys) {
                if (name.contains(k)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                black.add(m);
            } else {
                normal.add(m);
            }
        }
    }

    private static String fmtTime(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
    }

    // ------------------------------------------------------------------
    // 实时监听：生命周期
    // ------------------------------------------------------------------
    public Map<String, Object> startListen(boolean media, boolean fresh) {
        synchronized (listenLock) {
            if (listening) {
                return statusMap("已在监听中", true);
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPython());
            cmd.add(props.getScript());
            cmd.add("listen");
            // 每次启动监听都清空上一轮未刷的缓冲，避免把旧消息当成新消息处理（历史已入库，由定时回收兜底分类）
            buffers.clear();
            bufferedKeys.clear();
            if (fresh) {
                cmd.add("--fresh");
                listenHistory.clear(); // fresh=只从此刻开始，历史上下文一并清空
            }
            if (media) {
                cmd.add("--media");
                cmd.add(props.getMediaDir());
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                listenProcess = p;
                listening = true;
                listenCount.set(0);
                listenThread = new Thread(() -> readLinesUntilStop(p), "wechat-listen");
                listenThread.setDaemon(true);
                listenThread.start();
                log.info("微信监听已启动：{}", cmd);
                return statusMap("监听已启动", true);
            } catch (Exception e) {
                listening = false;
                log.error("启动微信监听失败", e);
                return statusMap("启动失败: " + e.getMessage(), false);
            }
        }
    }

    public Map<String, Object> stopListen() {
        synchronized (listenLock) {
            if (!listening) {
                return statusMap("当前未在监听", false);
            }
            Process p = listenProcess;
            listening = false;
            if (p != null && p.isAlive()) {
                p.destroy();
            }
            listenProcess = null;
            log.info("微信监听已停止，共处理 {} 条", listenCount.get());
            return statusMap("监听已停止", false);
        }
    }

    public Map<String, Object> listenStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", listening);
        m.put("processed", listenCount.get());
        m.put("filtered", filteredCount.get());
        long pendingMsgs = 0;
        int pendingChats = 0;
        for (ChatBuffer cb : buffers.values()) {
            if (!cb.pending.isEmpty()) {
                pendingChats++;
                pendingMsgs += cb.pending.size();
            }
        }
        m.put("pendingChats", pendingChats);
        m.put("pendingMsgs", pendingMsgs);
        return m;
    }

    public boolean isListening() {
        return listening;
    }

    // ------------------------------------------------------------------
    // 定时任务入口（由 WeChatClassifyScheduler 调用）
    // ------------------------------------------------------------------
    /** 扫描所有会话缓冲：超过 aiFlushMinutes 分钟仍未凑够触发条数的，调起分类。 */
    public void flushDueBuffers() {
        int minutes = Math.max(1, analysisInt("aiFlushMinutes", 3));
        long now = System.currentTimeMillis();
        List<String> due = new ArrayList<>();
        for (Map.Entry<String, ChatBuffer> e : buffers.entrySet()) {
            ChatBuffer cb = e.getValue();
            synchronized (cb) {
                if (!cb.pending.isEmpty() && now - cb.lastFlushAt >= minutes * 60_000L) {
                    due.add(e.getKey());
                }
            }
        }
        for (String key : due) {
            classifyPool.submit(() -> flushChat(key));
        }
    }

    /**
     * 定时分类：未分类消息数量达到 classifyTriggerCount 条，
     * 或最早未分类消息等待超过 classifyMaxIdleSec 秒时触发；
     * 每轮最多处理最早未分类的 classifyBatchSize 条(status=0 → 分类回写后置为 1)。
     */
    public void recoverUnclassified() {
        if (!llm.enabled()) {
            return;
        }
        int trigger = Math.max(1, analysisInt("classifyTriggerCount", 5));
        int idleSec = Math.max(1, analysisInt("classifyMaxIdleSec", 180));
        int batch = Math.max(1, Math.min(100, analysisInt("classifyBatchSize", 20)));
        Map<String, Object> st = store.unclassifiedStats();
        if (st == null || st.isEmpty()) {
            return;
        }
        long cnt = st.get("cnt") instanceof Number ? ((Number) st.get("cnt")).longValue() : 0L;
        if (cnt <= 0) {
            return;
        }
        boolean triggerNow = cnt >= trigger;
        if (!triggerNow) {
            String earliest = st.get("earliest") == null ? null : String.valueOf(st.get("earliest"));
            if (earliest != null && !earliest.isEmpty()) {
                try {
                    String s = earliest.replace('T', ' ');
                    if (s.length() > 19) {
                        s = s.substring(0, 19);
                    }
                    long eMs = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s).getTime();
                    triggerNow = System.currentTimeMillis() - eMs >= idleSec * 1000L;
                } catch (Exception ignored) {
                }
            }
        }
        if (!triggerNow) {
            return;
        }
        List<WeChatMessage> rows = store.query(null, null, 0, batch);
        if (rows.isEmpty()) {
            return;
        }
        // 跳过冷却中的会话（最近失败过）与监听缓冲中尚未处理的
        long now = System.currentTimeMillis();
        rows.removeIf(m -> now - lastFailAt.getOrDefault(keyOf(m), 0L) < FAIL_COOLDOWN_MS
                || bufferedKeys.contains(keyOf(m) + "|" + m.getLocalId()));
        if (rows.isEmpty()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        classifySequence(rows, true);
        log.info("定时分类完成: 处理 {} 条, 耗时 {}ms", rows.size(), System.currentTimeMillis() - t0);
    }

    /** 清理超过 msgRetentionDays 天的消息。 */
    public void purgeOldMessages() {
        int days = Math.max(1, analysisInt("msgRetentionDays", 3));
        store.purgeOlderThanDays(days);
    }

    // ------------------------------------------------------------------
    // INQ/OFFER 自动录入：已分类(status=1)的INQ/OFFER → AI解析 → crm_offer
    // ------------------------------------------------------------------

    /**
     * 定时/手动录入入口：把已分类未处理(status=1)且分类命中 offerEntryCategories(默认INQ,OFFER)
     * 的消息，按 Offer 列表 AI 录入的相同逻辑解析并写入 crm_offer，同时写入来源消息关联字段
     * wechat_server_id/wechat_username/wechat_local_id。
     * 录入成功 → status=2(已分类处理未发送)；AI 解析/入库失败累计 entry_fail_count，
     * 达到 offerEntryMaxFail 次置 status=3(无需处理)，未达上限保持 status=1 下轮重试。
     * chat 非空时只处理指定会话(手动测试用)；空=全部会话，且按 offerEntryScanSec 限频。
     */
    public Map<String, Object> offerEntryOnce(String chat) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = analysisBool("offerEntryEnabled", true);
        out.put("enabled", enabled);
        out.put("chat", StringUtils.defaultString(chat));
        // 防并发重入：定时任务与手动触发共用同一执行槽
        if (!entryRunning.compareAndSet(false, true)) {
            out.put("running", true);
            out.put("message", "上一轮录入仍在执行中，本次跳过");
            return out;
        }
        int processed = 0;
        try {
            // 定时自动扫描按字典 offerEntryScanSec 限频(手动指定 chat 不受限，便于测试)
            if (StringUtils.isBlank(chat)) {
                long scanSec = Math.max(5, analysisInt("offerEntryScanSec", 30));
                if (lastEntryFinishedAt > 0 && System.currentTimeMillis() - lastEntryFinishedAt < scanSec * 1000L) {
                    out.put("message", "未到扫描间隔(offerEntryScanSec=" + scanSec + "s)，跳过");
                    return out;
                }
            }
            if (!enabled) {
                out.put("message", "录入开关已关闭(offerEntryEnabled=false)，本次为手动触发仍执行");
            }
            int batch = Math.max(1, Math.min(100, analysisInt("offerEntryBatchSize", 5)));
            List<String> cats = entryCategories();
            if (cats.isEmpty()) {
                out.put("message", "offerEntryCategories 未配置任何有效分类，请检查字典");
                return out;
            }
            // 定时自动扫描时只处理已配置供应商的会话，避免每次都选中未配置会话而被跳过、永远轮不到已映射的群：
            // 配置了 offerEntrySupplierCode 默认值 → 全部会话均可；否则只处理 offerEntrySupplierMap 中出现的会话。
            List<String> chats = null;
            if (StringUtils.isBlank(chat) && StringUtils.isBlank(analysisStr("offerEntrySupplierCode", ""))) {
                chats = entrySupplierChats();
                if (chats.isEmpty()) {
                    out.put("categories", cats);
                    out.put("scanned", 0);
                    out.put("enteredMessages", 0);
                    out.put("enteredOffers", 0);
                    out.put("alreadyEntered", 0);
                    out.put("failed", 0);
                    out.put("skipped", 0);
                    out.put("message", "未配置任何供应商(offerEntrySupplierCode/offerEntrySupplierMap)，无会话可录入，请先配置字典");
                    return out;
                }
            }
            List<WeChatMessage> rows = store.queryProcessable(StringUtils.trimToNull(chat), chats, cats, batch);
            out.put("candidateChats", chats == null ? "全部会话" : String.join(",", chats));
            processed = rows.size();
            out.put("categories", cats);
            out.put("scanned", processed);
            if (rows.isEmpty()) {
                out.put("message", "没有待录入消息(status=1 且分类命中)");
                out.put("enteredMessages", 0);
                out.put("enteredOffers", 0);
                out.put("alreadyEntered", 0);
                out.put("failed", 0);
                out.put("skipped", 0);
                return out;
            }
            int maxFail = Math.max(1, analysisInt("offerEntryMaxFail", 3));
            double ratio = analysisDouble("offerEntryProfitRatio", 2d);
            String createBy = analysisStr("offerEntryCreateBy", "wechat_auto");
            int enteredMessages = 0;
            int enteredOffers = 0;
            int failed = 0;
            int skipped = 0;
            int already = 0;
            for (WeChatMessage m : rows) {
                if (m.getMsgCategory() == null || !cats.contains(m.getMsgCategory().toUpperCase())) {
                    skipped++;
                    continue;
                }
                String[] supplier = entrySupplierFor(m);
                if (supplier == null) {
                    skipped++;
                    log.warn("未配置供应商，跳过录入: chat={} sender={} serverId={}; 请在字典 offerEntrySupplierCode/offerEntrySupplierMap 配置",
                            m.getName(), m.getSender(), m.getServerId());
                    continue;
                }
                // 幂等保护：该消息已录入过(重复触发/历史脏数据) → 不重复解析插入，直接回写 status=2 并自愈为已处理
                if (offerMapper.countByWechat(StringUtils.defaultString(m.getServerId()),
                        StringUtils.defaultString(m.getUsername()), m.getLocalId()) > 0) {
                    already++;
                    store.updateEntryResult(m.getUsername(), m.getLocalId(), 2, 0);
                    log.info("消息已录入过，跳过重复录入并回写status=2: chat={} sender={} serverId={}",
                            m.getName(), m.getSender(), m.getServerId());
                    continue;
                }
                try {
                    List<Map<String, Object>> items = deepSeekAiService.parseMaterialContent(StringUtils.defaultString(m.getContent()));
                    if (items == null || items.isEmpty()) {
                        throw new IllegalStateException("AI未识别出物料信息");
                    }
                    int inserted = 0;
                    for (Map<String, Object> item : items) {
                        if (offerMapper.insertOffer(buildCrmOffer(m, item, supplier[0], ratio, createBy)) > 0) {
                            inserted++;
                        }
                    }
                    if (inserted <= 0) {
                        throw new IllegalStateException("物料行全部入库失败");
                    }
                    enteredMessages++;
                    enteredOffers += inserted;
                    store.updateEntryResult(m.getUsername(), m.getLocalId(), 2, 0);
                    log.info("微信消息已录入crm_offer: chat={} sender={} serverId={} 物料{}行",
                            m.getName(), m.getSender(), m.getServerId(), inserted);
                } catch (Exception e) {
                    failed++;
                    int failCnt = (m.getEntryFailCount() == null ? 0 : m.getEntryFailCount()) + 1;
                    int nextStatus = failCnt >= maxFail ? 3 : 1;
                    store.updateEntryResult(m.getUsername(), m.getLocalId(), nextStatus, failCnt);
                    log.warn("微信消息AI录入失败({}/{}): chat={} sender={} serverId={} 原因={}",
                            failCnt, maxFail, m.getName(), m.getSender(), m.getServerId(), e.getMessage());
                }
            }
            out.put("enteredMessages", enteredMessages);
            out.put("enteredOffers", enteredOffers);
            out.put("alreadyEntered", already);
            out.put("failed", failed);
            out.put("skipped", skipped);
            out.put("message", "录入完成: " + enteredMessages + " 条消息录入成功(物料 " + enteredOffers + " 行), 已录入跳过 " + already + ", 失败 " + failed + ", 跳过 " + skipped);
            return out;
        } finally {
            entryRunning.set(false);
            lastEntryFinishedAt = System.currentTimeMillis();
            lastEntryProcessed = processed;
        }
    }

    /** INQ/OFFER 自动录入参数与运行状态（便于 Postman 观察）。 */
    public Map<String, Object> offerEntryStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", analysisBool("offerEntryEnabled", true));
        m.put("scanSec", analysisInt("offerEntryScanSec", 30));
        m.put("batchSize", analysisInt("offerEntryBatchSize", 5));
        m.put("categories", entryCategories());
        m.put("supplierCode", analysisStr("offerEntrySupplierCode", ""));
        m.put("supplierMap", analysisStr("offerEntrySupplierMap", ""));
        boolean allChats = StringUtils.isNotBlank(analysisStr("offerEntrySupplierCode", ""));
        m.put("candidateScope", allChats ? "all(配置了默认供应商编码,全部已分类会话均可录入)" : "mapped");
        m.put("candidateChats", allChats ? java.util.Collections.emptyList() : entrySupplierChats());
        m.put("profitRatio", analysisDouble("offerEntryProfitRatio", 2d));
        m.put("maxFail", analysisInt("offerEntryMaxFail", 3));
        m.put("createBy", analysisStr("offerEntryCreateBy", "wechat_auto"));
        m.put("running", entryRunning.get());
        m.put("lastRunAt", lastEntryFinishedAt == 0 ? null : fmtTime(lastEntryFinishedAt));
        m.put("lastProcessed", lastEntryProcessed);
        java.util.Map<String, Object> st = store.processableStats(entryCategories());
        m.put("pendingCnt", st == null ? 0 : st.get("cnt"));
        m.put("earliestPending", st == null ? null : st.get("earliest"));
        return m;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------
    private static String keyOf(WeChatMessage m) {
        return m.getUsername() == null ? "" : m.getUsername();
    }

    private static StatusMap statusMap(String msg, boolean running) {
        return new StatusMap(msg, running);
    }

    /** 轻量状态返回对象(保持与旧接口相同的 JSON：msg/running)。 */
    public static final class StatusMap extends LinkedHashMap<String, Object> {
        public StatusMap(String msg, boolean running) {
            put("message", msg);
            put("running", running);
        }
    }

    private void readLinesUntilStop(Process p) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (WeChatMessageParser.isJsonLine(line)) {
                    dispatch(WeChatMessageParser.parse(line));
                } else if (StringUtils.isNotBlank(line)) {
                    log.info("[wx_gateway.listen] {}", line);
                }
            }
        } catch (IOException e) {
            if (listening) {
                log.error("读取监听输出失败", e);
            }
        } finally {
            synchronized (listenLock) {
                listening = false;
                listenProcess = null;
            }
        }
    }

    /** 监听单条消息：先入库，再过白/黑名单，最后进缓冲等待批量 AI 分类。 */
    private void dispatch(WeChatMessage msg) {
        try {
            if (msg == null || msg.getUsername() == null) {
                return;
            }
            store.save(msg);
            if (!passAnalysis(msg)) {
                filteredCount.incrementAndGet();
                msg.setMsgCategory("OTHER");
                msg.setCategorySource("FILTER");
                store.updateCategory(msg);
                return;
            }
            String key = keyOf(msg);
            ChatBuffer cb = buffers.computeIfAbsent(key, k -> new ChatBuffer());
            int trigger = Math.max(1, Math.min(20, analysisInt("aiTriggerCount", 5)));
            boolean flushNow;
            bufferedKeys.add(msg.getUsername() + "|" + msg.getLocalId());
            synchronized (cb) {
                cb.pending.add(msg);
                flushNow = cb.pending.size() >= trigger;
            }
            if (flushNow) {
                final String k = key;
                classifyPool.submit(() -> flushChat(k));
            }
        } catch (Exception e) {
            log.error("处理微信消息失败: {}", e.getMessage(), e);
        }
    }

    /** 触发某个会话的缓冲分类：固定标记 → AI 精判 → 回写DB → 回调Handler。 */
    private void flushChat(String key) {
        ChatBuffer cb = buffers.get(key);
        if (cb == null) {
            return;
        }
        List<WeChatMessage> pending;
        synchronized (cb) {
            if (cb.pending.isEmpty()) {
                return;
            }
            pending = new ArrayList<>(cb.pending);
            cb.pending.clear();
            cb.lastFlushAt = System.currentTimeMillis();
            for (WeChatMessage pm : pending) {
                bufferedKeys.remove(keyOf(pm) + "|" + pm.getLocalId());
            }
        }
        try {
            String chatName = pending.get(0).getName();
            int ctxWin = Math.max(0, analysisInt("aiContextWindow", 20));
            Deque<WeChatMessage> hq = listenHistory.get(key);
            List<WeChatMessage> context = hq == null ? new ArrayList<>() : lastN(hq, ctxWin);
            classifyWithContext(key, context, pending, chatName);
            for (WeChatMessage m : pending) {
                if (m.getMsgCategory() == null || m.getMsgCategory().isEmpty()) {
                    log.warn("消息未分类(留待定时重试): [{}|{}] {}", m.getName(), m.getServerId(), m.getSender());
                    continue;
                }
                store.updateCategory(m);
                pushHistory(key, m);
                handler.handle(m);
                listenCount.incrementAndGet();
            }
        } catch (Exception e) {
            log.error("会话分类失败: {}", key, e);
            lastFailAt.put(key, System.currentTimeMillis());
        }
    }

    /**
     * 对一批消息按会话归并分类（用于 fetch/恢复任务）：
     * 过滤掉的会话标记 OTHER/FILTER；其余 = 缓存命中 + 固定标记 + AI 精判，结果回写库。
     *
     * @param markFiltered 是否把黑/白名单过滤掉的会话标记为 OTHER/FILTER（避免重复扫描）
     */
    private void classifySequence(List<WeChatMessage> rows, boolean markFiltered) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, List<WeChatMessage>> byChat = new LinkedHashMap<>();
        for (WeChatMessage m : rows) {
            byChat.computeIfAbsent(keyOf(m), k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String, List<WeChatMessage>> e : byChat.entrySet()) {
            String key = e.getKey();
            List<WeChatMessage> chatMsgs = e.getValue();
            chatMsgs.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
            String chatName = chatMsgs.get(0).getName();
            if (markFiltered && !passAnalysis(chatMsgs.get(0))) {
                for (WeChatMessage m : chatMsgs) {
                    if (StringUtils.isBlank(m.getMsgCategory())) {
                        m.setMsgCategory("OTHER");
                        m.setCategorySource("FILTER");
                        store.updateCategory(m);
                    }
                }
                continue;
            }
            // 拆分：已分类=上下文；未分类=待判
            List<WeChatMessage> context = new ArrayList<>();
            List<WeChatMessage> pending = new ArrayList<>();
            for (WeChatMessage m : chatMsgs) {
                if (StringUtils.isNotBlank(m.getMsgCategory())) {
                    context.add(m);
                } else {
                    pending.add(m);
                }
            }
            if (pending.isEmpty()) {
                continue;
            }
            classifyWithContext(key, context, pending, chatName);
            // 业务关联：OFFER → relatInq；TRACE → relatInq + relatOffer（回写随 updateCategory 落库)
            linkRelational(chatName, pending);
            for (WeChatMessage m : pending) {
                if (m.getMsgCategory() != null && !m.getMsgCategory().isEmpty()) {
                    store.updateCategory(m);
                    pushHistory(key, m);
                }
            }
        }
    }

    /**
     * 业务关联标记（核心链路 INQ → OFFER → TRACE）：
     * 同一会话内按时间顺序维护「最近一条 INQ / 最近一条 OFFER」：
     * <ul>
     *   <li>OFFER：relat_inq = 它回应的最近一条 INQ（id/server_id/sender/content/create_time 的 JSON）；</li>
     *   <li>TRACE：relat_inq = 对应的最近 INQ，relat_offer = 对应的最近 OFFER；</li>
     *   <li>INQ：只更新「最近 INQ」指针，自身无关联字段。</li>
     * </ul>
     * 关联基于会话内的全部已入库消息(含历史已分类)，不受本次分类批次限制。
     */
    private void linkRelational(String chatName, List<WeChatMessage> pending) {
        if (pending == null || pending.isEmpty() || StringUtils.isBlank(chatName)) {
            return;
        }
        Map<String, WeChatMessage> targets = new HashMap<>();
        for (WeChatMessage m : pending) {
            if (StringUtils.isNotBlank(m.getMsgCategory())) {
                targets.put(keyOf(m) + "|" + m.getLocalId(), m);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        // 合并两类已分类消息：
        // 1) 已落库的历史分类(status=1，库副本)；2) 本次刚分类的结果(pending，内存对象，尚未回写)。
        // 必须用 pending 对象参与匹配，否则同批 INQ/OFFER 互相关联不到(库副本此时分类为空)。
        Map<String, WeChatMessage> uniq = new HashMap<>();
        for (WeChatMessage m : store.query(null, chatName, 1, DB_QUERY_MAX)) {
            uniq.put(keyOf(m) + "|" + m.getLocalId(), m);
        }
        for (WeChatMessage m : pending) {
            if (StringUtils.isNotBlank(m.getMsgCategory())) {
                uniq.put(keyOf(m) + "|" + m.getLocalId(), m);
            }
        }
        if (uniq.isEmpty()) {
            return;
        }
        List<WeChatMessage> all = new ArrayList<>(uniq.values());
        all.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
        WeChatMessage lastInq = null;
        WeChatMessage lastOffer = null;
        for (WeChatMessage m : all) {
            String cat = m.getMsgCategory() == null ? "" : m.getMsgCategory().trim();
            WeChatMessage t = targets.get(keyOf(m) + "|" + m.getLocalId());
            if ("OFFER".equals(cat)) {
                if (t != null && lastInq != null) {
                    t.setRelatInq(toRelJson(lastInq));
                }
            } else if ("TRACE".equals(cat)) {
                if (t != null) {
                    if (lastInq != null) {
                        t.setRelatInq(toRelJson(lastInq));
                    }
                    if (lastOffer != null) {
                        t.setRelatOffer(toRelJson(lastOffer));
                    }
                }
            }
            if ("INQ".equals(cat)) {
                lastInq = m;
            } else if ("OFFER".equals(cat)) {
                lastOffer = m;
            }
        }
    }

    /** 把被关联消息的关键信息序列化为 JSON（content 截前 800 字，防字段过大）。 */
    private String toRelJson(WeChatMessage m) {
        try {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("server_id", m.getServerId());
            o.put("sender", m.getSender());
            String c = m.getContent();
            if (c != null && c.length() > 800) {
                c = c.substring(0, 800);
            }
            o.put("content", c);
            o.put("create_time", m.getCreateTime());
            return jackson.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("构造关联 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 单会话分类管线：缓存命中 → 固定标记 → AI 精判。返回时 pending 内已带分类。 */
    private void classifyWithContext(String chatKey, List<WeChatMessage> context, List<WeChatMessage> pending, String chatName) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        // 1) 缓存命中
        List<WeChatMessage> blank = new ArrayList<>();
        for (WeChatMessage m : pending) {
            String cached = llm.cached(m);
            if (cached != null) {
                m.setMsgCategory(cached);
                m.setCategorySource("CACHE");
            } else {
                blank.add(m);
            }
        }
        // 2) 固定标记（配置了 offerFixedSenders/Groups 才生效；未配置则为纯 AI）
        List<WeChatMessage> seq = new ArrayList<>(context);
        for (WeChatMessage m : blank) {
            if (m.getMsgCategory() == null || m.getMsgCategory().isEmpty()) {
                seq.add(m);
            }
        }
        FixedOfferMarker.markFixed(seq);
        // 3) AI 精判（剩余未分类）
        List<WeChatMessage> aiPending = new ArrayList<>();
        List<WeChatMessage> ctxForAi = new ArrayList<>();
        for (WeChatMessage m : seq) {
            if (m.getMsgCategory() != null && !m.getMsgCategory().isEmpty()) {
                ctxForAi.add(m);
            } else {
                aiPending.add(m);
            }
        }
        if (!aiPending.isEmpty()) {
            llm.classify(ctxForAi, aiPending, chatName);
            boolean anyFail = false;
            for (WeChatMessage m : aiPending) {
                if (m.getMsgCategory() == null || m.getMsgCategory().isEmpty()) {
                    anyFail = true;
                    break;
                }
            }
            if (anyFail) {
                lastFailAt.put(chatKey == null ? "" : chatKey, System.currentTimeMillis());
            }
        }
    }

    /** 输出过滤：黑/白名单 + 每会话 limit 截断（保留最新）。 */
    private List<WeChatMessage> applyOutputFilters(List<WeChatMessage> rows, Integer limitPerChat) {
        List<WeChatMessage> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        Map<String, List<WeChatMessage>> byChat = new LinkedHashMap<>();
        for (WeChatMessage m : rows) {
            if (!passAnalysis(m)) {
                continue;
            }
            byChat.computeIfAbsent(keyOf(m), k -> new ArrayList<>()).add(m);
        }
        for (List<WeChatMessage> chatMsgs : byChat.values()) {
            chatMsgs.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
            if (limitPerChat != null && limitPerChat > 0 && chatMsgs.size() > limitPerChat) {
                chatMsgs = new ArrayList<>(chatMsgs.subList(chatMsgs.size() - limitPerChat, chatMsgs.size()));
            }
            out.addAll(chatMsgs);
        }
        return out;
    }

    private void pushHistory(String key, WeChatMessage m) {
        Deque<WeChatMessage> dq = listenHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
        dq.addLast(m);
        while (dq.size() > Math.max(1, analysisInt("aiContextWindow", 20))) {
            dq.removeFirst();
        }
    }

    private static List<WeChatMessage> lastN(Deque<WeChatMessage> dq, int n) {
        List<WeChatMessage> all = new ArrayList<>(dq);
        if (n <= 0) {
            return Collections.emptyList();
        }
        if (all.size() <= n) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - n, all.size()));
    }

    private List<WeChatMessage> runProcess(List<String> cmd, boolean collect) {
        List<WeChatMessage> out = collect ? new ArrayList<>() : null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (WeChatMessageParser.isJsonLine(line)) {
                        if (out != null) {
                            out.add(WeChatMessageParser.parse(line));
                        }
                    } else if (StringUtils.isNotBlank(line)) {
                        log.info("[wx_gateway] {}", line);
                    }
                }
            }
            int code = p.waitFor();
            log.info("wx_gateway 进程退出码: {}", code);
        } catch (Exception e) {
            log.error("wx_gateway 执行失败", e);
            throw new IllegalStateException("wx_gateway 执行失败: " + e.getMessage(), e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 批量发送：拟人节奏 + 安全参数全部来自配置字典
    // ------------------------------------------------------------------
    /** 发送参数数据字典类型（sys_dict_type.dict_type） */
    private static final String SEND_DICT_TYPE = "wechat_send_config";

    private String sendCfg(String key, String def) {
        List<SysDictData> list = DictUtils.getDictCache(SEND_DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (key.equals(d.getDictLabel())) {
                    String v = d.getDictValue();
                    if (v != null && !v.trim().isEmpty()) {
                        return v.trim();
                    }
                }
            }
        }
        return def;
    }

    private double sendDouble(String key, double def) {
        try {
            return Double.parseDouble(sendCfg(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int sendInt(String key, int def) {
        try {
            return Integer.parseInt(sendCfg(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean sendBool(String key, boolean def) {
        String v = sendCfg(key, String.valueOf(def));
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public List<WeChatSendResult> batchSend(List<String> names, String template) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("names", names);
        if (template != null && !template.trim().isEmpty()) {
            cfg.put("template", template);
        }
        // 拟人节奏 + 安全参数：优先读 RuoYi 数据字典(wechat_send_config)，
        // 字典缺失该项时回退到 wechat.send.* 的 @Value 默认值。
        cfg.put("rhythm", sendCfg("rhythm", props.getSendRhythm()));
        cfg.put("nap", Arrays.asList(sendDouble("napMin", props.getSendNapMin()),
                                     sendDouble("napMax", props.getSendNapMax())));
        cfg.put("interval", Arrays.asList(sendDouble("intervalMin", props.getSendIntervalMin()),
                                          sendDouble("intervalMax", props.getSendIntervalMax())));
        cfg.put("max_batch", sendInt("maxBatch", props.getSendMaxBatch()));
        cfg.put("verify", sendBool("verify", props.isSendVerify()));
        return runBatchSend(cfg);
    }

    private List<WeChatSendResult> runBatchSend(Map<String, Object> cfg) {
        List<WeChatSendResult> out = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(props.getPython(), props.getScript(), "batch_send");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
                w.write(jackson.writeValueAsString(cfg));
                w.newLine();
                w.flush();
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (WeChatMessageParser.isJsonLine(line)) {
                        try {
                            out.add(jackson.readValue(line, WeChatSendResult.class));
                        } catch (IOException e) {
                            log.warn("解析发送结果失败: {}", line, e);
                        }
                    } else if (StringUtils.isNotBlank(line)) {
                        log.info("[wx_gateway.batch_send] {}", line);
                    }
                }
            }
            int code = p.waitFor();
            log.info("wx_gateway batch_send 退出码: {}", code);
        } catch (Exception e) {
            log.error("批量发送执行失败", e);
            throw new IllegalStateException("批量发送失败: " + e.getMessage(), e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 分析配置
    // ------------------------------------------------------------------
    /** 读取字典里的名单（dict_label=whiteNames/blackNames，逗号拼接） */
    private Set<String> analysisNames(String label) {
        Set<String> names = new HashSet<>();
        List<SysDictData> list = DictUtils.getDictCache(ANALYSIS_DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    for (String n : d.getDictValue().split(",")) {
                        if (StringUtils.isNotBlank(n)) {
                            names.add(n.trim());
                        }
                    }
                }
            }
        }
        return names;
    }

    /** 读取字典布尔值（dict_label=参数名，dict_value=true/1/false/0），失败返回默认值。 */
    private boolean analysisBool(String label, boolean def) {
        String v = analysisStr(label, null);
        if (v == null) {
            return def;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    /** 读取字典字符串（dict_label=参数名），缺失返回默认值。 */
    private String analysisStr(String label, String def) {
        List<SysDictData> list = DictUtils.getDictCache(ANALYSIS_DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    return d.getDictValue().trim();
                }
            }
        }
        return def;
    }

    /** 读取字典整数值（dict_label=参数名，dict_value=数值），失败返回默认值。 */
    private int analysisInt(String label, int def) {
        try {
            List<SysDictData> list = DictUtils.getDictCache(ANALYSIS_DICT_TYPE);
            if (list != null) {
                for (SysDictData d : list) {
                    if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                        return Integer.parseInt(d.getDictValue().trim());
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return def;
    }

    /** 读取字典浮点值（dict_label=参数名，dict_value=数值），失败返回默认值。 */
    private double analysisDouble(String label, double def) {
        try {
            String v = analysisStr(label, null);
            if (v != null) {
                return Double.parseDouble(v);
            }
        } catch (NumberFormatException ignored) {
        }
        return def;
    }

    /** 参与录入的消息分类列表：字典 offerEntryCategories 逗号分隔(默认INQ,OFFER)，去空去重。 */
    private List<String> entryCategories() {
        List<String> cats = new ArrayList<>();
        String raw = analysisStr("offerEntryCategories", "INQ,OFFER");
        if (raw != null) {
            for (String c : raw.split(",")) {
                String t = c.trim().toUpperCase();
                if (!t.isEmpty() && !cats.contains(t)) {
                    cats.add(t);
                }
            }
        }
        return cats;
    }

    /**
     * 解析消息所属供应商编码：优先匹配字典 offerEntrySupplierMap(会话名=供应商编码,英文逗号分隔多组)，
     * 未命中回退默认 offerEntrySupplierCode；都未配置返回 null(该消息跳过录入、保持 status=1 等待配置)。
     */
    private String[] entrySupplierFor(WeChatMessage m) {
        String mapCfg = analysisStr("offerEntrySupplierMap", "");
        String name = StringUtils.trimToEmpty(m.getName());
        if (StringUtils.isNotBlank(mapCfg)) {
            for (String pair : mapCfg.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).trim().equals(name)) {
                    String code = pair.substring(eq + 1).trim();
                    return code.isEmpty() ? null : new String[]{code};
                }
            }
        }
        String code = analysisStr("offerEntrySupplierCode", "");
        return code.isEmpty() ? null : new String[]{code.trim()};
    }

    /** 已配置供应商映射的会话名列表（offerEntrySupplierMap 中所有 "会话名=编码" 的会话名，去空去重）。 */
    private List<String> entrySupplierChats() {
        List<String> chats = new ArrayList<>();
        String mapCfg = analysisStr("offerEntrySupplierMap", "");
        if (StringUtils.isNotBlank(mapCfg)) {
            for (String pair : mapCfg.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String nm = pair.substring(0, eq).trim();
                    if (!nm.isEmpty() && !chats.contains(nm)) {
                        chats.add(nm);
                    }
                }
            }
        }
        return chats;
    }

    /**
     * 按 Offer 列表 AI 录入相同口径构造 crm_offer：
     * OFFER 报价价=成本价×(1+利润率/100)向上取整到5的倍数；supplier_name 统一存供应商编码(与现有约定一致)；
     * 来源字段写 wechat_server_id/wechat_username/wechat_local_id 回查聊天记录。
     */
    private CrmOffer buildCrmOffer(WeChatMessage m, Map<String, Object> item, String supplierCode,
                                   double ratio, String createBy) {
        CrmOffer offer = new CrmOffer();
        offer.setProductCode(str(item.get("partNumber")));
        offer.setProductBrand(str(item.get("brand")));
        offer.setProductDetailCode(str(item.get("model")));
        String aiDetail = str(item.get("productDetail"));
        offer.setProductDetail(aiDetail.isEmpty() ? str(item.get("spec")) : aiDetail);
        offer.setSpec(str(item.get("spec")));
        offer.setDc(str(item.get("dc")));
        offer.setCoo(str(item.get("coo")));
        offer.setDeliveryTime(str(item.get("deliveryTime")));
        offer.setProductType(str(item.get("productType")));
        Object qty = item.get("quantity");
        offer.setQuantity(qty instanceof Number ? ((Number) qty).intValue() : null);
        Object price = item.get("price");
        Double cost = price instanceof Number ? ((Number) price).doubleValue() : null;
        offer.setPriceCost(cost);
        boolean isOffer = "OFFER".equalsIgnoreCase(m.getMsgCategory());
        if (isOffer && cost != null) {
            offer.setPriceOffer(ceilTo5(cost * (1d + ratio / 100d)));
        }
        offer.setPriceUnit("USD");
        offer.setStockDate(new Date());
        offer.setSheetName(buildSheetName(m));
        offer.setSupplierCode(supplierCode);
        offer.setSupplierName(supplierCode);
        offer.setInqOfferType(isOffer ? "Offer" : "Inq");
        offer.setStatus(1);
        offer.setCreateBy(createBy);
        offer.setUpdateBy(createBy);
        offer.setWechatServerId(StringUtils.defaultString(m.getServerId()));
        offer.setWechatUsername(StringUtils.defaultString(m.getUsername()));
        offer.setWechatLocalId(m.getLocalId());
        String remark = "微信自动录入|群:" + StringUtils.trimToEmpty(m.getName())
                + "|发言人:" + StringUtils.trimToEmpty(m.getSender())
                + "|消息时间:" + StringUtils.trimToEmpty(m.getCreateTime())
                + "|server_id:" + StringUtils.defaultString(m.getServerId());
        offer.setRemark(remark.length() > 500 ? remark.substring(0, 500) : remark);
        return offer;
    }

    /** 来源表名：WX_时间戳_会话名（会话名非法字符替换为下划线，超长截断）。 */
    private String buildSheetName(WeChatMessage m) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String chat = StringUtils.trimToEmpty(m.getName()).replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (chat.length() > 60) {
            chat = chat.substring(0, 60);
        }
        String sn = "WX_" + ts + "_" + chat;
        return sn.length() > 500 ? sn.substring(0, 500) : sn;
    }

    /** 报价向上取整到5的倍数（与 Offer 列表 AI 录入一致：以0或5结尾，保留整数）。 */
    private Double ceilTo5(double v) {
        return Math.ceil(v / 5.0d) * 5.0d;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 第一层过滤器：白名单非空=只放行白名单；否则只过滤黑名单，其余放行。 */
    private boolean passAnalysis(WeChatMessage m) {
        if (m == null) {
            return false;
        }
        Set<String> white = analysisNames("whiteNames");
        Set<String> black = analysisNames("blackNames");
        String name = StringUtils.trimToEmpty(m.getName());
        if (!white.isEmpty()) {
            return white.contains(name);
        }
        return !black.contains(name);
    }
}
