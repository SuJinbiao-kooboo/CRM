package com.crm.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.customer.domain.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.ICrmCustomerService;
import org.springframework.stereotype.Service;

/**
 * 客户信息Service业务层处理
 * 
 * @author crm
 * @date 2024-01-01
 */
@Service
public class CrmCustomerServiceImpl extends ServiceImpl<CrmCustomerMapper, CrmCustomer> implements ICrmCustomerService {
    
}