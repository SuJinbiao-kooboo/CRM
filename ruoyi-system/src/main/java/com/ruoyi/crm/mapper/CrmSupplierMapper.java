package com.ruoyi.crm.mapper;

import java.util.List;

import com.ruoyi.crm.domain.dto.CrmSendEmailTaskDTO;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import com.ruoyi.crm.domain.dto.SubscribeEmailDTO;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmSupplier;

public interface CrmSupplierMapper {
    List<CrmSupplierVO> selectSupplierListJoined(CrmSupplier supplier);
    CrmSupplier selectSupplierById(Long id);
    int insertSupplier(CrmSupplier supplier);
    int updateSupplier(CrmSupplier supplier);
    int deleteSupplierByIds(Long[] ids);
    int deleteSupplierById(Long id);
    int deleteContactsBySupplierId(Long supplierId);
    int deleteAttachmentsBySupplier(@Param("fromType") String fromType, @Param("fromId") Long fromId);
    List<CrmSupplier> selectSupplierOptions(CrmSupplier supplier);
    List<CrmSupplier> selectSupplierSimpleList(CrmSupplier supplier);
    void batchInsertWithDefault(@Param("batchNo") String batchNo, @Param("emailList") List<String> emailList);
    void deleteAllTask();
    void updateSendResult(@Param("email")String email, @Param("result") String result, @Param("msg") String msg);
    List<CrmSendEmailTaskDTO> selectEmailResultList();
    List<SubscribeEmailDTO> selectSubscribeEmailList();
}
