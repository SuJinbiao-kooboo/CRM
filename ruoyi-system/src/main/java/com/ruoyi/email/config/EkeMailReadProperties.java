package com.ruoyi.email.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业邮箱读取配置（eke.mail.read.*）
 * <p>
 * 邮箱账号、密码、IMAP服务器等信息均在此统一配置，默认值见 application.yml 中的
 * eke.mail.read 节点，切换邮箱账号/服务器时无需改动代码。
 *
 * @author ruoyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "eke.mail.read")
public class EkeMailReadProperties
{
    /** IMAP收件服务器地址 */
    private String imapHost = "imapv.global-mail.cn";

    /** IMAP端口（SSL，默认993） */
    private int imapPort = 993;

    /** 邮箱账号 */
    private String username = "eke@meelectronic.cn";

    /** 邮箱密码（生产环境建议通过配置中心/环境变量注入，不要明文硬编码） */
    private String password = "emailME123";

    /** 读取的邮件文件夹，默认收件箱 */
    private String folder = "INBOX";

    /** 附件保存根目录，按日期自动分子目录 */
    private String attachmentPath = "D:/ruoyi/uploadPath/email-attachments";

    /** 参数配置中无上次读取时间时，默认往前读取的小时数 */
    private int defaultLookbackHours = 24;

    /** 邮件类型匹配规则字典类型（字典value=类型编码、label=类型中文名、remark=逗号分隔的关键词，按字典排序匹配） */
    private String typeDictType = "eke_email_type_dict";

    /** 单次读取邮件的数量上限：起始时间过旧导致邮件数超限时，本次只读最新的N封且不更新参数，剩余邮件下次调用继续读取 */
    private int maxEmailsPerRead = 500;

    /** 发件人过滤规则：发件人地址模糊匹配该值（如内部域名 meelectronic.cn）时跳过该邮件，不读取不返回；留空则不过滤 */
    private String senderBlackFilter = "meelectronic.cn";

    /** 发件人邮箱过滤规则字典类型（第一优先级：字典value=类型编码与主题字典一致、label=类型中文名、remark=逗号拼接的邮箱地址，命中直接归类） */
    private String filterEmailDictType = "eke_email_filter_email_special";
}
