package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 消息分类器（第二层分析编排器）。
 *
 * <p>按顺序执行过滤器链：OFFER → INQ → TRACE，第一个命中的类型即结果；
 * 链上都不命中返回 OTHER。OFFER 在前，因为带 INQ 字样的报价单（有价格行）仍是报价。分类入参是 {@link MessageContext}（当前消息 + 同会话历史），
 * 这样每个类型的规则都可以参考群名、发言人、上一条/前 N 条消息。</p>
 *
 * <p>新增类型：实现 {@link MessageTypeFilter} 并把实例加进 {@link #FILTERS}；
 * 细化规则：进对应的过滤器实现类改 {@code buildRules()}，与其它类型互不影响。</p>
 */
public final class WeChatMessageClassifier {

    public static final String INQ = "INQ";
    public static final String OFFER = "OFFER";
    public static final String TRACE = "TRACE";
    public static final String OTHER = "OTHER";

    /** 过滤器链（顺序即优先级）：INQ → OFFER → TRACE；OTHER 为兜底不需要实现类。 */
    private static final List<MessageTypeFilter> FILTERS =
            Arrays.asList(new OfferMessageFilter(), new InqMessageFilter(), new TraceMessageFilter());

    private WeChatMessageClassifier() {
    }

    /** 分类主入口（带上下文）：返回 INQ / OFFER / TRACE / OTHER。 */
    public static String classify(MessageContext ctx) {
        if (ctx == null || ctx.getCurrent() == null) {
            return OTHER;
        }
        WeChatMessage m = ctx.getCurrent();
        // 系统消息与未解析的 XML 原形（拍了拍/系统模板/无引用卡片）不参与询报价分类
        if ("系统消息".equals(m.getType())) {
            return OTHER;
        }
        String c = m.getContent();
        if (org.apache.commons.lang3.StringUtils.isBlank(m.getQuote()) && c != null) {
            String t = c.trim();
            if (t.startsWith("<msg") || t.startsWith("<sysmsg") || t.startsWith("<?xml")) {
                return OTHER;
            }
        }
        for (MessageTypeFilter filter : FILTERS) {
            if (filter.matches(ctx)) {
                return filter.type();
            }
        }
        return OTHER;
    }

    /** 单条消息分类（无历史上下文，兼容简单场景）。 */
    public static String classify(WeChatMessage m) {
        return classify(new MessageContext(m, Collections.<WeChatMessage>emptyList()));
    }
}
