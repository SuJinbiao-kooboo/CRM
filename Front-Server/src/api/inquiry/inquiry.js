import request from '@/utils/request'

// 查询询价信息列表
export function listInquiry(query) {
  return request({
    url: '/inquiry/inquiry/list',
    method: 'get',
    params: query
  })
}

// 查询询价信息详细
export function getInquiry(inquiryId) {
  return request({
    url: '/inquiry/inquiry/' + inquiryId,
    method: 'get'
  })
}

// 新增询价信息
export function addInquiry(data) {
  return request({
    url: '/inquiry/inquiry',
    method: 'post',
    data: data
  })
}

// 修改询价信息
export function updateInquiry(data) {
  return request({
    url: '/inquiry/inquiry',
    method: 'put',
    data: data
  })
}

// 删除询价信息
export function delInquiry(inquiryId) {
  return request({
    url: '/inquiry/inquiry/' + inquiryId,
    method: 'delete'
  })
}

// 生成询价单号
export function generateInquiryNo() {
  return request({
    url: '/inquiry/inquiry/generateNo',
    method: 'get'
  })
}

// 查询询价项目列表
export function listInquiryItem(inquiryId) {
  return request({
    url: '/inquiry/inquiry/items/' + inquiryId,
    method: 'get'
  })
}

// 更新询价状态
export function updateInquiryStatus(inquiryId, status) {
  return request({
    url: '/inquiry/inquiry/status',
    method: 'put',
    data: {
      inquiryId: inquiryId,
      status: status
    }
  })
}