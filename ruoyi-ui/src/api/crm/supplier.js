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
