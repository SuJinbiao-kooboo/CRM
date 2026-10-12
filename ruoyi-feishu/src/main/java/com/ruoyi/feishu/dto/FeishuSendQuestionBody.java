package com.ruoyi.feishu.dto;

/**
 * 发送 ABCD 选择题卡片的请求体。
 */
public class FeishuSendQuestionBody extends FeishuReceiveBody {

    /**
     * 问题正文（卡片顶部展示）。
     * 缺省时使用默认问题。
     */
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
