import request from '@/utils/request'

// 获取报表统计数据
export function getReportStatistics() {
  return request({
    url: '/report/statistics',
    method: 'get'
  })
}

// 获取报表列表
export function getReportList(query) {
  return request({
    url: '/report/list',
    method: 'get',
    params: query
  })
}

// 导出报表
export function exportReport(query) {
  return request({
    url: '/report/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  })
}

// 获取销售趋势数据
export function getSalesTrend(query) {
  return request({
    url: '/report/sales-trend',
    method: 'get',
    params: query
  })
}

// 获取客户分布数据
export function getCustomerDistribution() {
  return request({
    url: '/report/customer-distribution',
    method: 'get'
  })
}

// 获取线索转化率数据
export function getLeadConversion(query) {
  return request({
    url: '/report/lead-conversion',
    method: 'get',
    params: query
  })
}

// 获取订单分析数据
export function getOrderAnalysis(query) {
  return request({
    url: '/report/order-analysis',
    method: 'get',
    params: query
  })
}

// 获取库存分析数据
export function getStockAnalysis() {
  return request({
    url: '/report/stock-analysis',
    method: 'get'
  })
}