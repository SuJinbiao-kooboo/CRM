import request from '@/utils/request'

// 查询订单列表
export function listOrder(query) {
  return request({ url: '/crm/order/list', method: 'get', params: query })
}

// 查询订单详情（含明细）
export function getOrder(id) {
  return request({ url: '/crm/order/' + id, method: 'get' })
}

// 新增订单
export function addOrder(data) {
  return request({ url: '/crm/order', method: 'post', data: data })
}

// 修改订单
export function updateOrder(data) {
  return request({ url: '/crm/order', method: 'put', data: data })
}

// 删除订单
export function delOrder(id) {
  return request({ url: '/crm/order/' + id, method: 'delete' })
}

// 生成下一个订单编号
export function nextOrderNo(orderType) {
  return request({ url: '/crm/order/nextOrderNo', method: 'get', params: { orderType: orderType } })
}

// 选择供应商后解析 Seller 信息块
export function resolveSeller(supplierId) {
  return request({ url: '/crm/order/resolveSeller/' + supplierId, method: 'get' })
}

// 生成单据 Excel（PO）
export function generateOrderExcel(data) {
  return request({ url: '/crm/order/generateExcel', method: 'post', data: data, responseType: 'blob' })
}

// 生成单据 PDF（PO）
export function generateOrderPdf(data) {
  return request({ url: '/crm/order/generatePdf', method: 'post', data: data, responseType: 'blob' })
}


// 单据类型定义（驱动动态表单）
export function docTypes() {
  return request({ url: '/crm/order/doc/types', method: 'get' })
}

// 生成单据 Excel
export function generateDoc(payload, file) {
  const formData = new FormData()
  formData.append('payload', JSON.stringify(payload))
  if (file) {
    formData.append('file', file)
  }
  return request({
    url: '/crm/order/doc/generate',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob'
  })
}

// 生成单据 PDF
export function generateDocPdf(payload, file) {
  const formData = new FormData()
  formData.append('payload', JSON.stringify(payload))
  if (file) {
    formData.append('file', file)
  }
  return request({
    url: '/crm/order/doc/generatePdf',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob'
  })
}
