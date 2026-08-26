package com.ruoyi.crm.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;

public interface ICrmSupplierService {
    List<CrmSupplierVO> selectSupplierListJoined(CrmSupplier supplier);
    CrmSupplier selectSupplierById(Long id);
    /** 按编码查询供应商并校验数据权限：非管理员/超级管理员仅能操作跟进人包含自己的供应商（AI录入/批量导入提交时校验） */
    CrmSupplier selectAuthorizedSupplierByCode(String supplierCode);
    int insertSupplier(CrmSupplier supplier);
    int updateSupplier(CrmSupplier supplier);
    int deleteSupplierByIds(Long[] ids);
    java.util.List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier);
    List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier);
    /** 跟进人选择数据源：当前系统启用用户列表（存登录名，显示昵称） */
    List<Map<String, Object>> selectUserOptions(String keyword);
}
