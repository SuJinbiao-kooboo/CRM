package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息分类上下文：当前消息 + 同会话最近 N 条历史消息（按时间升序，不含当前）。
 *
 * <p>自定义规则可以同时参考当前消息的任意字段（content/quote/name/sender/...）
 * 与历史消息（历史消息已带有分类出的 bizType），从而实现“上一条是报价、
 * 则本条可能是跟进”这类跨消息逻辑。</p>
 */
public class MessageContext {

    /** 当前（待分类）消息 */
    private final WeChatMessage current;

    /** 同会话、时间早于当前消息的最近若干条（升序=旧→新，不含 current；可能为空） */
    private final List<WeChatMessage> history;

    public MessageContext(WeChatMessage current, List<WeChatMessage> history) {
        this.current = current;
        this.history = history == null ? Collections.emptyList() : new ArrayList<>(history);
    }

    public WeChatMessage getCurrent() {
        return current;
    }

    public List<WeChatMessage> getHistory() {
        return history;
    }

    /** 紧邻上一条消息；无历史返回 null。 */
    public WeChatMessage getLastMessage() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }
}
