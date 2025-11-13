package com.crm.customer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户信息实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CrmCustomer extends BaseEntity {
    
    /** 客户ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 客户编号 */
    private String customerCode;
    
    /** 客户名称 */
    private String customerName;
    
    /** 客户简称 */
    private String customerShortName;
    
    /** 所属国家 */
    private String country;
    
    /** 客户类型 */
    private String customerType;
    
    /** 官网地址 */
    private String website;
    
    /** 主营业务 */
    private String mainBusiness;
    
    /** 公司规模 */
    private String companySize;
    
    /** 录入人 */
    private Long inputBy;
    
    /** 跟进人 */
    private Long followBy;
    
    /** 客户状态 */
    private String customerStatus;
    
    /** 客户等级 */
    private String customerLevel;
    
    /** 意向产品 */
    private String intendedProducts;
    
    /** 客户来源 */
    private String customerSource;
    
    /** 介绍信息 */
    private String introduction;
    
    /** 信用额度 */
    private BigDecimal creditLimit;
    
    /** 下次跟进时间 */
    private Date nextFollowTime;
    
    /** 来源线索ID */
    private Long leadId;
}