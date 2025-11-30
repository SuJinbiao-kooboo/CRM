package com.ruoyi.crm.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.crm.domain.CrmAttachment;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.CrmSupplierContact;
import com.ruoyi.crm.mapper.CrmAttachmentMapper;
import com.ruoyi.crm.mapper.CrmSupplierContactMapper;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmSupplierService;

@Service
public class CrmSupplierServiceImpl implements ICrmSupplierService {

    @Autowired
    private CrmSupplierMapper supplierMapper;
    @Autowired
    private CrmSupplierContactMapper contactMapper;
    @Autowired
    private CrmAttachmentMapper attachmentMapper;

    @Override
    public List<CrmSupplier> selectSupplierListJoined(CrmSupplier supplier) {
        return supplierMapper.selectSupplierListJoined(supplier);
    }

    @Override
    public List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier) {
        return supplierMapper.selectSupplierOptions(supplier);
    }

    @Override
    public CrmSupplier selectSupplierById(Long id) {
        CrmSupplier s = supplierMapper.selectSupplierById(id);
        if (s != null) {
            List<CrmSupplierContact> contacts = contactMapper.selectBySupplierId(id);
            s.setContacts(contacts);
            List<CrmAttachment> atts = attachmentMapper.selectBy("supplier", id);
            s.setAttachments(atts);
        }
        return s;
    }

    @Override
    @Transactional
    public int insertSupplier(CrmSupplier supplier) {
        int rows = supplierMapper.insertSupplier(supplier);
        if (supplier.getContacts() != null && !supplier.getContacts().isEmpty()) {
            List<CrmSupplierContact> toSave = new ArrayList<>();
            for (CrmSupplierContact c : supplier.getContacts()) {
                c.setSupplierId(supplier.getId());
                toSave.add(c);
            }
            contactMapper.insertBatch(toSave);
        }
        if (supplier.getAttachments() != null && !supplier.getAttachments().isEmpty()) {
            List<CrmAttachment> toSaveAtt = new ArrayList<>();
            for (CrmAttachment a : supplier.getAttachments()) {
                a.setFromType("supplier");
                a.setFromId(supplier.getId());
                toSaveAtt.add(a);
            }
            attachmentMapper.insertBatch(toSaveAtt);
        }
        return rows;
    }

    @Override
    @Transactional
    public int updateSupplier(CrmSupplier supplier) {
        int rows = supplierMapper.updateSupplier(supplier);
        supplierMapper.deleteContactsBySupplierId(supplier.getId());
        attachmentMapper.deleteBy("supplier", supplier.getId());
        if (supplier.getContacts() != null && !supplier.getContacts().isEmpty()) {
            List<CrmSupplierContact> toSave = new ArrayList<>();
            for (CrmSupplierContact c : supplier.getContacts()) {
                c.setSupplierId(supplier.getId());
                toSave.add(c);
            }
            contactMapper.insertBatch(toSave);
        }
        if (supplier.getAttachments() != null && !supplier.getAttachments().isEmpty()) {
            List<CrmAttachment> toSaveAtt = new ArrayList<>();
            for (CrmAttachment a : supplier.getAttachments()) {
                a.setFromType("supplier");
                a.setFromId(supplier.getId());
                toSaveAtt.add(a);
            }
            attachmentMapper.insertBatch(toSaveAtt);
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteSupplierByIds(Long[] ids) {
        for (Long id : ids) {
            supplierMapper.deleteContactsBySupplierId(id);
            attachmentMapper.deleteBy("supplier", id);
        }
        return supplierMapper.deleteSupplierByIds(ids);
    }
}
