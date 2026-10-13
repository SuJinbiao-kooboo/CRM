package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 发送文件消息的请求体。
 */
@Data
public class FeishuSendFileBody extends FeishuReceiveBody {

    /** 要发送的本地文件绝对路径，例如 C:/tmp/report.docx */
    private String filePath;

    /** 展示用文件名（可省略，缺省用文件真实文件名） */
    private String fileName;
}
