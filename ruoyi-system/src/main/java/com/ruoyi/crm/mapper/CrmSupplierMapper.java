package com.ruoyi.crm.mapper;

import java.util.List;
import java.util.Map;

import com.ruoyi.crm.domain.dto.CrmSendEmailTaskDTO;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import com.ruoyi.crm.domain.dto.SubscribeEmailDTO;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmSupplier;

public interface CrmSupplierMapper {
    List<CrmSupplierVO> selectSupplierListJoined(CrmSupplier supplier);
    CrmSupplier selectSupplierById(Long id);
    /** 按编码查询供应商（AI录入/批量导入提交时的数据权限校验用） */
    CrmSupplier selectSupplierByCode(@Param("supplierCode") String supplierCode);
    /** 统计供应商表与Offer表中占用该编码的记录数（编码唯一性校验，供应商编号自动生成时查重） */
    int countBySupplierCode(@Param("supplierCode") String supplierCode);
    int insertSupplier(CrmSupplier supplier);
    int updateSupplier(CrmSupplier supplier);
    /** 写跟进：仅更新跟进记录字段（上次/下次跟进时间、结论/目标富文本），全量覆盖以支持清空 */
    int updateFollowUp(CrmSupplier supplier);
    int deleteSupplierByIds(Long[] ids);
    int deleteSupplierById(Long id);
    int deleteContactsBySupplierId(Long supplierId);
    int deleteAttachmentsBySupplier(@Param("fromType") String fromType, @Param("fromId") Long fromId);
    List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier);
    List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier);
    /** 跟进人昵称解析：登录名转昵称（存储为登录名保证唯一，展示用昵称） */
    List<String> selectNickNamesByUserNames(@Param("userNames") String userNames);
    /** 跟进人选择数据源：当前系统启用用户列表 */
    List<Map<String, Object>> selectUserOptions(@Param("keyword") String keyword);
    void batchInsertWithDefault(@Param("batchNo") String batchNo, @Param("emailList") List<String> emailList);
    void deleteAllTask();
    void updateSendResult(@Param("email")String email, @Param("result") String result, @Param("msg") String msg);
    List<CrmSendEmailTaskDTO> selectEmailResultList();
    List<SubscribeEmailDTO> selectSubscribeEmailList();
}
