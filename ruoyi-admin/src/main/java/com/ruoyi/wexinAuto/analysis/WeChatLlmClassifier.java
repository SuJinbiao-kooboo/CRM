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
        int ctxWindow = Math.max(0, cfgInt("aiContextWindow", 20));
        String promptBase = cfg("llmPromptBase", "");
        String examples = cfg("llmExamples", "");
        int timeoutMs = Math.max(10, cfgInt("llmTimeoutSec", 180)) * 1000;

        List<WeChatMessage> ctx = new ArrayList<>();
        if (context != null) {
            for (int i = Math.max(0, context.size() - ctxWindow); i < context.size(); i++) {
                WeChatMessage m = context.get(i);
                if (m != null && StringUtils.isNotBlank(m.getMsgCategory())) {
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
            for (List<WeChatMessage> batch : batches) {
                futures.add(pool.submit(() ->
                        classifyBatch(batch, ctx, chatName, promptBase, examples, timeoutMs)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(20, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("AI 批分类异常: {}", e.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private List<List<WeChatMessage>> partition(List<WeChatMessage> list, int size) {
        List<List<WeChatMessage>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return out;
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
                put(m, cat);
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
            "你是微信「询报价」业务消息分类专家。用户会把一个聊天会话最近的一批消息给你" +
            "（每条含序号、发言人、时间、内容，开头可能带【上下文】=该条之前已分类的消息）。" +
            "请对【每条待判消息】输出唯一分类：\n" +
            "INQ=询价/求购（买方要货：型号+数量、问价、有没有货、目标价TP、RFQ/pls chk）\n" +
            "OFFER=报价/出货（卖方给价：型号+单价/表格式报价单、$USD/RMB价格、出货清单、对询价的报价回复）\n" +
            "TRACE=对询报价的跟进/追问（更新、考虑得怎样、确认、优惠、货期、排单、数量多少、还在吗、订了吗）\n" +
            "OTHER=闲聊/广告/收购广告/系统消息/图片XML/无业务含义\n\n" +
            "判断要点：\n" +
            "1. 必须结合上下文：上一条是INQ时，纯数字或短报价回复多为OFFER；上一条是OFFER时，还价/问交期多为TRACE或INQ。\n" +
            "2. 含$、USD、RMB、元、¥等价格证据且说话人在给价(非问价) → OFFER；纯「型号+数量」无价格且是询问语气 → INQ。\n" +
            "3. TP5400usd这类「目标价」是INQ不是OFFER；「这个950」「2900」对上一条询价的回复是OFFER。\n" +
            "4. 收购广告(长期收/高价收/有货滴滴/实单收等)、闲聊、单字回复(好/没/收到/谢谢)、XML/图片/语音 → OTHER。\n" +
            "5. 拿不准时按 OTHER，不要编造。\n\n" +
            "输出格式：只输出JSON数组，不要输出任何其他文字：\n" +
            "[{\"i\":0,\"cat\":\"INQ\",\"why\":\"一句话理由\"},{\"i\":1,\"cat\":\"OFFER\",\"why\":\"...\"}]";

    private static final String DEFAULT_EXAMPLES =
            "真实示例（<换人>表示消息来自不同发言人）：\n" +
            "示例1：<A> inq: 9275F 2pcs → INQ；<B> 2900 → OFFER（对上一条询价的报价）\n" +
            "示例2：<A> INQ\\n6444Y - 60pcs\\n6538Y+ - 12pcs（型号+数量、无价格）→ INQ；" +
            "<B> 同样型号但每行带价格(3650/2300) → OFFER\n" +
            "示例3：<A> 现货出 5955WX 470 / 7995wx 7800（型号+价格清单）→ OFFER\n" +
            "示例4：<A> @朝阳 2565X 1pcs有货吗 → INQ；<B> 没 → OTHER（答复但无报价）\n" +
            "示例5：<A> please check today's offer: 4310 USD 435 FS 7-9days → OFFER\n" +
            "示例6：<A> 早上好 / 收到 / 谢谢 → OTHER；<A> 早上好，询一下 5317 10pcs → INQ";

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
        if (ctx != null && !ctx.isEmpty()) {
            sb.append("【上下文-此前的消息，仅作参考，不需要输出分类】\n");
            for (WeChatMessage m : ctx) {
                sb.append("- [").append(sender(m)).append("] ").append(line(m)).append("\n");
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
