package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.analysis.WeChatMessageClassifier;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Inventory-quantity-answer rule (guard).
 *
 * <p>Scenario: A posts a model list and asks "帮忙推推看"; B asks "数量有多少" (TRACE);
 * A then answers with "型号 数量" rows (WUH721816ALE6L4 500 ...). Such an answer is
 * neither an OFFER nor an INQ, so this rule returns true and the message stays OTHER.
 * Used as a NOT-guard in both the OFFER and the INQ filter chain.</p>
 */
public class QuantityReplyRule implements Rule {

    private static final Pattern QTY_ASK = Pattern.compile(
            "\u6570\u91cf\u6709\u591a\u5c11|\u6709\u591a\u5c11|\u6570\u91cf\u591a\u5c11");
    private static final Pattern MODEL_NUM = Pattern.compile(
            "^[A-Za-z0-9/\\-. ]*[A-Za-z].*\\d[\\d,.]*\\s*$");

    @Override
    public boolean test(MessageContext ctx) {
        if (ctx == null || ctx.getLastMessage() == null) {
            return false;
        }
        WeChatMessage prev = ctx.getLastMessage();
        if (!WeChatMessageClassifier.TRACE.equals(prev.getMsgCategory())) {
            return false;
        }
        String prevText = prev.getContent();
        if (prevText == null || !QTY_ASK.matcher(prevText).find()) {
            return false;
        }
        WeChatMessage m = ctx.getCurrent();
        if (m == null || m.getContent() == null) {
            return false;
        }
        boolean any = false;
        for (String line : m.getContent().split("[\r\n]+")) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            if (!MODEL_NUM.matcher(l).matches()) {
                return false;
            }
            any = true;
        }
        return any;
    }
}