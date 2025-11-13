package com.crm.supplier.domain;

import lombok.Data;
import java.util.List;

/**
 * 供应商视图对象，包含联系人信息
 */
@Data
public class SupplierVO {
    
    /** 供应商ID */
    private Long id;
    
    /** 供应商编码 */
    private String supplierCode;
    
    /** 供应商名称 */
    private String supplierName;
    
    /** 供应商简称 */
    private String supplierShortName;
    
    /** 所属国家 */
    private String country;
    
    /** 供应商类型 */
    private String supplierType;
    
    /** 官网地址 */
    private String website;
    
    /** 主营产品 */
    private String mainProducts;
    
    /** 合作等级 */
    private String cooperationLevel;
    
    /** 信用评级 */
    private String creditRating;
    
    /** 付款条件 */
    private String paymentTerms;
    
    /** 营业执照号 */
    private String businessLicense;
    
    /** 税号 */
    private String taxNumber;
    
    /** 开户银行 */
    private String bankName;
    
    /** 银行账号 */
    private String bankAccount;
    
    /** 地址 */
    private String address;
    
    /** 介绍信息 */
    private String introduction;
    
    /** 标签 */
    private String tags;
    
    /** 状态 */
    private Integer status;
    
    /** 创建人 */
    private Long createBy;
    
    /** 创建时间 */
    private String createTime;
    
    /** 更新人 */
    private Long updateBy;
    
    /** 更新时间 */
    private String updateTime;
    
    /** 备注 */
    private String remark;
    
    /** 联系人姓名（用于列表显示） */
    private String contactName;
    
    /** 联系人电话（用于列表显示） */
    private String contactPhone;
    
    /** 联系人邮箱（用于列表显示） */
    private String contactEmail;
    
    /** 联系人职位（用于列表显示） */
    private String contactPosition;
    
    /** WhatsApp（用于列表显示） */
    private String whatsapp;
    
    /** 微信（用于列表显示） */
    private String wechat;
    
    /** QQ（用于列表显示） */
    private String qq;
    
    /** 其他联系方式（用于列表显示） */
    private String otherContact;
}