package com.crm.lead.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 线索联系人实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead_contact")
public class CrmLeadContact extends BaseEntity {
    
    /** 联系人ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 线索ID */
    private Long leadId;
    
    /** 联系人姓名 */
    private String contactName;
    
    /** 职位 */
    private String position;
    
    /** 手机号码 */
    private String phone;
    
    /** 邮箱 */
    private String email;
    
    /** WhatsApp */
    private String whatsapp;
    
    /** 微信 */
    private String wechat;
    
    /** QQ */
    private String qq;
    
    /** 其他联系方式 */
    private String otherContact;
    
    /** 是否主要联系人（1是，0否） */
    private Integer isPrimary;
}