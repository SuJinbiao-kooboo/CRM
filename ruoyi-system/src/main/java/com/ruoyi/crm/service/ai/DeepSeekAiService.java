package com.ruoyi.crm.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 接口服务（OpenAI 兼容协议）
 * 用于 Offer AI智能录入：将用户粘贴的无规则物料文本整理为结构化数据
 */
@Component
public class DeepSeekAiService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiService.class);

    /** API Key（环境变量 DEEPSEEK_API_KEY 或 application.yml 配置） */
    @Value("${deepseek.api-key:}")
    private String apiKey;

    /** 接口地址（OpenAI 兼容格式） */
    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    /** 模型名称 */
    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    /** AI整理物料信息的系统提示词：要求输出固定JSON结构 */
    private static final String SYSTEM_PROMPT = "你是IT硬件物料信息整理助手。用户会粘贴包含IT配件（SSD、HDD、CPU、GPU、内存、主板、交换机等）的物料信息文本，"
            + "其中可能包含品牌、料号、型号、规格、数量、报价等信息。"
            + "请把文本中的每一条物料整理成结构化数据，并严格按如下JSON格式输出："
            + "{\"items\":[{\"brand\":\"品牌\",\"partNumber\":\"料号\",\"model\":\"型号\",\"spec\":\"规格型号\",\"quantity\":数量,\"price\":报价}]}"
            + "要求：1. brand/partNumber/model/spec为字符串，无法识别时输出空字符串；"
            + "2. quantity为整数，无法识别时输出null；"
            + "3. price为纯数字（去掉货币符号和单位），无法识别时输出null；"
            + "4. 不要遗漏任何一条物料，也不要编造原文中不存在的信息；"
            + "5. 只输出JSON，不要输出任何其他解释或说明。";

    /**
     * 调用 DeepSeek 接口，把无规则的物料文本整理为结构化物料列表
     * 返回的每行 Map 包含键：brand、partNumber、model、spec、quantity、price
     */
    public List<Map<String, Object>> parseMaterialContent(String content) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ServiceException("未配置DeepSeek API Key，请在application.yml或环境变量DEEPSEEK_API_KEY中配置后重试");
        }
        // 组装请求体（OpenAI 兼容格式）
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        Map<String, String> usr = new HashMap<>();
        usr.put("role", "user");
        usr.put("content", content);
        messages.add(sys);
        messages.add(usr);
        payload.put("messages", messages);
        Map<String, String> respFormat = new HashMap<>();
        respFormat.put("type", "json_object");
        payload.put("response_format", respFormat);

        String body;
        try {
            HttpResponse resp = HttpRequest.post(baseUrl + "/chat/completions")
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
        // 解析返回的JSON并提取物料列表
        JSONArray items = extractItems(extractContent(body));
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
        return result;
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
