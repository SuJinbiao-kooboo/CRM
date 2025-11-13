package com.crm.inventory.domain.dto;

import lombok.Data;
import java.util.Map;

/**
 * 库存导入请求DTO
 */
@Data
public class StockImportDTO {
    
    /** Excel文件数据 */
    private byte[] fileData;
    
    /** 列映射配置 */
    private Map<String, String> columnMapping;
    
    /** 是否更新已存在的数据 */
    private Boolean updateSupport;
    
    /** 工作表名称 */
    private String sheetName;
}