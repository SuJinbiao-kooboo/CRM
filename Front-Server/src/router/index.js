import Vue from 'vue'
import VueRouter from 'vue-router'
import Layout from '@/layout'

Vue.use(VueRouter)

/**
 * Note: 路由配置项
 *
 * hidden: true                   // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true               // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect           // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'             // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * meta : {
    roles: ['admin','editor']    // 设置该路由进入的权限，支持多个权限叠加
    title: 'title'               // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'/'el-icon-x' // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false            // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/example/list'  // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

// 公共路由
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        component: () => import('@/views/dashboard/index'),
        name: 'Dashboard',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  }
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/system',
    component: Layout,
    redirect: '/system/dict',
    name: 'System',
    meta: {
      title: '系统管理',
      icon: 'system'
    },
    children: [
      {
        path: 'dict',
        component: () => import('@/views/system/dict/index'),
        name: 'Dict',
        meta: { title: '字典管理', icon: 'dict' }
      },
      {
        path: 'dict-data/index/:dictId(.*)',
        component: () => import('@/views/system/dict/data'),
        name: 'DictData',
        meta: { title: '字典数据', activeMenu: '/system/dict' },
        hidden: true
      },
      {
        path: 'user',
        component: () => import('@/views/system/user/index'),
        name: 'User',
        meta: { title: '用户管理', icon: 'user' }
      },
      {
        path: 'role',
        component: () => import('@/views/system/role/index'),
        name: 'Role',
        meta: { title: '角色管理', icon: 'peoples' }
      },
      {
        path: 'dept',
        component: () => import('@/views/system/dept/index'),
        name: 'Dept',
        meta: { title: '部门管理', icon: 'tree' }
      }
    ]
  },
  {
    path: '/lead',
    component: Layout,
    redirect: '/lead/index',
    name: 'Lead',
    meta: {
      title: '线索管理',
      icon: 'lead'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/lead/index'),
        name: 'LeadIndex',
        meta: { title: '线索列表', icon: 'list' }
      },
      {
        path: 'follow',
        component: () => import('@/views/lead/follow'),
        name: 'LeadFollow',
        meta: { title: '线索跟进', icon: 'follow' }
      }
    ]
  },
  {
    path: '/customer',
    component: Layout,
    redirect: '/customer/index',
    name: 'Customer',
    meta: {
      title: '客户管理',
      icon: 'customer'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/customer/index'),
        name: 'CustomerIndex',
        meta: { title: '客户列表', icon: 'list' }
      },
      {
        path: 'follow',
        component: () => import('@/views/customer/follow'),
        name: 'CustomerFollow',
        meta: { title: '客户跟进', icon: 'follow' }
      }
    ]
  },
  {
    path: '/order',
    component: Layout,
    redirect: '/order/index',
    name: 'Order',
    meta: {
      title: '订单管理',
      icon: 'order'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/order/index'),
        name: 'OrderIndex',
        meta: { title: '订单列表', icon: 'list' }
      }
    ]
  },
  {
    path: '/inventory',
    component: Layout,
    redirect: '/inventory/index',
    name: 'Inventory',
    meta: {
      title: '库存管理',
      icon: 'inventory'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/inventory/index'),
        name: 'InventoryIndex',
        meta: { title: '库存列表', icon: 'list' }
      },
      {
        path: 'stock',
        component: () => import('@/views/inventory/stock/index'),
        name: 'StockIndex',
        meta: { title: '库存管理', icon: 'stock' },
        hidden: true
      },
      {
        path: 'import',
        component: () => import('@/views/inventory/stock/import'),
        name: 'StockImport',
        meta: { title: '库存导入', icon: 'import' },
        hidden: true
      }
    ]
  },
  {
    path: '/inquiry',
    component: Layout,
    redirect: '/inquiry/index',
    name: 'Inquiry',
    meta: {
      title: '询价管理',
      icon: 'inquiry'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/inquiry/index'),
        name: 'InquiryIndex',
        meta: { title: '询价列表', icon: 'list' }
      }
    ]
  },
  {
    path: '/supplier',
    component: Layout,
    redirect: '/supplier/index',
    name: 'Supplier',
    meta: {
      title: '供应商管理',
      icon: 'supplier'
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/supplier/index'),
        name: 'SupplierIndex',
        meta: { title: '供应商列表', icon: 'list' }
      }
    ]
  }
]

const createRouter = () => new VueRouter({
  mode: 'history', // 去掉url中的#
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

// Detail see: https://github.com/vuejs/vue-router/issues/1234#issuecomment-357941465
export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher // reset router
}

export default router