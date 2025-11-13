package com.crm.supplier.domain;

import lombok.Data;
import java.util.List;

/**
 * 供应商请求对象，包含联系人信息
 */
@Data
public class SupplierRequest {
    
    /** 供应商信息 */
    private CrmSupplier supplier;
    
    /** 联系人列表 */
    private List<CrmSupplierContact> contacts;
}