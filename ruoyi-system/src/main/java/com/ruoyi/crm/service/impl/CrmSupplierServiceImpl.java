package com.ruoyi.crm.service.impl;

import java.util.ArrayList;
import java.util.List;

import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.crm.domain.CrmAttachment;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.CrmSupplierAttachment;
import com.ruoyi.crm.domain.CrmSupplierContact;
import com.ruoyi.crm.mapper.CrmSupplierAttachmentMapper;
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
    private CrmSupplierAttachmentMapper supplierAttachmentMapper;

    @Override
    public List<CrmSupplierVO> selectSupplierListJoined(CrmSupplier supplier) {
        return supplierMapper.selectSupplierListJoined(supplier);
    }

    @Override
    public List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier) {
        return supplierMapper.selectSupplierOptions(supplier);
    }

    @Override
    public List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier) {
        return supplierMapper.selectSupplierSimpleList(supplier);
    }

    @Override
    public CrmSupplier selectSupplierById(Long id) {
        CrmSupplier s = supplierMapper.selectSupplierById(id);
        if (s != null) {
            List<CrmSupplierContact> contacts = contactMapper.selectBySupplierId(id);
            s.setContacts(contacts);
            // 附件已改为内容入库（crm_supplier_attachment 表），此处查询未删除附件并转为轻量视图返回（不含附件内容）
            List<CrmSupplierAttachment> atts = supplierAttachmentMapper.selectListBySupplierId(id);
            List<CrmAttachment> attViews = new ArrayList<>();
            for (CrmSupplierAttachment att : atts)
            {
                CrmAttachment view = new CrmAttachment();
                view.setId(att.getId());
                view.setFileName(att.getFileName());
                view.setCreateTime(att.getCreateTime());
                attViews.add(view);
            }
            s.setAttachments(attViews);
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
            // 附件在上传时已写入附件表（crm_supplier_attachment），此处仅回填供应商ID（新增时供应商ID尚不存在）
            for (CrmAttachment a : supplier.getAttachments()) {
                if (a.getId() != null) {
                    supplierAttachmentMapper.updateSupplierId(a.getId(), supplier.getId());
                }
            }
        }
        return rows;
    }

    @Override
    @Transactional
    public int updateSupplier(CrmSupplier supplier) {
        int rows = supplierMapper.updateSupplier(supplier);
        supplierMapper.deleteContactsBySupplierId(supplier.getId());
        if (supplier.getContacts() != null && !supplier.getContacts().isEmpty()) {
            List<CrmSupplierContact> toSave = new ArrayList<>();
            for (CrmSupplierContact c : supplier.getContacts()) {
                c.setSupplierId(supplier.getId());
                toSave.add(c);
            }
            contactMapper.insertBatch(toSave);
        }
        if (supplier.getAttachments() != null && !supplier.getAttachments().isEmpty()) {
            // 编辑态附件已在上传时绑定供应商ID，此处防御性回填，保证数据一致
            for (CrmAttachment a : supplier.getAttachments()) {
                if (a.getId() != null) {
                    supplierAttachmentMapper.updateSupplierId(a.getId(), supplier.getId());
                }
            }
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteSupplierByIds(Long[] ids) {
        for (Long id : ids) {
            supplierMapper.deleteContactsBySupplierId(id);
            // 供应商删除时，软删除其名下全部正常附件（不物理删除附件记录）
            supplierAttachmentMapper.softDeleteBySupplierId(id);
        }
        return supplierMapper.deleteSupplierByIds(ids);
    }
}
