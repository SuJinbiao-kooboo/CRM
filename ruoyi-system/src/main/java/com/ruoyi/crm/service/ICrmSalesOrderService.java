package com.ruoyi.crm.service;

import java.util.List;

import com.ruoyi.crm.domain.CrmSalesOrder;
import com.ruoyi.crm.domain.CrmSupplierContact;

public interface ICrmSalesOrderService {

    List<CrmSalesOrder> selectSalesOrderList(CrmSalesOrder order);

    CrmSalesOrder selectSalesOrderById(Long id);

    CrmSalesOrder createSalesOrder(CrmSalesOrder order);

    int updateSalesOrder(CrmSalesOrder order);

    int deleteSalesOrderByIds(Long[] ids);

    List<CrmSupplierContact> selectContactOptions(Long partnerId);

    List<CrmSalesOrder> selectOrderOptions(String keyword);
}
