package com.ruoyi.crm.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
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
        applyPermFilter(supplier);
        return supplierMapper.selectSupplierListJoined(supplier);
    }

    @Override
    public List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier) {
        applyPermFilter(supplier);
        return supplierMapper.selectSupplierOptions(supplier);
    }

    @Override
    public List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier) {
        applyPermFilter(supplier);
        return supplierMapper.selectSupplierSimpleList(supplier);
    }

    @Override
    public List<Map<String, Object>> selectUserOptions(String keyword) {
        return supplierMapper.selectUserOptions(keyword);
    }

    @Override
    public CrmSupplier selectSupplierById(Long id) {
        CrmSupplier s = supplierMapper.selectSupplierById(id);
        if (s == null) {
            return null;
        }
        // 数据权限：非管理员/超级管理员仅能查看跟进人包含自己的供应商
        if (!isFullAccess() && !isFollowedByCurrentUser(s.getFollowUpBy())) {
            throw new ServiceException("无权查看该供应商信息");
        }
        // 解析跟进人昵称用于展示（存储为登录名保证唯一）
        if (s.getFollowUpBy() != null && !s.getFollowUpBy().trim().isEmpty()) {
            List<String> nickNames = supplierMapper.selectNickNamesByUserNames(s.getFollowUpBy());
            s.setFollowUpByNames(String.join(",", nickNames));
        }
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
        return s;
    }

    @Override
    public CrmSupplier selectAuthorizedSupplierByCode(String supplierCode) {
        CrmSupplier s = supplierMapper.selectSupplierByCode(supplierCode);
        if (s == null) {
            throw new ServiceException("供应商不存在：" + supplierCode);
        }
        // 数据权限：非管理员/超级管理员仅能操作跟进人包含自己的供应商（防止绕过前端直接提交）
        if (!isFullAccess() && !isFollowedByCurrentUser(s.getFollowUpBy())) {
            throw new ServiceException("无权操作该供应商，请选择跟进人包含自己的供应商");
        }
        return s;
    }

    @Override
    @Transactional
    public int insertSupplier(CrmSupplier supplier) {
        // 供应商编号为空时自动生成（防泄密：编号不含供应商名称信息，统一使用VC+月日+随机3位规则）
        if (supplier.getSupplierCode() == null || supplier.getSupplierCode().trim().isEmpty()) {
            supplier.setSupplierCode(generateSupplierCode());
        }
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
    public int updateSupplierFollowUp(CrmSupplier supplier) {
        // 数据权限校验：供应商不存在或非管理员/跟进人不含当前登录用户时，详情查询会抛出异常
        CrmSupplier exist = selectSupplierById(supplier.getId());
        if (exist == null) {
            throw new ServiceException("供应商不存在");
        }
        return supplierMapper.updateFollowUp(supplier);
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

    /**
     * 生成供应商编号：VC + 月日(MMdd) + 随机3位数字，查重保证唯一
     * 统一规则防泄密（编号不含供应商名称/简称等可识別信息）
     */
    private String generateSupplierCode() {
        Random random = new Random();
        String prefix = "VC" + new SimpleDateFormat("MMdd").format(new Date());
        // 随机3位数字查重，冲突则重新生成
        while (true) {
            String code = prefix + String.format("%03d", random.nextInt(1000));
            if (supplierMapper.countBySupplierCode(code) == 0) {
                return code;
            }
        }
    }

    /**
     * 数据权限：角色包含管理员(manager)或超级管理员(admin)时不限制；
     * 无登录上下文的系统级调用（如匿名邮件任务）也不限制
     */
    private boolean isFullAccess() {
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            List<SysRole> roles = loginUser.getUser().getRoles();
            if (roles == null) {
                return false;
            }
            for (SysRole role : roles) {
                if ("admin".equals(role.getRoleKey()) || "manager".equals(role.getRoleKey())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 当前登录用户名（登录名，与follow_up_by存储格式一致）；无登录上下文返回null */
    private String currentUserName() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    /** 跟进人是否包含当前登录用户（逗号分隔匹配登录名） */
    private boolean isFollowedByCurrentUser(String followUpBy) {
        String userName = currentUserName();
        if (userName == null || userName.isEmpty() || followUpBy == null || followUpBy.isEmpty()) {
            return false;
        }
        return Arrays.stream(followUpBy.split(",")).anyMatch(t -> t.trim().equals(userName));
    }

    /** 非管理员/超级管理员：供应商查询仅返回跟进人包含当前登录用户的记录 */
    private void applyPermFilter(CrmSupplier query) {
        if (isFullAccess()) {
            return;
        }
        String userName = currentUserName();
        if (userName != null && !userName.isEmpty()) {
            query.getParams().put("permUserName", userName);
        }
    }
}
