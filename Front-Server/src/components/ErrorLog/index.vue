<template>
  <div class="errLog-container">
    <el-button type="primary" @click="showErrorLog">
      <i class="el-icon-warning-outline"></i>
      错误日志
    </el-button>
    
    <el-dialog
      title="错误日志"
      :visible.sync="dialogVisible"
      width="80%"
      top="5vh"
    >
      <el-table :data="errorLogs" style="width: 100%">
        <el-table-column prop="message" label="错误信息" width="300" />
        <el-table-column prop="stack" label="错误堆栈" show-overflow-tooltip />
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column label="操作" width="100">
          <template slot-scope="scope">
            <el-button size="mini" @click="clearErrorLog(scope.$index)">
              清除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="clearAllErrorLogs">清空所有</el-button>
        <el-button type="primary" @click="dialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ErrorLog',
  data() {
    return {
      dialogVisible: false,
      errorLogs: []
    }
  },
  methods: {
    showErrorLog() {
      this.dialogVisible = true
      this.getErrorLogs()
    },
    getErrorLogs() {
      // 从localStorage获取错误日志
      const logs = localStorage.getItem('errorLogs')
      this.errorLogs = logs ? JSON.parse(logs) : []
    },
    clearErrorLog(index) {
      this.errorLogs.splice(index, 1)
      localStorage.setItem('errorLogs', JSON.stringify(this.errorLogs))
    },
    clearAllErrorLogs() {
      this.errorLogs = []
      localStorage.removeItem('errorLogs')
    }
  }
}
</script>

<style scoped>
.errLog-container {
  display: inline-block;
}
</style>