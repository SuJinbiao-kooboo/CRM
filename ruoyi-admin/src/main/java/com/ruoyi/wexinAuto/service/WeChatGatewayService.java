package com.ruoyi.wexinAuto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.CrmOfferPriceMode;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ai.AiChannelService;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.wexinAuto.analysis.WeChatLlmClassifier;
import com.ruoyi.wexinAuto.analysis.WeChatTextUtils;
import com.ruoyi.wexinAuto.config.WeChatAutomationConfig;
import com.ruoyi.wexinAuto.config.WeChatGatewayProperties;
import com.ruoyi.wexinAuto.model.PartyMatch;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatSendItem;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.msg.WeChatMessageHandler;
import com.ruoyi.wexinAuto.msg.WeChatMessageParser;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysDictDataService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 聊天记录页面允许查看的文档文件类型，不开放音频、视频。 */
    private static final Set<String> DOCUMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "xls", "xlsx", "csv", "txt", "md", "json", "xml", "log",
            "doc", "docx", "ppt", "pptx", "jpg", "jpeg", "png", "gif", "webp", "bmp"));

    /** 卡片、链接占位文本和裸链接没有可分类正文，不送模型。 */
    private static final Pattern NON_TEXT_ONLY = Pattern.compile(
            "^(?:【(?:卡片|图片|文件|链接|表情|语音|视频)】|\\[(?:卡片|图片|文件|链接|表情|语音|视频)\\]|(?:https?://|www\\.)\\S+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"));
    private static final Pattern FILE_EXT_PATTERN = Pattern.compile(
            "<fileext(?:\\s[^>]*)?>([\\s\\S]*?)</fileext>", Pattern.CASE_INSENSITIVE);

    private final WeChatGatewayProperties props;
    private final WeChatMessageHandler handler;
    private final WeChatLlmClassifier llm;
    private final WeChatMessageStore store;
    private final CrmOfferMapper offerMapper;
    private final ICrmOfferService offerService;
    private final AiChannelService aiChannelService;
    private final WeChatAutomationConfig config;
    private final WeChatPartyService partyService;

    @Autowired
    private ISysDictDataService dictDataService;

    private final Object listenLock = new Object();
    private Process listenProcess;
    private Thread listenThread;
    private volatile boolean listening = false;
    private final AtomicLong listenCount = new AtomicLong(0);
    private final AtomicLong filteredCount = new AtomicLong(0);
    /** 排除会话配置指纹：首次加载或名单变化时纠正一次主表存量。 */
    private volatile String excludedChatsSignature;

    /** 监听路径：每个会话最近 N 条已分类消息（供 AI 上下文使用） */
    private final Map<String, Deque<WeChatMessage>> listenHistory = new ConcurrentHashMap<>();

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
    /** 最近一次轮询失败原因；空=最近一次成功 */
    private volatile String lastPollError = "";

    /** 正在推送 TRACE 预警的消息键，防止分类回写与定时补扫并发重复推送。 */
    private final Set<String> traceAlerting = ConcurrentHashMap.newKeySet();
    /** 同一会话的 TRACE 预警互斥，保证冷却检查与发送之间不会被并发绕过。 */
    private final Set<String> traceAlertChatLocks = ConcurrentHashMap.newKeySet();

    // ---------- INQ/OFFER 自动录入状态 ----------
    /** 录入任务互斥锁（定时扫描与手动触发共用，防止并发重入） */
    private final AtomicBoolean entryRunning = new AtomicBoolean(false);
    /** 上次录入完成时间(ms)，用于按字典 offerEntryScanSec 控制扫描间隔 */
    private volatile long lastEntryFinishedAt = 0L;
    /** 最近一轮录入处理的原始条数 */
    private volatile int lastEntryProcessed = 0;

    private final ObjectMapper jackson = new ObjectMapper();

    /** whoami 结果短时缓存，避免每轮发送扫描都解密一次微信数据库。 */
    private volatile String currentWxName = "";
    private volatile long whoamiAt = 0L;

    @Autowired
    public WeChatGatewayService(WeChatGatewayProperties props, WeChatMessageHandler handler,
                                WeChatLlmClassifier llm, WeChatMessageStore store,
                                CrmOfferMapper offerMapper, ICrmOfferService offerService,
                                AiChannelService aiChannelService, WeChatAutomationConfig config,
                                WeChatPartyService partyService) {
        this.props = props;
        this.handler = handler;
        this.llm = llm;
        this.store = store;
        this.offerMapper = offerMapper;
        this.offerService = offerService;
        this.aiChannelService = aiChannelService;
        this.config = config;
        this.partyService = partyService;
    }

    // ------------------------------------------------------------------
    // 主动拉取：按时间(到秒,闭区间) + 可选群/好友名
    // ------------------------------------------------------------------
    public List<WeChatMessage> fetch(String since, String chat, boolean media, Integer limit, Integer status) {
        reconcileBlacklistedMessages();
        // 先增量同步最新消息(继承轮询水位；指定 chat 时只同步该会话)，随后从库里查询
        dumpIncremental(chat, null, media);
        // 从库里读(带历史分类结果)。注意：查询接口不触发 AI 分类——
        // 分类由独立定时任务(recoverUnclassified)或手动 POST /wechat/classify/run 完成，
        // 未分类消息按 status=0 原样返回。
        List<WeChatMessage> rows = store.query(since, chat, status, DB_QUERY_MAX);
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
        reconcileBlacklistedMessages();
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
        out.put("message", ok ? "拉取完成" : ("拉取失败: " + StringUtils.defaultIfBlank(lastPollError, "上一轮仍在执行")));
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
        if (!config.bool("classifyEnabled", false)) {
            out.put("classified", 0);
            out.put("message", "自动分类已关闭(classifyEnabled=false)，仅允许按1～20条明确消息键排障");
            return out;
        }
        // 先同步拉一次最新(指定 chat 只拉该会话)，保证测试数据是最新的
        dumpIncremental(chat, null, false);
        int batch = Math.max(1, Math.min(100, analysisInt("classifyBatchSize", 20)));
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        int cooldown = Math.max(0, config.integer("classifyRetryCooldownSec", 300));
        List<WeChatMessage> rows = store.queryClassifyCandidates(batch, cooldown, timeout);
        if (StringUtils.isNotBlank(chat)) {
            rows.removeIf(m -> !StringUtils.equals(chat, m.getName()));
        }
        out.put("chat", StringUtils.defaultString(chat));
        if (rows.isEmpty()) {
            out.put("message", "没有待分类消息");
            out.put("classified", 0);
            return out;
        }
        processClassification(rows, false, false);
        long done = 0;
        for (WeChatMessage m : rows) {
            if ("SUCCESS".equalsIgnoreCase(m.getClassifyStatus())) {
                done++;
            }
        }
        out.put("message", "分类完成,共处理 " + rows.size() + " 条");
        out.put("classified", done);
        out.put("total", rows.size());
        return out;
    }

    /** 按明确消息键分类，最多20条；关闭总开关时仍允许该排障入口。 */
    public Map<String, Object> classifyKeys(List<String> keys, boolean dryRun) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty() || keys.size() > 20) {
            throw new IllegalArgumentException("明确消息键必须为1～20条");
        }
        List<WeChatMessage> rows = store.queryByKeys(keys);
        if (!dryRun) {
            rows.removeIf(m -> "SUCCESS".equalsIgnoreCase(StringUtils.trimToEmpty(m.getEntryStatus()))
                    || "SUCCESS_WARN".equalsIgnoreCase(StringUtils.trimToEmpty(m.getEntryStatus()))
                    || "GENERATED".equalsIgnoreCase(StringUtils.trimToEmpty(m.getTaskStatus())));
        }
        out.put("requested", keys.size());
        out.put("matched", rows.size());
        out.put("dryRun", dryRun);
        if (rows.isEmpty()) {
            out.put("classified", 0);
            out.put("message", dryRun ? "未找到可回放消息" : "未找到可分类消息，已录入或已生成任务的消息不允许覆盖");
            return out;
        }
        processClassification(rows, dryRun, true);
        List<Map<String, Object>> results = new ArrayList<>();
        int done = 0;
        int failed = 0;
        int skipped = 0;
        for (WeChatMessage m : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", m.getUsername() + ":" + m.getLocalId());
            item.put("category", m.getMsgCategory());
            item.put("reason", m.getCategoryReason());
            results.add(item);
            if ("FAILED".equalsIgnoreCase(m.getClassifyStatus())) {
                failed++;
            } else if ("SKIPPED".equalsIgnoreCase(m.getClassifyStatus())) {
                skipped++;
            } else if ("SUCCESS".equalsIgnoreCase(m.getClassifyStatus())
                    || (dryRun && StringUtils.isNotBlank(m.getMsgCategory()))) {
                done++;
            }
        }
        out.put("classified", done);
        out.put("failed", failed);
        out.put("skipped", skipped);
        out.put("results", results);
        if (dryRun) {
            out.put("message", "只读回放完成，未写入数据库");
        } else {
            String message = "分类完成：成功 " + done + " 条";
            if (failed > 0) {
                message += "，失败 " + failed + " 条";
            }
            if (skipped > 0) {
                message += "，跳过 " + skipped + " 条";
            }
            if (done == 0) {
                message += "。请查看“分类状态”旁的原因提示或本条详情中的“分类说明”";
            }
            out.put("message", message);
        }
        return out;
    }

    private void processClassification(List<WeChatMessage> rows, boolean dryRun, boolean force) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        int cooldown = dryRun ? 0 : Math.max(0, config.integer("classifyRetryCooldownSec", 300));
        Map<String, List<WeChatMessage>> byChat = new LinkedHashMap<>();
        for (WeChatMessage m : rows) {
            if (StringUtils.equalsIgnoreCase("AI_AUTO", m.getCategorySource())) {
                String reason = "AI_AUTO消息跳过分类";
                if (!dryRun) {
                    store.markClassifySkipped(m.getUsername(), m.getLocalId(), reason);
                }
                m.setClassifyStatus("SKIPPED");
                m.setCategoryReason(reason);
                continue;
            }
            if (!isTextMessage(m)) {
                String reason = classifySkipReason(m);
                if (!dryRun) {
                    store.markClassifySkipped(m.getUsername(), m.getLocalId(), reason);
                }
                m.setClassifyStatus("SKIPPED");
                m.setCategoryReason(reason);
                continue;
            }
            if (!passAnalysis(m)) {
                if (!dryRun) {
                    store.updateFiltered(m, "分类黑名单：会话不参与AI分类");
                }
                continue;
            }
            if (!dryRun && !store.markClassifyProcessing(m.getUsername(), m.getLocalId(), timeout, cooldown, force)) {
                continue;
            }
            if (!dryRun) {
                // 强制重分类时清空内存中的旧结果，避免模型失败后把旧分类误写为本次成功。
                m.setMsgCategory(null);
                m.setCategorySource(null);
                m.setCategoryReason(null);
                m.setRelatInq(null);
                m.setRelatOffer(null);
            }
            byChat.computeIfAbsent(keyOf(m), k -> new ArrayList<>()).add(m);
        }
        for (List<WeChatMessage> chatRows : byChat.values()) {
            chatRows.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
            String chatName = chatRows.get(0).getName();
            int ctxWin = Math.max(20, Math.min(100, config.integer("aiContextWindow", 20)));
            List<WeChatMessage> context = recentContext(keyOf(chatRows.get(0)), chatRows, ctxWin);
            try {
                classifyWithContext(keyOf(chatRows.get(0)), context, chatRows, chatName, !force);
            } catch (Exception e) {
                log.warn("分类批次异常: chat={} error={}", chatName, e.getMessage());
            }
            for (WeChatMessage m : chatRows) {
                if (StringUtils.isNotBlank(m.getMsgCategory())) {
                    if (!dryRun) {
                        store.updateCategory(m);
                        handler.handle(m);
                        maybeAlertTrace(m);
                    } else {
                        m.setClassifyStatus("SUCCESS");
                    }
                } else if (!dryRun) {
                    String reason = "模型未返回可识别的分类结果";
                    store.markClassifyFailed(m.getUsername(), m.getLocalId(), reason);
                    m.setClassifyStatus("FAILED");
                    m.setCategoryReason(reason);
                }
            }
        }
    }

    private boolean isTextMessage(WeChatMessage message) {
        if (message == null) {
            return false;
        }
        String type = StringUtils.trimToEmpty(message.getType());
        if (type.isEmpty() || "文本".equals(type) || "TEXT".equalsIgnoreCase(type)
                || "文件/链接/卡片".equals(type)) {
            return hasClassifiableText(message.getContent());
        }
        return false;
    }

    /** 引用消息在微信原始类型中仍是 appmsg，但正文可能是正常聊天文字，不能只按 type 跳过。 */
    private boolean hasClassifiableText(String content) {
        String text = StringUtils.trimToEmpty(content);
        if (text.isEmpty() || text.startsWith("<")) {
            return false;
        }
        return !NON_TEXT_ONLY.matcher(text).matches();
    }

    private String classifySkipReason(WeChatMessage message) {
        String type = StringUtils.defaultIfBlank(message.getType(), "空");
        if (StringUtils.isBlank(message.getContent())) {
            return "消息正文为空，不调用模型（消息类型=" + type + "）";
        }
        if ("文件/链接/卡片".equals(type)) {
            return "卡片/链接/文件消息没有可分类正文，不调用模型（消息类型=" + type + "）";
        }
        return "非文本或系统消息，不调用模型（消息类型=" + type + "）";
    }

    /** 清空AI分类缓存：提示词/语义规则调整后，重跑历史消息前使用。 */
    public Map<String, Object> clearClassifyCache() {
        llm.clearCache();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cacheCleared", true);
        out.put("message", "分类缓存已清空");
        return out;
    }

    /** 更新自动分类总开关；写入 wechat_analysis_config.classifyEnabled 并立即刷新字典缓存。 */
    public Map<String, Object> updateClassifyEnabled(boolean enabled) {
        SysDictData query = new SysDictData();
        query.setDictType(ANALYSIS_DICT_TYPE);
        List<SysDictData> rows = dictDataService.selectDictDataList(query);
        SysDictData target = null;
        if (rows != null) {
            for (SysDictData row : rows) {
                if ("classifyEnabled".equals(row.getDictLabel())) {
                    target = row;
                    break;
                }
            }
        }
        String value = enabled ? "true" : "false";
        if (target == null) {
            target = new SysDictData();
            target.setDictType(ANALYSIS_DICT_TYPE);
            target.setDictLabel("classifyEnabled");
            target.setDictValue(value);
            target.setDictSort(129L);
            target.setStatus("0");
            target.setCreateBy(SecurityUtils.getUsername());
            dictDataService.insertDictData(target);
        } else {
            target.setDictValue(value);
            target.setUpdateBy(SecurityUtils.getUsername());
            dictDataService.updateDictData(target);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("autoEnabled", enabled);
        out.put("message", enabled ? "自动分类已开启" : "自动分类已关闭，仅保留手动分类");
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
        m.put("blackNames", analysisStr("blackNames", ""));
        m.put("whiteNames", analysisStr("whiteNames", ""));
        m.put("blackTable", "wechat_message_black");
        m.put("classifyTriggerCount", analysisInt("classifyTriggerCount", 5));
        m.put("classifyMaxIdleSec", analysisInt("classifyMaxIdleSec", 180));
        m.put("classifyBatchSize", analysisInt("classifyBatchSize", 20));
        m.put("classifyEnabled", config.bool("classifyEnabled", false));
        m.put("running", pollRunning.get());
        m.put("lastRunAt", lastPollFinishedAt == 0 ? null : fmtTime(lastPollFinishedAt));
        m.put("lastFetched", lastPollFetched);
        m.put("lastError", lastPollError);
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
     * 解析出的消息先过 pollIgnoreChats / blackNames 名称关键词过滤，命中写黑表，其余入库(status=0)。
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
                lastPollError = "";
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
                    splitExcludedChats(raw, normal, black);
                    if (!black.isEmpty()) {
                        log.info("排除会话消息 {} 条转存 wechat_message_black(不处理)", black.size());
                        store.saveBlackAll(black);
                    }
                    if (!normal.isEmpty()) {
                        kept = store.saveAll(normal);
                    }
                }
                lastPollFetched = raw.size();
                log.info("轮询拉取完成: 拉到 {} 条, 入库 {} 条, 水位 {}", raw.size(), kept, fmtTime(lastPollMaxSeqMs));
                return true;
            } catch (Exception e) {
                lastPollError = e.getMessage() == null ? e.toString() : e.getMessage();
                log.error("轮询拉取失败: {}", lastPollError, e);
                return false;
            } finally {
                lastPollFinishedAt = System.currentTimeMillis();
                pollRunning.set(false);
            }
        }
    }

    /**
     * pollIgnoreChats / blackNames 黑名单拆分：名称包含任一关键词(不区分大小写)的消息 → black(转存
     * wechat_message_black，只落库不做任何处理)；其余 → normal(正常入库参与分类)。
     */
    private void splitExcludedChats(List<WeChatMessage> raw, List<WeChatMessage> normal, List<WeChatMessage> black) {
        List<String> keys = excludedChatKeys();
        if (keys.isEmpty()) {
            normal.addAll(raw);
            return;
        }
        for (WeChatMessage m : raw) {
            if (matchesExcludedChat(m, keys)) {
                black.add(m);
            } else {
                normal.add(m);
            }
        }
    }

    private List<String> excludedChatKeys() {
        Set<String> keys = new LinkedHashSet<>();
        addChatKeys(keys, analysisStr("pollIgnoreChats", ""));
        addChatKeys(keys, analysisStr("blackNames", ""));
        return new ArrayList<>(keys);
    }

    private void addChatKeys(Set<String> keys, String cfg) {
        if (StringUtils.isBlank(cfg)) {
            return;
        }
        for (String key : cfg.split(",")) {
            if (StringUtils.isNotBlank(key)) {
                keys.add(key.trim().toLowerCase());
            }
        }
    }

    private boolean matchesExcludedChat(WeChatMessage m, List<String> keys) {
        String name = StringUtils.trimToEmpty(m == null ? null : m.getName()).toLowerCase();
        for (String key : keys) {
            if (!key.isEmpty() && name.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 名单变化时把主表中已命中的历史消息一次性迁到黑表，防止页面继续显示。 */
    private int reconcileBlacklistedMessages() {
        List<String> keys = excludedChatKeys();
        String signature = String.join("\u0001", keys);
        if (signature.equals(excludedChatsSignature)) {
            return 0;
        }
        try {
            int moved = store.moveBlacklistedMessages(keys);
            excludedChatsSignature = signature;
            if (moved > 0) {
                log.info("排除会话存量纠偏完成: 从 wechat_message 迁移 {} 条到 wechat_message_black", moved);
            }
            return moved;
        } catch (Exception e) {
            log.error("排除会话存量纠偏失败，将在下一轮重试", e);
            return 0;
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
        return m;
    }

    public boolean isListening() {
        return listening;
    }

    // ------------------------------------------------------------------
    // 定时任务入口（由 WeChatClassifyScheduler 调用）
    // ------------------------------------------------------------------
    /**
     * 定时分类：未分类消息数量达到 classifyTriggerCount 条，
     * 或最早未分类消息等待超过 classifyMaxIdleSec 秒时触发；
     * 每轮最多处理最早未分类的 classifyBatchSize 条(status=0 → 分类回写后置为 1)。
     */
    public void recoverUnclassified() {
        if (!config.bool("classifyEnabled", false) || !llm.enabled()) {
            return;
        }
        int trigger = Math.max(1, analysisInt("classifyTriggerCount", 5));
        int idleSec = Math.max(1, analysisInt("classifyMaxIdleSec", 180));
        int batch = Math.max(1, Math.min(100, analysisInt("classifyBatchSize", 20)));
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        int cooldown = Math.max(0, config.integer("classifyRetryCooldownSec", 300));
        List<WeChatMessage> rows = store.queryClassifyCandidates(Math.max(batch, Math.min(100, trigger)),
                cooldown, timeout);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WeChatMessage m : rows) {
            counts.merge(keyOf(m), 1, Integer::sum);
        }
        boolean triggerNow = false;
        long now = System.currentTimeMillis();
        for (WeChatMessage m : rows) {
            if (counts.getOrDefault(keyOf(m), 0) >= trigger) {
                triggerNow = true;
                break;
            }
            try {
                String value = StringUtils.trimToEmpty(m.getCreateTime()).replace('T', ' ');
                if (value.length() > 19) {
                    value = value.substring(0, 19);
                }
                long created = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value).getTime();
                if (now - created >= idleSec * 1000L) {
                    triggerNow = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (!triggerNow) {
            return;
        }
        List<WeChatMessage> selected = new ArrayList<>(rows.subList(0, Math.min(batch, rows.size())));
        long t0 = System.currentTimeMillis();
        processClassification(selected, false, false);
        log.info("定时分类完成: 处理 {} 条, 耗时 {}ms", selected.size(), System.currentTimeMillis() - t0);
    }

    /** 清理超过 msgRetentionDays 天的消息。 */
    public void purgeOldMessages() {
        int days = Math.max(1, analysisInt("msgRetentionDays", 30));
        store.purgeOlderThanDays(days);
    }

    // ------------------------------------------------------------------
    // INQ/OFFER 自动录入：已分类(status=1)的INQ/OFFER → AI解析 → crm_offer
    // ------------------------------------------------------------------

    /**
     * 定时/手动录入入口。群身份只从 crm_supplier 的 sales_group/purchase_group 解析，
     * 每条消息原子领取后只调用一次 AI 录入，再用返回的料号做比价。
     */
    public Map<String, Object> offerEntryOnce(String chat) {
        return offerEntryOnce(chat, false);
    }

    /** force=true 用于后台管理页的人工扫描；定时扫描仍受 offerEntryEnabled 控制。 */
    public Map<String, Object> offerEntryOnce(String chat, boolean force) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = config.bool("offerEntryEnabled", false);
        boolean manual = force || StringUtils.isNotBlank(chat);
        out.put("enabled", enabled);
        out.put("chat", StringUtils.defaultString(chat));
        if (!enabled && !manual) {
            out.put("running", false);
            out.put("message", "自动录入已关闭(offerEntryEnabled=false)；如需排障请指定会话或消息键手动触发");
            return out;
        }
        if (!entryRunning.compareAndSet(false, true)) {
            out.put("running", true);
            out.put("message", "上一轮录入仍在执行中，本次跳过");
            return out;
        }
        boolean actualScan = false;
        try {
            if (!manual) {
                long scanSec = Math.max(5, config.integer("offerEntryScanSec", 30));
                if (lastEntryFinishedAt > 0 && System.currentTimeMillis() - lastEntryFinishedAt < scanSec * 1000L) {
                    out.put("message", "未到扫描间隔(offerEntryScanSec=" + scanSec + "s)，跳过");
                    return out;
                }
            }
            actualScan = true;
            List<String> cats = entryCategories();
            if (cats.isEmpty()) {
                out.put("message", "offerEntryCategories 未配置任何有效分类");
                return out;
            }
            int batch = Math.max(1, Math.min(100, config.integer("offerEntryBatchSize", 5)));
            int minAgeSec = Math.max(0, config.integer("offerEntryMinAgeSec", 60));
            int maxAgeHours = Math.max(1, config.integer("offerEntryMaxAgeHours", 24));
            int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
            String since = fmtTime(System.currentTimeMillis() - maxAgeHours * 3600_000L);
            List<WeChatMessage> rows = store.queryProcessable(StringUtils.trimToNull(chat), null, cats,
                    since, minAgeSec, batch, timeout);
            out.put("categories", cats);
            out.put("maxAgeHours", maxAgeHours);
            out.put("minAgeSec", minAgeSec);
            EntryStats stats = processEntryRows(rows, cats);
            lastEntryProcessed = rows.size();
            return entryResult(out, stats, rows.size());
        } finally {
            entryRunning.set(false);
            if (actualScan) {
                lastEntryFinishedAt = System.currentTimeMillis();
            }
        }
    }

    /** 按 1～20 条明确消息键录入；绕过消息年龄窗口，但仍执行分类、群映射和方向校验。 */
    public Map<String, Object> offerEntryKeys(List<String> keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty() || keys.size() > 20) {
            throw new IllegalArgumentException("明确消息键必须为1～20条");
        }
        if (!entryRunning.compareAndSet(false, true)) {
            out.put("message", "上一轮录入仍在执行中，本次跳过");
            return out;
        }
        try {
            store.requeueEntryKeys(keys);
            List<WeChatMessage> rows = store.queryByKeys(keys);
            EntryStats stats = processEntryRows(rows, entryCategories());
            out.put("requested", keys.size());
            lastEntryProcessed = rows.size();
            return entryResult(out, stats, rows.size());
        } finally {
            entryRunning.set(false);
            lastEntryFinishedAt = System.currentTimeMillis();
        }
    }

    private Map<String, Object> entryResult(Map<String, Object> out, EntryStats stats, int scanned) {
        out.put("scanned", scanned);
        out.put("enteredMessages", stats.enteredMessages);
        out.put("enteredOffers", stats.enteredOffers);
        out.put("alreadyEntered", stats.alreadyEntered);
        out.put("failed", stats.failed);
        out.put("skipped", stats.skipped);
        out.put("message", "录入完成: " + stats.enteredMessages + " 条消息成功(物料 " + stats.enteredOffers
                + " 行), 已录入 " + stats.alreadyEntered + ", 失败 " + stats.failed + ", 跳过 " + stats.skipped);
        return out;
    }

    private EntryStats processEntryRows(List<WeChatMessage> rows, List<String> cats) {
        EntryStats stats = new EntryStats();
        if (rows == null || rows.isEmpty()) {
            return stats;
        }
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        for (WeChatMessage m : rows) {
            if (m.getMsgCategory() == null || !cats.contains(m.getMsgCategory().toUpperCase())
                    || !"SUCCESS".equalsIgnoreCase(StringUtils.trimToEmpty(m.getClassifyStatus()))
                    || (!"LLM".equalsIgnoreCase(m.getCategorySource())
                    && !"CACHE".equalsIgnoreCase(m.getCategorySource()))) {
                stats.skipped++;
                continue;
            }
            if (!store.markEntryProcessing(m.getUsername(), m.getLocalId(), timeout)) {
                continue;
            }
            try {
                enterOne(m, stats);
            } catch (Exception e) {
                String reason = safeMessage(e);
                if (isPermanentEntrySkip(e)) {
                    stats.skipped++;
                    store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SKIPPED",
                            clipEntryMessage("需人工处理: " + reason), 0, "", 0);
                    store.updateTaskState(m.getUsername(), m.getLocalId(), "MANUAL_REVIEW",
                            "ENTRY_BUSINESS_SKIP");
                    log.warn("微信消息录入业务跳过: chat={} sender={} serverId={} 原因={}",
                            m.getName(), m.getSender(), m.getServerId(), reason);
                    continue;
                }
                int maxFail = Math.max(1, config.integer("offerEntryMaxFail", 3));
                int failCnt = (m.getEntryFailCount() == null ? 0 : m.getEntryFailCount()) + 1;
                boolean dead = failCnt >= maxFail;
                stats.failed++;
                store.updateEntryOnly(m.getUsername(), m.getLocalId(), dead ? "DEAD" : "FAILED",
                        clipEntryMessage("AI录入失败(" + failCnt + "/" + maxFail + "): " + reason),
                        0, "", failCnt);
                if (dead) {
                    store.updateTaskState(m.getUsername(), m.getLocalId(), "MANUAL_REVIEW",
                            "录入连续失败，已停止自动重试");
                }
                log.warn("微信消息AI录入失败({}/{}): chat={} sender={} serverId={} 原因={}",
                        failCnt, maxFail, m.getName(), m.getSender(), m.getServerId(), reason);
            }
        }
        return stats;
    }

    private void enterOne(WeChatMessage m, EntryStats stats) throws Exception {
        int existing = offerMapper.countByWechat(StringUtils.defaultString(m.getServerId()),
                StringUtils.defaultString(m.getUsername()), m.getLocalId());
        if (existing > 0) {
            List<CrmOffer> old = offerMapper.selectByWechat(StringUtils.defaultString(m.getServerId()),
                    StringUtils.defaultString(m.getUsername()), m.getLocalId());
            String ids = joinOfferIds(old);
            stats.alreadyEntered++;
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SUCCESS",
                    "已存在微信来源的crm_offer记录，幂等跳过", existing, ids, 0);
            return;
        }

        PartyMatch party = partyService.resolve(m.getName());
        if (party.isUnmappedOrAmbiguous()) {
            stats.skipped++;
            String prefix = PartyMatch.UNMAPPED.equals(party.getStatus()) ? "群未映射" : "群映射冲突";
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SKIPPED",
                    prefix + "：" + StringUtils.defaultString(party.getMessage()), 0, "", 0);
            store.updateTaskState(m.getUsername(), m.getLocalId(), "PENDING", "UNMAPPED_CHAT");
            return;
        }
        if (!party.isOk()) {
            stats.skipped++;
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SKIPPED",
                    party.getStatus() + ": " + StringUtils.defaultString(party.getMessage()), 0, "", 0);
            store.updateTaskState(m.getUsername(), m.getLocalId(), "MANUAL_REVIEW", party.getStatus());
            return;
        }

        boolean self = WeChatTextUtils.isSelf(m.getSender(), selfNames());
        boolean offer = "OFFER".equalsIgnoreCase(m.getMsgCategory());
        if (self && offer && !hasExternalCustomerInq(m)) {
            stats.skipped++;
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SKIPPED",
                    "我方外发OFFER无客户INQ，完全人工处理", 0, "", 0);
            store.updateTaskState(m.getUsername(), m.getLocalId(), "SKIPPED", "SELF_OUTBOUND_ONLY");
            return;
        }

        CrmOfferPriceMode priceMode = CrmOfferPriceMode.NONE;
        String inqOfferType = "Inq";
        if (offer && self) {
            priceMode = CrmOfferPriceMode.AS_IS;
            inqOfferType = "Offer";
        } else if (offer) {
            priceMode = CrmOfferPriceMode.COST_PLUS_PROFIT;
        }

        List<CrmOffer> saved = offerService.aiEntryOffersReturningAsSystem(
                party.getSupplier().getSupplierCode(), party.getSupplier().getSupplierName(),
                inqOfferType, party.getSupplier().getProfitRatio() != null
                        ? party.getSupplier().getProfitRatio()
                        : config.decimal("offerEntryProfitRatio", 2d),
                StringUtils.defaultString(m.getContent()),
                priceMode, config.str("offerEntryCreateBy", "wechat_auto"),
                StringUtils.defaultString(m.getServerId()), StringUtils.defaultString(m.getUsername()),
                m.getLocalId(), buildEntryRemark(m));
        if (saved == null || saved.isEmpty()) {
            throw new IllegalStateException("AI录入未生成任何crm_offer记录");
        }

        List<String> partNumbers = new ArrayList<>();
        for (CrmOffer offerRow : saved) {
            if (offerRow != null && StringUtils.isNotBlank(offerRow.getProductCode())
                    && !partNumbers.contains(offerRow.getProductCode())) {
                partNumbers.add(offerRow.getProductCode());
            }
        }
        int compareRows = 0;
        String compareError = "";
        try {
            List<Map<String, Object>> compare = offerService.compareRecentOffers(partNumbers);
            compareRows = compare == null ? 0 : compare.size();
        } catch (Exception ce) {
            compareError = "；比价失败: " + safeMessage(ce);
            log.warn("微信AI录入后比价失败(不影响已入库记录): chat={} serverId={}",
                    m.getName(), m.getServerId(), ce);
        }
        String entryStatus = compareError.isEmpty() ? "SUCCESS" : "SUCCESS_WARN";
        String entryMessage = "AI录入: " + saved.size() + "行; 比价: " + compareRows + "条" + compareError;
        stats.enteredMessages++;
        stats.enteredOffers += saved.size();
        store.updateEntryOnly(m.getUsername(), m.getLocalId(), entryStatus,
                clipEntryMessage(entryMessage), saved.size(), joinOfferIds(saved), 0);
        log.info("微信消息已录入crm_offer: chat={} sender={} serverId={} type={} priceMode={} 物料{}行 状态={}",
                m.getName(), m.getSender(), m.getServerId(), inqOfferType, priceMode, saved.size(), entryStatus);
    }

    private String joinOfferIds(List<CrmOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return "";
        }
        List<String> ids = new ArrayList<>();
        for (CrmOffer offer : offers) {
            if (offer != null && offer.getId() != null) {
                ids.add(String.valueOf(offer.getId()));
            }
        }
        return String.join(",", ids);
    }

    /** 我方 OFFER 只在分类已关联外部客户 INQ 时自动归档，避免凭空消耗一次 AI 录入。 */
    private boolean hasExternalCustomerInq(WeChatMessage m) {
        if (StringUtils.isBlank(m.getRelatInq())) {
            return false;
        }
        try {
            JsonNode node = jackson.readTree(m.getRelatInq());
            return "INQ".equalsIgnoreCase(node.path("category").asText(""))
                    && !WeChatTextUtils.isSelf(node.path("sender").asText(""), selfNames());
        } catch (Exception e) {
            log.warn("解析我方OFFER关联INQ失败: chat={} serverId={}", m.getName(), m.getServerId());
            return false;
        }
    }

    private static final class EntryStats {
        private int enteredMessages;
        private int enteredOffers;
        private int alreadyEntered;
        private int failed;
        private int skipped;
    }

    /** crm_offer 的来源备注，便于从报价记录反查微信原消息。 */
    private String buildEntryRemark(WeChatMessage m) {
        String remark = "微信自动录入|群:" + StringUtils.trimToEmpty(m.getName())
                + "|发言人:" + StringUtils.trimToEmpty(m.getSender())
                + "|消息时间:" + StringUtils.trimToEmpty(m.getCreateTime())
                + "|server_id:" + StringUtils.defaultString(m.getServerId());
        return remark.length() > 500 ? remark.substring(0, 500) : remark;
    }

    /** 业务前置条件不满足时转人工，不消耗自动重试次数。 */
    private boolean isPermanentEntrySkip(Exception e) {
        String msg = safeMessage(e);
        return msg.contains("未能从内容中识别出料号")
                || msg.contains("物料内容不能为空")
                || msg.contains("供应商不存在")
                || msg.contains("供应商信息不能为空");
    }

    private String safeMessage(Exception e) {
        if (e == null) {
            return "未知错误";
        }
        String msg = StringUtils.trimToEmpty(e.getMessage());
        return msg.isEmpty() ? e.getClass().getSimpleName() : clipEntryMessage(msg);
    }

    private String clipEntryMessage(String message) {
        String value = StringUtils.defaultString(message);
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    /** INQ/OFFER 自动录入参数与运行状态（便于 Postman 观察）。 */
    public Map<String, Object> offerEntryStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", analysisBool("offerEntryEnabled", false));
        m.put("scanSec", analysisInt("offerEntryScanSec", 30));
        m.put("batchSize", analysisInt("offerEntryBatchSize", 5));
        m.put("categories", entryCategories());
        m.put("mappingSource", "crm_supplier.sales_group/purchase_group");
        m.put("profitRatio", analysisDouble("offerEntryProfitRatio", 2d));
        m.put("maxFail", analysisInt("offerEntryMaxFail", 3));
        m.put("selfNames", analysisStr("selfNames", ""));
        m.put("createBy", analysisStr("offerEntryCreateBy", "wechat_auto"));
        int minAgeSec = Math.max(0, analysisInt("offerEntryMinAgeSec", 60));
        int maxAgeHours = Math.max(1, analysisInt("offerEntryMaxAgeHours", 24));
        m.put("minAgeSec", minAgeSec);
        m.put("maxAgeHours", maxAgeHours);
        m.put("aiFlow", "单次AI录入 -> 返回料号比价");
        m.put("entryStatusLegend",
                "PENDING=待录入/可重试, PROCESSING=处理中, SUCCESS=成功, SUCCESS_WARN=已录入但比价告警, "
                        + "FAILED=可重试失败, DEAD=达到失败上限, SKIPPED=需人工确认, NOT_APPLICABLE=不参与录入");
        m.put("running", entryRunning.get());
        m.put("lastRunAt", lastEntryFinishedAt == 0 ? null : fmtTime(lastEntryFinishedAt));
        m.put("lastProcessed", lastEntryProcessed);
        String since = fmtTime(System.currentTimeMillis() - maxAgeHours * 3600_000L);
        java.util.Map<String, Object> st = store.processableStats(entryCategories(), since);
        m.put("pendingCnt", st == null ? 0 : st.get("cnt"));
        m.put("earliestPending", st == null ? null : st.get("earliest"));
        m.put("entryStatusStats", store.entryStatusStats(since));
        return m;
    }

    /** 人工确认后，把指定会话或全部 SKIPPED/FAILED/DEAD 的 INQ/OFFER 重新放入录入队列。 */
    public Map<String, Object> requeueOfferEntry(String chat) {
        String target = StringUtils.trimToNull(chat);
        int count = store.requeueEntry(target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chat", StringUtils.defaultString(target));
        out.put("requeued", count);
        out.put("message", "已重新入队 " + count + " 条；下一轮自动录入会重新执行AI录入");
        return out;
    }

    /** 按明确消息键重新入队，最多 20 条；已成功消息不会被覆盖。 */
    public Map<String, Object> requeueOfferEntryKeys(List<String> keys) {
        if (keys == null || keys.isEmpty() || keys.size() > 20) {
            throw new IllegalArgumentException("明确消息键必须为1～20条");
        }
        int count = store.requeueEntryKeys(keys);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requested", keys.size());
        out.put("requeued", count);
        out.put("message", "已重新入队 " + count + " 条");
        return out;
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
            if (matchesExcludedChat(msg, excludedChatKeys())) {
                store.saveBlackAll(Collections.singletonList(msg));
                filteredCount.incrementAndGet();
                return;
            }
            store.save(msg);
            if (!passAnalysis(msg)) {
                filteredCount.incrementAndGet();
                store.updateFiltered(msg, "分类黑名单：会话不参与AI分类");
                return;
            }
            // 实时监听只负责入库；分类统一由数据库扫描入口执行，避免两套触发规则。
            listenCount.incrementAndGet();
        } catch (Exception e) {
            log.error("处理微信消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 对一批消息按会话归并分类（用于 fetch/恢复任务）：
     * 过滤掉的会话标记 OTHER/FILTER；其余 = 缓存命中 + AI 分类，结果回写库。
     *
     * @param markFiltered 是否把黑/白名单过滤掉的会话标记为 OTHER/FILTER（避免重复扫描）
     */
    private synchronized void classifySequence(List<WeChatMessage> rows, boolean markFiltered) {
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
                log.info("会话命中黑/白名单过滤: chat={} 消息{}条 -> OTHER/FILTER", chatName, chatMsgs.size());
                for (WeChatMessage m : chatMsgs) {
                    if (StringUtils.isBlank(m.getMsgCategory())) {
                        store.updateFiltered(m, "分类黑名单：会话不参与AI分类");
                    }
                }
                continue;
            }
            // 最近N条原始消息作为上下文(允许未分类)；本批 rows 才是待判消息。
            int ctxWin = Math.max(20, Math.min(100, analysisInt("aiContextWindow", 20)));
            List<WeChatMessage> pending = new ArrayList<>(chatMsgs);
            List<WeChatMessage> context = recentContext(key, pending, ctxWin);
            classifyWithContext(key, context, pending, chatName, true);
            for (WeChatMessage m : pending) {
                if (m.getMsgCategory() != null && !m.getMsgCategory().isEmpty()) {
                    store.updateCategory(m);
                    pushHistory(key, m);
                    maybeAlertTrace(m);
                }
            }
        }
    }

    /** TRACE 飞书预警：用 trace_alerted_at 去重，失败原因单独记录，下一轮可重试。 */
    private boolean maybeAlertTrace(WeChatMessage m) {
        if (m == null || !"TRACE".equalsIgnoreCase(StringUtils.trimToEmpty(m.getMsgCategory()))
                || !analysisBool("traceAlertEnabled", true)) {
            return false;
        }
        if (WeChatTextUtils.isSelf(m.getSender(), config.selfNames())) {
            log.info("我方成员消息不推送TRACE飞书: chat={} sender={} localId={}",
                    m.getName(), m.getSender(), m.getLocalId());
            return false;
        }
        if (StringUtils.isNotBlank(m.getTraceAlertedAt())) {
            return false;
        }
        if (!"LLM".equalsIgnoreCase(m.getCategorySource())
                && !"CACHE".equalsIgnoreCase(m.getCategorySource())) {
            return false;
        }
        if (StringUtils.isBlank(m.getRelatInq()) && StringUtils.isBlank(m.getRelatOffer())) {
            return false;
        }
        int maxAgeMin = analysisInt("traceAlertMaxAgeMin", 1440);
        if (!WeChatTextUtils.isRecent(m.getCreateTime(), maxAgeMin)) {
            log.info("历史TRACE不推送飞书(超过{}分钟): chat={} sender={} time={}",
                    maxAgeMin, m.getName(), m.getSender(), m.getCreateTime());
            return false;
        }
        String username = StringUtils.trimToEmpty(m.getUsername());
        boolean chatLocked = false;
        if (StringUtils.isNotBlank(username)) {
            if (!traceAlertChatLocks.add(username)) {
                return false;
            }
            chatLocked = true;
        }
        try {
            int cooldownMin = Math.max(0, analysisInt("traceAlertChatCooldownMin", 5));
            if (cooldownMin > 0 && StringUtils.isNotBlank(username)) {
                String since = fmtTime(System.currentTimeMillis() - cooldownMin * 60_000L);
                if (store.hasRecentTraceAlert(username, since)) {
                    log.info("同会话{}分钟内已发送过TRACE预警，本次跳过飞书: chat={} localId={}",
                            cooldownMin, m.getName(), m.getLocalId());
                    return false;
                }
            }
            String alertKey = "TRACE|" + m.getUsername() + "|" + m.getLocalId();
            if (!traceAlerting.add(alertKey)) {
                return false;
            }
            try {
                String detail = "会话: " + StringUtils.defaultString(m.getName()) + "\n"
                        + "发言人: " + StringUtils.defaultString(m.getSender()) + "\n"
                        + "时间: " + StringUtils.defaultString(m.getCreateTime()) + "\n"
                        + "内容: " + WeChatTextUtils.clip(m.getContent(), 600) + "\n"
                        + "分类理由: " + StringUtils.defaultString(m.getCategoryReason()) + "\n"
                        + "关联INQ: " + relText(m.getRelatInq()) + "\n"
                        + "关联OFFER: " + relText(m.getRelatOffer()) + "\n"
                        + "建议: 立即确认库存/价格/交期及订单推进动作。";
                try {
                    if (!aiChannelService.alert(alertKey,
                            "【微信TRACE预警】询报价二次跟进，存在订单机会", detail)) {
                        store.markTraceAlertError(m.getUsername(), m.getLocalId(), "飞书预警返回失败");
                        log.warn("TRACE预警推送未成功，等待下一轮重试: chat={} sender={} localId={}",
                                m.getName(), m.getSender(), m.getLocalId());
                        return false;
                    }
                } catch (Exception e) {
                    store.markTraceAlertError(m.getUsername(), m.getLocalId(), safeMessage(e));
                    log.warn("TRACE预警异常: chat={} sender={} localId={} error={}",
                            m.getName(), m.getSender(), m.getLocalId(), e.getMessage());
                    return false;
                }
                if (!store.markTraceAlerted(m.getUsername(), m.getLocalId())) {
                    log.warn("TRACE预警已发送但去重状态回写失败: chat={} localId={}",
                            m.getName(), m.getLocalId());
                    return false;
                }
                m.setTraceAlertedAt(fmtTime(System.currentTimeMillis()));
                log.info("TRACE预警已推送: chat={} sender={} localId={}", m.getName(), m.getSender(), m.getLocalId());
                return true;
            } finally {
                traceAlerting.remove(alertKey);
            }
        } finally {
            if (chatLocked) {
                traceAlertChatLocks.remove(username);
            }
        }
    }

    /** 定时补扫已关联但尚未成功预警的 TRACE。 */
    public Map<String, Object> scanTraceAlerts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", analysisBool("traceAlertEnabled", true));
        if (!analysisBool("traceAlertEnabled", true)) {
            out.put("message", "TRACE预警已关闭(traceAlertEnabled=false)");
            out.put("scanned", 0);
            out.put("sent", 0);
            return out;
        }
        int maxAgeMin = Math.max(1, analysisInt("traceAlertMaxAgeMin", 1440));
        int batch = Math.max(1, Math.min(500, analysisInt("traceAlertScanBatchSize", 50)));
        String since = fmtTime(System.currentTimeMillis() - maxAgeMin * 60_000L);
        List<WeChatMessage> rows = store.queryTraceAlerts(since, batch);
        int sent = 0;
        for (WeChatMessage m : rows) {
            if (maybeAlertTrace(m)) {
                sent++;
            }
        }
        out.put("scanned", rows.size());
        out.put("sent", sent);
        out.put("message", "TRACE预警扫描完成: 候选 " + rows.size() + " 条, 成功 " + sent + " 条");
        return out;
    }

    private String relText(String json) {
        if (StringUtils.isBlank(json)) {
            return "无";
        }
        try {
            JsonNode n = jackson.readTree(json);
            String sender = n.path("sender").asText("");
            String content = n.path("content").asText("");
            return StringUtils.defaultString(sender) + ": " + WeChatTextUtils.clip(content, 300);
        } catch (Exception e) {
            return WeChatTextUtils.clip(json, 300);
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

    /** 单会话分类管线：缓存命中 → AI 分类。返回时 pending 内已带分类和关联字段。 */
    private synchronized void classifyWithContext(String chatKey, List<WeChatMessage> context,
                                                  List<WeChatMessage> pending, String chatName,
                                                  boolean useCache) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        // 1) 缓存命中；未命中的全部交给提示词驱动的 AI 分类
        List<WeChatMessage> blank = new ArrayList<>();
        for (WeChatMessage m : pending) {
            if (!useCache || !llm.applyCached(m)) {
                blank.add(m);
            }
        }
        if (!blank.isEmpty()) {
            llm.classify(context, blank, chatName);
            boolean anyFail = false;
            for (WeChatMessage m : blank) {
                if (m.getMsgCategory() == null || m.getMsgCategory().isEmpty()) {
                    anyFail = true;
                    break;
                }
            }
            if (anyFail) {
                log.debug("分类批次存在未返回结果的消息: chat={}", chatName);
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

    /**
     * 构造待判消息之前的最近 max 条上下文：优先合并内存历史和数据库最近消息，再由 pending
     * 覆盖同键对象。这样监听刚启动、内存历史不足时也能保证拿到数据库里的最近20条。
     */
    private List<WeChatMessage> recentContext(String username, List<WeChatMessage> pending, int max) {
        int window = Math.max(0, max);
        if (window == 0) {
            return Collections.emptyList();
        }
        Map<String, WeChatMessage> merged = new LinkedHashMap<>();
        Deque<WeChatMessage> hq = listenHistory.get(username);
        if (hq != null) {
            for (WeChatMessage m : lastN(hq, window + (pending == null ? 0 : pending.size()))) {
                merged.put(keyOf(m) + "|" + m.getLocalId(), m);
            }
        }
        int load = window + (pending == null ? 0 : pending.size());
        for (WeChatMessage m : store.queryRecent(username, Math.max(window, load))) {
            merged.put(keyOf(m) + "|" + m.getLocalId(), m);
        }
        Set<String> pendingKeys = new HashSet<>();
        if (pending != null) {
            for (WeChatMessage m : pending) {
                String pendingKey = keyOf(m) + "|" + m.getLocalId();
                pendingKeys.add(pendingKey);
                merged.put(pendingKey, m);
            }
        }
        List<WeChatMessage> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
        List<WeChatMessage> context = new ArrayList<>();
        for (WeChatMessage m : ordered) {
            if (!pendingKeys.contains(keyOf(m) + "|" + m.getLocalId())) {
                context.add(m);
            }
        }
        if (context.size() > window) {
            context = new ArrayList<>(context.subList(context.size() - window, context.size()));
        }
        return context;
    }

    private void pushHistory(String key, WeChatMessage m) {
        Deque<WeChatMessage> dq = listenHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
        dq.addLast(m);
        while (dq.size() > Math.max(20, Math.min(100, analysisInt("aiContextWindow", 20)))) {
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

    /** 读取一条引用消息的真实文本/类型；历史占位引用在前端按需补全。 */
    public Map<String, Object> quoteInfo(WeChatMessage message) {
        if (message == null || StringUtils.isBlank(message.getUsername())) {
            throw new IllegalArgumentException("消息不存在");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getPython());
        cmd.add(props.getScript());
        cmd.add("quote-info");
        cmd.add("--username");
        cmd.add(message.getUsername());
        cmd.add("--local-id");
        cmd.add(String.valueOf(message.getLocalId()));
        List<String> lines = runGatewayCommand(cmd, "引用解析");
        for (String line : lines) {
            if (!line.startsWith("{")) {
                continue;
            }
            try {
                JsonNode node = jackson.readTree(line);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("kind", node.path("kind").asText(""));
                info.put("quote", node.path("quote").asText(""));
                info.put("fileName", node.path("file_name").asText(""));
                info.put("hasMedia", node.path("has_media").asBoolean(false));
                return info;
            } catch (Exception e) {
                throw new IllegalStateException("引用解析结果格式错误", e);
            }
        }
        throw new IllegalStateException("引用解析没有返回结果");
    }

    /** 按需下载引用中的图片或文档文件。 */
    public Path ensureQuoteMedia(WeChatMessage message) {
        if (message == null || StringUtils.isBlank(message.getUsername())) {
            throw new IllegalArgumentException("消息不存在");
        }
        Path root = Paths.get(props.getMediaDir()).toAbsolutePath().normalize();
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getPython());
        cmd.add(props.getScript());
        cmd.add("quote-media");
        cmd.add("--username");
        cmd.add(message.getUsername());
        cmd.add("--local-id");
        cmd.add(String.valueOf(message.getLocalId()));
        cmd.add("--media");
        cmd.add(props.getMediaDir());
        List<String> lines = runGatewayCommand(cmd, "引用媒体下载");
        for (String line : lines) {
            if (!line.startsWith("MEDIA_PATH=")) {
                continue;
            }
            Path file = Paths.get(line.substring("MEDIA_PATH=".length()).trim())
                    .toAbsolutePath().normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("引用媒体文件路径无效");
            }
            String ext = pathExtension(file);
            if (!IMAGE_EXTENSIONS.contains(ext) && !DOCUMENT_EXTENSIONS.contains(ext)) {
                throw new IllegalStateException("该引用文件类型暂不支持查看（不支持音频、视频）");
            }
            return file;
        }
        throw new IllegalStateException("引用媒体没有返回文件");
    }

    private List<String> runGatewayCommand(List<String> cmd, String operation) {
        List<String> lines = new ArrayList<>();
        String error = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("[错误]")) {
                        error = line.replaceFirst("^\\[错误\\]\\s*", "");
                    } else if (StringUtils.isNotBlank(line)) {
                        lines.add(line.trim());
                    }
                    log.info("[wx_gateway {}] {}", operation, line);
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException(StringUtils.defaultIfBlank(error,
                        operation + "失败，exitCode=" + code));
            }
            return lines;
        } catch (Exception e) {
            log.warn("微信网关{}失败: username={} localId={}",
                    operation, cmd.size() > 4 ? cmd.get(4) : "", cmd.size() > 6 ? cmd.get(6) : "", e);
            throw new IllegalStateException(StringUtils.defaultIfBlank(error,
                    operation + "失败: " + e.getMessage()), e);
        }
    }

    /** 按需下载单条图片或文档文件，返回可读取的本地路径；音频、视频不在此接口开放。 */
    public Path ensureMedia(WeChatMessage message) {
        if (message == null || StringUtils.isBlank(message.getUsername())) {
            throw new IllegalArgumentException("消息不存在");
        }
        String type = StringUtils.trimToEmpty(message.getType());
        boolean image = "图片".equals(type);
        if (!image && !"文件".equals(type) && !"文件/链接/卡片".equals(type)) {
            throw new IllegalArgumentException("该消息没有可下载的图片或文档文件");
        }
        if (!image && !DOCUMENT_EXTENSIONS.contains(fileExtension(message))) {
            throw new IllegalArgumentException("该消息不是可查看的文档文件（不支持音频、视频）");
        }
        Path root = Paths.get(props.getMediaDir()).toAbsolutePath().normalize();
        Path existing = existingMedia(root, message.getMediaPath());
        if (existing != null) {
            if (!isAllowedMediaPath(existing, image)) {
                throw new IllegalArgumentException("该消息不是可查看的图片或文档文件");
            }
            return existing;
        }

        synchronized (dumpLock) {
            existing = existingMedia(root, message.getMediaPath());
            if (existing != null) {
                if (!isAllowedMediaPath(existing, image)) {
                    throw new IllegalArgumentException("该消息不是可查看的图片或文档文件");
                }
                return existing;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPython());
            cmd.add(props.getScript());
            cmd.add("media");
            cmd.add("--username");
            cmd.add(message.getUsername());
            cmd.add("--local-id");
            cmd.add(String.valueOf(message.getLocalId()));
            cmd.add("--media");
            cmd.add(props.getMediaDir());

            Path filePath;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String mediaLine = null;
                String errorLine = null;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("MEDIA_PATH=")) {
                            mediaLine = line.substring("MEDIA_PATH=".length()).trim();
                        } else if (StringUtils.isNotBlank(line)) {
                            if (line.contains("[错误]")) {
                                errorLine = line.replaceFirst("^\\[错误\\]\\s*", "");
                            }
                            log.info("[wx_gateway media] {}", line);
                        }
                    }
                }
                int code = process.waitFor();
                if (code != 0) {
                    throw new IllegalStateException(StringUtils.defaultIfBlank(errorLine,
                            "媒体下载进程异常退出，exitCode=" + code));
                }
                if (StringUtils.isBlank(mediaLine)) {
                    throw new IllegalStateException("媒体文件未下载或本机不存在");
                }
                filePath = Paths.get(mediaLine).toAbsolutePath().normalize();
            } catch (Exception e) {
                log.warn("微信媒体下载失败: username={} localId={} type={}",
                        message.getUsername(), message.getLocalId(), type, e);
                throw new IllegalStateException("媒体下载失败: " + e.getMessage(), e);
            }
            if (!filePath.startsWith(root) || !Files.isRegularFile(filePath)) {
                throw new IllegalStateException("媒体文件路径无效");
            }
            if (!isAllowedMediaPath(filePath, image)) {
                throw new IllegalStateException("该文件不是可查看的图片或文档文件（不支持音频、视频）");
            }
            store.updateMediaPath(message, filePath.toString());
            return filePath;
        }
    }

    private static String fileExtension(WeChatMessage message) {
        String content = StringUtils.defaultString(message.getContent());
        Matcher matcher = FILE_EXT_PATTERN.matcher(content);
        if (matcher.find()) {
            return normalizeExtension(matcher.group(1));
        }
        String name = StringUtils.substringBetween(content, "<title>", "</title>");
        if (StringUtils.isBlank(name)) {
            name = content;
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            return normalizeExtension(name.substring(dot + 1));
        }
        String mediaPath = message.getMediaPath();
        return StringUtils.isBlank(mediaPath) ? "" : pathExtension(Paths.get(mediaPath));
    }

    private static String normalizeExtension(String extension) {
        return StringUtils.defaultString(extension)
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .trim()
                .replaceFirst("^\\.", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String pathExtension(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? normalizeExtension(name.substring(dot + 1)) : "";
    }

    private static boolean isAllowedMediaPath(Path file, boolean image) {
        String extension = pathExtension(file);
        return image ? IMAGE_EXTENSIONS.contains(extension) : DOCUMENT_EXTENSIONS.contains(extension);
    }

    private Path existingMedia(Path root, String mediaPath) {
        if (StringUtils.isBlank(mediaPath)) {
            return null;
        }
        try {
            Path file = Paths.get(mediaPath).toAbsolutePath().normalize();
            return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
        } catch (Exception e) {
            return null;
        }
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
            if (code != 0) {
                throw new IllegalStateException("wx_gateway 进程异常退出，exitCode=" + code);
            }
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
        Map<String, Object> cfg = sendConfig();
        cfg.put("names", names);
        if (template != null && !template.trim().isEmpty()) {
            cfg.put("template", template);
        }
        return runBatchSend(cfg);
    }

    /** 逐目标独立文本发送；每个目标对应一个 {name,text}，保持相同的拟人节奏和安全配置。 */
    public List<WeChatSendResult> batchSendTargets(List<WeChatSendItem> items) {
        Map<String, Object> cfg = sendConfig();
        cfg.put("targets", items);
        return runBatchSend(cfg);
    }

    private Map<String, Object> sendConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("rhythm", sendCfg("rhythm", props.getSendRhythm()));
        cfg.put("nap", Arrays.asList(sendDouble("napMin", props.getSendNapMin()),
                                     sendDouble("napMax", props.getSendNapMax())));
        cfg.put("interval", Arrays.asList(sendDouble("intervalMin", props.getSendIntervalMin()),
                                          sendDouble("intervalMax", props.getSendIntervalMax())));
        cfg.put("max_batch", sendInt("maxBatch", props.getSendMaxBatch()));
        cfg.put("verify", sendBool("verify", props.isSendVerify()));
        return cfg;
    }

    /** 发送前校验当前微信登录人；结果短时缓存，避免频繁启动网关。 */
    public boolean isExpectedSendAccount(String expected) {
        String wanted = StringUtils.trimToEmpty(expected);
        if (wanted.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - whoamiAt < 120_000L) {
            return wanted.equalsIgnoreCase(currentWxName);
        }
        currentWxName = readCurrentWxName();
        whoamiAt = System.currentTimeMillis();
        return wanted.equalsIgnoreCase(currentWxName);
    }

    private String readCurrentWxName() {
        try {
            ProcessBuilder pb = new ProcessBuilder(props.getPython(), props.getScript(), "whoami");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String name = "";
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String text = StringUtils.trimToEmpty(line);
                    if (text.startsWith("{")) {
                        JsonNode node = jackson.readTree(text);
                        String nick = node.path("nick_name").asText("");
                        name = StringUtils.isNotBlank(nick) ? nick : node.path("username").asText("");
                    } else if (text.contains("失败") || text.contains("错误")) {
                        log.warn("[wx_gateway.whoami] {}", text);
                    }
                }
            }
            int code = p.waitFor();
            if (code != 0) {
                log.error("wx_gateway whoami 退出码 {}", code);
                return "";
            }
            return StringUtils.trimToEmpty(name);
        } catch (Exception e) {
            log.error("读取微信当前登录账号失败", e);
            return "";
        }
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
            if (code != 0) {
                throw new IllegalStateException("wx_gateway batch_send 进程异常退出，exitCode=" + code);
            }
        } catch (Exception e) {
            log.error("批量发送执行失败", e);
            throw new IllegalStateException("批量发送失败: " + e.getMessage(), e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 分析配置
    // ------------------------------------------------------------------
    /** 读取字典里的精确名单（dict_label=whiteNames/selfNames，逗号拼接）。 */
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

    /** 我方成员固定包含四人，字典只做补充别名；用于判断外发 OFFER 的录入类型。 */
    private Set<String> selfNames() {
        Set<String> names = analysisNames("selfNames");
        Collections.addAll(names, "Eke", "May", "Jin", "Jasmine");
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

    /** 分类兜底：已命中排除关键词或不在白名单的会话不进入 AI 分类。 */
    private boolean passAnalysis(WeChatMessage m) {
        if (m == null) {
            return false;
        }
        Set<String> white = analysisNames("whiteNames");
        String name = StringUtils.trimToEmpty(m.getName());
        if (matchesExcludedChat(m, excludedChatKeys())) {
            return false;
        }
        if (!white.isEmpty()) {
            return white.contains(name);
        }
        return true;
    }
}
