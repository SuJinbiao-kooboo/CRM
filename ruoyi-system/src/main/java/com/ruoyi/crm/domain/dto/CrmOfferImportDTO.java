package com.ruoyi.crm.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Date;

@Data
public class CrmOfferImportDTO implements Serializable {
    @Excel(name = "PN")
    private String productCode;
    @Excel(name = "Brand")
    private String productBrand;
    @Excel(name = "Desc")
    private String productDetail;
    @Excel(name = "Price")
    private Double priceOffer;
    @Excel(name = "Unit")
    private String priceUnit;
    @Excel(name = "Quantity")
    private Integer quantity;
    @Excel(name = "LT")
    private String deliveryTime;
    @Excel(name = "Remark")
    private String remark;
    @Excel(name = "MOQ")
    private Integer moqQuantity;
    @Excel(name = "warranty")
    private String warrantyDetail;
    @Excel(name = "DC")
    private String dc;
}
