package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 关键词规则：当前消息的指定字段包含任意一个关键词即命中。
 *
 * <p>关键词只是众多规则中的一种；需要时把本规则与
 * {@link SenderRule} / {@link ChatNameRule} / {@link HistoryRule}
 * 用 {@link AllRule} / {@link AnyRule} 组合成更复杂的条件。</p>
 */
public class KeywordRule implements Rule {

    /** 匹配字段：content(正文+引用，默认) / name(群名) / sender(发言人) */
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_SENDER = "sender";

    private final String field;
    private final Set<String> keywords;

    public KeywordRule(Collection<String> keywords) {
        this(FIELD_CONTENT, keywords);
    }

    public KeywordRule(String field, Collection<String> keywords) {
        this.field = (field == null || field.trim().isEmpty()) ? FIELD_CONTENT : field.trim();
        this.keywords = new HashSet<>();
        if (keywords != null) {
            for (String k : keywords) {
                if (k != null && !k.trim().isEmpty()) {
                    this.keywords.add(k.trim());
                }
            }
        }
    }

    public static KeywordRule of(String... keywords) {
        return new KeywordRule(FIELD_CONTENT, Arrays.asList(keywords));
    }

    public static KeywordRule ofField(String field, String... keywords) {
        return new KeywordRule(field, Arrays.asList(keywords));
    }

    @Override
    public boolean test(MessageContext ctx) {
        if (ctx == null || ctx.getCurrent() == null || keywords.isEmpty()) {
            return false;
        }
        String text = pickup(ctx.getCurrent());
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private String pickup(WeChatMessage m) {
        if (FIELD_NAME.equals(field)) {
            return m.getName();
        }
        if (FIELD_SENDER.equals(field)) {
            return m.getSender();
        }
        return nvl(m.getContent()) + "\n" + nvl(m.getQuote());
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
