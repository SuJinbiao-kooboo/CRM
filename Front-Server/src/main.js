import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import 'normalize.css/normalize.css' // CSS重置

import '@/styles/index.scss' // 全局样式

// 强制加载菜单样式覆盖
import '@/styles/global-menu.scss'

import '@/permission' // 权限控制

// 全局组件注册
import '@/components/index'

// 引入SVG图标
import '@/icons'

// 全局过滤器
import '@/filters'

// 全局指令
import '@/directive'

Vue.use(ElementUI)

Vue.config.productionTip = false

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')