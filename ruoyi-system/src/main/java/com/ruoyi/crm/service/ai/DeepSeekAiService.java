package com.ruoyi.crm.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.crm.util.OfferMaterialNormalizer;
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


    @Autowired
    private ISysConfigService sysConfigService;

    /** AI 通道统一服务：负责渠道配置（sys.ai.*）、请求发起与主/备渠道故障切换 */
    @Autowired
    private AiChannelService aiChannelService;

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

    /** Excel/CSV报价附件通常包含多条物料，单次解析比普通粘贴文本耗时更长，至少保留3分钟。 */
    private static final int ATTACHMENT_TIMEOUT_MS = 3 * 60 * 1000;

    @Autowired
    private RedisCache redisCache;

    /** 数据库提示词版本标记：旧版配置缺少此标记时自动使用内置精简版，避免历史长提示词覆盖拆机修复。 */
    private static final String PROMPT_VERSION_MARK = "v2026-11-09";

    /** 默认基础提示词：精简版，定义JSON格式、字段规则、料号特例和拆机识别（配合解析示例一起生效）。 */
    private static final String DEFAULT_SYSTEM_PROMPT_BASE = String.join("\n",
            "# Offer解析提示词 v2026-11-09",
            "你是IT硬件物料录入助手。把用户文本中的每条报价整理成JSON，只输出JSON：",
            "{\"items\":[{\"brand\":\"\",\"partNumber\":\"\",\"model\":\"\",\"spec\":\"\",\"quantity\":null,\"price\":null,\"currency\":\"USD\",\"dc\":\"\",\"coo\":\"\",\"deliveryTime\":\"\",\"productType\":\"\",\"productDetail\":\"\",\"remark\":\"\"}]}",
            "",
            "规则：",
            "1. 每条报价一行；同一料号不同价格、数量或成色必须分行，禁止合并、遗漏。缺货/没货/无货/no stock/out of stock不生成行。",
            "2. 只提取原文或已确认型号支持的信息，禁止编造。字符串缺失填\"\"，数量/价格缺失填null。",
            "3. partNumber是唯一料号，原样保留，不改写、不截断。CPU料号可能很短（如6338），SRKJ9等辅助码不能当料号。",
            "4. quantity取整数：1k=1000，500+=500，40pcs=40。price只取数字，去掉货币符号；currency原文非USD时填代码（如CNY、EUR），否则USD。",
            "5. deliveryTime提取L/T、LT、3d、2w、4-6周、货期、现货等；dc提取批次/年份码；coo提取产地（NON CN、CN、MY、TW、JP、KR等），多个用逗号。",
            "6. productType只能是SSD、HDD、RAM、CPU、GPU、Motherboard、颗粒、其他；根据料号前缀、描述判断，无法确定填其他。",
            "7. spec：SSD/HDD/RAM/颗粒必须写容量；HDD补接口、转速、尺寸，SSD补接口、形态，RAM补代际、速率、模组，颗粒补容量；CPU/GPU无明确规格可留空。productDetail写简短准确的型号和关键规格，不确定时用原文。",
            "8. brand统一为常见名称：Samsung、Intel、NVIDIA、AMD、Western Digital、Seagate、Micron、SK hynix、Solidigm、Kingston、Crucial、SanDisk、Kioxia等。",
            "",
            "料号特例：",
            "- AMD EPYC：EPYC9015/100-000001553 → partNumber=9015，model=EPYC 9015，OPN 100-000001553放productDetail。",
            "- WD：0F62796-WUH722424ALE6L4 → partNumber=WUH722424ALE6L4；0F内部编码不作料号。",
            "- Samsung SSD：能确认系列时model写PM1655/PM1653/PM893/PM9A3/PM9A1，partNumber保留完整SKU；MZILG1T6/MZILG3T2→PM1655，MZILG960/MZILG1T9/MZILG3T8/MZILG7T6/MZILG15T/MZILG30T→PM1653。",
            "- NVIDIA：5090/RTX5090/RTX 5090（含涡轮、品牌、OC等）→partNumber=RTX 5090 32G，model=RTX 5090，spec=32GB GDDR7；PRO 6000 Server Edition/服务器版→PRO 6000 SE 96G / RTX PRO 6000 SE；Workstation Edition/工作站版→PRO 6000 WS 96G / RTX PRO 6000 WS；只写PRO 6000时保持PRO 6000，不猜SE/WS；5090D、5090D V2、PRO 6000D、MAX-Q保持原样。",
            "",
            "拆机/二手规则（最高优先级，适用于CPU、GPU、RAM、SSD、HDD、主板、颗粒等所有类型）：",
            "- 原文出现「拆」「拆机」「拆新」「拆箱」「拆件」「拆封」「二手」，或出现独立成词的「CP」「Pull」「Pulled」「Clean Pull」「Renew」「Renewed」「Refurb」「Refurbished」「Used」时，该行按拆机处理（CPU/PCS/PCIe中的CP不算）。",
            "- 该行partNumber=原带拆字料号+「拆」，中间不加空格（如6760P拆、9015拆、RTX 5090 32G拆）；料号已有「拆」不重复添加。",
            "- productDetail必须含「拆」；remark=partNumber（如6760P拆）。",
            "- 同一料号同时有全新和拆机报价时拆成两行；一行带拆，一行不带拆。",
            "- 「不拆」「未拆」「非拆」「勿拆」「无拆」「没有拆」「不是拆」不是拆机。");

    /**
     * 调用 DeepSeek 接口，把无规则的物料文本整理为结构化物料列表
     * 返回的每行 Map 包含键：brand、partNumber、model、spec、quantity、price
     * 会话复用：参数 sys.ai.session.enabled=1 时，同一用户2天内（sys.ai.session.ttl.days）
     * 的连续调用共享会话上下文（仅保留最近 sys.ai.session.max.turns 轮），AI可基于
     * 上一轮内容继续解析/修正，无需重复描述；开关为0或读取失败时退回每次全新会话
     */
    public List<Map<String, Object>> parseMaterialContent(String content) {
        if (!aiChannelService.enabled()) {
            throw new ServiceException("未配置AI接口参数，请在系统参数配置(sys.ai.api.key)中配置后重试");
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

        // 统一走 AI 通道服务：自动按当前渠道填充 model，主渠道（第三方平台）故障时自动切换备用渠道。
        // Excel/CSV附件可能包含几十条报价，沿用普通文本的2分钟超时很容易在返回前被截断。
        int timeoutMs = content != null && content.contains("【附件:")
                ? Math.max(aiChannelService.timeoutMs(), ATTACHMENT_TIMEOUT_MS) : 0;
        String body = aiChannelService.chatCompletions(payload, timeoutMs);
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
            row.put("currency", strVal(item, "currency"));
            row.put("dc", strVal(item, "dc"));
            row.put("coo", strVal(item, "coo"));
            row.put("deliveryTime", strVal(item, "deliveryTime"));
            row.put("productType", strVal(item, "productType"));
            row.put("productDetail", strVal(item, "productDetail"));
            row.put("remark", strVal(item, "remark"));
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
        // 对可确定判断的料号格式统一归一化，保证 AI 录入与 AI 查询结果一致
        OfferMaterialNormalizer.normalizeItems(result, content);
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
            // 定时任务/匿名调用等无登录上下文场景属正常，无需打印完整堆栈
            log.warn("非登录上下文(定时任务/匿名调用)，本次AI解析不启用会话复用: {}", e.getMessage());
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

    /** 组装系统提示词：数据库新版(base+tips)优先；检测到旧版配置时自动使用内置精简版，避免历史冗余提示词覆盖修复。 */
    private String buildSystemPrompt() {
        String base = null;
        try {
            base = sysConfigService.selectConfigByKey(TIPS_BASE_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("读取基础提示词参数{}失败，将使用默认基础提示词", TIPS_BASE_CONFIG_KEY, e);
        }
        boolean baseCurrent = base != null && !base.trim().isEmpty()
                && base.contains(PROMPT_VERSION_MARK);
        if (!baseCurrent) {
            if (base != null && !base.trim().isEmpty()) {
                log.warn("检测到旧版Offer解析基础提示词，自动使用内置精简版{}；可执行 sql/20261109_offer_prompt_compact.sql 同步系统参数",
                        PROMPT_VERSION_MARK);
            }
            base = DEFAULT_SYSTEM_PROMPT_BASE;
        }
        String tips = null;
        try {
            tips = sysConfigService.selectConfigByKey(TIPS_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("读取解析示例参数{}失败，将仅使用基础提示词", TIPS_CONFIG_KEY, e);
        }
        // 旧版示例与精简版基础提示词大量重复/冲突，只接受带同一版本标记的示例
        if (tips != null && !tips.trim().isEmpty() && !tips.contains(PROMPT_VERSION_MARK)) {
            log.warn("检测到旧版Offer解析示例，与精简版基础提示词不兼容，已忽略；可执行 sql/20261109_offer_prompt_compact.sql 同步系统参数");
            tips = null;
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
                + "{\"items\":[{\"brand\":\"品牌\",\"partNumber\":\"料号\",\"model\":\"型号\",\"spec\":\"规格\",\"quantity\":数量,\"price\":价格,\"currency\":\"币种\"}]}";
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
