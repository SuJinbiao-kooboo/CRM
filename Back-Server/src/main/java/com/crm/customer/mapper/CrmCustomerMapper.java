package com.crm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.customer.domain.CrmCustomer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户信息Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmCustomerMapper extends BaseMapper<CrmCustomer> {
    
}