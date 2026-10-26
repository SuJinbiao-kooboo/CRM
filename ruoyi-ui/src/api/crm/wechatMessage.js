import request from '@/utils/request'

export function listWechatMessages(query) {
  return request({ url: '/crm/wechat-message/list', method: 'get', params: query })
}

export function getWechatMessageStats() {
  return request({ url: '/crm/wechat-message/stats', method: 'get' })
}

export function getWechatMedia(params) {
  return request({
    url: '/crm/wechat-message/media',
    method: 'get',
    params: params,
    responseType: 'blob',
    timeout: 180000,
    silent: true
  })
}

export function getWechatQuoteInfo(params) {
  return request({
    url: '/crm/wechat-message/quote-info',
    method: 'get',
    params: params,
    silent: true
  })
}

export function getWechatQuoteMedia(params) {
  return request({
    url: '/crm/wechat-message/quote-media',
    method: 'get',
    params: params,
    responseType: 'blob',
    timeout: 180000,
    silent: true
  })
}

export function runWechatClassify(data) {
  return request({ url: '/crm/wechat-message/classify/run', method: 'post', data: data, timeout: 480000 })
}

export function clearWechatClassifyCache() {
  return request({ url: '/crm/wechat-message/classify/cache/clear', method: 'post' })
}

export function scanWechatTrace() {
  return request({ url: '/crm/wechat-message/trace/scan', method: 'post' })
}

export function getWechatEntryStatus() {
  return request({ url: '/crm/wechat-message/entry/status', method: 'get' })
}

export function runWechatEntry(data) {
  return request({ url: '/crm/wechat-message/entry/run', method: 'post', data: data, timeout: 480000 })
}

export function requeueWechatEntry(data) {
  return request({ url: '/crm/wechat-message/entry/requeue', method: 'post', data: data })
}

export function runWechatPoll(data) {
  return request({ url: '/crm/wechat-message/poll/run', method: 'post', data: data, timeout: 180000 })
}
