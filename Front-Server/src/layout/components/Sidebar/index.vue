<template>
  <div :class="{'has-logo':showLogo}" style="background-color: #2C3E50 !important;">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="false"
        background-color="#2C3E50"
        text-color="#FFFFFF"
        :unique-opened="false"
        active-text-color="#5DADE2"
        :collapse-transition="false"
        mode="vertical"
        class="custom-sidebar-menu"
        style="background-color: #2C3E50 !important; border: none !important;"
      >
        <sidebar-item v-for="route in permission_routes" :key="route.path" :item="route" :base-path="route.path" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import variables from '@/styles/variables.scss'

export default {
  components: { SidebarItem, Logo },
  computed: {
    ...mapGetters([
      'permission_routes',
      'sidebar'
    ]),
    activeMenu() {
      const route = this.$route
      const { meta, path } = route
      // if set path, the sidebar will highlight the path you set
      if (meta.activeMenu) {
        return meta.activeMenu
      }
      return path
    },
    showLogo() {
      return this.$store.state.settings.sidebarLogo
    },
    variables() {
      return variables
    },
    isCollapse() {
      return !this.sidebar.opened
    }
  }
}
</script>

<style lang="scss" scoped>
// 强制覆盖菜单样式
.custom-sidebar-menu {
  background-color: #2C3E50 !important;
  border: none !important;
  
  // 一级菜单项
  ::v-deep .el-menu-item {
    background-color: #2C3E50 !important;
    color: #FFFFFF !important;
    
    &:hover {
      background-color: #34495E !important;
      color: #5DADE2 !important;
    }
    
    &.is-active {
      background-color: #5DADE2 !important;
      color: #FFFFFF !important;
    }
  }
  
  // 子菜单标题
  ::v-deep .el-submenu__title {
    background-color: #2C3E50 !important;
    color: #FFFFFF !important;
    
    &:hover {
      background-color: #34495E !important;
      color: #5DADE2 !important;
    }
  }
  
  // 子菜单容器
  ::v-deep .el-submenu .el-menu {
    background-color: #34495E !important;
    
    .el-menu-item {
      background-color: #34495E !important;
      color: #FFFFFF !important;
      
      span {
        color: #FFFFFF !important;
        opacity: 1 !important;
        visibility: visible !important;
      }
      
      &:hover {
        background-color: #3F5A6E !important;
        color: #5DADE2 !important;
        
        span {
          color: #5DADE2 !important;
        }
      }
      
      &.is-active {
        background-color: #5DADE2 !important;
        color: #FFFFFF !important;
        
        span {
          color: #FFFFFF !important;
        }
      }
    }
  }
}
</style>
