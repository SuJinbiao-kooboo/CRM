package com.ruoyi.crm.mapper;

import java.util.List;
import com.ruoyi.crm.domain.CrmSupplierContact;

public interface CrmSupplierContactMapper {
    List<CrmSupplierContact> selectBySupplierId(Long supplierId);
    int insertBatch(List<CrmSupplierContact> list);
    int deleteBySupplierId(Long supplierId);
}

