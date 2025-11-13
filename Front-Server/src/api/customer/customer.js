import request from '@/utils/request'

// 查询客户信息列表
export function listCustomer(query) {
  return request({
    url: '/customer/customer/list',
    method: 'get',
    params: query
  })
}

// 查询客户信息详细
export function getCustomer(customerId) {
  return request({
    url: '/customer/customer/' + customerId,
    method: 'get'
  })
}

// 新增客户信息
export function addCustomer(data) {
  return request({
    url: '/customer/customer',
    method: 'post',
    data: data
  })
}

// 修改客户信息
export function updateCustomer(data) {
  return request({
    url: '/customer/customer',
    method: 'put',
    data: data
  })
}

// 删除客户信息
export function delCustomer(customerId) {
  return request({
    url: '/customer/customer/' + customerId,
    method: 'delete'
  })
}

// 生成客户编号
export function generateCustomerNo() {
  return request({
    url: '/customer/customer/generateNo',
    method: 'get'
  })
}

// 获取客户统计信息
export function getCustomerStatistics() {
  return request({
    url: '/customer/customer/statistics',
    method: 'get'
  })
}