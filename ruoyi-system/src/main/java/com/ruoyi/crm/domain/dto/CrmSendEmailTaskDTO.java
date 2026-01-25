package com.ruoyi.crm.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 邮件发送任务实体类
 *
 * @author ruoyi
 */
@Data
public class CrmSendEmailTaskDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 发送结果
     */
    private String result;

    /**
     * 详细日志/消息
     */
    private String msg;

    /**
     * 更新时间(自动更新)
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}