package com.ruoyi.crm.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 接口服务（OpenAI 兼容协议）
 * 用于 Offer AI智能录入：将用户粘贴的无规则物料文本整理为结构化数据
 */
@Component
public class DeepSeekAiService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiService.class);

    /** DeepSeek API Key 参数键：密钥在系统参数配置（sys_config）中维护，不入代码与配置文件，避免明文泄露 */
    private static final String API_KEY_CONFIG_KEY = "sys.ai.api.key";

    /** DeepSeek 接口地址参数键：未配置时使用默认地址 */
    private static final String BASE_URL_CONFIG_KEY = "sys.ai.base.url";

    /** DeepSeek 模型名称参数键：未配置时使用默认模型 */
    private static final String MODEL_CONFIG_KEY = "sys.ai.model";

    /** 默认接口地址（OpenAI 兼容格式） */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /** 默认模型名称（与历史 application.yml 默认值保持一致） */
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    @Autowired
    private ISysConfigService sysConfigService;

    /** 解析示例参数键：用户在系统参数配置中维护，内容为整理规范与输入输出对照示例 */
    private static final String TIPS_CONFIG_KEY = "sys.offer.resolve.tips";

    /** 基础提示词参数键：用户在系统参数配置中维护；未配置或为空时使用下方默认基础提示词 */
    private static final String TIPS_BASE_CONFIG_KEY = "sys.offer.resolve.tips.base";

    /** 会话复用开关参数键：1=启用（有效期内复用同一会话上下文）；0=每次新建会话 */
    private static final String SESSION_ENABLED_KEY = "sys.ai.session.enabled";

    /** 会话有效期参数键（天）：默认2天，超过有效期自动新建会话 */
    private static final String SESSION_TTL_KEY = "sys.ai.session.ttl.days";

    /** 会话保留轮数参数键：默认3轮（1轮=一问一答），防止历史消息无限累积导致请求越来越慢 */
    private static final String SESSION_MAX_TURNS_KEY = "sys.ai.session.max.turns";

    /** 会话缓存key前缀：按登录用户隔离（AI录入与料号查询共用物料解析会话） */
    private static final String SESSION_KEY_PREFIX = "ai:session:material:";

    @Autowired
    private RedisCache redisCache;

    /** 默认基础提示词：定义JSON输出格式与字段映射（配合解析示例一起生效） */
    private static final String DEFAULT_SYSTEM_PROMPT_BASE = "你是IT硬件物料信息整理助手。用户会粘贴包含IT配件（SSD、HDD、CPU、GPU、内存、主板、交换机等）的物料信息文本，"
            + "其中可能包含品牌、料号、型号、规格、数量、报价等信息。"
            + "请把文本中的每一条物料整理成结构化数据，并严格按如下JSON格式输出："
            + "{\"items\":[{\"brand\":\"品牌\",\"partNumber\":\"料号\",\"model\":\"型号\",\"spec\":\"规格型号\",\"quantity\":数量,\"price\":报价}]}"
            + "字段规则：1. brand为品牌名称，按常见品牌对照表统一名称；"
            + "2. partNumber为料号/型号（唯一标识符），不要修改或截断；注意CPU的料号"
            + "3. 注意交期字段也要，默认为空，L/T，LT，Lead Time，3d 2w 之类的字眼就是交期相关信息，没有的时候置空"
            + "4. spec为其余规格描述与附加信息（容量、接口、DC、COO、备注等，用逗号分隔）；若数量带+或范围等不确定后缀，在spec中保留原始数量表达；"
            + "5. 品牌字段是必须的"
            + "6. quantity为整数（1k换算为1000、500+取500、40pcs取40），无法识别时输出null；"
            + "7. price为纯数字（去掉货币符号和单位），无法识别或原文为No price/Not specified时输出null；"
            + "8. 不要遗漏任何一条物料，也不要编造原文中不存在的信息；"
            + "9. 料号的识别要特别注意，特别是CPU的，料号可能很短，比如 6338         SRKJ9      $1010    440pcs  6338 才是我要的料号，对料号的识别要做二次确认才行"
            + "10. 最终输出字段需要至少有料号 型号 spec 品牌 数量 价格 品牌大部分时候是空的，需要帮我根据料号查询信息补充，详情也最好也补充，但是一定要保证正确才能补充且需要简短 说明规格和主要参数即可";

    /**
     * 调用 DeepSeek 接口，把无规则的物料文本整理为结构化物料列表
     * 返回的每行 Map 包含键：brand、partNumber、model、spec、quantity、price
     * 会话复用：参数 sys.ai.session.enabled=1 时，同一用户2天内（sys.ai.session.ttl.days）
     * 的连续调用共享会话上下文（仅保留最近 sys.ai.session.max.turns 轮），AI可基于
     * 上一轮内容继续解析/修正，无需重复描述；开关为0或读取失败时退回每次全新会话
     */
    public List<Map<String, Object>> parseMaterialContent(String content) {
        String apiKey = readConfig(API_KEY_CONFIG_KEY, null);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ServiceException("未配置DeepSeek API Key，请在系统参数配置(sys.ai.api.key)中配置后重试");
        }
        // 会话复用：开启时读取历史上下文（最近maxTurns轮）；关闭或非登录场景（如定时任务）则每次全新会话
        String sessionKey = null;
        List<Map<String, String>> history = null;
        if (isSessionEnabled()) {
            sessionKey = buildSessionKey();
            if (sessionKey != null) {
                history = loadHistory(sessionKey);
            }
        }
        // 组装请求体（OpenAI 兼容格式）
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", readConfig(MODEL_CONFIG_KEY, DEFAULT_MODEL));
        payload.put("temperature", 0);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", buildSystemPrompt());
        messages.add(sys);
        if (history != null) {
            messages.addAll(history);
        }
        Map<String, String> usr = new HashMap<>();
        usr.put("role", "user");
        usr.put("content", content);
        messages.add(usr);
        payload.put("messages", messages);
        Map<String, String> respFormat = new HashMap<>();
        respFormat.put("type", "json_object");
        payload.put("response_format", respFormat);

        String body;
        try {
            HttpResponse resp = HttpRequest.post(readConfig(BASE_URL_CONFIG_KEY, DEFAULT_BASE_URL) + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(payload))
                    .timeout(120000)
                    .execute();
            body = resp.body();
            if (!resp.isOk()) {
                log.error("DeepSeek接口调用失败, status={}, body={}", resp.getStatus(), body);
                throw new ServiceException("DeepSeek接口调用失败，请检查API Key配置：" + extractErrorMsg(body));
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek接口调用异常", e);
            throw new ServiceException("DeepSeek接口调用异常：" + e.getMessage());
        }
        // 解析返回的JSON并提取物料列表（调用失败不写回会话，避免污染上下文）
        String aiContent = extractContent(body);
        JSONArray items = extractItems(aiContent);
        if (items == null || items.isEmpty()) {
            throw new ServiceException("AI未从内容中识别出物料信息，请检查粘贴内容后重试");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("brand", strVal(item, "brand"));
            row.put("partNumber", strVal(item, "partNumber"));
            row.put("model", strVal(item, "model"));
            row.put("spec", strVal(item, "spec"));
            row.put("quantity", intVal(item, "quantity"));
            row.put("price", doubleVal(item, "price"));
            // 关键字段全空的无效行直接跳过
            if (isBlank((String) row.get("partNumber")) && isBlank((String) row.get("brand"))
                    && isBlank((String) row.get("model")) && isBlank((String) row.get("spec"))) {
                continue;
            }
            result.add(row);
        }
        if (result.isEmpty()) {
            throw new ServiceException("AI未从内容中识别出有效的物料信息，请检查粘贴内容后重试");
        }
        // 调用成功后才把本轮问答追加进会话历史（带过期时间，自动续期）
        if (sessionKey != null) {
            saveHistory(sessionKey, history, content, aiContent);
        }
        return result;
    }

    /** 会话复用开关：sys.ai.session.enabled=0 时每次新建会话；其余情况（含未配置）默认复用 */
    private boolean isSessionEnabled() {
        try {
            String v = sysConfigService.selectConfigByKey(SESSION_ENABLED_KEY);
            return !"0".equals(v);
        } catch (Exception e) {
            log.warn("读取会话复用开关参数{}失败，按默认开启处理", SESSION_ENABLED_KEY, e);
            return true;
        }
    }

    /** 读取整数型参数：sys_config 配置值非法或读取失败时回退默认值 */
    private int configInt(String key, int def) {
        try {
            String v = sysConfigService.selectConfigByKey(key);
            if (v != null && !v.trim().isEmpty()) {
                return Integer.parseInt(v.trim());
            }
        } catch (Exception e) {
            log.warn("读取整数参数{}失败，使用默认值{}", key, def, e);
        }
        return def;
    }

    /** 读取字符串型参数：sys_config 未配置、为空或读取失败时回退默认值（def为null表示必须配置） */
    private String readConfig(String key, String def) {
        try {
            String v = sysConfigService.selectConfigByKey(key);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Exception e) {
            log.warn("读取参数{}失败，使用默认值{}", key, def, e);
        }
        return def;
    }

    /**
     * 构建会话缓存key：按登录用户隔离；非登录场景（定时任务、匿名调用）返回null表示本次不启用会话复用
     */
    private String buildSessionKey() {
        try {
            Long userId = SecurityUtils.getUserId();
            if (userId == null) {
                return null;
            }
            return SESSION_KEY_PREFIX + userId;
        } catch (Exception e) {
            log.warn("获取当前登录用户失败，本次调用不启用会话复用", e);
            return null;
        }
    }

    /** 读取会话历史（最近maxTurns轮问答），无历史或已过期返回null */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadHistory(String sessionKey) {
        try {
            List<Map<String, String>> history = redisCache.getCacheObject(sessionKey);
            if (history == null || history.isEmpty()) {
                return null;
            }
            return trimToMaxTurns(history);
        } catch (Exception e) {
            log.warn("读取会话历史失败，key={}", sessionKey, e);
            return null;
        }
    }

    /** 保存会话历史：追加本轮一问一答后按maxTurns轮截断，并按ttl天数续期 */
    @SuppressWarnings("unchecked")
    private void saveHistory(String sessionKey, List<Map<String, String>> history, String userContent, String assistantContent) {
        try {
            List<Map<String, String>> newHistory = new ArrayList<>();
            if (history != null) {
                newHistory.addAll(history);
            }
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            newHistory.add(userMsg);
            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", assistantContent);
            newHistory.add(assistantMsg);
            newHistory = trimToMaxTurns(newHistory);
            int ttlDays = Math.max(configInt(SESSION_TTL_KEY, 2), 1);
            redisCache.setCacheObject(sessionKey, newHistory, ttlDays * 24 * 3600, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("保存会话历史失败，key={}", sessionKey, e);
        }
    }

    /** 按maxTurns轮截断历史：1轮=一问一答（2条消息），超出部分丢弃最早的消息，防止历史无限累积 */
    private List<Map<String, String>> trimToMaxTurns(List<Map<String, String>> history) {
        int maxTurns = Math.max(configInt(SESSION_MAX_TURNS_KEY, 3), 1);
        int maxMessages = maxTurns * 2;
        if (history.size() <= maxMessages) {
            return history;
        }
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    /** 组装系统提示词：基础提示词（参数sys.offer.resolve.tips.base，未配置时用默认）+ 用户在参数配置中维护的整理规范与示例（sys.offer.resolve.tips） */
    private String buildSystemPrompt() {
        String base = null;
        try {
            base = sysConfigService.selectConfigByKey(TIPS_BASE_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("读取基础提示词参数{}失败，将使用默认基础提示词", TIPS_BASE_CONFIG_KEY, e);
        }
        if (base == null || base.trim().isEmpty()) {
            base = DEFAULT_SYSTEM_PROMPT_BASE;
        }
        String tips = null;
        try {
            tips = sysConfigService.selectConfigByKey(TIPS_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("读取解析示例参数{}失败，将仅使用基础提示词", TIPS_CONFIG_KEY, e);
        }
        if (tips == null || tips.trim().isEmpty()) {
            return ensureJsonKeyword(base.trim());
        }
        return ensureJsonKeyword(base.trim() + "\n以下为数据整理规范与输入输出对照示例，必须严格遵守：\n" + tips.trim());
    }

    /**
     * DeepSeek在启用response_format=json_object时要求提示词必须包含"json"字样，
     * 用户可能在参数配置中修改提示词时移除该字样导致接口报错，这里兜底补上输出格式说明
     */
    private String ensureJsonKeyword(String prompt) {
        if (prompt == null || prompt.toLowerCase().contains("json")) {
            return prompt;
        }
        return prompt + "\n请严格按照json格式输出结果，输出结构示例："
                + "{\"items\":[{\"brand\":\"品牌\",\"partNumber\":\"料号\",\"model\":\"型号\",\"spec\":\"规格\",\"quantity\":数量,\"price\":价格}]}";
    }

    /** 从响应体提取 choices[0].message.content */
    private String extractContent(String body) {
        try {
            JSONObject root = JSON.parseObject(body);
            JSONArray choices = root.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject first = choices.getJSONObject(0);
                if (first != null && first.getJSONObject("message") != null) {
                    String content = first.getJSONObject("message").getString("content");
                    if (content != null && !content.trim().isEmpty()) return content.trim();
                }
            }
        } catch (Exception e) {
            log.error("解析DeepSeek响应失败, body={}", body, e);
        }
        throw new ServiceException("DeepSeek接口响应格式异常");
    }

    /** 从响应body提取接口错误信息 */
    private String extractErrorMsg(String body) {
        try {
            JSONObject root = JSON.parseObject(body);
            JSONObject error = root.getJSONObject("error");
            if (error != null && error.getString("message") != null) return error.getString("message");
        } catch (Exception ignored) {
        }
        return body;
    }

    /** 提取AI返回的items数组：兼容json_object与纯数组两种返回格式，去除可能的代码块包裹 */
    private JSONArray extractItems(String aiContent) {
        String text = aiContent.trim();
        // 截取第一个{或[到最后一个对应的}或]，去除markdown代码块等包裹内容
        int startObj = text.indexOf('{');
        int startArr = text.indexOf('[');
        int start;
        char endChar;
        if (startObj >= 0 && (startArr < 0 || startObj < startArr)) {
            start = startObj;
            endChar = '}';
        } else if (startArr >= 0) {
            start = startArr;
            endChar = ']';
        } else {
            throw new ServiceException("AI返回内容不是有效JSON");
        }
        int end = text.lastIndexOf(endChar);
        if (end <= start) {
            throw new ServiceException("AI返回内容不是有效JSON");
        }
        String json = text.substring(start, end + 1);
        try {
            Object parsed = JSON.parse(json);
            if (parsed instanceof JSONArray) {
                return (JSONArray) parsed;
            }
            if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                JSONArray items = obj.getJSONArray("items");
                if (items != null) return items;
                // 兼容以其他键名包裹数组的情况
                for (String key : obj.keySet()) {
                    Object v = obj.get(key);
                    if (v instanceof JSONArray) return (JSONArray) v;
                }
            }
        } catch (Exception e) {
            log.error("解析AI返回JSON失败, content={}", aiContent, e);
        }
        throw new ServiceException("AI返回内容解析失败，请重试");
    }

    private String strVal(JSONObject o, String key) {
        Object v = o.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Integer intVal(JSONObject o, String key) {
        Object v = o.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (Exception e) {
            return null;
        }
    }

    private Double doubleVal(JSONObject o, String key) {
        Object v = o.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
