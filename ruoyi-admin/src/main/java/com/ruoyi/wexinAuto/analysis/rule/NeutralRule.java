package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Neutral-words rule: short social/acknowledgement messages (收到 / 没 / check / 早)
 * must never be classified as OFFER/INQ by context rules.
 */
public class NeutralRule implements Rule {

    private static final Set<String> NEUTRAL = new HashSet<>(Arrays.asList(
            "\u6ca1", "\u6ca1\u6709", "\u65e0", "\u6536\u5230", "\u597d\u7684", "ok", "okay",
            "check", "checking", "\u8c22\u8c22", "\u611f\u8c22", "\u65e9", "\u55ef", "\u597d",
            "\u53ef\u4ee5", "\u5728", "\u5bf9", "\u884c", "\u4e0d", "\u5475\u5475"));

    /** Whole-message exact match (trimmed, case-insensitive). */
    public static boolean isNeutral(String content) {
        if (content == null) {
            return false;
        }
        String t = content.trim().toLowerCase(Locale.ROOT);
        return !t.isEmpty() && NEUTRAL.contains(t);
    }

    /** Substring check used by supplement rules (any neutral word inside). */
    public static boolean containsNeutralWord(String content) {
        if (content == null) {
            return false;
        }
        String low = content.toLowerCase(Locale.ROOT);
        for (String w : NEUTRAL) {
            if (low.contains(w)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        return m != null && isNeutral(m.getContent());
    }
}