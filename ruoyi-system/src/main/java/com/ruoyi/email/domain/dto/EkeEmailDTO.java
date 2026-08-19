package com.ruoyi.email.domain.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 企业邮箱邮件信息（读取结果）
 *
 * @author ruoyi
 */
@Data
public class EkeEmailDTO
{
    /** 邮件唯一标识（Message-ID） */
    private String messageId;

    /** 发件人邮箱地址 */
    private String sender;

    /** 发件人名称 */
    private String senderName;

    /** 收件人邮箱地址列表 */
    private List<String> recipients = new ArrayList<>();

    /** 邮件主题 */
    private String subject;

    /** 邮件正文（纯文本，HTML邮件会去除标签转成文本） */
    private String content;

    /** 发送时间 */
    private Date sentDate;

    /** 接收时间 */
    private Date receivedDate;

    /** 发送时间（格式化 yyyy-MM-dd HH:mm:ss） */
    private String sentTimeStr;

    /** 接收时间（格式化 yyyy-MM-dd HH:mm:ss） */
    private String receivedTimeStr;

    /** 邮件类型（分析结果：字典label中文名称，未匹配时为“未归属”） */
    private String type;

    /** 邮件类型编码（分析结果：字典value，未匹配时为 UNKNOWN） */
    private String typeCode;

    /** 附件列表 */
    private List<EkeEmailAttachmentDTO> attachments = new ArrayList<>();
}
