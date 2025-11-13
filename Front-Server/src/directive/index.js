// 全局指令
import Vue from 'vue'

// 权限指令
Vue.directive('hasPermi', {
  inserted(el, binding) {
    const { value } = binding
    const permissions = ['*:*:*'] // 默认拥有所有权限，实际项目中应该从store获取
    
    if (value && value instanceof Array && value.length > 0) {
      const hasPermission = permissions.some(permission => {
        return value.includes(permission) || permission === '*:*:*'
      })
      
      if (!hasPermission) {
        el.parentNode && el.parentNode.removeChild(el)
      }
    } else {
      throw new Error('权限指令需要传入权限数组')
    }
  }
})

// 角色指令
Vue.directive('hasRole', {
  inserted(el, binding) {
    const { value } = binding
    const roles = ['admin'] // 默认角色，实际项目中应该从store获取
    
    if (value && value instanceof Array && value.length > 0) {
      const hasRole = roles.some(role => {
        return value.includes(role)
      })
      
      if (!hasRole) {
        el.parentNode && el.parentNode.removeChild(el)
      }
    } else {
      throw new Error('角色指令需要传入角色数组')
    }
  }
})