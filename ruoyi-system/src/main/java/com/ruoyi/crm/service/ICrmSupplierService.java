package com.ruoyi.crm.service;

import java.util.List;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;

public interface ICrmSupplierService {
    List<CrmSupplierVO> selectSupplierListJoined(CrmSupplier supplier);
    CrmSupplier selectSupplierById(Long id);
    int insertSupplier(CrmSupplier supplier);
    int updateSupplier(CrmSupplier supplier);
    int deleteSupplierByIds(Long[] ids);
    java.util.List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier);
    List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier);
}
