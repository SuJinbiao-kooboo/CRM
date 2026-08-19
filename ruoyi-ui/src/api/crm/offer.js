import request from '@/utils/request'

export function listOffer(query) {
  return request({ url: '/crm/offer/list', method: 'get', params: query })
}

export function getOffer(id) {
  return request({ url: '/crm/offer/' + id, method: 'get' })
}

export function addOffer(data) {
  return request({ url: '/crm/offer', method: 'post', data: data })
}

export function updateOffer(data) {
  return request({ url: '/crm/offer', method: 'put', data: data })
}

export function delOffer(id) {
  return request({ url: '/crm/offer/' + id, method: 'delete' })
}

export function exportOffer(params) {
  return request({ url: '/crm/offer/export', method: 'post', params: params, responseType: 'blob' })
}

export function batchEditOffer(ids, data) {
  const idStr = Array.isArray(ids) ? ids.join(',') : ids
  return request({ url: '/crm/offer/batchEdit', method: 'post', params: { ids: idStr }, data: data })
}

export function importOffer(formData) {
  return request({ url: '/crm/offer/import', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function parseOffer(data) {
  return request({ url: '/crm/offer/parse', method: 'post', data: data })
}

// AI智能录入：粘贴物料内容，后端调用DeepSeek整理后批量入库
export function aiEntry(data) {
  return request({ url: '/crm/offer/aiEntry', method: 'post', data: data })
}

export function sendOffer(data) {
  return request({ url: '/crm/offer/sendOffer', method: 'post', data: data })
}

export function listEmailResults() {
  return request({ url: '/crm/offer/emailResults', method: 'get' })
}
