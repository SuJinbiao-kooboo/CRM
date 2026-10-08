package com.ruoyi.wexinAuto.msg;

import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 NDJSON 解析器（字段均为扁平字符串/数字），
 * 与 wx_gateway.py 输出的消息字段一一对应。
 */
public final class WeChatMessageParser {

    private static final Pattern STR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUM = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+)");

    private WeChatMessageParser() {
    }

    /** 网关程序每打一条消息 = 单行以 { 开头的 JSON。 */
    public static boolean isJsonLine(String line) {
        return line != null && line.trim().startsWith("{");
    }

    public static WeChatMessage parse(String line) {
        WeChatMessage m = new WeChatMessage();
        Matcher s = STR.matcher(line);
        while (s.find()) {
            String v = unescape(s.group(2));
            switch (s.group(1)) {
                case "username":    m.setUsername(v); break;
                case "name":        m.setName(v); break;
                case "type":        m.setType(v); break;
                case "sender":      m.setSender(v); break;
                case "content":     m.setContent(v); break;
                case "quote":       m.setQuote(v); break;
                case "quote_kind":  m.setQuoteKind(v); break;
                case "media_path":  m.setMediaPath(v); break;
                case "md5":         m.setMd5(v); break;
                case "create_time": m.setCreateTime(v); break;
                default:            break;
            }
        }
        Matcher n = NUM.matcher(line);
        while (n.find()) {
            switch (n.group(1)) {
                case "local_id":    m.setLocalId(Long.parseLong(n.group(2))); break;
                case "sort_seq":    m.setSortSeq(Long.parseLong(n.group(2))); break;
                default:            break;
            }
        }
        return m;
    }

    private static String unescape(String v) {
        if (v == null) return "";
        return v.replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"");
    }
}




