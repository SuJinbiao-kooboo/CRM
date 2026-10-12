package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 消息发送请求的基础入参。
 * <p>所有“发送消息到某个会话”的接口都复用此对象携带接收者信息。</p>
 */
@Data
public class FeishuReceiveBody {

    /**
     * 接收者类型：chat_id / open_id / user_id。
     * 缺省时使用配置文件 feishu.self-test-receive-type。
     */
    private String receiveIdType;

    /**
     * 接收者 id（会话 id / 用户 open_id 等）。
     * 可留空，缺省使用配置文件 feishu.self-test-receive-id。
     */
    private String receiveId;

}
