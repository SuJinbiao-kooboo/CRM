package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** 发言人规则：当前消息的 sender（发送者显示名）精确命中指定集合才匹配。 */
public class SenderRule implements Rule {

    private final Set<String> senders;

    public SenderRule(Collection<String> senders) {
        this.senders = new HashSet<>();
        if (senders != null) {
            for (String s : senders) {
                if (s != null && !s.trim().isEmpty()) {
                    this.senders.add(s.trim());
                }
            }
        }
    }

    public static SenderRule of(String... senders) {
        return new SenderRule(Arrays.asList(senders));
    }

    @Override
    public boolean test(MessageContext ctx) {
        if (ctx == null || senders.isEmpty()) {
            return false;
        }
        WeChatMessage m = ctx.getCurrent();
        return m != null && senders.contains(m.getSender());
    }
}
