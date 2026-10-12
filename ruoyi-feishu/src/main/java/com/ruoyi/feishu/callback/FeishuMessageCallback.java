package com.ruoyi.feishu.callback;

/**
 * 飞书消息回调接口（封装回调入口）。
 * 业务方可以实现此接口，注册为 Spring Bean，即可在机器人收到消息时被调用。
 * 默认实现：{@link DefaultMessageCallback}。
 */
public interface FeishuMessageCallback {

    /**
     * 判断该回调是否处理某种消息类型。
     * @param messageType 消息类型（text / image / post ...）
     * @return true 表示处理
     */
    default boolean supports(String messageType) {
        return "text".equals(messageType);
    }

    /**
     * 收到消息后的处理入口。
     * @param message 统一封装的消息对象
     */
    void onMessage(FeishuMessage message);
}
