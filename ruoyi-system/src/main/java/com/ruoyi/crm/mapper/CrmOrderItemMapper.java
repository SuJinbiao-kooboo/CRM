package com.ruoyi.crm.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.crm.domain.CrmOrderItem;

public interface CrmOrderItemMapper {

    List<CrmOrderItem> selectItemsByOrderId(Long orderId);

    int batchInsertItems(@Param("list") List<CrmOrderItem> list);

    int deleteItemsByOrderId(Long orderId);

    int deleteItemsByOrderIds(Long[] orderIds);
}
