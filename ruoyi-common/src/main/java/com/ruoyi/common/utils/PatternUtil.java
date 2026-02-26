package com.ruoyi.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternUtil {

    /**
     * 提取字符串中的数字和小数点
     * @param input 输入字符串
     * @return 只包含数字和小数点的字符串，如果没有匹配则返回空字符串
     */
    private static String extractNumbersAndDot(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // 正则表达式：匹配数字和小数点
        // [0-9.] 表示匹配数字0-9和小数点
        Pattern pattern = Pattern.compile("[0-9.]");
        Matcher matcher = pattern.matcher(input);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            result.append(matcher.group());
        }

        return result.toString();
    }

    /**
     * 提取字符串中的数字和小数点（保留第一个小数点，后续的小数点会被过滤）
     * @param input 输入字符串
     * @return 处理后的字符串
     */
    public static String extractNumbersAndFirstDot(String input) {
        String extracted = extractNumbersAndDot(input);

        // 如果包含多个小数点，只保留第一个
        int firstDotIndex = extracted.indexOf('.');
        if (firstDotIndex != -1) {
            String beforeDot = extracted.substring(0, firstDotIndex + 1);
            String afterDot = extracted.substring(firstDotIndex + 1).replace(".", "");
            return beforeDot + afterDot;
        }

        return extracted;
    }

    /**
     * 提取字符串中的数字和小数点，并尝试转换为Double
     * @param input 输入字符串
     * @return Double值，如果转换失败返回null
     */
    public static Double extractNumber(String input) {
        String extracted = extractNumbersAndFirstDot(input);

        if (extracted.isEmpty() || ".".equals(extracted)) {
            return null;
        }

        // 如果字符串以小数点开头或结尾，补充0
        if (extracted.startsWith(".")) {
            extracted = "0" + extracted;
        }
        if (extracted.endsWith(".")) {
            extracted = extracted + "0";
        }

        try {
            return Double.parseDouble(extracted);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
