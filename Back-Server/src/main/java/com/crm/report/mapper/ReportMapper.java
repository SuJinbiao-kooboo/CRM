package com.crm.report.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 报表统计Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
public interface ReportMapper 
{
    // 基础统计数据
    Integer getTotalLeads();
    Integer getTotalCustomers();
    Integer getTotalOrders();
    BigDecimal getTotalSales();
    
    // 今日数据
    Integer getTodayLeads(@Param("today") String today);
    Integer getTodayOrders(@Param("today") String today);
    BigDecimal getTodaySales(@Param("today") String today);
    
    // 本月数据
    Integer getMonthLeads(@Param("monthStart") String monthStart, @Param("monthEnd") String monthEnd);
    Integer getMonthOrders(@Param("monthStart") String monthStart, @Param("monthEnd") String monthEnd);
    BigDecimal getMonthSales(@Param("monthStart") String monthStart, @Param("monthEnd") String monthEnd);
    
    // 销售趋势
    List<Map<String, Object>> getSalesTrend(@Param("days") Integer days);
    
    // 订单状态分布
    List<Map<String, Object>> getOrderStatusDistribution();
    
    // 热门产品
    List<Map<String, Object>> getTopProducts(@Param("limit") Integer limit);
    
    // 优质客户
    List<Map<String, Object>> getTopCustomers(@Param("limit") Integer limit);
    
    // 销售报表相关
    BigDecimal getSalesAmount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    Integer getOrderCount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    BigDecimal getTotalProfit(@Param("startDate") String startDate, @Param("endDate") String endDate);
    BigDecimal getAvgOrderAmount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    List<Map<String, Object>> getSalesTrendByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getSalesTrendByMonth(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getSalesBySalesman(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getSalesByCategory(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getSalesByCustomer(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    // 客户分析相关
    Integer getCustomerCount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    Integer getNewCustomerCount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    Integer getActiveCustomerCount(@Param("startDate") String startDate, @Param("endDate") String endDate);
    BigDecimal getCustomerRetentionRate(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    List<Map<String, Object>> getCustomerByRegion();
    List<Map<String, Object>> getCustomerByType();
    List<Map<String, Object>> getCustomerValueAnalysis(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getCustomerChurnAnalysis(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getNewCustomerTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    // 产品分析相关
    List<Map<String, Object>> getProductSalesRanking(@Param("startDate") String startDate, @Param("endDate") String endDate, @Param("limit") Integer limit);
    List<Map<String, Object>> getProductProfitRanking(@Param("startDate") String startDate, @Param("endDate") String endDate, @Param("limit") Integer limit);
    List<Map<String, Object>> getCategorySalesAnalysis(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getBrandSalesAnalysis(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getInventoryTurnoverAnalysis();
    List<Map<String, Object>> getSlowMovingProducts(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    // 绩效分析相关
    List<Map<String, Object>> getPerformanceRanking(@Param("startDate") String startDate, @Param("endDate") String endDate);
    Map<String, Object> getPersonalPerformance(@Param("startDate") String startDate, @Param("endDate") String endDate, @Param("userId") Long userId);
    List<Map<String, Object>> getTeamPerformance(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getPerformanceTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getCustomerDevelopment(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getFollowUpStats(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    // 库存分析相关
    Integer getTotalProducts();
    BigDecimal getTotalInventoryValue();
    Integer getLowStockProductCount();
    Integer getOutOfStockProductCount();
    
    List<Map<String, Object>> getInventoryAlerts();
    List<Map<String, Object>> getInventoryByCategory();
    List<Map<String, Object>> getInventoryByBrand();
    List<Map<String, Object>> getSlowMovingInventory();
    List<Map<String, Object>> getInventoryBySupplier();
    
    // 财务分析相关
    BigDecimal getTotalRevenue(@Param("startDate") String startDate, @Param("endDate") String endDate);
    BigDecimal getTotalCost(@Param("startDate") String startDate, @Param("endDate") String endDate);
    BigDecimal getProfitMargin(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    BigDecimal getTotalReceivable();
    BigDecimal getOverdueReceivable();
    List<Map<String, Object>> getReceivableByAge();
    
    BigDecimal getTotalPayable();
    BigDecimal getOverduePayable();
    List<Map<String, Object>> getPayableByAge();
    
    List<Map<String, Object>> getCashFlowAnalysis(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getProfitTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<Map<String, Object>> getCostStructure(@Param("startDate") String startDate, @Param("endDate") String endDate);
}