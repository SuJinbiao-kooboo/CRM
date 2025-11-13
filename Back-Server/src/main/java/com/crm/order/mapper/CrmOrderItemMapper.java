package com.crm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.order.domain.CrmOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmOrderItemMapper extends BaseMapper<CrmOrderItem> {
    
}