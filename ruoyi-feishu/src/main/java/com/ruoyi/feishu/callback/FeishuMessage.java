package com.ruoyi.feishu.callback;

/**
 * 飞书收到消息/事件后封装而成的统一消息对象，供回调接口使用。
 * 与具体的 SDK 事件模型解耦，便于业务方替换与测试。
 */
public class FeishuMessage {

    /** 消息 id */
    private String messageId;

    /** 会话 id（用于回复消息） */
    private String chatId;

    /** 会话类型：p2p / group / topic */
    private String chatType;

    /** 消息类型：text / image / file / post 等 */
    private String messageType;

    /** 消息原始 content（JSON 字符串） */
    private String content;

    /** 发送者 id */
    private String senderId;

    /** 发送者类型：user / app / system */
    private String senderType;

    /** 纯文本内容（仅文本消息有值，已解析） */
    private String textContent;

    public FeishuMessage() {
    }

    /* ---------- getter / setter ---------- */
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getChatType() { return chatType; }
    public void setChatType(String chatType) { this.chatType = chatType; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
}
