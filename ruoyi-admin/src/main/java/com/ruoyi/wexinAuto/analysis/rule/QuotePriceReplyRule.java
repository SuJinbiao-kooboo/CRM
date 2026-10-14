package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Quoted-reply-with-price rule (OFFER).
 *
 * <p>The message quotes an earlier message and replies with a price:
 * "这个950", a pure number, or a currency line such as "$550 rcf".
 * Only short replies (<=50 chars) count, neutral words are excluded.</p>
 */
public class QuotePriceReplyRule implements Rule {

    private static final String CUR = "(?:US\\$|USD|RMB|CNY|\\$|\u00a5|\u20ac)";
    private static final Pattern THIS_PRICE = Pattern.compile("\u8fd9\u4e2a\\s*[\\d,.]*\\s*$");
    private static final Pattern PURE_NUM = Pattern.compile("^[\\d,.]*\\s*$");
    private static final Pattern CURRENCY = Pattern.compile(CUR, Pattern.CASE_INSENSITIVE);

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null) {
            return false;
        }
        String text = m.getContent();
        String quote = m.getQuote();
        if (quote == null || quote.trim().isEmpty() || text == null) {
            return false;
        }
        String t = text.trim();
        if (t.isEmpty() || t.length() > 50 || NeutralRule.containsNeutralWord(t)) {
            return false;
        }
        return THIS_PRICE.matcher(t).find() || PURE_NUM.matcher(t).matches() || CURRENCY.matcher(t).find();
    }
}