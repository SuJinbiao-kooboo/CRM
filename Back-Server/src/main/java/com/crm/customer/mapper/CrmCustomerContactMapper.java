package com.crm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.customer.domain.CrmCustomerContact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户联系人Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmCustomerContactMapper extends BaseMapper<CrmCustomerContact> {
    
}