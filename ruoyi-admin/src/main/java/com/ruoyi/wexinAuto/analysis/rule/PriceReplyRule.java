package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.analysis.WeChatMessageClassifier;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure-price-reply rule (OFFER).
 *
 * <p>The whole message is one or more price rows ("2900", "$3200", "2100 30颗").
 * Walk back over the last 5 history messages skipping OTHER; if the nearest
 * business message is an INQ from a different sender, this is the reply OFFER.
 * The different-sender requirement avoids classifying an inquiry list itself
 * (e.g. "9654 100个") as a price reply.</p>
 */
public class PriceReplyRule implements Rule {

    private static final String CUR = "(?:US\\$|USD|RMB|CNY|\\$|\u00a5|\u20ac)";
    private static final Pattern PRICE_LINE = Pattern.compile(
            "^" + CUR + "?\\s*[\\d,.]+\\s*(?:USD|US\\$|\\$|RMB|CNY|\u00a5|\u20ac)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_QTY_LINE = Pattern.compile(
            "^[\\d,.]+\\s*\\d+\\s*(?:\u9897|\u4e2a|\u7247|\u53ea|\u6761|\u4ef6|pcs)$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null || NeutralRule.isNeutral(m.getContent())) {
            return false;
        }
        if (!isPurePrice(m.getContent())) {
            return false;
        }
        String me = m.getSender() == null ? "" : m.getSender();
        List<WeChatMessage> history = ctx.getHistory();
        int from = Math.max(0, history.size() - 5);
        for (int i = history.size() - 1; i >= from; i--) {
            WeChatMessage h = history.get(i);
            String cat = h.getMsgCategory();
            if (WeChatMessageClassifier.OTHER.equals(cat)) {
                continue;
            }
            String hs = h.getSender() == null ? "" : h.getSender();
            return WeChatMessageClassifier.INQ.equals(cat) && !me.equals(hs);
        }
        return false;
    }

    /** Every non-empty line is a pure price or a price+qty row. */
    private boolean isPurePrice(String content) {
        boolean anyLine = false;
        for (String line : content.split("[\r\n]+")) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            if (!PRICE_LINE.matcher(l).matches() && !PRICE_QTY_LINE.matcher(l).matches()) {
                return false;
            }
            anyLine = true;
        }
        return anyLine;
    }
}