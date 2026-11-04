import request from '@/utils/request'

export function listWechatTasks(query) {
  return request({ url: '/crm/wechat-task/list', method: 'get', params: query })
}

export function getWechatTask(id) {
  return request({ url: '/crm/wechat-task/' + id, method: 'get' })
}

export function getWechatTaskConfig() {
  return request({ url: '/crm/wechat-task/config', method: 'get' })
}

export function setWechatTaskGenerateConfig(enabled) {
  return request({ url: '/crm/wechat-task/generate/config', method: 'post', data: { enabled: enabled } })
}

export function setWechatTaskSendConfig(enabled) {
  return request({ url: '/crm/wechat-task/send/config', method: 'post', data: { enabled: enabled } })
}

export function approveWechatTask(id, data) {
  return request({ url: '/crm/wechat-task/' + id + '/approve', method: 'post', data: data })
}

export function batchApproveWechatTasks(data) {
  return request({ url: '/crm/wechat-task/batch-approve', method: 'post', data: data })
}

export function generateWechatTasks(data) {
  return request({ url: '/crm/wechat-task/generate', method: 'post', data: data, timeout: 480000 })
}

export function sendWechatTasks() {
  return request({ url: '/crm/wechat-task/send', method: 'post', timeout: 480000 })
}

export function confirmWechatTaskNotSent(id) {
  return request({ url: '/crm/wechat-task/' + id + '/confirm-not-sent', method: 'post' })
}

export function delWechatTask(id) {
  return request({ url: '/crm/wechat-task/' + id, method: 'delete' })
}

export function batchRemoveWechatTasks(data) {
  return request({ url: '/crm/wechat-task/batch-remove', method: 'post', data: data })
}
