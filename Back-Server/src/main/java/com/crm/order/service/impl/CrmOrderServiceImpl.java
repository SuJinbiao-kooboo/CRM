package com.crm.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.order.domain.CrmOrder;
import com.crm.order.mapper.CrmOrderMapper;
import com.crm.order.service.ICrmOrderService;
import org.springframework.stereotype.Service;

/**
 * 订单信息Service业务层处理
 * 
 * @author crm
 * @date 2024-01-01
 */
@Service
public class CrmOrderServiceImpl extends ServiceImpl<CrmOrderMapper, CrmOrder> implements ICrmOrderService {
    
}