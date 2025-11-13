package com.crm.supplier.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商信息实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_supplier")
public class CrmSupplier extends BaseEntity {
    
    /** 供应商ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 供应商编号 */
    @TableField("supplier_code")
    private String supplierCode;
    
    /** 供应商名称 */
    @TableField("supplier_name")
    private String supplierName;
    
    /** 供应商简称 */
    @TableField("supplier_short_name")
    private String supplierShortName;
    
    /** 供应商类型 */
    @TableField("supplier_type")
    private String supplierType;
    
    /** 所属国家/地区 */
    @TableField("country")
    private String country;
    
    /** 官网地址 */
    @TableField("website")
    private String website;
    
    /** 主营产品 */
    @TableField("main_products")
    private String mainProducts;
    
    /** 合作等级 */
    @TableField("cooperation_level")
    private String cooperationLevel;
    
    /** 信用评级 */
    @TableField("credit_rating")
    private String creditRating;
    
    /** 付款条件 */
    @TableField("payment_terms")
    private String paymentTerms;
    
    /** 营业执照号 */
    @TableField("business_license")
    private String businessLicense;
    
    /** 税号 */
    @TableField("tax_number")
    private String taxNumber;
    
    /** 开户银行 */
    @TableField("bank_name")
    private String bankName;
    
    /** 银行账号 */
    @TableField("bank_account")
    private String bankAccount;
    
    /** 地址 */
    @TableField("address")
    private String address;
    
    /** 介绍信息 */
    @TableField("introduction")
    private String introduction;
    
    /** 状态（1正常 0停用） */
    @TableField("status")
    private Integer status;
    
    /** 标签 */
    @TableField("tags")
    private String tags;
}
