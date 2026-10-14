package com.ruoyi.crm.domain.dto;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

@Data
public class CrmOfferExportDTO extends BaseEntity {

    @Excel(name = "产品编码")
    private String productCode;
    @Excel(name = "产品品牌")
    private String productBrand;
    @Excel(name = "单价-成本")
    private Double priceCost;
    @Excel(name = "单价-报价")
    private Double priceOffer;
    @Excel(name = "数量")
    private Integer quantity;
    @Excel(name = "DC")
    private String dc;

    @Excel(name = "供应商编号")
    private String supplierName;
}
