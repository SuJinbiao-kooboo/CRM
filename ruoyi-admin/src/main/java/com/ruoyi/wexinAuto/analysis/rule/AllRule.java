package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;

import java.util.Arrays;
import java.util.List;

/** 全部子规则都命中才算命中（AND）。空规则列表恒为 true。 */
public class AllRule implements Rule {

    private final List<Rule> rules;

    public AllRule(List<Rule> rules) {
        this.rules = rules == null ? java.util.Collections.emptyList() : rules;
    }

    public static AllRule of(Rule... rules) {
        return new AllRule(Arrays.asList(rules));
    }

    @Override
    public boolean test(MessageContext ctx) {
        for (Rule r : rules) {
            if (!r.test(ctx)) {
                return false;
            }
        }
        return true;
    }
}
