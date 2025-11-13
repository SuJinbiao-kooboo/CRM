import request from '@/utils/request'

// 查询供应商信息列表
export function listSupplier(query) {
  return request({
    url: '/supplier/supplier/list',
    method: 'get',
    params: query
  })
}

// 查询供应商信息详细
export function getSupplier(supplierId) {
  return request({
    url: '/supplier/supplier/' + supplierId,
    method: 'get'
  })
}

// 新增供应商信息（包含联系人）
export function addSupplier(data) {
  return request({
    url: '/supplier/supplier',
    method: 'post',
    data: data
  })
}

// 修改供应商信息（包含联系人）
export function updateSupplier(data) {
  return request({
    url: '/supplier/supplier',
    method: 'put',
    data: data
  })
}

// 获取供应商联系人列表
export function getSupplierContacts(supplierId) {
  return request({
    url: '/supplier/contact/' + supplierId,
    method: 'get'
  })
}

// 保存供应商联系人
export function saveSupplierContacts(data) {
  return request({
    url: '/supplier/contact',
    method: 'post',
    data: data
  })
}

// 删除供应商信息
export function delSupplier(supplierId) {
  return request({
    url: '/supplier/supplier/' + supplierId,
    method: 'delete'
  })
}

// 生成供应商编号
export function generateSupplierNo() {
  return request({
    url: '/supplier/supplier/generateNo',
    method: 'get'
  })
}

// 获取供应商统计信息
export function getSupplierStatistics() {
  return request({
    url: '/supplier/supplier/statistics',
    method: 'get'
  })
}

// 导出供应商信息
export function exportSupplier(query) {
  return request({
    url: '/supplier/supplier/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  })
}