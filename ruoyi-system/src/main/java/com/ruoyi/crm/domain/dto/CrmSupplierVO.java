package com.ruoyi.crm.domain.dto;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.crm.domain.CrmAttachment;
import com.ruoyi.crm.domain.CrmSupplierContact;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class CrmSupplierVO extends BaseEntity {
    @Excel(name = "供应商ID", cellType = Excel.ColumnType.NUMERIC)
    private Long id;
    @Excel(name = "供应商编号")
    private String supplierCode;
    @Excel(name = "供应商名称")
    private String supplierName;
    @Excel(name = "供应商简称")
    private String supplierShortName;
    @Excel(name = "供应商类型")
    private String supplierType;
    @Excel(name = "品牌")
    private String brands;
    @Excel(name = "所属国家")
    private String country;
    @Excel(name = "地址")
    private String address;
    @Excel(name = "官网地址")
    private String website;
    @Excel(name = "主营产品")
    private String mainProducts;
    @Excel(name = "合作等级")
    private String cooperationLevel;
    @Excel(name = "风险等级")
    private String riskLevel;
    @Excel(name = "付款条件")
    private String paymentTerms;
    @Excel(name = "合作状态")
    private String cooperationStatus;
    @Excel(name = "营业执照号")
    private String businessLicense;
    @Excel(name = "税号")
    private String taxNumber;
    @Excel(name = "银行信息")
    private String bankInfo;
    @Excel(name = "银行账号")
    private String bankAccount;
    @Excel(name = "Bill to")
    private String billTo;
    @Excel(name = "Ship to")
    private String shipTo;
    @Excel(name = "介绍信息")
    private String introduction;
    @Excel(name = "备注1")
    private String remark;
    @Excel(name = "备注2")
    private String remarkSecond;
    @Excel(name = "状态", readConverterExp = "1=正常,0=停用")
    private Integer status;
    @Excel(name = "跟进人")
    private String followUpBy;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    @Excel(name = "标签1")
    private String tagsFirst;
    @Excel(name = "标签2")
    private String tagsSecond;
    @Excel(name = "标签3")
    private String tagsThird;
    @Excel(name = "标签4")
    private String tagsSi;

    private List<CrmSupplierContact> contacts;
    private List<CrmAttachment> attachments;

    @Excel(name = "联系人姓名")
    private String contactName;
    @Excel(name = "联系人岗位")
    private String post;
    @Excel(name = "联系人职位")
    private String position;
    @Excel(name = "联系人手机号")
    private String phone;
    @Excel(name = "联系人邮箱")
    private String email;
    @Excel(name = "联系人WhatsApp")
    private String whatsapp;
    @Excel(name = "联系人微信")
    private String wechat;
    @Excel(name = "联系人Teams")
    private String teams;
    @Excel(name = "联系人其他联系方式1")
    private String otherContactFirst;
    @Excel(name = "联系人其他联系方式2")
    private String otherContactSecond;
    @Excel(name = "联系人备注1")
    private String contactRemarkFirst;
    @Excel(name = "联系人备注2")
    private String contactRemarkSecond;
    @Excel(name = "是否主要", readConverterExp = "1=是,0=否")
    private Integer isPrimary;
}
