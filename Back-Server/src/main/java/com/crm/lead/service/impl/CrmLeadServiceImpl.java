package com.crm.lead.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.crm.lead.domain.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.lead.service.ICrmLeadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 线索信息Service业务层处理
 * 
 * @author crm
 * @date 2024-01-01
 */
@Service
public class CrmLeadServiceImpl extends ServiceImpl<CrmLeadMapper, CrmLead> implements ICrmLeadService {

    /**
     * 查询线索信息列表
     * 
     * @param lead 线索信息
     * @return 线索信息
     */
    @Override
    public List<CrmLead> selectLeadList(CrmLead lead) {
        QueryWrapper<CrmLead> queryWrapper = new QueryWrapper<>();
        
        // 线索名称模糊查询
        if (StrUtil.isNotEmpty(lead.getLeadName())) {
            queryWrapper.like("lead_name", lead.getLeadName());
        }
        
        // 线索状态精确查询
        if (StrUtil.isNotEmpty(lead.getLeadStatus())) {
            queryWrapper.eq("lead_status", lead.getLeadStatus());
        }
        
        // 线索类型精确查询
        if (StrUtil.isNotEmpty(lead.getLeadType())) {
            queryWrapper.eq("lead_type", lead.getLeadType());
        }
        
        // 跟进人精确查询
        if (Objects.nonNull(lead.getFollowBy())) {
            queryWrapper.eq("follow_by", lead.getFollowBy());
        }
        
        // 所属国家精确查询
        if (StrUtil.isNotEmpty(lead.getCountry())) {
            queryWrapper.eq("country", lead.getCountry());
        }
        
        // 按创建时间倒序排列
        queryWrapper.orderByDesc("create_time");
        
        return this.list(queryWrapper);
    }

    /**
     * 查询线索信息详情
     * 
     * @param leadId 线索ID
     * @return 线索信息
     */
    @Override
    public CrmLead selectLeadById(Long leadId) {
        return this.getById(leadId);
    }

    /**
     * 新增线索信息
     * 
     * @param lead 线索信息
     * @return 结果
     */
    @Override
    @Transactional
    public boolean insertLead(CrmLead lead) {
        // 生成线索编号
        lead.setLeadCode(generateLeadNo());
        lead.setCreateTime(new Date());
        return this.save(lead);
    }

    /**
     * 修改线索信息
     * 
     * @param lead 线索信息
     * @return 结果
     */
    @Override
    @Transactional
    public boolean updateLead(CrmLead lead) {
        lead.setUpdateTime(new Date());
        return this.updateById(lead);
    }

    /**
     * 批量删除线索信息
     * 
     * @param leadIds 需要删除的线索ID
     * @return 结果
     */
    @Override
    @Transactional
    public boolean deleteLeadByIds(Long[] leadIds) {
        return this.removeByIds(Arrays.asList(leadIds));
    }

    /**
     * 线索转客户
     * 
     * @param leadId 线索ID
     * @return 结果
     */
    @Override
    @Transactional
    public boolean convertToCustomer(Long leadId) {
        // 获取线索信息
        CrmLead lead = this.getById(leadId);
        if (lead == null) {
            return false;
        }
        
        // 更新线索状态为已转客户
        lead.setLeadStatus("converted");
        lead.setUpdateTime(new Date());
        
        // TODO: 这里需要创建客户记录，将线索信息转换为客户信息
        // 由于客户模块还未实现，这里先更新线索状态
        
        return this.updateById(lead);
    }

    /**
     * 生成线索编号
     * 
     * @return 线索编号
     */
    @Override
    public String generateLeadNo() {
        // 生成格式：LD + 年月日 + 3位序号，如：LD20240101001
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        
        // 查询当天已有的线索数量
        QueryWrapper<CrmLead> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("lead_no", "LD" + dateStr);
        long count = this.count(queryWrapper);
        
        // 生成序号，从001开始
        String sequence = String.format("%03d", count + 1);
        
        return "LD" + dateStr + sequence;
    }
}