package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史消息规则：借助 {@link MessageContext#getHistory()} 判断前 N 条消息的特征。
 *
 * <p>历史消息已带有分类出的 bizType，适合“上一条是 OFFER，则本条是 TRACE 跟进”
 * 这类跨消息规则。与其它规则用 {@link AllRule} / {@link AnyRule} 组合。</p>
 */
public class HistoryRule implements Rule {

    private enum Mode { PREVIOUS, ANY_OF_LAST }

    private final Mode mode;
    private final int count;
    private final Set<String> types;

    private HistoryRule(Mode mode, int count, Collection<String> types) {
        this.mode = mode;
        this.count = Math.max(1, count);
        this.types = new HashSet<>();
        if (types != null) {
            for (String t : types) {
                if (t != null && !t.trim().isEmpty()) {
                    this.types.add(t.trim());
                }
            }
        }
    }

    /** 紧邻上一条消息的 bizType 命中指定类型之一。 */
    public static HistoryRule previousIs(String... bizTypes) {
        return new HistoryRule(Mode.PREVIOUS, 1, Arrays.asList(bizTypes));
    }

    /** 最近 n 条历史中有任意一条的 bizType 命中指定类型之一。 */
    public static HistoryRule anyOfLast(int n, String... bizTypes) {
        return new HistoryRule(Mode.ANY_OF_LAST, n, Arrays.asList(bizTypes));
    }

    @Override
    public boolean test(MessageContext ctx) {
        if (ctx == null || types.isEmpty()) {
            return false;
        }
        List<WeChatMessage> history = ctx.getHistory();
        if (history == null || history.isEmpty()) {
            return false;
        }
        if (mode == Mode.PREVIOUS) {
            return types.contains(history.get(history.size() - 1).getMsgCategory());
        }
        int from = Math.max(0, history.size() - count);
        for (int i = from; i < history.size(); i++) {
            if (types.contains(history.get(i).getMsgCategory())) {
                return true;
            }
        }
        return false;
    }
}
