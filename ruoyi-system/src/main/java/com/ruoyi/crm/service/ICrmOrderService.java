package com.ruoyi.crm.service;

import java.util.List;
import java.util.Map;

import com.ruoyi.crm.domain.CrmOrder;

public interface ICrmOrderService {

    List<CrmOrder> selectOrderList(CrmOrder order);

    CrmOrder selectOrderById(Long id);

    int insertOrder(CrmOrder order);

    int updateOrder(CrmOrder order);

    int deleteOrderByIds(Long[] ids);

    /** 生成订单编号，如 PO20261020-001 */
    String generateOrderNo(String orderType);

    /** 根据供应商解析 Seller 信息块文本（含主联系人），供页面填充与编辑 */
    Map<String, Object> resolveSeller(Long supplierId);

    /** 生成单据 Excel（PO） */
    byte[] generateExcel(CrmOrder order);

}
