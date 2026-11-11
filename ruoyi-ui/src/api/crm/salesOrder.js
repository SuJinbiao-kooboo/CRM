import request from '@/utils/request'

// 订单列表
export function listSalesOrder(query) {
  return request({ url: '/crm/salesOrder/list', method: 'get', params: query })
}

// 订单详情
export function getSalesOrder(id) {
  return request({ url: '/crm/salesOrder/' + id, method: 'get' })
}

// 新增订单（后端自动生成订单编号）
export function addSalesOrder(data) {
  return request({ url: '/crm/salesOrder', method: 'post', data: data || {} })
}

// 修改订单
export function updateSalesOrder(data) {
  return request({ url: '/crm/salesOrder', method: 'put', data: data })
}

// 删除订单
export function delSalesOrder(ids) {
  return request({ url: '/crm/salesOrder/' + ids, method: 'delete' })
}

// 订单下拉选项（SN批量录入用）
export function listSalesOrderOptions(keyword) {
  return request({ url: '/crm/salesOrder/options', method: 'get', params: { keyword: keyword } })
}

// 客户/供应商联系人选项
export function listSalesOrderContacts(partnerId) {
  return request({ url: '/crm/salesOrder/contacts/' + partnerId, method: 'get' })
}

// SN列表
export function listSalesOrderSn(query) {
  return request({ url: '/crm/salesOrderSn/list', method: 'get', params: query })
}

// SN批量录入
export function batchAddSalesOrderSn(data) {
  return request({ url: '/crm/salesOrderSn/batch', method: 'post', data: data })
}

// SN修改
export function updateSalesOrderSn(data) {
  return request({ url: '/crm/salesOrderSn', method: 'put', data: data })
}

// SN删除
export function delSalesOrderSn(ids) {
  return request({ url: '/crm/salesOrderSn/' + ids, method: 'delete' })
}
