package com.crm.inquiry.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 客户询价实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_inquiry")
public class CrmInquiry extends BaseEntity {
    
    /** 询价ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 询价编号 */
    private String inquiryCode;
    
    /** 客户ID */
    private Long customerId;
    
    /** 询价日期 */
    private Date inquiryDate;
    
    /** 有效期 */
    private Date validUntil;
    
    /** 询价人（客户联系人ID） */
    private Long inquirerContactId;
    
    /** 跟进人 */
    private Long followBy;
    
    /** 询价状态 */
    private String inquiryStatus;
    
    /** 紧急程度 */
    private String urgencyLevel;
}
