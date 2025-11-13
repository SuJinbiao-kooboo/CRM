import { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/login'
import Layout from '@/layout/index'

/**
 * 通过meta.role判断是否与当前用户权限匹配
 * @param roles
 * @param route
 */
function hasPermission(roles, route) {
  if (route.meta && route.meta.roles) {
    return roles.some(role => route.meta.roles.includes(role))
  } else {
    return true
  }
}

/**
 * 递归过滤异步路由表，返回符合用户角色权限的路由表
 * @param routes asyncRoutes
 * @param roles
 */
export function filterAsyncRoutes(routes, roles) {
  const res = []

  routes.forEach(route => {
    const tmp = { ...route }
    if (hasPermission(roles, tmp)) {
      if (tmp.children) {
        tmp.children = filterAsyncRoutes(tmp.children, roles)
      }
      res.push(tmp)
    }
  })

  return res
}

const state = {
  routes: [],
  addRoutes: []
}

const mutations = {
  SET_ROUTES: (state, routes) => {
    state.addRoutes = routes
    state.routes = constantRoutes.concat(routes)
  }
}

const actions = {
  generateRoutes({ commit }, roles) {
    return new Promise((resolve, reject) => {
      console.log('开始生成路由，用户角色:', roles)
      // 从后端获取路由数据
      getRouters().then(res => {
        console.log('获取到后端路由数据:', res)
        console.log('后端路由数据详情:', JSON.stringify(res.data, null, 2))
        const backendRoutes = res.data || []
        const processedRoutes = filterAsyncRouter(backendRoutes)
        console.log('处理后的路由:', processedRoutes)
        console.log('处理后的路由详情:', JSON.stringify(processedRoutes, null, 2))
        commit('SET_ROUTES', processedRoutes)
        resolve(processedRoutes)
      }).catch(error => {
        // 如果获取后端路由失败，使用本地路由
        console.error('获取后端路由失败，使用本地路由:', error)
        let accessedRoutes
        if (roles.includes('admin')) {
          accessedRoutes = dynamicRoutes || []
        } else {
          accessedRoutes = filterAsyncRoutes(dynamicRoutes, roles)
        }
        console.log('使用本地路由:', accessedRoutes)
        commit('SET_ROUTES', accessedRoutes)
        resolve(accessedRoutes)
      })
    })
  }
}

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  if (!Array.isArray(asyncRouterMap)) {
    return []
  }
  
  return asyncRouterMap.filter(route => {
    // 过滤掉首页路由，避免与constantRoutes中的重复
    return route.path !== '//dashboard'
  }).map(route => {
    const tmp = { ...route }
    
    // 修复路径格式问题 - 将 //path 转换为 /path
    if (tmp.path && tmp.path.startsWith('//')) {
      tmp.path = tmp.path.substring(1) // 去掉开头的一个 /
    }
    
    // 修复hidden属性 - 主菜单不应该隐藏
    if (tmp.path && !tmp.path.includes('/')) {
      tmp.hidden = false // 主菜单显示
    }
    
    if (tmp.component) {
      // Layout 组件特殊处理
      if (tmp.component === 'Layout') {
        tmp.component = Layout
      } else {
        tmp.component = loadView(tmp.component)
      }
    }
    
    // 处理子路由
    if (tmp.children && tmp.children.length > 0) {
      tmp.children = filterAsyncRouter(tmp.children, tmp, type).map(child => {
        // 子路由也不应该隐藏
        child.hidden = false
        return child
      })
    }
    
    // 设置唯一的路由名称，避免冲突
    if (tmp.meta && tmp.meta.title) {
      tmp.name = tmp.meta.title + 'Route' // 添加后缀避免冲突
    }
    
    return tmp
  })
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    // 简化权限检查，admin用户可以访问所有路由
    res.push(route)
  })
  return res
}

export const loadView = (view) => {
  if (process.env.NODE_ENV === 'development') {
    return (resolve) => require([`@/views/${view}`], resolve)
  } else {
    // 使用 import 实现生产环境的路由懒加载
    return () => import(`@/views/${view}`)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}