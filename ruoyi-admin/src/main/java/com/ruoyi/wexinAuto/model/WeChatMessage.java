package com.ruoyi.wexinAuto.model;

import lombok.Data;

/**
 * 微信消息（与 wechat-gateway/wx_gateway.py 输出的 NDJSON 字段一一对应）。
 */
@Data
public class WeChatMessage {

    /** 会话（微信号/群id） */
    private String username;

    /** 会话显示名 */
    private String name;

    /** 库内消息 ID */
    private long localId;

    /** 消息类型：文本/图片/语音/视频/文件/系统… */
    private String type;

    /** 发言人显示名 */
    private String sender;

    /** 正文（媒体消息为占位+md5）；引用/卡片消息为此消息的原文 */
    private String content;

    /** 被引用内容（引用消息）：文本截前256字；图片/表情等只给【图片】【表情】等说明 */
    private String quote;

    /** 被引用类型：text/image/emoji/voice/video/card/other（非引用为空） */
    private String quoteKind;

    /** 已下载媒体绝对路径（未下载为空） */
    private String mediaPath;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss（本地时区） */
    private String createTime;

    /** 排序键（增量断点） */
    private long sortSeq;

    /** 媒体消息 md5 */
    private String md5;
}



