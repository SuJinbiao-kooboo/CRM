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

/** 手工新增任务：按销售群/采购群、供应商名称或合作方向筛选可选供应商。 */
export function listManualWechatTaskSuppliers(query) {
  return request({ url: '/crm/wechat-task/manual/suppliers', method: 'get', params: query, timeout: 60000 })
}

/** 手工新增任务：粘贴原文，并发送到所选供应商的销售群或采购群。 */
export function createManualWechatTask(data) {
  return request({ url: '/crm/wechat-task/manual', method: 'post', data: data, timeout: 60000 })
}

/** 手工新增任务并直接置为“已审批待发送”。 */
export function createManualApprovedWechatTask(data) {
  return request({ url: '/crm/wechat-task/manual/approved', method: 'post', data: data, timeout: 60000 })
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
  return request({ url: '/crm/wechat-task/generate', method: 'post', data: data, timeout: 660000 })
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
