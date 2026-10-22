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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信聊天 AI 分类器。
 *
 * <p>分类口径全部交给提示词和大模型：INQ/OFFER 买卖双向，TRACE 结合最近20条上下文判断。
 * 代码只负责拼上下文、校验 AI 返回的编号结构并回写字段，不再用本地正则二次判类。</p>
 */
@Slf4j
@Component
public class WeChatLlmClassifier {

    private static final String DICT_TYPE = "wechat_analysis_config";
    private static final String DEFAULT_SELF_NAMES = "Eke,May,Jin,Jasmine";
    private static final Set<String> CATS =
            new HashSet<>(Arrays.asList("INQ", "OFFER", "TRACE", "OTHER"));

    /** 控制单次请求的上下文长度，避免同一段聊天反复消耗大量 token。 */
    private static final int CONTEXT_CONTENT_MAX = 240;
    private static final int PENDING_CONTENT_MAX = 600;
    private static final int QUOTE_MAX = 100;
    private static final int EXAMPLES_MAX = 800;
    private static final int CACHE_MAX = 50000;

    private final ObjectMapper jackson = new ObjectMapper();
    private final Map<String, Classification> cache = new ConcurrentHashMap<>();

    @Autowired
    private AiChannelService aiChannelService;

    /** 是否启用 AI 分类：由统一通道配置决定（sys.ai.enabled + sys.ai.api.key）。 */
    public boolean enabled() {
        return aiChannelService.enabled();
    }

    /** 缓存命中时连同关联字段一起恢复，避免只恢复分类而丢掉 relat_inq/relat_offer。 */
    public boolean applyCached(WeChatMessage m) {
        Classification c = m == null ? null : cache.get(cacheKey(m));
        if (c == null) {
            return false;
        }
        m.setMsgCategory(c.category);
        m.setCategorySource("CACHE");
        m.setCategoryReason(c.reason);
        m.setRelatInq(c.relatInq);
        m.setRelatOffer(c.relatOffer);
        return true;
    }

    public synchronized void clearCache() {
        cache.clear();
    }

    /**
     * 对同一会话的待判消息做 AI 分类（时间正序）。
     *
     * @param context  最近的历史消息，作为模型上下文
     * @param pending  待判消息
     * @param chatName 会话名
     */
    public void classify(List<WeChatMessage> context, List<WeChatMessage> pending, String chatName) {
        if (pending == null || pending.isEmpty() || !enabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(20, cfgInt("llmBatchSize", 20)));
        int ctxWindow = Math.max(20, Math.min(50, cfgInt("aiContextWindow", 20)));
        int timeoutMs = Math.max(10, cfgInt("llmTimeoutSec", 180)) * 1000;
        String promptBase = cfg("llmPromptBase", "");
        String examples = cfg("llmExamples", "");
        // 兼容线上历史长示例：提示词可配置，但超过上限时回退内置简短示例，避免每次分类重复烧 token。
        if (examples != null && examples.length() > EXAMPLES_MAX) {
            log.debug("llmExamples 长度 {} 超过 {}，本次使用内置简短示例", examples.length(), EXAMPLES_MAX);
            examples = "";
        }
        String systemPrompt = buildSystemPrompt(promptBase, examples);

        List<WeChatMessage> known = tail(context, ctxWindow);
        for (List<WeChatMessage> batch : partition(pending, batchSize)) {
            List<WeChatMessage> input = new ArrayList<>(tail(known, ctxWindow));
            int batchStart = input.size();
            input.addAll(batch);
            classifyBatch(batch, input, batchStart, chatName, systemPrompt, timeoutMs);
            for (WeChatMessage m : batch) {
                if (StringUtils.isNotBlank(m.getMsgCategory())) {
                    known.add(m);
                    putCache(m);
                }
            }
        }
    }

    private void classifyBatch(List<WeChatMessage> batch, List<WeChatMessage> input, int batchStart,
                               String chatName, String systemPrompt, int timeoutMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("temperature", 0);
        // 输出只有 i/cat/inq/offer，按待判条数动态给额度，不再固定 4000。
        payload.put("max_tokens", Math.min(1200, Math.max(200, batch.size() * 40 + 80)));
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", buildPrompt(input, batchStart, chatName)));
        payload.put("messages", messages);

        String respText;
        try {
            respText = aiChannelService.chatCompletions(payload, timeoutMs);
        } catch (Exception e) {
            log.warn("AI 分类无结果，保留待重试: chat={} count={} error={}",
                    chatName, batch.size(), e.getMessage());
            return;
        }
        Map<Integer, Classification> parsed = parse(respText, input, batch, batchStart);
        for (int i = 0; i < batch.size(); i++) {
            Classification c = parsed.get(batchStart + i);
            if (c == null) {
                continue;
            }
            WeChatMessage m = batch.get(i);
            m.setMsgCategory(c.category);
            m.setCategorySource("LLM");
            m.setCategoryReason(c.reason);
            m.setRelatInq(c.relatInq);
            m.setRelatOffer(c.relatOffer);
        }
    }

    private Map<Integer, Classification> parse(String respText, List<WeChatMessage> input,
                                               List<WeChatMessage> batch, int batchStart) {
        Map<Integer, Classification> out = new LinkedHashMap<>();
        try {
            JsonNode root = jackson.readTree(respText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode arr = extractArray(content);
            if (arr == null || !arr.isArray()) {
                log.warn("AI 分类返回不可解析: {}", snippet(content));
                return out;
            }
            Map<Integer, JsonNode> valid = new LinkedHashMap<>();
            for (JsonNode item : arr) {
                int idx = item.path("i").asInt(-1);
                if (idx < batchStart || idx >= input.size() || input.get(idx) != batch.get(idx - batchStart)) {
                    continue;
                }
                String cat = normalizeCat(item.path("cat").asText(""));
                if (cat == null) {
                    continue;
                }
                valid.put(idx, item);
            }
            // 先写入本批分类，再生成关联 JSON，保证同批「INQ → OFFER/TRACE」的引用也带 category。
            for (Map.Entry<Integer, JsonNode> e : valid.entrySet()) {
                batch.get(e.getKey() - batchStart).setMsgCategory(
                        normalizeCat(e.getValue().path("cat").asText("")));
            }
            for (Map.Entry<Integer, JsonNode> e : valid.entrySet()) {
                int idx = e.getKey();
                JsonNode item = e.getValue();
                String cat = normalizeCat(item.path("cat").asText(""));
                WeChatMessage inq = "OFFER".equals(cat) || "TRACE".equals(cat)
                        ? earlier(input, idx, item.path("inq").asInt(-1)) : null;
                WeChatMessage offer = "TRACE".equals(cat)
                        ? earlier(input, idx, item.path("offer").asInt(-1)) : null;
                String relatInq = inq == null ? null : toRelJson(inq);
                String relatOffer = offer == null ? null : toRelJson(offer);
                out.put(idx, new Classification(cat, relatInq, relatOffer,
                        reason(cat, inq, offer)));
            }
        } catch (Exception e) {
            log.warn("AI 分类响应解析失败: {}", e.toString());
        }
        return out;
    }

    private static WeChatMessage earlier(List<WeChatMessage> input, int current, int ref) {
        if (ref < 0 || ref >= current || ref >= input.size()) {
            return null;
        }
        return input.get(ref);
    }

    private static String reason(String cat, WeChatMessage inq, WeChatMessage offer) {
        StringBuilder s = new StringBuilder("AI:").append(cat);
        if (inq != null) {
            s.append(" INQ=").append(StringUtils.defaultString(inq.getSender(), "?"));
        }
        if (offer != null) {
            s.append(" OFFER=").append(StringUtils.defaultString(offer.getSender(), "?"));
        }
        if ("OFFER".equals(cat) && inq == null) {
            s.append(" 未关联INQ");
        } else if ("TRACE".equals(cat) && inq == null && offer == null) {
            s.append(" 未关联");
        }
        return WeChatTextUtils.clip(s.toString(), 300);
    }

    private String toRelJson(WeChatMessage m) {
        try {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("server_id", m.getServerId());
            o.put("sender", m.getSender());
            o.put("category", m.getMsgCategory());
            o.put("content", WeChatTextUtils.clip(m.getContent(), 800));
            o.put("create_time", m.getCreateTime());
            return jackson.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("构造关联 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode extractArray(String content) {
        if (content == null) {
            return null;
        }
        String t = content.trim();
        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return jackson.readTree(t.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String snippet(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s);
    }

    private String buildPrompt(List<WeChatMessage> input, int batchStart, String chatName) {
        StringBuilder sb = new StringBuilder();
        sb.append("会话：").append(WeChatTextUtils.clip(chatName, 80)).append('\n');
        Set<String> selfNames = selfNames();
        if (!selfNames.isEmpty()) {
            sb.append("我方成员：").append(String.join(",", selfNames))
              .append("（只帮助理解角色，不决定INQ/OFFER方向）\n");
        }
        sb.append("H=历史上下文，P=待判；只输出P行。\n");
        for (int i = 0; i < input.size(); i++) {
            WeChatMessage m = input.get(i);
            boolean pending = i >= batchStart;
            String tag = pending ? "P" : "H";
            String cat = pending ? "待判" : (StringUtils.isBlank(m.getMsgCategory()) ? "?" : m.getMsgCategory());
            sb.append('#').append(i).append(' ').append(tag).append(" [").append(cat).append("] [")
              .append(WeChatTextUtils.clip(m.getSender(), 30)).append("] [")
              .append(shortTime(m.getCreateTime())).append("] ")
              .append(line(m, pending ? PENDING_CONTENT_MAX : CONTEXT_CONTENT_MAX)).append('\n');
        }
        sb.append("只输出JSON数组，每条P消息一项，例如：")
          .append("[{\"i\":").append(batchStart).append(",\"cat\":\"INQ\"}]")
          .append("；OFFER必须填所回应的INQ编号，TRACE填对应的inq/offer编号。不要输出理由。");
        return sb.toString();
    }

    private static String line(WeChatMessage m, int max) {
        StringBuilder sb = new StringBuilder();
        String content = StringUtils.trimToEmpty(m.getContent())
                .replace('\r', ' ').replace('\n', ' ');
        sb.append(content.isEmpty() ? "(无文本)" : content);
        if (StringUtils.isNotBlank(m.getQuote()) && !m.getQuote().equals(m.getContent())) {
            sb.append(" 【引用: ").append(WeChatTextUtils.clip(m.getQuote(), QUOTE_MAX)).append('】');
        }
        return WeChatTextUtils.clip(sb.toString(), max);
    }

    private static String shortTime(String time) {
        String t = StringUtils.trimToEmpty(time);
        if (t.length() >= 16 && t.charAt(4) == '-') {
            return t.substring(5, 16);
        }
        return t;
    }

    private static String buildSystemPrompt(String base, String examples) {
        StringBuilder sb = new StringBuilder(StringUtils.isNotBlank(base)
                ? base.trim() : DEFAULT_SYSTEM_PROMPT);
        sb.append("\n示例：\n").append(StringUtils.isNotBlank(examples)
                ? examples.trim() : DEFAULT_EXAMPLES);
        return sb.toString();
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static List<WeChatMessage> tail(List<WeChatMessage> list, int max) {
        if (list == null || list.isEmpty() || max <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(Math.max(0, list.size() - max), list.size()));
    }

    private static List<List<WeChatMessage>> partition(List<WeChatMessage> list, int size) {
        List<List<WeChatMessage>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return out;
    }

    private static String normalizeCat(String cat) {
        String c = StringUtils.trimToEmpty(cat).toUpperCase();
        return CATS.contains(c) ? c : null;
    }

    private String cfg(String label, String def) {
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
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

    /** 我方成员始终包含内置四人，字典可再补充别名；只用于录入方向，不参与分类。 */
    private Set<String> selfNames() {
        Set<String> out = new HashSet<>();
        for (String n : DEFAULT_SELF_NAMES.split(",")) {
            out.add(n.trim());
        }
        String value = cfg("selfNames", "");
        if (StringUtils.isNotBlank(value)) {
            for (String n : value.split(",")) {
                if (StringUtils.isNotBlank(n)) {
                    out.add(n.trim());
                }
            }
        }
        return out;
    }

    private String cacheKey(WeChatMessage m) {
        return StringUtils.defaultString(m.getUsername()) + "|" + m.getLocalId();
    }

    private void putCache(WeChatMessage m) {
        cache.put(cacheKey(m), new Classification(m.getMsgCategory(), m.getRelatInq(),
                m.getRelatOffer(), m.getCategoryReason()));
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
    }

    private static final class Classification {
        private final String category;
        private final String relatInq;
        private final String relatOffer;
        private final String reason;

        private Classification(String category, String relatInq, String relatOffer, String reason) {
            this.category = category;
            this.relatInq = relatInq;
            this.relatOffer = relatOffer;
            this.reason = reason;
        }
    }

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是微信询报价聊天分类器，只输出JSON数组，不解释。\n" +
            "INQ=任一方询问/寻找具体货物的价格、库存、交期、数量或型号；我方问供应商也算。\n" +
            "OFFER=针对前面某条INQ的对向报价/供货回应，必须指向具体INQ；广告、自问自答不算。\n" +
            "TRACE=围绕前面INQ/OFFER的后续业务推进，可以是问句，也可以是确认价格、数量、交期、下单等陈述；必须指向具体记录。\n" +
            "OTHER=其他。\n" +
            "结合最近20条上下文，买卖双向，不按发送人定方向；拿不准输出OTHER。\n" +
            "输出每条P消息：i=消息编号，cat=INQ/OFFER/TRACE/OTHER，inq/offer=对应消息编号，无关联可省略。";

    private static final String DEFAULT_EXAMPLES =
            "#8 [客户] MBD-H13SSL-N-B 24pcs 有货吗 → {\"i\":8,\"cat\":\"INQ\"}\n" +
            "#9 [我方] 有，24pcs 660USD，2周交期 → {\"i\":9,\"cat\":\"OFFER\",\"inq\":8}\n" +
            "#20 [我方] 找64G 5600 100条，有货来报 → {\"i\":20,\"cat\":\"INQ\"}\n" +
            "#21 [供应商] 有货，100条 320USD，明天发 → {\"i\":21,\"cat\":\"OFFER\",\"inq\":20}\n" +
            "#22 [客户] 660美金含税吗？ → {\"i\":22,\"cat\":\"TRACE\",\"offer\":9}\n" +
            "#23 [客户] 这个价格可以，按24pcs安排吧 → {\"i\":23,\"cat\":\"TRACE\",\"offer\":9}\n" +
            "#24 [群友] 现货出5090风扇200片 → {\"i\":24,\"cat\":\"OTHER\"}";
}
