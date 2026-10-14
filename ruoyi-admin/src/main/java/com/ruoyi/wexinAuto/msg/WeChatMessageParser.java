package com.ruoyi.wexinAuto.msg;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.wexinAuto.model.WeChatMessage;

/**
 * NDJSON 解析器：把 wx_gateway.py 打出的一行 JSON 转成 {@link WeChatMessage}。
 *
 * <p>使用 Jackson 解析（snake_case 字段自动映射到 camelCase POJO），
 * 不再用「正则逐个字符回溯」。长消息时 Java 正则 {@code *}/{@code +} 的量词
 * 会按字符深度递归，容易在小栈的 {@code wechat-listen} 线程上触发
 * {@link StackOverflowError}，所以这里不回归正则方案。</p>
 */
public final class WeChatMessageParser {

    /** 与 wx_gateway.py 输出的字段一一对应（JSON key 为 snake_case）。 */
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private WeChatMessageParser() {
    }

    /** 网关程序每打一条消息 = 单行以 { 开头的 JSON。 */
    public static boolean isJsonLine(String line) {
        return line != null && line.trim().startsWith("{");
    }

    /** 解析一行 NDJSON 为 {@link WeChatMessage}；解析失败返回空对象（不抛异常）。 */
    public static WeChatMessage parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new WeChatMessage();
        }
        try {
            return MAPPER.readValue(line, WeChatMessage.class);
        } catch (Exception e) {
            return new WeChatMessage();
        }
    }
}