package com.crm.report.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.crm.report.service.IReportService;
import com.crm.report.mapper.ReportMapper;
import cn.hutool.core.date.DateUtil;

/**
 * 报表统计Service业务层处理
 * 
 * @author crm
 * @date 2024-01-01
 */
@Service
public class ReportServiceImpl implements IReportService 
{
    @Autowired
    private ReportMapper reportMapper;

    /**
     * 获取首页统计数据
     */
    @Override
    public Map<String, Object> getDashboardData()
    {
        Map<String, Object> result = new HashMap<>();
        
        // 基础统计数据
        Map<String, Object> basicStats = new HashMap<>();
        basicStats.put("totalLeads", reportMapper.getTotalLeads());
        basicStats.put("totalCustomers", reportMapper.getTotalCustomers());
        basicStats.put("totalOrders", reportMapper.getTotalOrders());
        basicStats.put("totalSales", reportMapper.getTotalSales());
        
        // 今日数据
        Map<String, Object> todayStats = new HashMap<>();
        String today = DateUtil.today();
        todayStats.put("todayLeads", reportMapper.getTodayLeads(today));
        todayStats.put("todayOrders", reportMapper.getTodayOrders(today));
        todayStats.put("todaySales", reportMapper.getTodaySales(today));
        
        // 本月数据
        Map<String, Object> monthStats = new HashMap<>();
        String monthStart = DateUtil.today().substring(0, 7) + "-01";
        String monthEnd = DateUtil.today();
        monthStats.put("monthLeads", reportMapper.getMonthLeads(monthStart, monthEnd));
        monthStats.put("monthOrders", reportMapper.getMonthOrders(monthStart, monthEnd));
        monthStats.put("monthSales", reportMapper.getMonthSales(monthStart, monthEnd));
        
        // 最近7天销售趋势
        List<Map<String, Object>> salesTrend = reportMapper.getSalesTrend(7);
        
        // 订单状态分布
        List<Map<String, Object>> orderStatusDistribution = reportMapper.getOrderStatusDistribution();
        
        // 热门产品TOP10
        List<Map<String, Object>> topProducts = reportMapper.getTopProducts(10);
        
        // 优质客户TOP10
        List<Map<String, Object>> topCustomers = reportMapper.getTopCustomers(10);
        
        result.put("basicStats", basicStats);
        result.put("todayStats", todayStats);
        result.put("monthStats", monthStats);
        result.put("salesTrend", salesTrend);
        result.put("orderStatusDistribution", orderStatusDistribution);
        result.put("topProducts", topProducts);
        result.put("topCustomers", topCustomers);
        
        return result;
    }

    /**
     * 获取销售报表数据
     */
    @Override
    public Map<String, Object> getSalesReport(String startDate, String endDate, String groupBy)
    {
        Map<String, Object> result = new HashMap<>();
        
        // 设置默认时间范围（最近30天）
        if (startDate == null || endDate == null) {
            endDate = DateUtil.today();
            startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -30));
        }
        
        // 销售总览
        Map<String, Object> salesOverview = new HashMap<>();
        salesOverview.put("totalSales", reportMapper.getSalesAmount(startDate, endDate));
        salesOverview.put("totalOrders", reportMapper.getOrderCount(startDate, endDate));
        salesOverview.put("totalProfit", reportMapper.getTotalProfit(startDate, endDate));
        salesOverview.put("avgOrderAmount", reportMapper.getAvgOrderAmount(startDate, endDate));
        
        // 销售趋势（按日期分组）
        List<Map<String, Object>> salesTrend;
        if ("month".equals(groupBy)) {
            salesTrend = reportMapper.getSalesTrendByMonth(startDate, endDate);
        } else {
            salesTrend = reportMapper.getSalesTrendByDay(startDate, endDate);
        }
        
        // 按业务员分组的销售数据
        List<Map<String, Object>> salesBySalesman = reportMapper.getSalesBySalesman(startDate, endDate);
        
        // 按产品分类分组的销售数据
        List<Map<String, Object>> salesByCategory = reportMapper.getSalesByCategory(startDate, endDate);
        
        // 按客户分组的销售数据
        List<Map<String, Object>> salesByCustomer = reportMapper.getSalesByCustomer(startDate, endDate);
        
        result.put("salesOverview", salesOverview);
        result.put("salesTrend", salesTrend);
        result.put("salesBySalesman", salesBySalesman);
        result.put("salesByCategory", salesByCategory);
        result.put("salesByCustomer", salesByCustomer);
        Map<String, String> dateRange = new HashMap<>();
        dateRange.put("startDate", startDate);
        dateRange.put("endDate", endDate);
        result.put("dateRange", dateRange);
        
        return result;
    }

    /**
     * 获取客户分析报表
     */
    @Override
    public Map<String, Object> getCustomerReport(String startDate, String endDate)
    {
        Map<String, Object> result = new HashMap<>();
        
        // 设置默认时间范围
        if (startDate == null || endDate == null) {
            endDate = DateUtil.today();
            startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -90));
        }
        
        // 客户总览
        Map<String, Object> customerOverview = new HashMap<>();
        customerOverview.put("totalCustomers", reportMapper.getCustomerCount(startDate, endDate));
        customerOverview.put("newCustomers", reportMapper.getNewCustomerCount(startDate, endDate));
        customerOverview.put("activeCustomers", reportMapper.getActiveCustomerCount(startDate, endDate));
        customerOverview.put("customerRetentionRate", reportMapper.getCustomerRetentionRate(startDate, endDate));
        
        // 客户分布（按地区）
        List<Map<String, Object>> customerByRegion = reportMapper.getCustomerByRegion();
        
        // 客户分布（按类型）
        List<Map<String, Object>> customerByType = reportMapper.getCustomerByType();
        
        // 客户价值分析
        List<Map<String, Object>> customerValue = reportMapper.getCustomerValueAnalysis(startDate, endDate);
        
        // 客户流失分析
        List<Map<String, Object>> customerChurn = reportMapper.getCustomerChurnAnalysis(startDate, endDate);
        
        // 新增客户趋势
        List<Map<String, Object>> newCustomerTrend = reportMapper.getNewCustomerTrend(startDate, endDate);
        
        result.put("customerOverview", customerOverview);
        result.put("customerByRegion", customerByRegion);
        result.put("customerByType", customerByType);
        result.put("customerValue", customerValue);
        result.put("customerChurn", customerChurn);
        result.put("newCustomerTrend", newCustomerTrend);
        Map<String, String> dateRange2 = new HashMap<>();
        dateRange2.put("startDate", startDate);
        dateRange2.put("endDate", endDate);
        result.put("dateRange", dateRange2);
        
        return result;
    }

    /**
     * 获取产品分析报表
     */
    @Override
    public Map<String, Object> getProductReport(String startDate, String endDate)
    {
        Map<String, Object> result = new HashMap<>();
        
        // 设置默认时间范围
        if (startDate == null || endDate == null) {
            endDate = DateUtil.today();
            startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -30));
        }
        
        // 产品销售排行
        List<Map<String, Object>> productSalesRanking = reportMapper.getProductSalesRanking(startDate, endDate, 20);
        
        // 产品利润排行
        List<Map<String, Object>> productProfitRanking = reportMapper.getProductProfitRanking(startDate, endDate, 20);
        
        // 产品分类销售分析
        List<Map<String, Object>> categorySalesAnalysis = reportMapper.getCategorySalesAnalysis(startDate, endDate);
        
        // 品牌销售分析
        List<Map<String, Object>> brandSalesAnalysis = reportMapper.getBrandSalesAnalysis(startDate, endDate);
        
        // 产品库存周转分析
        List<Map<String, Object>> inventoryTurnover = reportMapper.getInventoryTurnoverAnalysis();
        
        // 滞销产品分析
        List<Map<String, Object>> slowMovingProducts = reportMapper.getSlowMovingProducts(startDate, endDate);
        
        result.put("productSalesRanking", productSalesRanking);
        result.put("productProfitRanking", productProfitRanking);
        result.put("categorySalesAnalysis", categorySalesAnalysis);
        result.put("brandSalesAnalysis", brandSalesAnalysis);
        result.put("inventoryTurnover", inventoryTurnover);
        result.put("slowMovingProducts", slowMovingProducts);
        Map<String, String> dateRange5 = new HashMap<>();
        dateRange5.put("startDate", startDate);
        dateRange5.put("endDate", endDate);
        result.put("dateRange", dateRange5);
        
        return result;
    }

    /**
     * 获取业务员绩效报表
     */
    @Override
    public Map<String, Object> getPerformanceReport(String startDate, String endDate, Long userId)
    {
        Map<String, Object> result = new HashMap<>();
        
        // 设置默认时间范围
        if (startDate == null || endDate == null) {
            endDate = DateUtil.today();
            startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -30));
        }
        
        // 业务员绩效排行
        List<Map<String, Object>> performanceRanking = reportMapper.getPerformanceRanking(startDate, endDate);
        
        // 个人绩效详情（如果指定了用户ID）
        Map<String, Object> personalPerformance = null;
        if (userId != null) {
            personalPerformance = reportMapper.getPersonalPerformance(startDate, endDate, userId);
        }
        
        // 团队绩效对比
        List<Map<String, Object>> teamPerformance = reportMapper.getTeamPerformance(startDate, endDate);
        
        // 绩效趋势分析
        List<Map<String, Object>> performanceTrend = reportMapper.getPerformanceTrend(startDate, endDate);
        
        // 客户开发情况
        List<Map<String, Object>> customerDevelopment = reportMapper.getCustomerDevelopment(startDate, endDate);
        
        // 跟进活动统计
        List<Map<String, Object>> followUpStats = reportMapper.getFollowUpStats(startDate, endDate);
        
        result.put("performanceRanking", performanceRanking);
        result.put("personalPerformance", personalPerformance);
        result.put("teamPerformance", teamPerformance);
        result.put("performanceTrend", performanceTrend);
        result.put("customerDevelopment", customerDevelopment);
        result.put("followUpStats", followUpStats);
        Map<String, String> dateRange3 = new HashMap<>();
        dateRange3.put("startDate", startDate);
        dateRange3.put("endDate", endDate);
        result.put("dateRange", dateRange3);
        
        return result;
    }

    /**
     * 获取库存分析报表
     */
    @Override
    public Map<String, Object> getInventoryReport()
    {
        Map<String, Object> result = new HashMap<>();
        
        // 库存总览
        Map<String, Object> inventoryOverview = new HashMap<>();
        inventoryOverview.put("totalProducts", reportMapper.getTotalProducts());
        inventoryOverview.put("totalInventoryValue", reportMapper.getTotalInventoryValue());
        inventoryOverview.put("lowStockProducts", reportMapper.getLowStockProductCount());
        inventoryOverview.put("outOfStockProducts", reportMapper.getOutOfStockProductCount());
        
        // 库存预警
        List<Map<String, Object>> inventoryAlerts = reportMapper.getInventoryAlerts();
        
        // 库存分布（按分类）
        List<Map<String, Object>> inventoryByCategory = reportMapper.getInventoryByCategory();
        
        // 库存分布（按品牌）
        List<Map<String, Object>> inventoryByBrand = reportMapper.getInventoryByBrand();
        
        // 库存周转率分析
        List<Map<String, Object>> turnoverAnalysis = reportMapper.getInventoryTurnoverAnalysis();
        
        // 滞销库存分析
        List<Map<String, Object>> slowMovingInventory = reportMapper.getSlowMovingInventory();
        
        // 供应商库存分布
        List<Map<String, Object>> inventoryBySupplier = reportMapper.getInventoryBySupplier();
        
        result.put("inventoryOverview", inventoryOverview);
        result.put("inventoryAlerts", inventoryAlerts);
        result.put("inventoryByCategory", inventoryByCategory);
        result.put("inventoryByBrand", inventoryByBrand);
        result.put("turnoverAnalysis", turnoverAnalysis);
        result.put("slowMovingInventory", slowMovingInventory);
        result.put("inventoryBySupplier", inventoryBySupplier);
        
        return result;
    }

    /**
     * 获取财务报表
     */
    @Override
    public Map<String, Object> getFinanceReport(String startDate, String endDate)
    {
        Map<String, Object> result = new HashMap<>();
        
        // 设置默认时间范围
        if (startDate == null || endDate == null) {
            endDate = DateUtil.today();
            startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -30));
        }
        
        // 财务总览
        Map<String, Object> financeOverview = new HashMap<>();
        financeOverview.put("totalRevenue", reportMapper.getTotalRevenue(startDate, endDate));
        financeOverview.put("totalCost", reportMapper.getTotalCost(startDate, endDate));
        financeOverview.put("totalProfit", reportMapper.getTotalProfit(startDate, endDate));
        financeOverview.put("profitMargin", reportMapper.getProfitMargin(startDate, endDate));
        
        // 应收账款分析
        Map<String, Object> receivableAnalysis = new HashMap<>();
        receivableAnalysis.put("totalReceivable", reportMapper.getTotalReceivable());
        receivableAnalysis.put("overdueReceivable", reportMapper.getOverdueReceivable());
        receivableAnalysis.put("receivableByAge", reportMapper.getReceivableByAge());
        
        // 应付账款分析
        Map<String, Object> payableAnalysis = new HashMap<>();
        payableAnalysis.put("totalPayable", reportMapper.getTotalPayable());
        payableAnalysis.put("overduePayable", reportMapper.getOverduePayable());
        payableAnalysis.put("payableByAge", reportMapper.getPayableByAge());
        
        // 现金流分析
        List<Map<String, Object>> cashFlowAnalysis = reportMapper.getCashFlowAnalysis(startDate, endDate);
        
        // 利润趋势分析
        List<Map<String, Object>> profitTrend = reportMapper.getProfitTrend(startDate, endDate);
        
        // 成本结构分析
        List<Map<String, Object>> costStructure = reportMapper.getCostStructure(startDate, endDate);
        
        result.put("financeOverview", financeOverview);
        result.put("receivableAnalysis", receivableAnalysis);
        result.put("payableAnalysis", payableAnalysis);
        result.put("cashFlowAnalysis", cashFlowAnalysis);
        result.put("profitTrend", profitTrend);
        result.put("costStructure", costStructure);
        Map<String, String> dateRange4 = new HashMap<>();
        dateRange4.put("startDate", startDate);
        dateRange4.put("endDate", endDate);
        result.put("dateRange", dateRange4);
        
        return result;
    }
}