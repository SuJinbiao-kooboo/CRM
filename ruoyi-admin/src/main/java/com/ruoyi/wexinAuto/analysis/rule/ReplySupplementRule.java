package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.analysis.WeChatMessageClassifier;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Reply-supplement rule (OFFER).
 *
 * <p>A short message right after an OFFER that is itself a supplement
 * ("1wk", "7-9days", "现货", short numbers) stays OFFER.
 * Neutral words are excluded.</p>
 */
public class ReplySupplementRule implements Rule {

    private static final Pattern SUPP_A = Pattern.compile(
            "(?i)^[\\d\\s\\-~./,]*(?:wk|week|weeks|w|\u5929|\u5468|days|day|\u4e2a\u6708|\u6708|\u5e74|y|yr)$");
    private static final Pattern SUPP_B = Pattern.compile(
            "(?i)^[\\d,.]+(?:k|w|\u4e07|\u4e2a|\u9897|\u7247|pcs|usd|rmb)?$");

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null) {
            return false;
        }
        String t = m.getContent().trim();
        if (t.isEmpty() || t.length() > 16 || NeutralRule.containsNeutralWord(t)) {
            return false;
        }
        WeChatMessage prev = ctx.getLastMessage();
        if (prev == null || !WeChatMessageClassifier.OFFER.equals(prev.getMsgCategory())) {
            return false;
        }
        if (SUPP_A.matcher(t).matches()) {
            return true;
        }
        return t.length() <= 12 && SUPP_B.matcher(t).matches();
    }
}