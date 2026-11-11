package com.ruoyi.crm.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.crm.domain.CrmSalesOrder;

public interface CrmSalesOrderMapper {

    List<CrmSalesOrder> selectSalesOrderList(CrmSalesOrder order);

    CrmSalesOrder selectSalesOrderById(Long id);

    List<CrmSalesOrder> selectSalesOrderOptions(@Param("keyword") String keyword);

    int countOrderNo(@Param("orderNo") String orderNo, @Param("excludeId") Long excludeId);

    int insertSalesOrder(CrmSalesOrder order);

    int updateSalesOrder(CrmSalesOrder order);

    int deleteSalesOrderByIds(Long[] ids);
}
