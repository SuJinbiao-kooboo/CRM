package com.crm.lead.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 线索信息实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class CrmLead extends BaseEntity {
    
    /** 线索ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 线索编号 */
    private String leadCode;
    
    /** 线索名称 */
    private String leadName;
    
    /** 所属国家 */
    private String country;
    
    /** 线索类型 */
    private String leadType;
    
    /** 官网地址 */
    private String website;
    
    /** 录入人 */
    private Long inputBy;
    
    /** 跟进人 */
    private Long followBy;
    
    /** 线索状态 */
    private String leadStatus;
    
    /** 线索来源 */
    private String leadSource;
    
    /** 意向产品 */
    private String intendedProducts;
    
    /** 介绍信息 */
    private String introduction;
    
    /** 预估订单金额 */
    private BigDecimal estimatedAmount;
    
    /** 下次跟进时间 */
    private Date nextFollowTime;
    
    /** 联系人列表 */
    @TableField(exist = false)
    private List<CrmLeadContact> contacts;
    
    /** 跟进记录列表 */
    @TableField(exist = false)
    private List<CrmLeadFollow> followRecords;
}