package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;

/** 取反规则（NOT）：内部规则不命中才命中。 */
public class NotRule implements Rule {

    private final Rule inner;

    public NotRule(Rule inner) {
        this.inner = inner;
    }

    public static NotRule of(Rule inner) {
        return new NotRule(inner);
    }

    @Override
    public boolean test(MessageContext ctx) {
        return inner == null || !inner.test(ctx);
    }
}
