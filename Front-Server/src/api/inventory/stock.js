import request from '@/utils/request'

// 查询库存信息列表
export function listStock(query) {
  return request({
    url: '/inventory/stock/list',
    method: 'get',
    params: query
  })
}

// 查询库存信息详细
export function getStock(stockId) {
  return request({
    url: '/inventory/stock/' + stockId,
    method: 'get'
  })
}

// 新增库存信息
export function addStock(data) {
  return request({
    url: '/inventory/stock',
    method: 'post',
    data: data
  })
}

// 修改库存信息
export function updateStock(data) {
  return request({
    url: '/inventory/stock',
    method: 'put',
    data: data
  })
}

// 删除库存信息
export function delStock(stockId) {
  return request({
    url: '/inventory/stock/' + stockId,
    method: 'delete'
  })
}

// 导出库存信息
export function exportStock(query) {
  return request({
    url: '/inventory/stock/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  })
}

// 获取可导出字段列表
export function getExportFields() {
  return request({
    url: '/inventory/stock/exportFields',
    method: 'get'
  })
}

// 下载库存导入模板
export function importTemplate() {
  return request({
    url: '/inventory/stock/importTemplate',
    method: 'get',
    responseType: 'blob'
  })
}

// 导入库存数据（支持列映射）
export function importStockData(data) {
  return request({
    url: '/inventory/stock/importData',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 批量更新库存信息
export function batchUpdateStock(data) {
  return request({
    url: '/inventory/stock/batchUpdate',
    method: 'put',
    data: data
  })
}