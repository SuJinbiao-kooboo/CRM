package com.crm.customer.controller;

import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.customer.domain.CrmCustomer;
import com.crm.customer.domain.CrmCustomer;
import com.crm.customer.service.ICrmCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户信息Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/customer/customer")
public class CrmCustomerController extends BaseController {
    
    @Autowired
    private ICrmCustomerService customerService;

    /**
     * 查询客户信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CrmCustomer customer) {
        startPage();
        List<CrmCustomer> list = customerService.list();
        return getDataTable(list);
    }

    /**
     * 获取客户信息详细信息
     */
    @GetMapping(value = "/{customerId}")
    public AjaxResult getInfo(@PathVariable("customerId") Long customerId) {
        return AjaxResult.success(customerService.getById(customerId));
    }

    /**
     * 新增客户信息
     */
    @PostMapping
    public AjaxResult add(@RequestBody CrmCustomer customer) {
        return toAjax(customerService.save(customer));
    }

    /**
     * 修改客户信息
     */
    @PutMapping
    public AjaxResult edit(@RequestBody CrmCustomer customer) {
        return toAjax(customerService.updateById(customer));
    }

    /**
     * 删除客户信息
     */
    @DeleteMapping("/{customerIds}")
    public AjaxResult remove(@PathVariable Long[] customerIds) {
        return toAjax(customerService.removeByIds(java.util.Arrays.asList(customerIds)));
    }
}