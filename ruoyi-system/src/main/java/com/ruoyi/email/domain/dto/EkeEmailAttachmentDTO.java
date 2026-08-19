package com.ruoyi.email.domain.dto;

import lombok.Data;

/**
 * 邮件附件信息
 *
 * @author ruoyi
 */
@Data
public class EkeEmailAttachmentDTO
{
    /** 附件原始文件名 */
    private String fileName;

    /** 附件保存后的完整路径 */
    private String filePath;

    /** 附件大小（字节） */
    private long fileSize;

    /** 附件MIME类型 */
    private String contentType;
}
