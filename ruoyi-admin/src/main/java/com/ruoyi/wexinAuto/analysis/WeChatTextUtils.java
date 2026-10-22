package com.ruoyi.wexinAuto.analysis;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/** 微信链路通用文本工具，不包含任何 INQ/OFFER 业务判断。 */
public final class WeChatTextUtils {

    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private WeChatTextUtils() {
    }

    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 消息时间是否在最近 N 分钟内；无法解析时返回 false，历史异常数据不触发预警。 */
    public static boolean isRecent(String createTime, int minutes) {
        if (minutes <= 0) {
            return true;
        }
        LocalDateTime time = parseTime(createTime);
        return time != null && !time.isBefore(LocalDateTime.now().minusMinutes(minutes));
    }

    /** 我方成员精确匹配（忽略 @ 前缀与大小写），避免“May 星禾 SSD GPU”误匹配“May”。 */
    public static boolean isSelf(String sender, Set<String> selfNames) {
        if (StringUtils.isBlank(sender) || selfNames == null || selfNames.isEmpty()) {
            return false;
        }
        String value = normalizeName(sender);
        for (String name : selfNames) {
            if (StringUtils.isNotBlank(name) && value.equals(normalizeName(name))) {
                return true;
            }
        }
        return false;
    }

    /** 两个发送人是否同名；任一方为空时返回 true，自动录入按“无法证明不同人”处理。 */
    public static boolean sameName(String one, String another) {
        return StringUtils.isBlank(one) || StringUtils.isBlank(another)
                || normalizeName(one).equals(normalizeName(another));
    }

    private static String normalizeName(String value) {
        String name = StringUtils.trimToEmpty(value);
        while (name.startsWith("@") || name.startsWith("＠")) {
            name = name.substring(1).trim();
        }
        return name.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime parseTime(String value) {
        String time = StringUtils.trimToEmpty(value).replace('T', ' ');
        if (time.length() > 19) {
            time = time.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(time, SECOND);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(time, MINUTE);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}
