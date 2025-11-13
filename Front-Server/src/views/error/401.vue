<template>
  <div class="error-page">
    <div class="error-code">
      <div class="error-code-text">401</div>
    </div>
    <div class="error-desc">
      <h1>访问权限不足</h1>
      <p>抱歉，您没有访问该页面的权限，请联系管理员。</p>
      <el-button type="primary" @click="back">返回首页</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Page401',
  methods: {
    back() {
      this.$router.push({ path: '/dashboard' })
    }
  }
}
</script>

<style lang="scss" scoped>
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 500px;
  
  .error-code {
    .error-code-img {
      width: 300px;
      height: 300px;
    }
  }
  
  .error-desc {
    padding-left: 60px;
    
    h1 {
      font-size: 24px;
      color: #1f2329;
      margin-bottom: 16px;
    }
    
    p {
      font-size: 14px;
      color: #86909c;
      margin-bottom: 30px;
    }
  }
}
</style>