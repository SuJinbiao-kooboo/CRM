// 全局组件注册
import Vue from 'vue'

// 导入组件
import Pagination from '@/components/Pagination'
import RightToolbar from '@/components/RightToolbar'
import DictTag from '@/components/DictTag'
import SvgIcon from '@/components/SvgIcon'

// 全局注册组件
Vue.component('Pagination', Pagination)
Vue.component('RightToolbar', RightToolbar)
Vue.component('DictTag', DictTag)
Vue.component('SvgIcon', SvgIcon)
