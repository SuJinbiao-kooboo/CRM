package com.ruoyi.wexinAuto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.CrmOfferPriceMode;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ai.AiChannelService;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.util.OfferNoStockUtils;
import com.ruoyi.wexinAuto.analysis.WeChatExcelTextExtractor;
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
import com.ruoyi.common.exception.ServiceException;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.ZoneId;
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
    /** 可读取正文并交给AI判断的报价表格附件。 */
    private static final Set<String> EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList("xlsx", "xls", "csv"));
    private static final Pattern FILE_EXT_PATTERN = Pattern.compile(
            "<fileext(?:\\s[^>]*)?>([\\s\\S]*?)</fileext>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_TITLE_PATTERN = Pattern.compile(
            "<title(?:\\s[^>]*)?>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</title>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACHMENT_BUSINESS_NAME = Pattern.compile(
            "(?i)(offer|price|quote|quotation|stock|list|rfq|inq|报价|价格|库存|清单|询价|价单)");
    private static final Pattern ATTACHMENT_PRICE_SIGNAL = Pattern.compile(
            "(?i)(unit\\s*price|price|usd|报价|价格|单价|库存|stock|qty|数量|交期)");
    private static final Pattern ATTACHMENT_PART_SIGNAL = Pattern.compile(
            "(?i)(p/?n|part\\s*no|model|料号|型号)");
    private static final int ATTACHMENT_TEXT_MAX = 6000;

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
    /** 上次周期补偿扫描完成时间(ms)，用于捞回延迟写入微信本地库的旧消息 */
    private volatile long lastPollReconcileAt = 0L;
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

    /**
     * 换服务器/微信升级后修复发言人：按指定时间窗重新读取本地微信库，
     * 只按稳定消息身份(username+sortSeq+正文)回填 sender，不新增消息、不改分类/录入/任务状态。
     */
    public Map<String, Object> repairSenders(String chat, String since) {
        Map<String, Object> out = new LinkedHashMap<>();
        String start = StringUtils.trimToEmpty(since);
        if (start.isEmpty()) {
            start = fmtTime(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
        }
        synchronized (dumpLock) {
            if (!pollRunning.compareAndSet(false, true)) {
                throw new ServiceException("微信拉取正在执行，请稍后再修复发言人");
            }
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(props.getPython());
                cmd.add(props.getScript());
                cmd.add("dump");
                cmd.add("--since");
                cmd.add(start);
                if (StringUtils.isNotBlank(chat)) {
                    cmd.add("--chat");
                    cmd.add(chat.trim());
                }
                cmd.add("--limit");
                // 修复窗口可能跨多天；dump 的 limit 是每个会话的上限，
                // 再受全局 want 截断，固定给足 20 万条，避免 7 天只修复到前 5000 条。
                cmd.add(String.valueOf(Math.max(200000, analysisInt("pollLimit", 5000))));
                List<WeChatMessage> raw = runProcess(cmd, true);
                if (raw == null) {
                    raw = new ArrayList<>();
                }
                int updated = store.updateSenders(raw);
                out.put("since", start);
                out.put("chat", StringUtils.defaultString(chat));
                out.put("scanned", raw.size());
                out.put("updated", updated);
                out.put("message", "发言人修复完成：扫描 " + raw.size() + " 条，更新 " + updated + " 条");
                log.info("微信发言人修复完成: since={} chat={} scanned={} updated={}",
                        start, StringUtils.defaultString(chat), raw.size(), updated);
                return out;
            } catch (Exception e) {
                log.error("微信发言人修复失败: since={} chat={}", start, StringUtils.defaultString(chat), e);
                throw new ServiceException("发言人修复失败: " + e.getMessage());
            } finally {
                pollRunning.set(false);
            }
        }
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
        Set<String> currentSelfNames = selfNames();
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
            // 自己人消息直接归为 OTHER，非文本消息也不例外；后续分类入口不会再调用 AI。
            boolean self = WeChatTextUtils.isSelf(m.getSender(), currentSelfNames);
            if (!self) {
                prepareAttachment(m);
            }
            if (!self && !isTextMessage(m)) {
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
            return hasClassifiableText(message.getContent()) || hasUsableAttachment(message);
        }
        return false;
    }

    private boolean hasUsableAttachment(WeChatMessage message) {
        return message != null && StringUtils.isNotBlank(message.getAttachmentText());
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
        if (isSpreadsheetAttachment(message)) {
            if (StringUtils.isNotBlank(message.getAttachmentError())) {
                return "Excel/CSV报价附件未读取：" + message.getAttachmentError();
            }
            return "Excel/CSV附件没有可用报价内容，不调用模型（文件="
                    + StringUtils.defaultString(attachmentDisplayName(message), type) + "）";
        }
        if (StringUtils.isBlank(message.getContent())) {
            return "消息正文为空，不调用模型（消息类型=" + type + "）";
        }
        if ("文件/链接/卡片".equals(type)) {
            return "卡片/链接/文件消息没有可分类正文，不调用模型（消息类型=" + type + "）";
        }
        return "非文本或系统消息，不调用模型（消息类型=" + type + "）";
    }

    /**
     * 文件卡片默认没有可分类正文。仅对 xls/xlsx/csv 尝试找回本机附件并提取有限文本；
     * 提取结果只挂在当前消息对象上，不落库、不回传前端。
     */
    private void prepareAttachment(WeChatMessage message) {
        if (!isSpreadsheetAttachment(message) || message.getAttachmentText() != null
                || StringUtils.isNotBlank(message.getAttachmentError())) {
            return;
        }
        String name = attachmentDisplayName(message);
        message.setAttachmentName(name);
        try {
            Path file = ensureMedia(message);
            String text = WeChatExcelTextExtractor.extract(file);
            if (StringUtils.isBlank(text)) {
                message.setAttachmentError("附件为空或无法解析");
                return;
            }
            if (!looksLikeBusinessAttachment(name, text)) {
                message.setAttachmentError("附件内容未发现料号、价格或库存等报价特征");
                return;
            }
            message.setAttachmentText(WeChatTextUtils.clip(text, ATTACHMENT_TEXT_MAX));
        } catch (Exception e) {
            String reason = safeMessage(e);
            message.setAttachmentError(reason);
            log.warn("微信报价附件读取失败: localId={} file={} error={}",
                    message.getLocalId(), name, reason);
        }
    }

    private boolean isSpreadsheetAttachment(WeChatMessage message) {
        return message != null && "文件/链接/卡片".equals(StringUtils.trimToEmpty(message.getType()))
                && EXCEL_EXTENSIONS.contains(fileExtension(message));
    }

    private String attachmentDisplayName(WeChatMessage message) {
        if (message == null) {
            return "";
        }
        if (StringUtils.isNotBlank(message.getAttachmentName())) {
            return message.getAttachmentName();
        }
        Matcher matcher = FILE_TITLE_PATTERN.matcher(StringUtils.defaultString(message.getContent()));
        if (!matcher.find()) {
            return "";
        }
        return StringUtils.trimToEmpty(matcher.group(1))
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }

    private boolean looksLikeBusinessAttachment(String name, String text) {
        if (ATTACHMENT_BUSINESS_NAME.matcher(StringUtils.defaultString(name)).find()) {
            return true;
        }
        String body = StringUtils.defaultString(text);
        return ATTACHMENT_PRICE_SIGNAL.matcher(body).find()
                && ATTACHMENT_PART_SIGNAL.matcher(body).find();
    }

    /** 清空AI分类缓存：提示词/语义规则调整后，重跑历史消息前使用。 */
    public Map<String, Object> clearClassifyCache() {
        llm.clearCache();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cacheCleared", true);
        out.put("message", "分类缓存已清空");
        return out;
    }

    /** 人工修改单条消息分类；已录入或已生成任务的消息禁止覆盖，避免下游数据错乱。 */
    public Map<String, Object> updateCategoryManually(String username, long localId, String category) {
        String cat = StringUtils.trimToEmpty(category).toUpperCase(Locale.ROOT);
        if (!Arrays.asList("INQ", "OFFER", "TRACE", "OTHER").contains(cat)) {
            throw new ServiceException("分类只能是 INQ、OFFER、TRACE 或 OTHER");
        }
        if (StringUtils.isBlank(username)) {
            throw new ServiceException("消息键不能为空");
        }
        List<WeChatMessage> rows = store.queryByKeys(
                Collections.singletonList(username + ":" + localId));
        if (rows.isEmpty()) {
            throw new ServiceException("消息不存在或已被清理");
        }
        if ("SELF".equalsIgnoreCase(rows.get(0).getCategorySource())) {
            throw new ServiceException("我方成员消息按规则固定为 OTHER，不允许人工修改");
        }
        if (!store.updateCategoryManual(username, localId, cat, "人工修改为" + cat)) {
            throw new ServiceException("已进入录入或任务流程的消息不允许修改分类");
        }
        llm.clearCache();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", username);
        out.put("localId", localId);
        out.put("category", cat);
        out.put("message", "分类已修改为" + cat);
        return out;
    }

    /** 更新自动分类总开关；写入 wechat_analysis_config.classifyEnabled 并立即刷新字典缓存。 */
    public Map<String, Object> updateClassifyEnabled(boolean enabled) {
        return updateAnalysisSwitch("classifyEnabled", enabled, 129L,
                "自动分类已开启", "自动分类已关闭，仅保留手动分类");
    }

    /** 更新 INQ/OFFER 自动录入总开关；写入 wechat_analysis_config.offerEntryEnabled。 */
    public Map<String, Object> updateOfferEntryEnabled(boolean enabled) {
        return updateAnalysisSwitch("offerEntryEnabled", enabled, 110L,
                "自动录入已开启", "自动录入已关闭，仅保留手动录入");
    }

    /** 更新询报价任务自动生成开关；写入 wechat_analysis_config.autoTaskGenerateEnabled。 */
    public Map<String, Object> updateTaskGenerateEnabled(boolean enabled) {
        return updateAnalysisSwitch("autoTaskGenerateEnabled", enabled, 131L,
                "任务自动生成已开启", "任务自动生成已关闭，仅允许人工生成任务");
    }

    /** 更新询报价任务自动发送开关；写入 wechat_analysis_config.autoSendEnabled。 */
    public Map<String, Object> updateTaskSendEnabled(boolean enabled) {
        return updateAnalysisSwitch("autoSendEnabled", enabled, 133L,
                "任务自动发送已开启", "任务自动发送已关闭，仅允许人工发送扫描");
    }

    private Map<String, Object> updateAnalysisSwitch(String label, boolean enabled, Long defaultSort,
                                                     String enabledMessage, String disabledMessage) {
        SysDictData query = new SysDictData();
        query.setDictType(ANALYSIS_DICT_TYPE);
        List<SysDictData> rows = dictDataService.selectDictDataList(query);
        SysDictData target = null;
        if (rows != null) {
            for (SysDictData row : rows) {
                if (label.equals(row.getDictLabel())) {
                    target = row;
                    break;
                }
            }
        }
        String value = enabled ? "true" : "false";
        if (target == null) {
            target = new SysDictData();
            target.setDictType(ANALYSIS_DICT_TYPE);
            target.setDictLabel(label);
            target.setDictValue(value);
            target.setDictSort(defaultSort);
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
        out.put("message", enabled ? enabledMessage : disabledMessage);
        return out;
    }

    /** 轮询拉取/分类参数状态（便于 Postman 观察配置生效情况）。 */
    public Map<String, Object> pollStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", analysisBool("pollEnabled", true));
        m.put("intervalSec", analysisInt("pollIntervalSec", 10));
        m.put("backSec", analysisInt("pollBackSec", 120));
        m.put("initBackSec", analysisInt("pollInitBackSec", 259200));
        m.put("reconcileSec", analysisInt("pollReconcileSec", 600));
        m.put("reconcileBackSec", analysisInt("pollReconcileBackSec", 259200));
        m.put("reconcileLimit", analysisInt("pollReconcileLimit", 10000));
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
        m.put("lastReconcileAt", lastPollReconcileAt == 0 ? null : fmtTime(lastPollReconcileAt));
        Map<String, Object> st = store.unclassifiedStats();
        m.put("unclassifiedCnt", st == null ? 0 : st.get("cnt"));
        m.put("earliestUnclassified", st == null ? null : st.get("earliest"));
        return m;
    }

    /**
     * 增量拉取入库。
     * since 为空时：dump --since <水位-冗余回看>，即只拉新增；
     * since 非空时(如 2026-10-17 00:00:00)：dump --since <指定时间>，重新拉该时刻之后的消息，
     * 已入库的(唯一键 username+sort_seq+正文哈希冲突)由 SQL 直接跳过，不重复处理、不覆盖已有分类。
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
                int reconcileSec = Math.max(0, Math.min(86400, analysisInt("pollReconcileSec", 600)));
                long reconcileBackMs = Math.max(backMs,
                        Math.min(30L * 86400, Math.max(600, analysisInt("pollReconcileBackSec", 259200))) * 1000L);
                boolean reconcile = reconcileSec > 0 && StringUtils.isBlank(since) && StringUtils.isBlank(chat)
                        && (lastPollReconcileAt == 0L
                        || nowMs - lastPollReconcileAt >= reconcileSec * 1000L);
                long sinceMs = (lastPollMaxSeqMs > 0 ? lastPollMaxSeqMs : nowMs - initBackMs) - backMs;
                if (reconcile) {
                    // 微信客户端可能延迟写入旧消息；仅靠全局水位+短回看会永久漏拉。
                    // 周期性从源库重新扫描最近窗口，已有消息由幂等 upsert 自动去重。
                    sinceMs = Math.min(sinceMs, nowMs - reconcileBackMs);
                }
                int fetchLimit = Math.max(1, analysisInt(reconcile ? "pollReconcileLimit" : "pollLimit",
                        reconcile ? 10000 : 5000));
                int maxPages = reconcile ? 100 : 1;
                int pages = 0;
                long totalFetched = 0L;
                long totalKept = 0L;
                String sinceArg = StringUtils.isNotBlank(since) ? since.trim() : fmtTime(sinceMs);
                if (StringUtils.isNotBlank(since)) {
                    log.info("手动指定 since 拉取: {}", since.trim());
                }
                while (pages < maxPages) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(props.getPython());
                    cmd.add(props.getScript());
                    cmd.add("dump");
                    cmd.add("--since");
                    cmd.add(sinceArg);
                    if (StringUtils.isNotBlank(chat)) {
                        cmd.add("--chat");
                        cmd.add(chat);
                    }
                    if (media) {
                        cmd.add("--media");
                        cmd.add(props.getMediaDir());
                    }
                    cmd.add("--limit");
                    cmd.add(String.valueOf(fetchLimit));
                    List<WeChatMessage> raw = runProcess(cmd, true);
                    if (raw == null) {
                        raw = new ArrayList<>();
                    }
                    totalFetched += raw.size();
                    long batchMaxSeq = sinceMs;
                    long maxSeq = lastPollMaxSeqMs;
                    for (WeChatMessage m : raw) {
                        maxSeq = Math.max(maxSeq, m.getSortSeq());
                        batchMaxSeq = Math.max(batchMaxSeq, m.getSortSeq());
                    }
                    lastPollMaxSeqMs = maxSeq;
                    if (!raw.isEmpty()) {
                        List<WeChatMessage> normal = new ArrayList<>();
                        List<WeChatMessage> black = new ArrayList<>();
                        splitExcludedChats(raw, normal, black);
                        if (!black.isEmpty()) {
                            log.info("排除会话消息 {} 条转存 wechat_message_black(不处理)", black.size());
                            store.saveBlackAll(black);
                        }
                        if (!normal.isEmpty()) {
                            totalKept += store.saveAll(normal);
                        }
                    }
                    pages++;
                    if (!reconcile || raw.size() < fetchLimit) {
                        break;
                    }
                    long nextSinceMs = Math.max(sinceMs + 1000L, batchMaxSeq);
                    if (nextSinceMs <= sinceMs) {
                        log.warn("周期补偿扫描无法继续推进: since={}, batchMaxSeq={}", fmtTime(sinceMs), batchMaxSeq);
                        break;
                    }
                    sinceMs = nextSinceMs;
                    sinceArg = fmtTime(sinceMs);
                    log.info("周期补偿分批: 第 {} 页拉到 {} 条，继续从 {} 扫描", pages, raw.size(), sinceArg);
                }
                if (reconcile) {
                    lastPollReconcileAt = System.currentTimeMillis();
                }
                lastPollFetched = totalFetched;
                log.info("轮询拉取完成({}): 拉到 {} 条, 入库 {} 条, 水位 {}{}",
                        reconcile ? "周期补偿" : "增量", totalFetched, totalKept, fmtTime(lastPollMaxSeqMs),
                        pages > 1 ? "，补偿页数 " + pages : "");
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
            int minAgeSec = Math.max(0, config.integer("offerEntryMinAgeSec", 30));
            int groupGapSec = Math.max(1, config.integer("offerEntryGroupGapSec", 30));
            int quoteLinkSec = Math.max(groupGapSec, config.integer("offerEntryQuoteLinkSec", 1800));
            int maxAgeHours = Math.max(1, config.integer("offerEntryMaxAgeHours", 24));
            int timeout = entryProcessingTimeoutSec();
            int retryBaseSec = entryRetryBaseSec();
            int retryMax = entryRetryMax();
            String since = fmtTime(System.currentTimeMillis() - maxAgeHours * 3600_000L);
            int unlocked = store.recoverStaleEntryProcessing(since, timeout,
                    WeChatEntryRetryPolicy.maxAttempts(retryMax));
            if (unlocked > 0) {
                log.warn("已自动解锁超时INQ/OFFER录入 {} 条，按失败进入指数退避重试", unlocked);
            }
            List<WeChatMessage> rows = store.queryProcessable(StringUtils.trimToNull(chat), null, cats,
                    since, minAgeSec, batch, timeout, retryBaseSec);
            out.put("categories", cats);
            out.put("maxAgeHours", maxAgeHours);
            out.put("minAgeSec", minAgeSec);
            out.put("groupGapSec", groupGapSec);
            out.put("quoteLinkSec", quoteLinkSec);
            out.put("entryProcessingTimeoutSec", timeout);
            out.put("retryMax", retryMax);
            out.put("retryBaseSec", retryBaseSec);
            out.put("unlocked", unlocked);
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
        out.put("enteredGroups", stats.enteredGroups);
        out.put("enteredMessages", stats.enteredMessages);
        out.put("enteredOffers", stats.enteredOffers);
        out.put("alreadyEntered", stats.alreadyEntered);
        out.put("failed", stats.failed);
        out.put("skipped", stats.skipped);
        out.put("deferred", stats.deferred);
        out.put("message", "录入完成: " + stats.enteredGroups + " 组/" + stats.enteredMessages
                + " 条消息成功(物料 " + stats.enteredOffers + " 行), 已录入 " + stats.alreadyEntered
                + ", 失败 " + stats.failed + ", 跳过 " + stats.skipped
                + (stats.deferred > 0 ? ", 等待补充 " + stats.deferred : ""));
        return out;
    }

    /**
     * 录入主流程：同一会话、同一发送人在 offerEntryGroupGapSec 内的连续消息视为一组，
     * 引用消息作为料号上下文一并拼接，整组只调用一次 AI 录入，避免 INQ/OFFER 被拆开。
     */
    private EntryStats processEntryRows(List<WeChatMessage> rows, List<String> cats) {
        EntryStats stats = new EntryStats();
        if (rows == null || rows.isEmpty()) {
            return stats;
        }
        int timeout = entryProcessingTimeoutSec();
        int retryBaseSec = entryRetryBaseSec();
        int groupGapSec = Math.max(1, config.integer("offerEntryGroupGapSec", 30));
        Set<String> handled = new HashSet<>();
        for (WeChatMessage m : rows) {
            if (m == null || handled.contains(msgKey(m))) {
                continue;
            }
            if (OfferNoStockUtils.isNoStockResponse(m.getContent())) {
                markNoStockOnly(m, stats);
                continue;
            }
            EntryGroup group = buildEntryGroup(m, groupGapSec);
            handled.addAll(group.keys());
            WeChatMessage anchor = firstRetryableEntryMember(group, cats);
            if (anchor == null) {
                stats.skipped++;
                continue;
            }
            int waitSec = group.unsettledSec(groupGapSec);
            if (waitSec > 0) {
                stats.deferred++;
                log.info("消息组仍在补充中，等待{}秒后录入: chat={} sender={} 消息={}",
                        waitSec, anchor.getName(), anchor.getSender(), group.localIds());
                continue;
            }
            if (!store.markEntryProcessing(anchor.getUsername(), anchor.getLocalId(), timeout, retryBaseSec)) {
                continue;
            }
            try {
                enterGroup(group, anchor, cats, stats);
            } catch (Exception e) {
                handleEntryFailure(group, anchor, cats, e, stats);
            }
        }
        return stats;
    }

    /** 同一发送人、相邻时间差不超过 gapSec 的消息组；引用消息单独收集为上下文。 */
    private EntryGroup buildEntryGroup(WeChatMessage anchor, int gapSec) {
        EntryGroup group = new EntryGroup();
        group.members.add(anchor);
        long anchorMs = parseTime(anchor.getCreateTime());
        if (anchorMs <= 0) {
            collectQuotes(group);
            return group;
        }
        int quoteLinkSec = Math.max(gapSec, config.integer("offerEntryQuoteLinkSec", 1800));
        int windowSec = Math.max(300, Math.min(21600, quoteLinkSec));
        List<WeChatMessage> near = store.queryEntryNeighbors(anchor.getUsername(), anchor.getName(),
                anchor.getSender(), fmtTime(anchorMs - windowSec * 1000L), fmtTime(anchorMs + windowSec * 1000L));
        int idx = -1;
        for (int i = 0; near != null && i < near.size(); i++) {
            if (sameMessage(near.get(i), anchor)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            collectQuotes(group);
            return group;
        }
        List<WeChatMessage> chain = new ArrayList<>();
        chain.add(near.get(idx));
        long last = anchorMs;
        for (int i = idx - 1; i >= 0; i--) {
            long t = parseTime(near.get(i).getCreateTime());
            if (t <= 0 || last - t > gapSec * 1000L) {
                break;
            }
            if (!OfferNoStockUtils.isNoStockResponse(near.get(i).getContent())) {
                chain.add(0, near.get(i));
            }
            last = t;
        }
        last = anchorMs;
        for (int i = idx + 1; i < near.size(); i++) {
            long t = parseTime(near.get(i).getCreateTime());
            if (t <= 0 || t - last > gapSec * 1000L) {
                break;
            }
            if (!OfferNoStockUtils.isNoStockResponse(near.get(i).getContent())) {
                chain.add(near.get(i));
            }
            last = t;
        }
        group.members.clear();
        group.members.addAll(chain);
        collectQuotes(group);
        expandBySharedQuotes(group, near, gapSec * 1000L);
        collectQuotes(group);
        collectRecentEntryContext(group, anchor);
        group.members.sort(Comparator.comparingLong((WeChatMessage message) -> parseTime(message.getCreateTime()))
                .thenComparingLong(WeChatMessage::getLocalId));
        return group;
    }

    /**
     * 纯价格 OFFER 或纯数量 INQ 没有料号时，读取与分类一致的 aiContextWindow（默认最近 20 条）
     * 会话历史，交给 AI 结合上下文匹配料号。历史消息只作为上下文，不并入录入组，也不改变自身录入状态。
     */
    private void collectRecentEntryContext(EntryGroup group, WeChatMessage anchor) {
        if (anchor == null || !group.quotes.isEmpty()) {
            return;
        }
        String category = StringUtils.upperCase(StringUtils.trimToEmpty(anchor.getMsgCategory()));
        boolean priceOnlyOffer = "OFFER".equals(category) && isPriceOnlyContent(group.members);
        boolean quantityOnlyInq = "INQ".equals(category) && isQuantityOnlyContent(group.members);
        if (!priceOnlyOffer && !quantityOnlyInq) {
            return;
        }
        int contextWindow = Math.max(20, Math.min(100, config.integer("aiContextWindow", 20)));
        long beforeSortSeq = Long.MAX_VALUE;
        for (WeChatMessage member : group.members) {
            long seq = member == null ? 0L : member.getSortSeq();
            if (seq > 0L && seq < beforeSortSeq) {
                beforeSortSeq = seq;
            }
        }
        if (beforeSortSeq == Long.MAX_VALUE) {
            return;
        }
        List<WeChatMessage> recent = store.queryEntryContext(anchor.getUsername(), beforeSortSeq, contextWindow);
        if (recent == null || recent.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (EntryQuote quote : group.quotes) {
            if (StringUtils.isNotBlank(quote.id)) {
                seen.add("id:" + quote.id);
            }
            seen.add("c:" + quote.content);
        }
        for (WeChatMessage candidate : recent) {
            if (candidate == null || containsMember(group, candidate) || !isUsableEntryContext(candidate)) {
                continue;
            }
            String content = WeChatTextUtils.clip(candidate.getContent(), 800);
            String idKey = "id:" + candidate.getLocalId();
            String contentKey = "c:" + content;
            if (StringUtils.isBlank(content) || seen.contains(idKey) || seen.contains(contentKey)) {
                continue;
            }
            seen.add(idKey);
            seen.add(contentKey);
            group.quotes.add(new EntryQuote(String.valueOf(candidate.getLocalId()),
                    StringUtils.defaultString(candidate.getSender()), content));
        }
    }

    /** 当前 OFFER 组是否只有价格/数量和币种，没有可识别料号。 */
    private boolean isPriceOnlyContent(List<WeChatMessage> members) {
        StringBuilder text = new StringBuilder();
        for (WeChatMessage member : members) {
            String content = StringUtils.trimToEmpty(member.getContent());
            if (!content.isEmpty()) {
                text.append(' ').append(content);
            }
        }
        if (!text.toString().matches("(?s).*\\d.*")) {
            return false;
        }
        String rest = text.toString()
                .replaceAll("(?i)@[^\\s,，;；]+", " ")
                .replaceAll("(?i)USD|RMB|CNY|EUR|HKD|US\\$", " ")
                .replace("美元", " ").replace("美金", " ").replace("人民币", " ").replace("元", " ")
                .replaceAll("[0-9\\s.,，;；/、|\\-—–:：$¥￥€]", "");
        return rest.trim().isEmpty();
    }

    /** 当前 INQ 组是否只有数量，没有料号；例如对方在我方询问数量后单独回复“20pcs”。 */
    private boolean isQuantityOnlyContent(List<WeChatMessage> members) {
        StringBuilder text = new StringBuilder();
        for (WeChatMessage member : members) {
            String content = StringUtils.trimToEmpty(member.getContent());
            if (!content.isEmpty()) {
                text.append(' ').append(content);
            }
        }
        String raw = text.toString();
        if (!raw.matches("(?s).*\\d.*")) {
            return false;
        }
        String rest = raw
                .replaceAll("(?i)@[^\\s,，;；]+", " ")
                .replaceAll("(?i)(?:需要|要|来|拿|订|订购|采购|买)?\\s*\\d+(?:\\.\\d+)?\\s*"
                        + "(?:pcs?|pieces?|个|顆|颗|片|只|条|套|台|张)?", " ")
                .replaceAll("[0-9\\s.,，;；/、|\\-—–:：$¥￥€?？!！]", "");
        return rest.trim().isEmpty();
    }

    private boolean isUsableEntryContext(WeChatMessage message) {
        String content = StringUtils.trimToEmpty(message == null ? null : message.getContent());
        return !content.isEmpty() && !NON_TEXT_ONLY.matcher(content).matches()
                && !content.startsWith("<");
    }

    /**
     * 同一发送人对同一条引用消息的后续补充，不受 offerEntryGroupGapSec 的短间隔限制。
     * 例如先报“1010美金”，58 秒后再补“有240个 / WUH... / DC26”，同引用消息应交给 AI 一次解析。
     */
    private void expandBySharedQuotes(EntryGroup group, List<WeChatMessage> near, long gapMs) {
        if (near == null || near.isEmpty()) {
            return;
        }
        boolean changed;
        do {
            Set<Long> relationIds = collectRelationIds(group.members);
            if (relationIds.isEmpty()) {
                return;
            }
            changed = false;
            for (int i = 0; i < near.size(); i++) {
                WeChatMessage candidate = near.get(i);
                if (containsMember(group, candidate) || !sharesRelation(candidate, relationIds)) {
                    continue;
                }
                changed |= addContiguousMessages(group, near, i, gapMs);
            }
        } while (changed);
    }

    private Set<Long> collectRelationIds(List<WeChatMessage> messages) {
        Set<Long> ids = new HashSet<>();
        for (WeChatMessage message : messages) {
            addRelationId(ids, message.getRelatInq());
            addRelationId(ids, message.getRelatOffer());
        }
        return ids;
    }

    private void addRelationId(Set<Long> ids, String json) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            long id = jackson.readTree(json).path("id").asLong(0L);
            if (id > 0L) {
                ids.add(id);
            }
        } catch (Exception e) {
            log.debug("解析消息引用ID失败: {}", e.getMessage());
        }
    }

    private boolean sharesRelation(WeChatMessage message, Set<Long> relationIds) {
        Set<Long> candidateIds = new HashSet<>();
        addRelationId(candidateIds, message.getRelatInq());
        addRelationId(candidateIds, message.getRelatOffer());
        for (Long id : candidateIds) {
            if (relationIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean addContiguousMessages(EntryGroup group, List<WeChatMessage> near, int idx, long gapMs) {
        boolean changed = addMember(group, near.get(idx));
        long last = parseTime(near.get(idx).getCreateTime());
        for (int i = idx - 1; i >= 0; i--) {
            long time = parseTime(near.get(i).getCreateTime());
            if (time <= 0L || last - time > gapMs) {
                break;
            }
            changed |= addMember(group, near.get(i));
            last = time;
        }
        last = parseTime(near.get(idx).getCreateTime());
        for (int i = idx + 1; i < near.size(); i++) {
            long time = parseTime(near.get(i).getCreateTime());
            if (time <= 0L || time - last > gapMs) {
                break;
            }
            changed |= addMember(group, near.get(i));
            last = time;
        }
        return changed;
    }

    private static boolean addMember(EntryGroup group, WeChatMessage message) {
        if (containsMember(group, message) || OfferNoStockUtils.isNoStockResponse(message.getContent())) {
            return false;
        }
        group.members.add(message);
        return true;
    }

    private static boolean containsMember(EntryGroup group, WeChatMessage message) {
        for (WeChatMessage member : group.members) {
            if (sameMessage(member, message)) {
                return true;
            }
        }
        return false;
    }

    private void collectQuotes(EntryGroup group) {
        Set<Long> memberIds = new HashSet<>();
        for (WeChatMessage m : group.members) {
            memberIds.add(m.getLocalId());
        }
        Set<String> seen = new HashSet<>();
        for (WeChatMessage m : group.members) {
            addQuote(group, m.getRelatInq(), memberIds, seen);
            addQuote(group, m.getRelatOffer(), memberIds, seen);
        }
        for (WeChatMessage m : group.members) {
            String q = StringUtils.trimToEmpty(m.getQuote());
            if (q.isEmpty() || q.equals(StringUtils.trimToEmpty(m.getContent()))) {
                continue;
            }
            if (!seen.add("c:" + q)) {
                continue;
            }
            group.quotes.add(new EntryQuote("", "引用", WeChatTextUtils.clip(q, 800)));
        }
    }

    private void addQuote(EntryGroup group, String json, Set<Long> memberIds, Set<String> seen) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            JsonNode n = jackson.readTree(json);
            long id = n.path("id").asLong(0L);
            String content = StringUtils.trimToEmpty(n.path("content").asText(""));
            if (content.isEmpty() || (id > 0 && memberIds.contains(id))) {
                return;
            }
            String idKey = id > 0 ? "id:" + id : "";
            String contentKey = "c:" + content;
            if ((!idKey.isEmpty() && seen.contains(idKey)) || seen.contains(contentKey)) {
                return;
            }
            if (!idKey.isEmpty()) {
                seen.add(idKey);
            }
            seen.add(contentKey);
            group.quotes.add(new EntryQuote(id > 0 ? String.valueOf(id) : "",
                    n.path("sender").asText(""), WeChatTextUtils.clip(content, 800)));
        } catch (Exception e) {
            log.debug("解析引用消息失败: {}", e.getMessage());
        }
    }

    /** 引用/会话上下文放前面补全料号，连续消息按时间顺序放在后面，交给现有 AI 录入一次解析。 */
    private String buildGroupContent(EntryGroup group) {
        StringBuilder sb = new StringBuilder();
        if (!group.quotes.isEmpty()) {
            sb.append("【引用或最近会话上下文，仅用于补全料号/数量，不要单独输出为物料行；")
                    .append("优先采用距离本次价格最近、且料号数量与价格数量匹配的料号清单，更早历史仅辅助判断，不得拿无关旧料号配对；")
                    .append("当前消息只有价格时按该料号顺序与价格一一对应，历史中的其他报价不要重复输出；")
                    .append("当前消息只有数量时，按最近上下文中的料号补全本次INQ数量，历史价格不得当成本次报价；")
                    .append("价格数量少于上下文料号时，只为有价格对应的料号生成物料行，绝不为没有价格对应的料号补造物料行】\n");
            for (EntryQuote q : group.quotes) {
                sb.append(StringUtils.defaultIfBlank(q.sender, "对方")).append(": ").append(q.content).append('\n');
            }
        }
        sb.append("【本次连续消息】\n");
        for (WeChatMessage m : group.members) {
            prepareAttachment(m);
            String content = entryDisplayContent(m);
            if (content.isEmpty()) {
                continue;
            }
            sb.append(StringUtils.defaultIfBlank(m.getSender(), "未知")).append(": ").append(content).append('\n');
        }
        return sb.toString().trim();
    }

    /** 文件卡片不把原始 XML 交给录入模型；Excel报价附件使用提取后的有限文本。 */
    private String entryDisplayContent(WeChatMessage message) {
        if (hasUsableAttachment(message)) {
            return "【附件: " + StringUtils.defaultIfBlank(message.getAttachmentName(), "Excel/CSV")
                    + "】\n【Excel录入约束：只提取有明确料号且价格为正数的报价行；"
                    + "无价格、No stock、询价/缺货说明不要生成物料行】\n"
                    + message.getAttachmentText();
        }
        String content = StringUtils.trimToEmpty(message == null ? null : message.getContent());
        if (content.startsWith("<") && message != null && "文件/链接/卡片".equals(message.getType())) {
            String name = attachmentDisplayName(message);
            return StringUtils.isBlank(name) ? "【文件附件】" : "【文件附件: " + name + "】";
        }
        return content;
    }

    /**
     * 自动录入 Offer 后，按“复制 Offer”的自动勾选规则推送飞书待跟进：
     * CPU 料号全部选中，其他料号沿用价格优势阈值。分别发送报价与聊天记录两条消息。
     */
    private String notifyOfferFollowUp(EntryGroup group, WeChatMessage anchor, List<CrmOffer> saved,
                                       String supplierName) {
        if (!"OFFER".equalsIgnoreCase(StringUtils.trimToEmpty(anchor.getMsgCategory()))) {
            return "";
        }
        int maxAgeMin = Math.max(1, analysisInt("offerFollowUpMaxAgeMin", 360));
        if (!WeChatTextUtils.isRecent(anchor.getCreateTime(), maxAgeMin)) {
            log.info("历史OFFER不推送待跟进(超过{}分钟): chat={} localId={} time={}",
                    maxAgeMin, anchor.getName(), anchor.getLocalId(), anchor.getCreateTime());
            return "";
        }
        LinkedHashSet<String> savedPartNumbers = new LinkedHashSet<>();
        Set<String> cpuPartNumbers = new HashSet<>();
        for (CrmOffer row : saved) {
            if (row == null || StringUtils.isBlank(row.getProductCode())) {
                continue;
            }
            String partNumber = row.getProductCode().trim().toUpperCase(Locale.ROOT);
            savedPartNumbers.add(partNumber);
            if (isCpuProductType(row.getProductType())) {
                cpuPartNumbers.add(partNumber);
            }
        }
        if (savedPartNumbers.isEmpty()) {
            return "";
        }
        try {
            int days = 2;
            Map<String, Boolean> advantage = offerService.priceAdvantageBatch(
                    new ArrayList<>(savedPartNumbers), days);
            List<String> selectedPartNumbers = new ArrayList<>();
            for (String partNumber : savedPartNumbers) {
                if (cpuPartNumbers.contains(partNumber)
                        || Boolean.TRUE.equals(advantage.get(partNumber))) {
                    selectedPartNumbers.add(partNumber);
                }
            }
            if (selectedPartNumbers.isEmpty()) {
                log.info("微信自动录入Offer未达到待跟进条件: chat={} localId={} 料号={}",
                        anchor.getName(), anchor.getLocalId(), savedPartNumbers);
                return "";
            }

            String quoteText = offerService.buildAiQueryCopyText(
                    selectedPartNumbers, days, Collections.<String>emptyList());
            String threshold = BigDecimal.valueOf(offerService.getPriceAdvantageThreshold())
                    .stripTrailingZeros().toPlainString();
            StringBuilder quoteDetail = new StringBuilder();
            quoteDetail.append("【好价报价 Offer】\n")
                    .append("会话: ").append(StringUtils.defaultString(anchor.getName())).append('\n')
                    .append("供应商: ").append(StringUtils.defaultString(supplierName)).append('\n')
                    .append("微信消息ID: ").append(anchor.getLocalId()).append('\n')
                    .append("自动录入Offer ID: ").append(joinOfferIds(saved)).append('\n')
                    .append("价格优势阈值: ").append(threshold).append(" USD\n")
                    .append("选择规则: CPU料号默认全部选中；其他料号仅推送达到阈值的料号，每个料号取最近")
                    .append(days).append("天最低Offer。\n")
                    .append("本次待跟进料号: ").append(selectedPartNumbers.size()).append(" 个\n\n")
                    .append(clipFeishuText(quoteText, 16000));

            String eventBase = "OFFER_FOLLOW_UP|" + StringUtils.defaultString(anchor.getUsername())
                    + "|" + anchor.getLocalId();
            boolean quoteSent = aiChannelService.alert(eventBase + "|QUOTE", "Offer待跟进", quoteDetail.toString());
            String chatDetail = "【相关聊天记录】\n" + buildFollowUpChatHistory(group, anchor);
            boolean chatSent = aiChannelService.alert(eventBase + "|CHAT", "Offer待跟进",
                    clipFeishuText(chatDetail, 16000));
            if (!quoteSent || !chatSent) {
                log.warn("微信自动录入Offer飞书待跟进推送未全部成功: chat={} localId={} quoteSent={} chatSent={}",
                        anchor.getName(), anchor.getLocalId(), quoteSent, chatSent);
                return "；Offer待跟进飞书推送失败(报价" + (quoteSent ? "成功" : "失败")
                        + "/聊天记录" + (chatSent ? "成功" : "失败") + ")";
            }
            log.info("微信自动录入Offer已推送飞书待跟进: chat={} localId={} 料号={}",
                    anchor.getName(), anchor.getLocalId(), selectedPartNumbers);
            return "";
        } catch (Exception e) {
            log.warn("微信自动录入Offer待跟进推送异常(不影响已入库Offer): chat={} localId={}",
                    anchor.getName(), anchor.getLocalId(), e);
            return "；Offer待跟进飞书推送异常: " + safeMessage(e);
        }
    }

    /** 飞书跟进消息中的聊天记录：引用原文在前，本次连续消息按时间顺序在后。 */
    private String buildFollowUpChatHistory(EntryGroup group, WeChatMessage anchor) {
        StringBuilder sb = new StringBuilder();
        sb.append("会话: ").append(StringUtils.defaultString(anchor.getName())).append('\n')
                .append("分类: ").append(StringUtils.defaultString(anchor.getMsgCategory())).append('\n')
                .append("消息ID: ").append(group.localIds()).append('\n');
        if (!group.quotes.isEmpty()) {
            sb.append("\n【引用原消息】\n");
            for (EntryQuote quote : group.quotes) {
                sb.append(StringUtils.isNotBlank(quote.id) ? "#" + quote.id + " " : "")
                        .append(StringUtils.defaultIfBlank(quote.sender, "对方")).append(": ")
                        .append(quote.content).append('\n');
            }
        }
        sb.append("\n【本次连续消息】\n");
        for (WeChatMessage message : group.members) {
            prepareAttachment(message);
            sb.append(StringUtils.defaultIfBlank(message.getSender(), "未知"))
                    .append(" [").append(StringUtils.defaultString(message.getCreateTime())).append("]")
                    .append(" #").append(message.getLocalId()).append(": ")
                    .append(clipFeishuText(entryDisplayContent(message), 3000))
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /** 判断当前 Offer 是否属于 CPU；与复制 Offer 的 CPU 默认勾选口径一致。 */
    private boolean isCpuProductType(String productType) {
        if (StringUtils.isBlank(productType)) {
            return false;
        }
        for (String value : productType.split("[,，/、;；\\s]+")) {
            if ("CPU".equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 飞书文本上限保护，保留原始换行，避免报价/聊天记录被压成一行。 */
    private String clipFeishuText(String text, int max) {
        String value = StringUtils.trimToEmpty(text);
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private WeChatMessage firstRetryableEntryMember(EntryGroup group, List<String> cats) {
        for (WeChatMessage m : group.members) {
            if (isEntryCandidate(m, cats) && isEntryRetryable(m)) {
                return m;
            }
        }
        return null;
    }

    private boolean isEntryCandidate(WeChatMessage m, List<String> cats) {
        return m != null && m.getMsgCategory() != null
                && cats.contains(m.getMsgCategory().toUpperCase())
                && "SUCCESS".equalsIgnoreCase(StringUtils.trimToEmpty(m.getClassifyStatus()))
                && ("LLM".equalsIgnoreCase(m.getCategorySource())
                || "CACHE".equalsIgnoreCase(m.getCategorySource())
                || "MANUAL".equalsIgnoreCase(m.getCategorySource()));
    }

    private boolean isEntryRetryable(WeChatMessage m) {
        String status = StringUtils.trimToEmpty(m.getEntryStatus()).toUpperCase();
        return status.isEmpty() || "PENDING".equals(status) || "FAILED".equals(status)
                || "PROCESSING".equals(status);
    }

    private void enterGroup(EntryGroup group, WeChatMessage anchor, List<String> cats, EntryStats stats)
            throws Exception {
        List<WeChatMessage> entryMembers = new ArrayList<>();
        for (WeChatMessage m : group.members) {
            if (isEntryCandidate(m, cats)) {
                entryMembers.add(m);
            }
        }
        if (entryMembers.isEmpty()) {
            stats.skipped++;
            return;
        }

        // 组内任一消息已有 crm_offer，视为整组已经录入，避免补充消息重复生成报价行。
        List<CrmOffer> existing = null;
        WeChatMessage existingOwner = null;
        for (WeChatMessage m : entryMembers) {
            int count = offerMapper.countByWechat(StringUtils.defaultString(m.getServerId()),
                    StringUtils.defaultString(m.getUsername()), m.getLocalId());
            if (count > 0) {
                existing = offerMapper.selectByWechat(StringUtils.defaultString(m.getServerId()),
                        StringUtils.defaultString(m.getUsername()), m.getLocalId());
                existingOwner = m;
                break;
            }
        }
        if (existing != null && !existing.isEmpty()) {
            stats.alreadyEntered++;
            String ids = joinOfferIds(existing);
            String message = "聚合组已存在微信来源crm_offer(消息#" + existingOwner.getLocalId() + ")，幂等跳过";
            for (WeChatMessage m : entryMembers) {
                if (!isEntryRetryable(m)) {
                    continue;
                }
                store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SUCCESS", message,
                        existing.size(), ids, 0);
                if (!sameMessage(m, anchor)) {
                    store.updateTaskState(m.getUsername(), m.getLocalId(), "SKIPPED",
                            "MERGED_ENTRY:" + anchor.getLocalId());
                }
            }
            return;
        }

        PartyMatch party = partyService.resolve(anchor.getName());
        if (party.isUnmappedOrAmbiguous()) {
            String prefix = PartyMatch.UNMAPPED.equals(party.getStatus()) ? "群未映射" : "群映射冲突";
            markGroupSkipped(group, anchor, prefix + "：" + StringUtils.defaultString(party.getMessage()),
                    "PENDING", "UNMAPPED_CHAT", stats);
            return;
        }
        if (!party.isOk()) {
            markGroupSkipped(group, anchor, party.getStatus() + ": " + StringUtils.defaultString(party.getMessage()),
                    "MANUAL_REVIEW", party.getStatus(), stats);
            return;
        }

        boolean self = WeChatTextUtils.isSelf(anchor.getSender(), selfNames());
        boolean offer = "OFFER".equalsIgnoreCase(anchor.getMsgCategory());
        if (self && offer && !hasExternalCustomerInq(anchor)) {
            markGroupSkipped(group, anchor, "我方外发OFFER无客户INQ，完全人工处理",
                    "SKIPPED", "SELF_OUTBOUND_ONLY", stats);
            return;
        }

        CrmOfferPriceMode priceMode = CrmOfferPriceMode.NONE;
        String inqOfferType = "Inq";
        if (offer && self) {
            priceMode = CrmOfferPriceMode.AS_IS;
            inqOfferType = "Offer";
        } else if (offer) {
            priceMode = CrmOfferPriceMode.COST_PLUS_PROFIT;
            inqOfferType = "Offer";
        }

        String content = buildGroupContent(group);
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("物料内容不能为空");
        }
        List<CrmOffer> saved = offerService.aiEntryOffersReturningAsSystem(
                party.getSupplier().getSupplierCode(), party.getSupplier().getSupplierName(),
                inqOfferType, party.getSupplier().getProfitRatio() != null
                        ? party.getSupplier().getProfitRatio()
                        : config.decimal("offerEntryProfitRatio", 2d),
                content, priceMode, config.str("offerEntryCreateBy", "wechat_auto"),
                StringUtils.defaultString(anchor.getServerId()), StringUtils.defaultString(anchor.getUsername()),
                anchor.getLocalId(), null);
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
                    anchor.getName(), anchor.getServerId(), ce);
        }
        String followUpError = notifyOfferFollowUp(group, anchor, saved,
                party.getSupplier().getSupplierName());
        String entryStatus = compareError.isEmpty() && followUpError.isEmpty() ? "SUCCESS" : "SUCCESS_WARN";
        String entryMessage = "聚合录入[" + (group.members.size() + group.quotes.size()) + "条]: 消息#" + anchor.getLocalId()
                + "; AI录入: " + saved.size() + "行; 比价: " + compareRows + "条" + compareError + followUpError;
        stats.enteredGroups++;
        stats.enteredMessages += group.members.size() + group.quotes.size();
        stats.enteredOffers += saved.size();
        String offerIds = joinOfferIds(saved);
        for (WeChatMessage m : entryMembers) {
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), entryStatus,
                    clipEntryMessage(entryMessage), saved.size(), offerIds, 0);
            if (!sameMessage(m, anchor)) {
                store.updateTaskState(m.getUsername(), m.getLocalId(), "SKIPPED",
                        "MERGED_ENTRY:" + anchor.getLocalId());
            }
        }
        log.info("微信消息已聚合录入crm_offer: chat={} sender={} 消息={} type={} priceMode={} 物料{}行 状态={}",
                anchor.getName(), anchor.getSender(), group.localIds(), inqOfferType, priceMode, saved.size(), entryStatus);
    }

    /**
     * 群映射类跳过只锁定主消息，其余组员保持待录入；待群映射补齐后还能从组员继续重试，
     * 不会因为整组置 SKIPPED 而永久丢失。
     */
    private void markNoStockOnly(WeChatMessage message, EntryStats stats) {
        stats.skipped++;
        String entryMessage = "缺货/无库存响应，不录入Offer";
        store.updateEntryOnly(message.getUsername(), message.getLocalId(), "SKIPPED",
                entryMessage, 0, "", 0);
        store.updateTaskState(message.getUsername(), message.getLocalId(), "SKIPPED", "NO_STOCK", "");
        log.info("微信消息为明确缺货响应，跳过Offer录入: chat={} sender={} localId={}",
                message.getName(), message.getSender(), message.getLocalId());
    }

    private void markGroupSkipped(EntryGroup group, WeChatMessage anchor, String entryMessage,
                                  String anchorTaskStatus, String anchorTaskMessage, EntryStats stats) {
        stats.skipped++;
        store.updateEntryOnly(anchor.getUsername(), anchor.getLocalId(), "SKIPPED",
                clipEntryMessage(entryMessage), 0, "", 0);
        String failureReason = "MANUAL_REVIEW".equals(anchorTaskStatus) ? clipEntryMessage(entryMessage) : "";
        store.updateTaskState(anchor.getUsername(), anchor.getLocalId(), anchorTaskStatus,
                anchorTaskMessage, failureReason);
        log.warn("微信消息组录入业务跳过: chat={} sender={} 消息={} 原因={}",
                anchor.getName(), anchor.getSender(), group.localIds(), entryMessage);
    }

    private void handleEntryFailure(EntryGroup group, WeChatMessage anchor, List<String> cats, Exception e,
                                    EntryStats stats) {
        String reason = safeMessage(e);
        List<WeChatMessage> targets = new ArrayList<>();
        for (WeChatMessage m : group.members) {
            if (isEntryCandidate(m, cats) && isEntryRetryable(m)) {
                targets.add(m);
            }
        }
        if (isPermanentEntrySkip(e)) {
            stats.skipped++;
            for (WeChatMessage m : targets) {
                store.updateEntryOnly(m.getUsername(), m.getLocalId(), "SKIPPED",
                        clipEntryMessage("需人工处理: " + reason), 0, "", 0);
                if (sameMessage(m, anchor)) {
                    store.updateTaskState(m.getUsername(), m.getLocalId(), "MANUAL_REVIEW", "ENTRY_BUSINESS_SKIP",
                            clipEntryMessage("AI录入业务跳过：" + reason));
                } else {
                    store.updateTaskState(m.getUsername(), m.getLocalId(), "SKIPPED",
                            "MERGED_ENTRY:" + anchor.getLocalId());
                }
            }
            log.warn("微信消息组录入业务跳过: chat={} sender={} 消息={} 原因={}",
                    anchor.getName(), anchor.getSender(), group.localIds(), reason);
            return;
        }
        int retryMax = entryRetryMax();
        int retryBaseSec = entryRetryBaseSec();
        stats.failed++;
        for (WeChatMessage m : targets) {
            int failCnt = (m.getEntryFailCount() == null ? 0 : m.getEntryFailCount()) + 1;
            boolean dead = WeChatEntryRetryPolicy.exhausted(failCnt, retryMax);
            int delaySec = WeChatEntryRetryPolicy.delaySeconds(failCnt, retryBaseSec);
            String entryMessage = dead
                    ? "AI录入失败(已尝试" + WeChatEntryRetryPolicy.maxAttempts(retryMax) + "次，重试耗尽): " + reason
                    : "AI录入失败(第" + failCnt + "次，约" + delaySec + "秒后自动重试"
                    + failCnt + "/" + retryMax + "): " + reason;
            store.updateEntryOnly(m.getUsername(), m.getLocalId(), dead ? "DEAD" : "FAILED",
                    clipEntryMessage(entryMessage), 0, "", failCnt);
            if (dead) {
                store.updateTaskState(m.getUsername(), m.getLocalId(), "MANUAL_REVIEW",
                        "录入重试耗尽，已停止自动重试",
                        clipEntryMessage("AI录入连续失败(" + failCnt + "次)：" + reason));
            }
        }
        log.warn("微信消息组AI录入失败: chat={} sender={} 消息={} 重试上限={} 原因={}",
                anchor.getName(), anchor.getSender(), group.localIds(), retryMax, reason);
    }

    private static boolean sameMessage(WeChatMessage a, WeChatMessage b) {
        return a != null && b != null && StringUtils.equals(a.getUsername(), b.getUsername())
                && a.getLocalId() == b.getLocalId();
    }

    private static String msgKey(WeChatMessage m) {
        return StringUtils.defaultString(m.getUsername()) + ":" + m.getLocalId();
    }

    private static long parseTime(String time) {
        if (StringUtils.isBlank(time)) {
            return 0L;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(time.trim()).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static final class EntryGroup {
        private final List<WeChatMessage> members = new ArrayList<>();
        private final List<EntryQuote> quotes = new ArrayList<>();

        private List<String> keys() {
            List<String> keys = new ArrayList<>();
            for (WeChatMessage m : members) {
                keys.add(msgKey(m));
            }
            return keys;
        }

        private String localIds() {
            StringBuilder sb = new StringBuilder();
            for (WeChatMessage m : members) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(m.getLocalId());
            }
            return sb.toString();
        }

        /** 返回还需等待的秒数：组内最新消息未满 gapSec 时先不录入。 */
        private int unsettledSec(int gapSec) {
            long newest = 0L;
            for (WeChatMessage m : members) {
                newest = Math.max(newest, parseTime(m.getCreateTime()));
            }
            if (newest <= 0L) {
                return 0;
            }
            long age = (System.currentTimeMillis() - newest) / 1000L;
            return age < gapSec ? (int) (gapSec - age) : 0;
        }
    }

    private static final class EntryQuote {
        private final String id;
        private final String sender;
        private final String content;

        private EntryQuote(String id, String sender, String content) {
            this.id = id;
            this.sender = sender;
            this.content = content;
        }
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
        private int enteredGroups;
        private int enteredMessages;
        private int enteredOffers;
        private int alreadyEntered;
        private int failed;
        private int skipped;
        private int deferred;
    }

    /** 业务前置条件不满足时转人工，不消耗自动重试次数。 */
    private boolean isPermanentEntrySkip(Exception e) {
        String msg = safeMessage(e);
        return msg.contains("未能从内容中识别出料号")
                || msg.contains("未从内容中识别出有效料号")
                || msg.contains("物料内容不能为空")
                || msg.contains("AI未从内容中识别出物料信息")
                || msg.contains("未从内容中识别出有效的物料信息")
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

    /** offerEntryMaxFail 保留原字典键，语义为首次失败后的自动重试次数，默认3次。 */
    private int entryRetryMax() {
        return WeChatEntryRetryPolicy.normalizeMaxRetries(analysisInt("offerEntryMaxFail",
                WeChatEntryRetryPolicy.DEFAULT_MAX_RETRIES));
    }

    private int entryRetryBaseSec() {
        return WeChatEntryRetryPolicy.normalizeBaseDelaySec(analysisInt("offerEntryRetryBaseSec",
                WeChatEntryRetryPolicy.DEFAULT_BASE_DELAY_SEC));
    }

    /** 录入解锁时限必须大于AI总超时，避免模型仍在返回时被下一轮扫描误判为遗留锁。 */
    private int entryProcessingTimeoutSec() {
        int configured = Math.max(60, Math.min(1800,
                analysisInt("offerEntryProcessingTimeoutSec", 360)));
        int aiFloor = Math.max(60, Math.min(1800, (aiChannelService.timeoutMs() + 999) / 1000 + 60));
        return Math.max(configured, aiFloor);
    }

    /** 09:00-21:30 每10分钟检查一次近期分类、录入和任务失败，达到阈值后推送飞书。 */
    public Map<String, Object> alertFailureBurst() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = config.bool("failureAlertEnabled", true);
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
        boolean inWindow = !now.isBefore(LocalTime.of(9, 0)) && !now.isAfter(LocalTime.of(21, 30));
        out.put("enabled", enabled);
        out.put("inWindow", inWindow);
        out.put("checkedAt", fmtTime(System.currentTimeMillis()));
        if (!enabled) {
            out.put("alertSent", false);
            out.put("message", "失败聚合告警已关闭(failureAlertEnabled=false)");
            return out;
        }
        if (!inWindow) {
            out.put("alertSent", false);
            out.put("message", "当前不在失败聚合告警时段(09:00-21:30)");
            return out;
        }

        int windowMin = Math.max(10, Math.min(180, analysisInt("failureAlertWindowMin", 30)));
        int threshold = Math.max(1, Math.min(100, analysisInt("failureAlertThreshold", 3)));
        String since = fmtTime(System.currentTimeMillis() - windowMin * 60_000L);
        Map<String, Object> stats = store.failureBurstStats(since);
        int classifyFailed = mapInt(stats, "classify_failed");
        int entryFailed = mapInt(stats, "entry_failed");
        int entryDead = mapInt(stats, "entry_dead");
        int taskFailed = mapInt(stats, "task_failed");
        int total = classifyFailed + entryFailed + entryDead + taskFailed;
        out.put("windowMin", windowMin);
        out.put("threshold", threshold);
        out.put("classifyFailed", classifyFailed);
        out.put("entryFailed", entryFailed);
        out.put("entryDead", entryDead);
        out.put("taskFailed", taskFailed);
        out.put("totalFailed", total);
        if (total < threshold) {
            out.put("alertSent", false);
            out.put("message", "最近" + windowMin + "分钟失败" + total + "条，未达到告警阈值" + threshold);
            return out;
        }

        List<Map<String, Object>> samples = store.recentFailureSamples(since, 10);
        StringBuilder detail = new StringBuilder();
        detail.append("统计窗口: 最近").append(windowMin).append("分钟\n")
                .append("告警时段: 09:00-21:30，每10分钟最多一次\n")
                .append("分类失败: ").append(classifyFailed).append(" 条\n")
                .append("录入失败: ").append(entryFailed).append(" 条\n")
                .append("录入重试耗尽(DEAD): ").append(entryDead).append(" 条\n")
                .append("任务生成失败: ").append(taskFailed).append(" 条\n")
                .append("合计: ").append(total).append(" 条，告警阈值 ").append(threshold).append(" 条\n");
        if (!samples.isEmpty()) {
            detail.append("\n最近失败明细:\n");
            for (Map<String, Object> row : samples) {
                detail.append("- #").append(mapText(row, "id")).append(' ')
                        .append(mapText(row, "failure_type")).append(" | ")
                        .append(mapText(row, "name")).append(" / ")
                        .append(mapText(row, "sender")).append(" / ")
                        .append(mapText(row, "msg_category")).append('\n')
                        .append("  ").append(mapText(row, "failure_detail")).append('\n');
            }
        }
        detail.append("\n处理建议: 到“微信消息列表”按分类/录入/任务状态筛选，修正配置或数据后重新入队。");

        String event = "WECHAT_FAILURE|" + (System.currentTimeMillis() / 600_000L);
        boolean sent = aiChannelService.alert(event,
                "【微信自动化-失败告警】最近" + windowMin + "分钟失败 " + total + " 条",
                detail.toString());
        out.put("alertSent", sent);
        out.put("message", sent ? "失败聚合告警已发送" : "失败聚合告警发送失败，请检查 sys.ai.alert.webhook");
        return out;
    }

    private int mapInt(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null && map != null) {
            value = map.get(key.toUpperCase(Locale.ROOT));
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String mapText(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null && map != null) {
            value = map.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? "" : String.valueOf(value);
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
        int retryMax = entryRetryMax();
        int retryBaseSec = entryRetryBaseSec();
        m.put("maxFail", retryMax);
        m.put("retryMax", retryMax);
        m.put("maxAttempts", WeChatEntryRetryPolicy.maxAttempts(retryMax));
        m.put("retryBaseSec", retryBaseSec);
        m.put("retryPolicy", "指数退避: " + retryBaseSec + "s/" + (retryBaseSec * 2)
                + "s/" + (retryBaseSec * 4) + "s，最多重试" + retryMax + "次");
        m.put("processingTimeoutSec", entryProcessingTimeoutSec());
        m.put("selfNames", analysisStr("selfNames", ""));
        m.put("createBy", analysisStr("offerEntryCreateBy", "wechat_auto"));
        int minAgeSec = Math.max(0, analysisInt("offerEntryMinAgeSec", 30));
        int groupGapSec = Math.max(1, analysisInt("offerEntryGroupGapSec", 30));
        int quoteLinkSec = Math.max(groupGapSec, analysisInt("offerEntryQuoteLinkSec", 1800));
        int maxAgeHours = Math.max(1, analysisInt("offerEntryMaxAgeHours", 24));
        m.put("minAgeSec", minAgeSec);
        m.put("groupGapSec", groupGapSec);
        m.put("quoteLinkSec", quoteLinkSec);
        m.put("maxAgeHours", maxAgeHours);
        m.put("contextWindow", Math.max(20, Math.min(100, analysisInt("aiContextWindow", 20))));
        m.put("aiFlow", "同发送人连续消息 + 同引用补充消息聚合；纯价格OFFER、纯数量INQ补带最近 aiContextWindow 条会话上下文 -> 单次AI录入 -> 返回料号比价");
        m.put("entryStatusLegend",
                "PENDING=待录入/可重试, PROCESSING=处理中, SUCCESS=成功, SUCCESS_WARN=已录入但比价告警, "
                        + "FAILED=退避等待中, DEAD=重试耗尽, SKIPPED=需人工确认, NOT_APPLICABLE=不参与录入");
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

    /** 任务生成开关与扫描参数，供微信消息页解释 task_status=PENDING 的等待原因。 */
    public Map<String, Object> taskGenerateStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", analysisBool("autoTaskGenerateEnabled", false));
        m.put("scanSec", analysisInt("taskGenerateScanSec", 60));
        m.put("batchSize", analysisInt("taskGenerateBatchSize", 20));
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
                && !"CACHE".equalsIgnoreCase(m.getCategorySource())
                && !"MANUAL".equalsIgnoreCase(m.getCategorySource())) {
            return false;
        }
        if (StringUtils.isBlank(m.getRelatInq()) && StringUtils.isBlank(m.getRelatOffer())) {
            return false;
        }
        int maxAgeMin = analysisInt("traceAlertMaxAgeMin", 360);
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
                    if (store.markTraceAlertSuppressed(m.getUsername(), m.getLocalId())) {
                        m.setTraceAlertSuppressedAt(fmtTime(System.currentTimeMillis()));
                    }
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
        int maxAgeMin = Math.max(1, analysisInt("traceAlertMaxAgeMin", 360));
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
