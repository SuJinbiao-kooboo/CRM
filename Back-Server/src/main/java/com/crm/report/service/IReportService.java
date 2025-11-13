package com.crm.report.service;

import java.util.Map;

/**
 * 报表统计Service接口
 * 
 * @author crm
 * @date 2024-01-01
 */
public interface IReportService 
{
    /**
     * 获取首页统计数据
     * 
     * @return 统计数据
     */
    Map<String, Object> getDashboardData();

    /**
     * 获取销售报表数据
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param groupBy 分组方式
     * @return 销售报表数据
     */
    Map<String, Object> getSalesReport(String startDate, String endDate, String groupBy);

    /**
     * 获取客户分析报表
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 客户分析数据
     */
    Map<String, Object> getCustomerReport(String startDate, String endDate);

    /**
     * 获取产品分析报表
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 产品分析数据
     */
    Map<String, Object> getProductReport(String startDate, String endDate);

    /**
     * 获取业务员绩效报表
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param userId 用户ID
     * @return 绩效数据
     */
    Map<String, Object> getPerformanceReport(String startDate, String endDate, Long userId);

    /**
     * 获取库存分析报表
     * 
     * @return 库存分析数据
     */
    Map<String, Object> getInventoryReport();

    /**
     * 获取财务报表
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 财务数据
     */
    Map<String, Object> getFinanceReport(String startDate, String endDate);
}