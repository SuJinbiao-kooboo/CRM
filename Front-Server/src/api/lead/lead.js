import request from '@/utils/request'

// 查询线索信息列表
export function listLead(query) {
  return request({
    url: '/lead/lead/list',
    method: 'get',
    params: query
  })
}

// 查询线索信息详细
export function getLead(id) {
  return request({
    url: '/lead/lead/' + id,
    method: 'get'
  })
}

// 新增线索信息
export function addLead(data) {
  return request({
    url: '/lead/lead',
    method: 'post',
    data: data
  })
}

// 修改线索信息
export function updateLead(data) {
  return request({
    url: '/lead/lead',
    method: 'put',
    data: data
  })
}

// 删除线索信息
export function delLead(id) {
  return request({
    url: '/lead/lead/' + id,
    method: 'delete'
  })
}

// 线索转客户
export function convertToCustomer(id) {
  return request({
    url: '/lead/lead/convert/' + id,
    method: 'post'
  })
}

// 生成线索编号
export function generateLeadCode() {
  return request({
    url: '/lead/lead/generateCode',
    method: 'get'
  })
}
