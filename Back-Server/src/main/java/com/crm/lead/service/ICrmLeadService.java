package com.crm.lead.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.lead.domain.CrmLead;

import java.util.List;

/**
 * 线索信息Service接口
 * 
 * @author crm
 * @date 2024-01-01
 */
public interface ICrmLeadService extends IService<CrmLead> {
    
    /**
     * 查询线索信息列表
     * 
     * @param lead 线索信息
     * @return 线索信息集合
     */
    List<CrmLead> selectLeadList(CrmLead lead);
    
    /**
     * 查询线索信息详情
     * 
     * @param leadId 线索ID
     * @return 线索信息
     */
    CrmLead selectLeadById(Long leadId);
    
    /**
     * 新增线索信息
     * 
     * @param lead 线索信息
     * @return 结果
     */
    boolean insertLead(CrmLead lead);
    
    /**
     * 修改线索信息
     * 
     * @param lead 线索信息
     * @return 结果
     */
    boolean updateLead(CrmLead lead);
    
    /**
     * 删除线索信息
     * 
     * @param leadIds 线索ID数组
     * @return 结果
     */
    boolean deleteLeadByIds(Long[] leadIds);
    
    /**
     * 线索转客户
     * 
     * @param leadId 线索ID
     * @return 结果
     */
    boolean convertToCustomer(Long leadId);
    
    /**
     * 生成线索编号
     * 
     * @return 线索编号
     */
    String generateLeadNo();
}