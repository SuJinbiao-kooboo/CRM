package com.ruoyi.crm.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.crm.domain.CrmSalesOrderFile;

public interface CrmSalesOrderFileMapper {

    int insert(CrmSalesOrderFile file);

    List<CrmSalesOrderFile> selectListByOrderId(@Param("orderId") Long orderId);

    CrmSalesOrderFile selectById(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("updateBy") String updateBy);

    int softDeleteByOrderIds(@Param("orderIds") Long[] orderIds, @Param("updateBy") String updateBy);
}
