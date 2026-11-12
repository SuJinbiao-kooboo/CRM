package com.ruoyi.crm.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售订单附件（文件内容入库存储）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmSalesOrderFile extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;

    /** 附件栏位，对应订单上的文件字段名，如 piOurFile */
    private String fieldKey;
    private String fileName;
    private String contentType;
    private Long fileSize;

    /** 附件二进制内容，列表查询时不返回 */
    @JsonIgnore
    private byte[] fileContent;

    /** 删除标记：0=正常，1=已删除 */
    private String delFlag;
}
