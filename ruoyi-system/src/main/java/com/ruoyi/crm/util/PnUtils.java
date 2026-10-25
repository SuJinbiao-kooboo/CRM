package com.ruoyi.crm.util;

import java.util.Locale;

/** 料号 P/N 标准化工具。保留连字符、斜杠、点号等有业务含义的字符。 */
public final class PnUtils {

    private PnUtils() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** 双向前缀匹配，供任务生成前的内存过滤与单元测试使用。 */
    public static boolean prefixMatch(String one, String another) {
        String a = normalize(one);
        String b = normalize(another);
        return !a.isEmpty() && !b.isEmpty() && (a.startsWith(b) || b.startsWith(a));
    }

    /** SQL LIKE 前缀模式转义：! 为显式 ESCAPE 字符。 */
    public static String escapeLikePrefix(String value) {
        return normalize(value).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
