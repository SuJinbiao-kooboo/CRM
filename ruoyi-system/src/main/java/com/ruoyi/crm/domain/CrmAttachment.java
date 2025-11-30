package com.ruoyi.crm.domain;

import java.util.Date;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

@Data
public class CrmAttachment extends BaseEntity {
    private Long id;
    @Excel(name = "来源类型")
    private String fromType;
    private Long fromId;
    @Excel(name = "附件名称")
    private String fileName;
    @Excel(name = "附件地址")
    private String fileUrl;
    @Excel(name = "备注")
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFromType() { return fromType; }
    public void setFromType(String fromType) { this.fromType = fromType; }
    public Long getFromId() { return fromId; }
    public void setFromId(Long fromId) { this.fromId = fromId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    @Override public String getCreateBy() { return createBy; }
    @Override public void setCreateBy(String createBy) { this.createBy = createBy; }
    @Override public Date getCreateTime() { return createTime; }
    @Override public void setCreateTime(Date createTime) { this.createTime = createTime; }
    @Override public String getUpdateBy() { return updateBy; }
    @Override public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    @Override public Date getUpdateTime() { return updateTime; }
    @Override public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("fromType", getFromType())
                .append("fromId", getFromId())
                .append("fileName", getFileName())
                .append("fileUrl", getFileUrl())
                .append("remark", getRemark())
                .toString();
    }
}

