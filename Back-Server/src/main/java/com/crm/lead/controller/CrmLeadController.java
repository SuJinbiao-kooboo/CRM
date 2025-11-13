package com.crm.lead.controller;

import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.lead.domain.CrmLead;
import com.crm.lead.service.ICrmLeadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 线索信息Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/lead/lead")
public class CrmLeadController extends BaseController {
    
    @Autowired
    private ICrmLeadService leadService;

    /**
     * 查询线索信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CrmLead lead) {
        startPage();
        List<CrmLead> list = leadService.selectLeadList(lead);
        return getDataTable(list);
    }

    /**
     * 获取线索信息详细信息
     */
    @GetMapping(value = "/{leadId}")
    public AjaxResult getInfo(@PathVariable("leadId") Long leadId) {
        return AjaxResult.success(leadService.selectLeadById(leadId));
    }

    /**
     * 新增线索信息
     */
    @PostMapping
    public AjaxResult add(@RequestBody CrmLead lead) {
        return toAjax(leadService.insertLead(lead));
    }

    /**
     * 修改线索信息
     */
    @PutMapping
    public AjaxResult edit(@RequestBody CrmLead lead) {
        return toAjax(leadService.updateLead(lead));
    }

    /**
     * 删除线索信息
     */
    @DeleteMapping("/{leadIds}")
    public AjaxResult remove(@PathVariable Long[] leadIds) {
        return toAjax(leadService.deleteLeadByIds(leadIds));
    }

    /**
     * 线索转客户
     */
    @PostMapping("/convert/{leadId}")
    public AjaxResult convertToCustomer(@PathVariable Long leadId) {
        return toAjax(leadService.convertToCustomer(leadId));
    }

    /**
     * 生成线索编号
     */
    @GetMapping("/generateNo")
    public AjaxResult generateLeadNo() {
        return AjaxResult.success(leadService.generateLeadNo());
    }
}