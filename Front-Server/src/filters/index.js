// 全局过滤器
import Vue from 'vue'

// 日期格式化过滤器
Vue.filter('dateFormat', function (value, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!value) return ''
  const date = new Date(value)
  return formatDate(date, format)
})

// 状态过滤器
Vue.filter('statusFilter', function (status) {
  const statusMap = {
    0: '禁用',
    1: '启用'
  }
  return statusMap[status] || '未知'
})

// 简单的日期格式化函数
function formatDate(date, format) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}