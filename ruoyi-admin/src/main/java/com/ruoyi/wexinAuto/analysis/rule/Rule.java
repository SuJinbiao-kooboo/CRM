package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;

/**
 * 单条匹配规则（规则引擎的最小单元）。
 *
 * <p>规则可以只读当前消息，也可以借助 {@link MessageContext} 读群名、发言人、
 * 历史消息等。通过 {@link AllRule} / {@link AnyRule} / {@link NotRule} 任意组合。</p>
 */
public interface Rule {

    /** 对一条消息（含上下文）做判定；true=命中。 */
    boolean test(MessageContext ctx);
}
