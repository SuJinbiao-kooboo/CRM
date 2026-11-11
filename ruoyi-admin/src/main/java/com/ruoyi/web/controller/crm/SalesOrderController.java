package com.ruoyi.web.controller.crm;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.crm.domain.CrmSalesOrder;
import com.ruoyi.crm.service.ICrmSalesOrderService;

/**
 * 订单需求管理-订单管理
 */
@RestController
@RequestMapping("/crm/salesOrder")
public class SalesOrderController extends BaseController {

    @Autowired
    private ICrmSalesOrderService salesOrderService;

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:list')")
    @GetMapping("/list")
    public TableDataInfo list(CrmSalesOrder order) {
        startPage();
        List<CrmSalesOrder> list = salesOrderService.selectSalesOrderList(order);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(salesOrderService.selectSalesOrderById(id));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:list')")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(value = "keyword", required = false) String keyword) {
        return success(salesOrderService.selectOrderOptions(keyword));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:list')")
    @GetMapping("/contacts/{partnerId}")
    public AjaxResult contactOptions(@PathVariable Long partnerId) {
        return success(salesOrderService.selectContactOptions(partnerId));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:add')")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody(required = false) CrmSalesOrder order) {
        return success(salesOrderService.createSalesOrder(order));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:edit')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CrmSalesOrder order) {
        salesOrderService.updateSalesOrder(order);
        return success(salesOrderService.selectSalesOrderById(order.getId()));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:remove')")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(salesOrderService.deleteSalesOrderByIds(ids));
    }
}
