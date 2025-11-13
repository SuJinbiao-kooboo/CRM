package com.crm.supplier.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商联系人实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_supplier_contact")
public class CrmSupplierContact extends BaseEntity {
    
    /** 联系人ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 供应商ID */
    private Long supplierId;
    
    /** 联系人姓名 */
    private String contactName;
    
    /** 职位 */
    private String position;
    
    /** 手机号 */
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
    
    /** 是否主要联系人：1是，0否 */
    private Integer isPrimary;
}