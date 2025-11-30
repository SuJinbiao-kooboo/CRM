package com.ruoyi.crm.domain;

import java.util.Date;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

@Data
public class CrmSupplierContact extends BaseEntity {
    @Excel(name = "联系人ID")
    private Long id;
    private Long supplierId;
    @Excel(name = "联系人姓名")
    private String contactName;
    @Excel(name = "岗位")
    private String post;
    @Excel(name = "职位")
    private String position;
    @Excel(name = "手机号")
    private String phone;
    @Excel(name = "邮箱")
    private String email;
    @Excel(name = "WhatsApp")
    private String whatsapp;
    @Excel(name = "微信")
    private String wechat;
    @Excel(name = "QQ")
    private String qq;
    @Excel(name = "其他联系方式1")
    private String otherContactFirst;
    @Excel(name = "其他联系方式2")
    private String otherContactSecond;
    @Excel(name = "备注1")
    private String remarkFirst;
    @Excel(name = "备注2")
    private String remarkSecond;
    @Excel(name = "是否主要", readConverterExp = "1=是,0=否")
    private Integer isPrimary;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

       @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("supplierId", getSupplierId())
                .append("contactName", getContactName())
                .append("post", getPost())
                .append("position", getPosition())
                .append("phone", getPhone())
                .append("email", getEmail())
                .append("whatsapp", getWhatsapp())
                .append("wechat", getWechat())
                .append("qq", getQq())
                .append("otherContactFirst", getOtherContactFirst())
                .append("otherContactSecond", getOtherContactSecond())
                .append("remarkFirst", getRemarkFirst())
                .append("remarkSecond", getRemarkSecond())
                .append("isPrimary", getIsPrimary())
                .toString();
    }
}

