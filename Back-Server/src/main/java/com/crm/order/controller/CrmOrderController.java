package com.crm.order.controller;

import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.order.domain.CrmOrder;
import com.crm.order.service.ICrmOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单信息Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/order/order")
public class CrmOrderController extends BaseController {
    
    @Autowired
    private ICrmOrderService orderService;

    /**
     * 查询订单信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(CrmOrder order) {
        startPage();
        List<CrmOrder> list = orderService.list();
        return getDataTable(list);
    }

    /**
     * 获取订单信息详细信息
     */
    @GetMapping(value = "/{orderId}")
    public AjaxResult getInfo(@PathVariable("orderId") Long orderId) {
        return AjaxResult.success(orderService.getById(orderId));
    }

    /**
     * 新增订单信息
     */
    @PostMapping
    public AjaxResult add(@RequestBody CrmOrder order) {
        return toAjax(orderService.save(order));
    }

    /**
     * 修改订单信息
     */
    @PutMapping
    public AjaxResult edit(@RequestBody CrmOrder order) {
        return toAjax(orderService.updateById(order));
    }

    /**
     * 删除订单信息
     */
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@PathVariable Long[] orderIds) {
        return toAjax(orderService.removeByIds(java.util.Arrays.asList(orderIds)));
    }
}