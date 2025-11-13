package com.crm.lead.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.lead.domain.CrmLead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索信息Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmLeadMapper extends BaseMapper<CrmLead> {
    
}