package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 发送 ABCD 选择题卡片的请求体。
 */
@Data
public class FeishuSendQuestionBody extends FeishuReceiveBody {

    /**
     * 问题正文（卡片顶部展示）。
     * 缺省时使用默认问题。
     */
    private String question;
}
