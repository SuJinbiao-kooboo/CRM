<template>
  <div class="app-container">
    <el-row :gutter="20">
      <el-col :span="24" :xs="24">
        <div class="text-center">
          <div class="avatar-placeholder">
            <i class="el-icon-user-solid" style="font-size: 60px; color: #409EFF;"></i>
          </div>
          <p>欢迎使用 CRM 管理系统</p>
        </div>
      </el-col>
    </el-row>
    <el-divider />
    <el-row :gutter="20">
      <el-col :span="6" :xs="24">
        <el-card class="update-log">
          <div slot="header" class="clearfix">
            <span>快速导航</span>
          </div>
          <div class="body">
            <p><i class="el-icon-s-custom"></i> 线索管理</p>
            <p><i class="el-icon-user"></i> 客户管理</p>
            <p><i class="el-icon-s-order"></i> 订单管理</p>
            <p><i class="el-icon-s-goods"></i> 库存管理</p>
            <p><i class="el-icon-s-cooperation"></i> 供应商管理</p>
            <p><i class="el-icon-s-marketing"></i> 询价管理</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="18" :xs="24">
        <el-card>
          <div slot="header" class="clearfix">
            <span>系统信息</span>
          </div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;">
              <tbody>
                <tr>
                  <td><div class="cell">CRM版本</div></td>
                  <td><div class="cell" v-text="version.version"></div></td>
                </tr>
                <tr>
                  <td><div class="cell">Java版本</div></td>
                  <td><div class="cell" v-text="version.javaVersion"></div></td>
                </tr>
                <tr>
                  <td><div class="cell">服务器</div></td>
                  <td><div class="cell" v-text="version.server"></div></td>
                </tr>
                <tr>
                  <td><div class="cell">数据库</div></td>
                  <td><div class="cell" v-text="version.database"></div></td>
                </tr>
                <tr>
                  <td><div class="cell">操作系统</div></td>
                  <td><div class="cell" v-text="version.os"></div></td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: "Index",
  data() {
    return {
      // 版本信息
      version: {
        version: "1.0.0",
        javaVersion: "Java 8",
        server: "SpringBoot 2.7.0",
        database: "MySQL 8.0",
        os: "Windows 11"
      }
    };
  },
  methods: {
    goTarget(href) {
      window.open(href, "_blank");
    }
  }
};
</script>

<style scoped lang="scss">
.text-center {
  text-align: center;
}

.avatar-placeholder {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  border: 2px solid #e4e7ed;
}

.update-log {
  .body {
    p {
      margin-top: 15px;
      margin-bottom: 15px;
      
      i {
        margin-right: 8px;
        color: #409EFF;
      }
    }
  }
}
</style>