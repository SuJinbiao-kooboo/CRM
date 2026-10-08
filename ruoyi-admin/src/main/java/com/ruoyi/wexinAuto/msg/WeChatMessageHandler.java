package com.ruoyi.wexinAuto.msg;

import com.ruoyi.wexinAuto.model.WeChatMessage;

/**
 * 微信消息逐条处理器。监听模式下每收到一条消息就会调用一次 {@link #handle}。
 * 你只需要实现自己的处理逻辑（入库 / 推送 / AI 调用等），
 * 用 {@code @Primary} 标注你的实现即可替换默认实现；或直接改 DefaultWeChatMessageHandler。
 */
public interface WeChatMessageHandler {

    /** 处理一条微信消息。 */
    void handle(WeChatMessage message);
}


