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

// 生成 PO 单据 Excel（订单列表行内导出）
export function generateOrderExcel(data) {
  return request({ url: '/crm/order/generateExcel', method: 'post', data: data, responseType: 'blob' })
}

// 单据类型定义（驱动动态表单）
export function docTypes() {
  return request({ url: '/crm/order/doc/types', method: 'get' })
}

function buildFormData(payload, files) {
  const formData = new FormData()
  formData.append('payload', JSON.stringify(payload))
  if (files) {
    Object.keys(files).forEach(key => {
      if (files[key]) {
        formData.append(key, files[key])
      }
    })
  }
  return formData
}

// 解析上传的 PO / PL 文件，返回可编辑明细
export function parseDocItems(payload, files) {
  return request({
    url: '/crm/order/doc/parseItems',
    method: 'post',
    data: buildFormData(payload, files),
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 生成单据 Excel
export function generateDoc(payload, files) {
  return request({
    url: '/crm/order/doc/generate',
    method: 'post',
    data: buildFormData(payload, files),
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob'
  })
}