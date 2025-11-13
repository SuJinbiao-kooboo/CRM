package com.crm.supplier.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.supplier.domain.CrmSupplierContact;
import java.util.List;

/**
 * 供应商联系人Service接口
 */
public interface ICrmSupplierContactService extends IService<CrmSupplierContact> {
    
    /**
     * 根据供应商ID查询联系人列表
     */
    List<CrmSupplierContact> getContactsBySupplierId(Long supplierId);
    
    /**
     * 批量保存联系人（新增或更新）
     */
    boolean saveContacts(Long supplierId, List<CrmSupplierContact> contacts);
    
    /**
     * 根据供应商ID删除联系人
     */
    boolean deleteBySupplierId(Long supplierId);
}