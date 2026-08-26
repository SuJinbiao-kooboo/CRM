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
// timeout: 130秒（必须大于后端DeepSeek调用超时120秒，否则前端先超时abort，Network面板显示canceled，但后端仍在执行会成功入库造成困惑）；silent: 错误提示由页面自行处理
export function aiEntry(data) {
  return request({ url: '/crm/offer/aiEntry', method: 'post', data: data, timeout: 130000, silent: true })
}

// AI料号查询：粘贴物料内容，后端调用DeepSeek提取料号并查询最近半年INQ/OFFER历史
// timeout: 130秒（同aiEntry）；silent: 错误提示由页面自行处理
export function aiQuery(data) {
  return request({ url: '/crm/offer/aiQuery', method: 'post', data: data, timeout: 130000, silent: true })
}

// 复制Offer：查询最近days天内INQ/OFFER记录（按品牌排序、相同料号取成本最低，无价格也保留），返回制表符分隔文本
// silent: 错误提示由页面自行处理
// timeout: 30秒（纯数据库查询，无AI调用）
export function copyOfferText(days) {
  return request({ url: '/crm/offer/copyOfferText', params: { days: days }, method: 'get', timeout: 30000, silent: true })
}

// AI查询复制：按料号集合+最近days天查询各料号报价最低的Offer（1=当天0点至当前，N=N-1天前0点至当前），返回"料号 报价 数量 交期 DC 货况"制表符分隔文本
// silent: 错误提示由页面自行处理；timeout: 30秒（纯数据库查询，无AI调用）
export function copyAiQueryOffers(data) {
  return request({ url: '/crm/offer/copyAiQueryOffers', method: 'post', data: data, timeout: 30000, silent: true })
}

export function sendOffer(data) {
  return request({ url: '/crm/offer/sendOffer', method: 'post', data: data })
}

export function listEmailResults() {
  return request({ url: '/crm/offer/emailResults', method: 'get' })
}
