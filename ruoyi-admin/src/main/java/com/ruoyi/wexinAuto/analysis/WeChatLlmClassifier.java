package com.ruoyi.wexinAuto.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.crm.service.ai.AiChannelService;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 大模型(AI)分类器：模仿 CRM「Offer AI 录入」的调用方式——
 * 提示词(基础规则+few-shot示例)放数据字典可随时调优，temperature=0，
 * json_object 输出；同一会话的待判消息按批调用，并把该会话最近
 * {@code aiContextWindow} 条已分类消息作为上下文一起给模型。
 *
 * <p>分类专属参数来自数据字典 {@code wechat_analysis_config}：llmBatchSize / llmConcurrency /
 * llmTimeoutSec / aiContextWindow / llmPromptBase(留空用内置默认) / llmExamples(留空用内置默认)。</p>
 * <p>大模型渠道配置（地址/Key/模型/备用渠道与故障切换）统一由 {@link AiChannelService}
 * 从系统参数 {@code sys.ai.*} 读取，本类不再单独维护连接信息。</p>
 */
@Slf4j
@Component
public class WeChatLlmClassifier {

    /** 消息分析参数数据字典类型 */
    private static final String DICT_TYPE = "wechat_analysis_config";

    private static final Set<String> CATS =
            new HashSet<>(Arrays.asList("INQ", "OFFER", "TRACE", "OTHER"));

    /** 单条消息送模型的正文长度上限(超长保头尾) */
    private static final int CONTENT_MAX = 1200;

    private static final int CACHE_MAX = 50000;

    private final ObjectMapper jackson = new ObjectMapper();

    /** AI 通道统一服务：渠道配置(sys.ai.*) + 主/备渠道故障切换，与 Offer AI 录入共用 */
    @Autowired
    private AiChannelService aiChannelService;

    /** 分类结果缓存：key=username|localId，value=INQ/OFFER/TRACE/OTHER */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // 字典配置
    // ------------------------------------------------------------------
    private String cfg(String label, String def) {
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel())
                        && d.getDictValue() != null && !d.getDictValue().trim().isEmpty()) {
                    return d.getDictValue().trim();
                }
            }
        }
        return def;
    }

    private int cfgInt(String label, int def) {
        try {
            return Integer.parseInt(cfg(label, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean cfgBool(String label, boolean def) {
        String v = cfg(label, null);
        if (v == null) {
            return def;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private Set<String> cfgNames(String label) {
        Set<String> out = new HashSet<>();
        String v = cfg(label, "");
        if (StringUtils.isNotBlank(v)) {
            for (String n : v.split(",")) {
                if (StringUtils.isNotBlank(n)) {
                    out.add(n.trim());
                }
            }
        }
        return out;
    }

    /** 是否启用 AI 分类：由统一通道配置决定（sys.ai.enabled + sys.ai.api.key）。 */
    public boolean enabled() {
        return aiChannelService.enabled();
    }

    // ------------------------------------------------------------------
    // 缓存
    // ------------------------------------------------------------------
    private String cacheKey(WeChatMessage m) {
        return (m.getUsername() == null ? "" : m.getUsername()) + "|" + m.getLocalId();
    }

    /** 查询缓存分类(无则 null)。 */
    public String cached(WeChatMessage m) {
        return cache.get(cacheKey(m));
    }

    private void put(WeChatMessage m, String cat) {
        if (m == null || cat == null) {
            return;
        }
        cache.put(cacheKey(m), cat);
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
    }

    /** 清空分类缓存：修改提示词/语义规则后重跑历史消息前调用。 */
    public synchronized void clearCache() {
        cache.clear();
    }

    // ------------------------------------------------------------------
    // 批分类主入口
    // ------------------------------------------------------------------
    /**
     * 对同一会话的待判消息做 AI 分类(时间正序)。
     *
     * @param context  该会话最近已分类消息(作为上下文给模型，可空)
     * @param pending  待判消息(每条都会尝试让模型给出分类；失败保留 null 由定时任务重试)
     * @param chatName 会话名
     */
    public void classify(List<WeChatMessage> context, List<WeChatMessage> pending, String chatName) {
        if (pending == null || pending.isEmpty() || !enabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(20, cfgInt("llmBatchSize", 20)));
        int concurrency = Math.max(1, Math.min(4, cfgInt("llmConcurrency", 2)));
        // 业务要求：必须结合最近20条原始消息判断省略句，配置不得低于20条。
        int ctxWindow = Math.max(20, Math.min(100, cfgInt("aiContextWindow", 20)));
        String promptBase = cfg("llmPromptBase", "");
        String examples = cfg("llmExamples", "");
        int timeoutMs = Math.max(10, cfgInt("llmTimeoutSec", 180)) * 1000;

        List<WeChatMessage> ctx = new ArrayList<>();
        if (context != null) {
            for (int i = Math.max(0, context.size() - ctxWindow); i < context.size(); i++) {
                WeChatMessage m = context.get(i);
                if (m != null) {
                    ctx.add(m);
                }
            }
        }

        List<List<WeChatMessage>> batches = partition(pending, batchSize);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "wechat-llm");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                final List<WeChatMessage> batch = batches.get(batchIndex);
                // 每批都带独立滑动窗口：前一批及更早待判消息也要进入后一批上下文，
                // 否则超过 llmBatchSize 时，后批 OFFER/TRACE 看不到紧邻的 INQ。
                final List<WeChatMessage> batchContext = batchContext(ctx, pending,
                        batchIndex * batchSize, ctxWindow);
                futures.add(pool.submit(() ->
                        classifyBatch(batch, batchContext, chatName, promptBase, examples, timeoutMs)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(20, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("AI 批分类异常: {}", e.toString());
                }
            }
            refineSemantics(context, pending, chatName);
            for (WeChatMessage m : pending) {
                if (StringUtils.isNotBlank(m.getMsgCategory())) {
                    put(m, m.getMsgCategory());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 构造某一批待判消息之前的最近 N 条上下文（基础上下文 + 此前所有待判原文）。 */
    private static List<WeChatMessage> batchContext(List<WeChatMessage> base,
                                                     List<WeChatMessage> pending,
                                                     int endExclusive, int max) {
        if (max <= 0) {
            return Collections.emptyList();
        }
        List<WeChatMessage> all = new ArrayList<>();
        if (base != null) {
            all.addAll(base);
        }
        for (int i = 0; i < endExclusive && i < pending.size(); i++) {
            all.add(pending.get(i));
        }
        int from = Math.max(0, all.size() - max);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    private List<List<WeChatMessage>> partition(List<WeChatMessage> list, int size) {
        List<List<WeChatMessage>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return out;
    }

    /** AI 结果后的业务语义校验：INQ/OFFER 与发送方无关，只校验业务意图和上下文关联。 */
    private void refineSemantics(List<WeChatMessage> context, List<WeChatMessage> pending, String chatName) {
        if (!cfgBool("strictBusinessSemantics", true)) {
            return;
        }
        int offerMaxAgeMin = Math.max(1, cfgInt("offerLinkMaxAgeMin", 180));
        int offerShortWindowMin = Math.max(1, cfgInt("offerShortReplyWindowMin", 30));
        int traceMaxAgeMin = Math.max(1, cfgInt("traceLinkMaxAgeMin", 180));
        int traceShortWindowMin = Math.max(1, cfgInt("traceShortWindowMin", 30));
        int contextWindow = Math.max(20, Math.min(100, cfgInt("aiContextWindow", 20)));
        List<WeChatMessage> seq = new ArrayList<>();
        if (context != null) {
            seq.addAll(context);
        }
        seq.addAll(pending);
        seq.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
        boolean groupChat = !seq.isEmpty() && SemanticMatcher.isGroupChat(seq.get(0));
        Set<WeChatMessage> pendingSet = Collections.newSetFromMap(
                new IdentityHashMap<WeChatMessage, Boolean>());
        pendingSet.addAll(pending);

        List<WeChatMessage> recentInqs = new ArrayList<>();
        List<WeChatMessage> recentOffers = new ArrayList<>();
        for (WeChatMessage m : seq) {
            String cat = norm(m.getMsgCategory());
            if (cat == null) {
                continue;
            }
            if (pendingSet.contains(m)) {
                String refined = cat;
                if ("INQ".equals(cat)) {
                    if (!SemanticMatcher.looksLikeInquiry(m.getContent())) {
                        refined = downgrade(m, "语义校验:无明确询价意图");
                    }
                } else if ("OFFER".equals(cat)) {
                    WeChatMessage relatedInq = findRelatedInq(m, recentInqs, groupChat,
                            offerMaxAgeMin, offerShortWindowMin);
                    if (relatedInq == null) {
                        refined = downgrade(m, "语义校验:未找到对向INQ的直接回应，按广播报价排除");
                    }
                } else if ("TRACE".equals(cat)) {
                    boolean related = hasRelatedRef(m, recentInqs, recentOffers, groupChat,
                            traceMaxAgeMin, traceShortWindowMin);
                    if (!related) {
                        refined = downgrade(m, "语义校验:未关联到具体INQ/OFFER");
                    }
                }
                if (!refined.equals(cat)) {
                    log.debug("分类语义修正: chat={} sender={} {} -> {} ({})", chatName, m.getSender(),
                            cat, refined, m.getCategoryReason());
                    cat = refined;
                }
            }
            if ("INQ".equals(cat) && SemanticMatcher.looksLikeInquiry(m.getContent())) {
                rememberRecent(recentInqs, m, contextWindow);
            } else if ("OFFER".equals(cat)) {
                rememberRecent(recentOffers, m, contextWindow);
            }
        }
    }

    private static WeChatMessage findRelatedInq(WeChatMessage offer, List<WeChatMessage> inqs,
                                                boolean groupChat, int maxAgeMin, int shortWindowMin) {
        for (int i = inqs.size() - 1; i >= 0; i--) {
            WeChatMessage inq = inqs.get(i);
            if (SemanticMatcher.isReplyTo(offer, inq, groupChat, maxAgeMin, shortWindowMin)) {
                return inq;
            }
        }
        return null;
    }

    private static boolean hasRelatedRef(WeChatMessage trace, List<WeChatMessage> inqs,
                                         List<WeChatMessage> offers, boolean groupChat,
                                         int maxAgeMin, int shortWindowMin) {
        for (int i = inqs.size() - 1; i >= 0; i--) {
            if (SemanticMatcher.isFollowUpTo(trace, inqs.get(i), groupChat, maxAgeMin, shortWindowMin)) {
                return true;
            }
        }
        for (int i = offers.size() - 1; i >= 0; i--) {
            if (SemanticMatcher.isFollowUpTo(trace, offers.get(i), groupChat, maxAgeMin, shortWindowMin)) {
                return true;
            }
        }
        return false;
    }

    private static void rememberRecent(List<WeChatMessage> list, WeChatMessage message, int max) {
        list.add(message);
        while (list.size() > max) {
            list.remove(0);
        }
    }

    private static String norm(String cat) {
        if (StringUtils.isBlank(cat)) {
            return null;
        }
        String c = cat.trim().toUpperCase();
        return CATS.contains(c) ? c : null;
    }

    private static String downgrade(WeChatMessage m, String why) {
        String old = m.getCategoryReason();
        m.setMsgCategory("OTHER");
        m.setCategorySource("SEMANTIC");
        m.setRelatInq(null);
        m.setRelatOffer(null);
        m.setCategoryReason(SemanticMatcher.clip(
                (StringUtils.isBlank(old) ? "" : old + "；") + why, 300));
        return "OTHER";
    }

    // ------------------------------------------------------------------
    // 单批调用与解析
    // ------------------------------------------------------------------
    private void classifyBatch(List<WeChatMessage> batch, List<WeChatMessage> ctx, String chatName,
                               String promptBase, String examples, int timeoutMs) {
        String prompt = buildPrompt(batch, ctx, chatName);
        String sysPrompt = buildSystemPrompt(promptBase, examples);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("temperature", 0);
        payload.put("max_tokens", 4000);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", sysPrompt);
        messages.add(sys);
        Map<String, String> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", prompt);
        messages.add(user);
        payload.put("messages", messages);
        String respText;
        try {
            // 统一通道调用：第三方平台故障时自动切换 DeepSeek 官方备用渠道
            respText = aiChannelService.chatCompletions(payload, timeoutMs);
        } catch (Exception e) {
            log.warn("AI 无结果：{} 条待判消息保留待重试 | chat={} | {}", batch.size(), chatName, e.getMessage());
            return;
        }
        Map<Long, String> parsed = parse(respText, batch);
        for (WeChatMessage m : batch) {
            String cat = parsed.get(m.getLocalId());
            if (cat != null) {
                m.setMsgCategory(cat);
                m.setCategorySource("LLM");
            }
        }
    }

    private Map<Long, String> parse(String respText, List<WeChatMessage> batch) {
        Map<Long, String> out = new LinkedHashMap<>();
        try {
            JsonNode root = jackson.readTree(respText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode arr = extractArray(content);
            if (arr == null || !arr.isArray()) {
                log.warn("AI 返回不可解析: {}", snippet(content));
                return out;
            }
            for (JsonNode item : arr) {
                int idx = item.path("i").asInt(-1);
                String cat = item.path("cat").asText("").trim().toUpperCase();
                String why = item.path("why").asText("").trim();
                if (idx >= 0 && idx < batch.size() && CATS.contains(cat)) {
                    WeChatMessage m = batch.get(idx);
                    out.put(m.getLocalId(), cat);
                    if (StringUtils.isNotBlank(why)) {
                        m.setCategoryReason(why.length() > 300 ? why.substring(0, 300) : why);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI 响应解析失败: {}", e.toString());
        }
        return out;
    }

    private JsonNode extractArray(String content) {
        if (content == null) {
            return null;
        }
        String t = content.trim();
        int s = t.indexOf('[');
        int e = t.lastIndexOf(']');
        if (s < 0 || e <= s) {
            return null;
        }
        try {
            return jackson.readTree(t.substring(s, e + 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    // ------------------------------------------------------------------
    // 提示词
    // ------------------------------------------------------------------
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是微信买卖询报价业务消息分类专家。用户会把一个聊天会话最近的20条消息给你" +
            "（每条含序号、发言人、时间、内容，开头可能带【上下文】=该条之前最近的消息）。" +
            "请严格按以下业务定义对【每条待判消息】输出唯一分类：\n" +
            "INQ=任何一方提出询价：问型号/数量/价格/库存/交期，或明确 RFQ、求购、找货。我方也可以向供应商发INQ。\n" +
            "OFFER=对某条INQ的对向回应：必须能对上前文料号或数量，并给出价格/交期/库存等报价信息。客户也可以向我方发送OFFER。\n" +
            "TRACE=围绕已有INQ/OFFER的后续业务沟通：可以是疑问句，也可以是价格确认、数量调整、交期确认、下单/备货安排等陈述，但必须结合最近上下文能看出是同一笔业务。\n" +
            "OTHER=无对应业务上下文的群发广告、无关闲聊、系统消息、图片XML等。\n\n" +
            "硬性规则：\n" +
            "1. 买卖双向：不要因为是“我方”或“客户/供应商”就固定成INQ或OFFER，必须看这句话是在问价还是在给价。\n" +
            "2. 群发报价/现货清单如果没有对应前文INQ，是OTHER。\n" +
            "3. TRACE不要求问号；但要能对应前文同一料号、数量或报价，并包含明确的后续业务动作。普通寒暄、无关陈述不是TRACE。\n" +
            "4. 纯数字/短报价只有紧接某条INQ且来自另一方才算OFFER；“目标价/预算价”仍是INQ。\n" +
            "5. 必须结合最近20条上下文，拿不准时按OTHER，宁缺毋滥。\n\n" +
            "输出格式：只输出JSON数组，不要输出任何其他文字：\n" +
            "[{\"i\":0,\"cat\":\"INQ\",\"why\":\"一句话理由\"},{\"i\":1,\"cat\":\"OFFER\",\"why\":\"...\"}]";

    private static final String DEFAULT_EXAMPLES =
            "双向业务示例（<我方>、<客户>、<供应商>只是角色提示，不代表固定方向）：\n" +
            "示例1：<客户> MBD-H13SSL-N-B 24pcs 有货吗 → INQ（客户询价）\n" +
            "示例2：<我方> 有，24pcs 660USD，2周交期 → OFFER（我方对客户INQ报价）\n" +
            "示例3：<我方> 找 64G 5600 100条，有货来报 → INQ（我方向供应商询价）\n" +
            "示例4：<供应商> 有货，100条 320USD，明天发 → OFFER（供应商对我方INQ报价）\n" +
            "示例5：<客户> 660美金含税吗？交期能提前吗 → TRACE（对报价继续询问）\n" +
            "示例6：<客户> 这个价格可以，按24pcs安排吧 → TRACE（非疑问句，但明确确认价格和数量）\n" +
            "示例7：<我方> 这个价能做，明天确认交期 → TRACE（非疑问句，围绕已有报价继续推进）\n" +
            "示例8：<群友> 现货出 5090风扇200片 → OTHER（无对应INQ的群发广告）\n" +
            "示例9：<客户> 收到/好的/谢谢 → OTHER（无业务延续信息）\n" +
            "示例10：<供应商> RFQ: 5955WX 100pcs → INQ（供应商反向询价同样成立）\n" +
            "示例11：<我方> 5955WX 485USD 现货 → OFFER（对不同方向INQ的报价）\n" +
            "示例12：<群友> 长期高价收64G内存 → OTHER（孤立的收购广告）\n" +
            "示例13：<客户> 客户都要下单了，我们还没货 → OTHER（没有对应料号/报价，是无关陈述）\n" +
            "示例14：<客户/供应商> 这批24pcs的交期多久？ → TRACE（已有报价上下文，对同一笔业务继续追问）";

    private String buildSystemPrompt(String base, String examples) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(base)) {
            sb.append(base);
        } else {
            sb.append(DEFAULT_SYSTEM_PROMPT);
        }
        sb.append("\n\n");
        if (StringUtils.isNotBlank(examples)) {
            sb.append("以下是你的业务示例（请严格参考）：\n").append(examples);
        } else {
            sb.append("以下是你的业务示例（请严格参考）：\n").append(DEFAULT_EXAMPLES);
        }
        return sb.toString();
    }

    private String buildPrompt(List<WeChatMessage> batch, List<WeChatMessage> ctx, String chatName) {
        StringBuilder sb = new StringBuilder();
        sb.append("聊天会话【").append(chatName == null ? "" : chatName).append("】\n");
        Set<String> selfNames = cfgNames("selfNames");
        if (!selfNames.isEmpty()) {
            sb.append("我方成员：").append(String.join(",", selfNames)).append("（仅用于理解角色，不代表INQ/OFFER方向）\n");
        }
        if (ctx != null && !ctx.isEmpty()) {
            sb.append("【上下文-此前的消息，仅作参考，不需要输出分类】\n");
            for (WeChatMessage m : ctx) {
                sb.append("- [").append(sender(m)).append("] ")
                  .append(StringUtils.isBlank(m.getMsgCategory()) ? "[未分类]" : "[" + m.getMsgCategory() + "]")
                  .append(" ").append(line(m)).append("\n");
            }
            sb.append("【待判消息】\n");
        }
        for (int i = 0; i < batch.size(); i++) {
            WeChatMessage m = batch.get(i);
            sb.append(i).append(". [").append(sender(m)).append("] ").append(m.getCreateTime() == null ? "" : m.getCreateTime())
              .append(" ").append(line(m)).append("\n");
        }
        sb.append("\n请对每条待判消息(编号i)输出分类，只输出JSON数组。");
        return sb.toString();
    }

    private static String sender(WeChatMessage m) {
        return m.getSender() == null ? "?" : m.getSender();
    }

    /** 内容+引用整理成一行内可读文本，超长保头尾。 */
    private static String line(WeChatMessage m) {
        StringBuilder sb = new StringBuilder();
        String c = m.getContent() == null ? "" : m.getContent().trim();
        if (c.isEmpty()) {
            sb.append("(无文本)");
        } else {
            c = c.replace('\r', ' ').replace('\n', ' ');
            sb.append(c);
        }
        if (StringUtils.isNotBlank(m.getQuote()) && !m.getQuote().equals(m.getContent())) {
            sb.append(" 【引用: ").append(m.getQuote().replace('\r', ' ').replace('\n', ' ')).append("】");
        }
        String t = sb.toString();
        if (t.length() <= CONTENT_MAX) {
            return t;
        }
        int head = CONTENT_MAX * 2 / 3;
        return t.substring(0, head) + "…[中间省略" + (t.length() - CONTENT_MAX) + "字]…"
                + t.substring(t.length() - (CONTENT_MAX - head));
    }
}
