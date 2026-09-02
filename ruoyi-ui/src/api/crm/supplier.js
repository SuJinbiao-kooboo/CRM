import request from '@/utils/request'

export function listSupplier(query) {
  return request({ url: '/crm/supplier/list', method: 'get', params: query })
}

export function getSupplier(id) {
  return request({ url: '/crm/supplier/' + id, method: 'get' })
}

export function getSupplierDetail(id) {
  return request({ url: '/crm/supplier/detail/' + id, method: 'get' })
}

export function addSupplier(data) {
  return request({ url: '/crm/supplier', method: 'post', data: data })
}

export function updateSupplier(data) {
  return request({ url: '/crm/supplier', method: 'put', data: data })
}

// 写跟进：仅更新上次/下次跟进时间与结论/目标（列表"写跟进"弹窗提交）
export function updateSupplierFollowUp(data) {
  return request({ url: '/crm/supplier/followUp', method: 'put', data: data })
}

export function delSupplier(id) {
  return request({ url: '/crm/supplier/' + id, method: 'delete' })
}

export function exportSupplier(params) {
  return request({ url: '/crm/supplier/export', method: 'post', params: params, responseType: 'blob' })
}

export function listSupplierOptions(query) {
  return request({ url: '/crm/supplier/options', method: 'get', params: query })
}

export function listSupplierSimple(query) {
  return request({ url: '/crm/supplier/simpleList', method: 'get', params: query })
}

// 跟进人选择数据源：当前系统启用用户（存登录名userName，显示昵称nickName）
export function listSupplierUsers(query) {
  return request({ url: '/crm/supplier/userOptions', method: 'get', params: query })
}

// 删除供应商附件（软删除，仅标记删除不物理删除）
export function delSupplierAttachment(id) {
  return request({ url: '/crm/supplier/attachment/' + id, method: 'delete' })
}
