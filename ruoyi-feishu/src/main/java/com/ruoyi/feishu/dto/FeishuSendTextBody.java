package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 发送文本消息的请求体。
 */
@Data
public class FeishuSendTextBody extends FeishuReceiveBody {

    /**
     * 要发送的文本内容（支持飞书 markdown 语法）。
     * 缺省时发送一条默认提示消息。
     */
    private String text;
}
