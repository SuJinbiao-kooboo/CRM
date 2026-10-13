package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;

import java.util.Arrays;
import java.util.List;

/** 任一子规则命中即命中（OR）。空规则列表恒为 false。 */
public class AnyRule implements Rule {

    private final List<Rule> rules;

    public AnyRule(List<Rule> rules) {
        this.rules = rules == null ? java.util.Collections.emptyList() : rules;
    }

    public static AnyRule of(Rule... rules) {
        return new AnyRule(Arrays.asList(rules));
    }

    @Override
    public boolean test(MessageContext ctx) {
        if (rules.isEmpty()) {
            return false;
        }
        for (Rule r : rules) {
            if (r.test(ctx)) {
                return true;
            }
        }
        return false;
    }
}
