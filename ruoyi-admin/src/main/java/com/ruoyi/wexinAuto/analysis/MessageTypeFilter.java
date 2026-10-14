package com.ruoyi.wexinAuto.analysis;

/**
 * 消息类型过滤器（第二层分析：INQ / OFFER / TRACE / OTHER）。
 *
 * <p>每个业务类型一个独立实现类，由 {@link WeChatMessageClassifier} 按顺序挂成过滤器链；
 * 匹配时拿到的是 {@link MessageContext}（当前消息 + 同会话最近 N 条历史），
 * 因此规则可以同时使用：正文/引用关键词、群名(name)、发言人(sender)、历史消息(bizType) 等。</p>
 *
 * <p>新增类型：实现本接口并加入 {@link WeChatMessageClassifier#FILTERS}；
 * 细化规则：改对应类型的过滤器实现类（继承 {@link AbstractKeywordFilter} 重写 buildRules）。</p>
 */
public interface MessageTypeFilter {

    /**
     * 本过滤器命中的业务类型：INQ / OFFER / TRACE
     * （OTHER 是链上都不命中时的兜底，不需要单独实现类）。
     */
    String type();

    /** 是否命中本类型；命中后由分类器返回 {@link #type()}。 */
    boolean matches(MessageContext ctx);
}
