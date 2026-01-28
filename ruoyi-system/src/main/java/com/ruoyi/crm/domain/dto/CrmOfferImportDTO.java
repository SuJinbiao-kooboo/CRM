package com.ruoyi.crm.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;

@Data
public class CrmOfferImportDTO implements Serializable {
    @Excel(name = "P/N")
    @ExcelProperty("P/N")
    private String productCode;
//    @Excel(name = "MFG/Brand")
//    @ExcelProperty("MFG/Brand")
//    private String productBrand;
//    @Excel(name = "Desp.")
//    @ExcelProperty("Desp.")
//    private String productDetail;
    @Excel(name = "EXW U/P USD")
    @ExcelProperty("EXW U/P USD")
    private Double priceOffer;
    @Excel(name = "QTY")
    @ExcelProperty("QTY")
    private Integer quantity;
    @Excel(name = "Lead time")
    @ExcelProperty("Lead time")
    private String deliveryTime;
    @Excel(name = "Remark")
    @ExcelProperty("Remark")
    private String remark;

}
