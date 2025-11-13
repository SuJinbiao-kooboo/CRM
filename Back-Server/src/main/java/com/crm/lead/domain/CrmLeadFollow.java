package com.crm.lead.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 线索跟进记录对象 crm_lead_follow
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("crm_lead_follow")
public class CrmLeadFollow extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 跟进ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 线索ID */
    @TableField("lead_id")
    private Long leadId;

    /** 跟进人 */
    @TableField("follow_by")
    private Long followBy;

    /** 跟进时间 */
    @TableField("follow_time")
    private Date followTime;

    /** 跟进方式 */
    @TableField("follow_method")
    private String followMethod;

    /** 跟进内容 */
    @TableField("follow_content")
    private String followContent;

    /** 跟进结果 */
    @TableField("follow_result")
    private String followResult;

    /** 下次跟进计划 */
    @TableField("next_plan")
    private String nextPlan;
    
    /** 下次跟进时间 */
    @TableField("next_follow_time")
    private Date nextFollowTime;
    
    /** 附件地址 */
    @TableField("attachment_url")
    private String attachmentUrl;

    /** 备注 */
    @TableField("remark")
    private String remark;
}