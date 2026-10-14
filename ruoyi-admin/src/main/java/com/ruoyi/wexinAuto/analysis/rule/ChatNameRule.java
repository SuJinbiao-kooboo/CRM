package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** 会话名规则：当前消息所属会话的 name（群名/好友备注）精确命中指定集合才匹配。 */
public class ChatNameRule implements Rule {

    private final Set<String> chatNames;

    public ChatNameRule(Collection<String> chatNames) {
        this.chatNames = new HashSet<>();
        if (chatNames != null) {
            for (String s : chatNames) {
                if (s != null && !s.trim().isEmpty()) {
                    this.chatNames.add(s.trim());
                }
            }
        }
    }

    public static ChatNameRule of(String... chatNames) {
        return new ChatNameRule(Arrays.asList(chatNames));
    }

    @Override
    public boolean test(MessageContext ctx) {
        if (ctx == null || chatNames.isEmpty()) {
            return false;
        }
        WeChatMessage m = ctx.getCurrent();
        return m != null && chatNames.contains(m.getName());
    }
}
