package com.crm.inquiry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.inquiry.domain.CrmInquiry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户询价Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmInquiryMapper extends BaseMapper<CrmInquiry> {
    
}