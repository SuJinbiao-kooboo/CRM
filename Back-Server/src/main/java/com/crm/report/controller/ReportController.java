package com.crm.report.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.report.service.IReportService;

/**
 * 报表统计Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/report")
public class ReportController extends BaseController
{
    @Autowired
    private IReportService reportService;

    /**
     * 获取首页统计数据
     */
    @GetMapping("/dashboard")
    public AjaxResult getDashboardData()
    {
        Map<String, Object> data = reportService.getDashboardData();
        return AjaxResult.success(data);
    }

    /**
     * 获取销售报表数据
     */
    @GetMapping("/sales")
    public AjaxResult getSalesReport(@RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate,
                                   @RequestParam(required = false) String groupBy)
    {
        Map<String, Object> data = reportService.getSalesReport(startDate, endDate, groupBy);
        return AjaxResult.success(data);
    }

    /**
     * 获取客户分析报表
     */
    @GetMapping("/customer")
    public AjaxResult getCustomerReport(@RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate)
    {
        Map<String, Object> data = reportService.getCustomerReport(startDate, endDate);
        return AjaxResult.success(data);
    }

    /**
     * 获取产品分析报表
     */
    @GetMapping("/product")
    public AjaxResult getProductReport(@RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate)
    {
        Map<String, Object> data = reportService.getProductReport(startDate, endDate);
        return AjaxResult.success(data);
    }

    /**
     * 获取业务员绩效报表
     */
    @GetMapping("/performance")
    public AjaxResult getPerformanceReport(@RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) Long userId)
    {
        Map<String, Object> data = reportService.getPerformanceReport(startDate, endDate, userId);
        return AjaxResult.success(data);
    }

    /**
     * 获取库存分析报表
     */
    @GetMapping("/inventory")
    public AjaxResult getInventoryReport()
    {
        Map<String, Object> data = reportService.getInventoryReport();
        return AjaxResult.success(data);
    }

    /**
     * 获取财务报表
     */
    @GetMapping("/finance")
    public AjaxResult getFinanceReport(@RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate)
    {
        Map<String, Object> data = reportService.getFinanceReport(startDate, endDate);
        return AjaxResult.success(data);
    }
}