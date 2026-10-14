package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Target-price rule (INQ): "TP1234" / "TP5400usd" is a buyer's target price,
 * i.e. an inquiry, never an offer.
 */
public class TargetPriceRule implements Rule {

    private static final Pattern TP = Pattern.compile("(?i)TP\\s*\\d+");

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null) {
            return false;
        }
        return TP.matcher(m.getContent()).find();
    }
}