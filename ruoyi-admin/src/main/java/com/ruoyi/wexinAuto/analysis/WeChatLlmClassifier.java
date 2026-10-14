package com.ruoyi.wexinAuto.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
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
 * 大模型（DeepSeek）精判分类器。
 *
 * <p>只负责“灰色地带”消息：{@link HardRuleClassifier} 无法确定的消息交给本类，
 * 按群分批（默认 20 条/批、2 路并行）调用 OpenAI 兼容接口，
 * 输出 INQ/OFFER/TRACE/OTHER 四选一。配置全部来自数据字典
 * {@code wechat_analysis_config}（dict_label：llmEnabled/llmApiKey/llmApiBase/llmModel/
 * llmBatchSize/llmConcurrency/llmTimeoutSec），改字典即可生效，无需重启。</p>
 *
 * <p>结果以 (username|localId) 为 key 缓存，重复查询直接命中，不重复调用模型。</p>
 */
@Slf4j
@Component
public class WeChatLlmClassifier {

    /** 消息分析参数数据字典类型 */
    private static final String DICT_TYPE = "wechat_analysis_config";

    private static final Set<String> CATS =
            new HashSet<>(Arrays.asList("INQ", "OFFER", "TRACE", "OTHER"));

    private static final int CACHE_MAX = 50000;

    private final ObjectMapper jackson = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

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

    private boolean cfgBool(String label, boolean def) {
        String v = cfg(label, def ? "true" : "false");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private int cfgInt(String label, int def) {
        try {
            return Integer.parseInt(cfg(label, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 是否启用大模型分类（字典 llmEnabled=true 且已配置 Key）。 */
    public boolean enabled() {
        if (!cfgBool("llmEnabled", true)) {
            return false;
        }
        return StringUtils.isNotBlank(apiKey());
    }

    private String apiKey() {
        String k = cfg("llmApiKey", "");
        if (StringUtils.isNotBlank(k)) {
            return k;
        }
        return System.getenv("DEEPSEEK_API_KEY");
    }

    // ------------------------------------------------------------------
    // 缓存
    // ------------------------------------------------------------------
    private String cacheKey(WeChatMessage m) {
        return (m.getUsername() == null ? "" : m.getUsername()) + "|" + m.getLocalId();
    }

    /** 查询缓存分类（无则 null）。 */
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
     * 对同一会话的一组消息（时间正序）做精判：只覆盖尚未分类（灰色）的消息。
     *
     * @param chatMessages 同会话全量消息（含已硬判的，作为上下文给模型）
     * @param chatName     会话名
     */
    public void classify(List<WeChatMessage> chatMessages, String chatName) {
        if (chatMessages == null || chatMessages.isEmpty()) {
            return;
        }
        List<WeChatMessage> grays = new ArrayList<>();
        for (WeChatMessage m : chatMessages) {
            if (m.getMsgCategory() == null) {
                grays.add(m);
            }
        }
        if (grays.isEmpty()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(20, cfgInt("llmBatchSize", 20)));
        int concurrency = Math.max(1, Math.min(4, cfgInt("llmConcurrency", 2)));
        final String api = apiKey();
        final String base = cfg("llmApiBase", "https://api.deepseek.com");
        final String model = cfg("llmModel", "deepseek-chat");

        List<List<WeChatMessage>> batches = partition(grays, batchSize);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "wechat-llm");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (List<WeChatMessage> batch : batches) {
                futures.add(pool.submit(() ->
                        classifyBatch(batch, chatName, api, base, model)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(20, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("LLM 批分类异常: {}", e.toString());
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
    private void classifyBatch(List<WeChatMessage> batch, String chatName,
                               String api, String base, String model) {
        String prompt = buildPrompt(batch, chatName);
        String body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 4000);
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", SYSTEM_PROMPT);
            messages.add(sys);
            Map<String, String> user = new LinkedHashMap<>();
            user.put("role", "user");
            user.put("content", prompt);
            messages.add(user);
            payload.put("messages", messages);
            body = jackson.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("LLM 请求构造失败: {}", e.getMessage());
            return;
        }
        String respText = post(base, body, api);
        if (respText == null) {
            for (WeChatMessage m : batch) {
                log.warn("LLM 无结果, 回退规则: {} | {}", m.getSender(), snippet(m));
            }
            return;
        }
        Map<Long, String> parsed = parse(respText, batch);
        for (WeChatMessage m : batch) {
            String cat = parsed.get(m.getLocalId());
            if (cat != null) {
                m.setMsgCategory(cat);
                put(m, cat);
            }
        }
        int miss = batch.size() - parsed.size();
        if (miss > 0) {
            log.warn("LLM 批分类缺 {} 条结果", miss);
        }
    }

    private String post(String base, String jsonBody, String apiKey) {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(3000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                Request req = new Request.Builder()
                        .url(base + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8")))
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    String text = resp.body() == null ? "" : resp.body().string();
                    if (!resp.isSuccessful()) {
                        log.warn("LLM HTTP {}: {}", resp.code(), snippet(text));
                        last = new IllegalStateException("HTTP " + resp.code());
                        continue;
                    }
                    return text;
                }
            } catch (Exception e) {
                last = e;
                log.warn("LLM 调用异常(第{}次): {}", attempt + 1, e.toString());
            }
        }
        log.error("LLM 调用最终失败: {}", last == null ? "unknown" : last.toString());
        return null;
    }

    private Map<Long, String> parse(String respText, List<WeChatMessage> batch) {
        Map<Long, String> out = new LinkedHashMap<>();
        try {
            JsonNode root = jackson.readTree(respText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode arr = extractArray(content);
            if (arr == null || !arr.isArray()) {
                log.warn("LLM 返回不可解析: {}", snippet(content));
                return out;
            }
            for (JsonNode item : arr) {
                int idx = item.path("i").asInt(-1);
                String cat = item.path("cat").asText("").trim().toUpperCase();
                if (idx >= 0 && idx < batch.size() && CATS.contains(cat)) {
                    out.put(batch.get(idx).getLocalId(), cat);
                }
            }
        } catch (Exception e) {
            log.warn("LLM 响应解析失败: {}", e.toString());
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

    private static String snippet(WeChatMessage m) {
        String c = m.getContent() == null ? "" : m.getContent();
        return c.length() > 40 ? c.substring(0, 40) : c;
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
    private static final String SYSTEM_PROMPT =
            "你是微信商业群\"询报价\"消息分类专家。只允许输出四种分类：\n" +
            "INQ=询价/求购（买方要货、问价）  OFFER=报价/出货（卖方给价、卖货清单）  TRACE=对询报价的跟进追问  OTHER=闲聊/广告/系统消息/无法判断\n\n" +
            "判断规则：\n" +
            "1. INQ：含 inq/rfq/询价/谁有/有货吗/报个价/目标价TP/pls chk/请查；纯\"型号+数量\"无价格；问\"这个多少钱\"；\"求购/找货\"+具体型号数量。注意TP5400usd是目标价=INQ。\n" +
            "2. OFFER：含 $/USD/RMB/CNY/元/¥ 等价格；报价单（型号+价格表）；引用回答\"这个950\"；上一条是询价时纯数字回复如\"2900\"；报价补充如\"1wk\"；以 出/现货出/一手现货/库存清单/在仓/好价出/特价出/源头供应/大量到货 开头的卖货清单即使没有价格也是OFFER。\n" +
            "3. TRACE：对询报价的追问：更新/考虑/确认/优惠/货期/排单/数量多少/给具体价格/还在吗/订了吗 等。\n" +
            "4. OTHER：闲聊(好/没/早/收到/谢谢/辛苦了)；收购广告(长期收/高价收/实单收/求购广告/有货滴滴/有货的++/换群/没好友/全国可飞/打款秒付)；既收又出的大段广告(含热线电话/++想加好友)；图片XML/系统消息；纯公司介绍或纯型号列表+热线电话但无出货动词的广告。\n\n" +
            "输出格式（不要输出任何其他文字）：\n" +
            "[{\"i\":0,\"cat\":\"INQ\",\"why\":\"一句话理由\"},{\"i\":1,\"cat\":\"OFFER\",\"why\":\"...\"},...]";

    private String buildPrompt(List<WeChatMessage> batch, String chatName) {
        StringBuilder sb = new StringBuilder();
        sb.append("微信群【").append(chatName == null ? "" : chatName).append("】最近消息（时间从早到晚，序号连续）：\n");
        for (int i = 0; i < batch.size(); i++) {
            WeChatMessage m = batch.get(i);
            sb.append(i).append(". [").append(m.getSender() == null ? "?" : m.getSender())
              .append("] ").append(snippet(m)).append("\n");
        }
        sb.append("\n请对【每条】消息输出分类，只输出 JSON 数组。");
        return sb.toString();
    }
}