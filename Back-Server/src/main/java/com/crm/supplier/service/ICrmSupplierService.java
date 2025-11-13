package com.crm.supplier.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.supplier.domain.CrmSupplier;
import com.crm.supplier.domain.SupplierVO;
import java.util.List;

/**
 * 供应商信息Service接口
 * 
 * @author crm
 * @date 2024-01-01
 */
public interface ICrmSupplierService extends IService<CrmSupplier> {
    
    /**
     * 查询供应商列表（包含联系人信息）
     */
    List<SupplierVO> selectSupplierListWithContact(SupplierVO supplierVO);
}