import request from '@/utils/request'

// 邮件订阅（供应商管理 Offer邮箱）
export function listSubscription(query) {
  return request({ url: '/crm/subscription/list', method: 'get', params: query })
}

export function addSubscriptions(emails) {
  return request({ url: '/crm/subscription/add', method: 'post', data: { emails: emails } })
}

export function updateSubscriptionStatus(ids, status) {
  return request({ url: '/crm/subscription/status', method: 'post', data: { ids: ids, status: status } })
}

export function deleteSubscriptions(ids) {
  return request({ url: '/crm/subscription/delete', method: 'post', data: { ids: ids } })
}
