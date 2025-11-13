package com.crm.supplier.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.supplier.domain.CrmSupplier;
import com.crm.supplier.domain.SupplierVO;
import com.crm.supplier.mapper.CrmSupplierMapper;
import com.crm.supplier.service.ICrmSupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 供应商信息Service业务层处理
 * 
 * @author crm
 * @date 2024-01-01
 */
@Service
public class CrmSupplierServiceImpl extends ServiceImpl<CrmSupplierMapper, CrmSupplier> implements ICrmSupplierService {
    
    @Autowired
    private CrmSupplierMapper crmSupplierMapper;

    /**
     * 查询供应商列表（包含联系人信息）
     */
    @Override
    public List<SupplierVO> selectSupplierListWithContact(SupplierVO supplierVO) {
        return crmSupplierMapper.selectSupplierListWithContact(supplierVO);
    }
}