package com.crm.supplier.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.supplier.domain.CrmSupplierContact;
import com.crm.supplier.mapper.CrmSupplierContactMapper;
import com.crm.supplier.service.ICrmSupplierContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 供应商联系人Service业务层处理
 */
@Service
public class CrmSupplierContactServiceImpl extends ServiceImpl<CrmSupplierContactMapper, CrmSupplierContact> implements ICrmSupplierContactService {

    @Autowired
    private CrmSupplierContactMapper contactMapper;

    /**
     * 根据供应商ID查询联系人列表
     */
    @Override
    public List<CrmSupplierContact> getContactsBySupplierId(Long supplierId) {
        return contactMapper.selectContactsBySupplierId(supplierId);
    }

    /**
     * 批量保存联系人（新增或更新）
     */
    @Override
    @Transactional
    public boolean saveContacts(Long supplierId, List<CrmSupplierContact> contacts) {
        // 先删除该供应商的所有联系人
        contactMapper.deleteBySupplierId(supplierId);
        
        // 批量插入新的联系人
        if (contacts != null && !contacts.isEmpty()) {
            for (CrmSupplierContact contact : contacts) {
                contact.setSupplierId(supplierId);
                this.save(contact);
            }
        }
        return true;
    }

    /**
     * 根据供应商ID删除联系人
     */
    @Override
    public boolean deleteBySupplierId(Long supplierId) {
        return contactMapper.deleteBySupplierId(supplierId) > 0;
    }
}