import request from '@/utils/request'

// 查询订单信息列表
export function listOrder(query) {
  return request({
    url: '/order/order/list',
    method: 'get',
    params: query
  })
}

// 查询订单信息详细
export function getOrder(orderId) {
  return request({
    url: '/order/order/' + orderId,
    method: 'get'
  })
}

// 新增订单信息
export function addOrder(data) {
  return request({
    url: '/order/order',
    method: 'post',
    data: data
  })
}

// 修改订单信息
export function updateOrder(data) {
  return request({
    url: '/order/order',
    method: 'put',
    data: data
  })
}

// 删除订单信息
export function delOrder(orderId) {
  return request({
    url: '/order/order/' + orderId,
    method: 'delete'
  })
}

// 生成订单编号
export function generateOrderNo() {
  return request({
    url: '/order/order/generateNo',
    method: 'get'
  })
}

// 更新订单状态
export function updateOrderStatus(data) {
  return request({
    url: '/order/order/status',
    method: 'put',
    data: data
  })
}

// 获取订单统计信息
export function getOrderStatistics() {
  return request({
    url: '/order/order/statistics',
    method: 'get'
  })
}