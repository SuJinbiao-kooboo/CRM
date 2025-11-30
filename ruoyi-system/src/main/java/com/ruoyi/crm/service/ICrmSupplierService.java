package com.ruoyi.crm.service;

import java.util.List;
import com.ruoyi.crm.domain.CrmSupplier;

public interface ICrmSupplierService {
    List<CrmSupplier> selectSupplierListJoined(CrmSupplier supplier);
    CrmSupplier selectSupplierById(Long id);
    int insertSupplier(CrmSupplier supplier);
    int updateSupplier(CrmSupplier supplier);
    int deleteSupplierByIds(Long[] ids);
    java.util.List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier);
}
