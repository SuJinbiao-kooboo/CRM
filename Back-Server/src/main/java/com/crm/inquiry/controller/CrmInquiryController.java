package com.crm.inquiry.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.inquiry.domain.CrmInquiry;
import com.crm.inquiry.service.ICrmInquiryService;
import com.crm.common.core.page.TableDataInfo;

/**
 * 询价管理Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/inquiry/inquiry")
public class CrmInquiryController extends BaseController {
    
    @Autowired
    private ICrmInquiryService crmInquiryService;

    /**
     * 查询询价管理列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CrmInquiry crmInquiry) {
        startPage();
        List<CrmInquiry> list = crmInquiryService.list();
        return getDataTable(list);
    }

    /**
     * 获取询价管理详细信息
     */
    @GetMapping(value = "/{inquiryId}")
    public AjaxResult getInfo(@PathVariable("inquiryId") Long inquiryId) {
        return success(crmInquiryService.getById(inquiryId));
    }

    /**
     * 新增询价管理
     */
    @PostMapping
    public AjaxResult add(@RequestBody CrmInquiry crmInquiry) {
        return toAjax(crmInquiryService.save(crmInquiry));
    }

    /**
     * 修改询价管理
     */
    @PutMapping
    public AjaxResult edit(@RequestBody CrmInquiry crmInquiry) {
        return toAjax(crmInquiryService.updateById(crmInquiry));
    }

    /**
     * 删除询价管理
     */
    @DeleteMapping("/{inquiryIds}")
    public AjaxResult remove(@PathVariable Long[] inquiryIds) {
        return toAjax(crmInquiryService.removeByIds(Arrays.stream(inquiryIds).collect(Collectors.toList())));
    }
}