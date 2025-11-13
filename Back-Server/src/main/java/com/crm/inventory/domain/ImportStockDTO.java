package com.crm.inventory.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImportStockDTO {
    @ExcelProperty(index = 0)
    private String stockDate;
    
    @ExcelProperty(index = 1)
    private String materialNumber;
    
    @ExcelProperty(index = 2)
    private String materialDetails;
    
    @ExcelProperty(index = 3)
    private BigDecimal unitPrice;
    
    @ExcelProperty(index = 4)
    private Integer quantity;
    
    @ExcelProperty(index = 5)
    private String deliveryDate;
    
    @ExcelProperty(index = 6)
    private String remarks;
    
    @ExcelProperty(index = 7)
    private String sheetName;
    
    @ExcelProperty(index = 8)
    private String supplier;
    
    @ExcelProperty(index = 9)
    private String brand;
}