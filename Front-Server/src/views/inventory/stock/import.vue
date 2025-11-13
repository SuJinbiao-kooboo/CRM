<template>
  <div class="app-container">
    <el-card>
      <div slot="header">
        <span>库存数据导入</span>
      </div>
      
      <el-form :model="form" ref="form" label-width="120px">
        <!-- 文件上传 -->
        <el-form-item label="Excel文件" prop="file">
          <el-upload
            ref="upload"
            :auto-upload="false"
            :on-change="handleFileChange"
            :file-list="fileList"
            accept=".xlsx,.xls"
          >
            <el-button size="small" type="primary">点击选择文件</el-button>
            <div slot="tip" class="el-upload__tip">只能上传xlsx/xls文件</div>
          </el-upload>
        </el-form-item>

        <!-- 工作表设置 -->
        <el-form-item label="工作表名称" prop="sheetName">
          <el-input v-model="form.sheetName" placeholder="默认为第一个工作表" style="width: 200px"/>
        </el-form-item>

        <!-- 更新选项 -->
        <el-form-item label="更新选项" prop="updateSupport">
          <el-checkbox v-model="form.updateSupport">更新已存在的数据</el-checkbox>
        </el-form-item>

        <!-- 列映射配置 -->
        <el-form-item label="列映射配置">
          <el-table :data="columnFields" border style="width: 100%">
            <el-table-column prop="field" label="字段" width="120"/>
            <el-table-column prop="name" label="说明" width="150"/>
            <el-table-column prop="required" label="必填" width="80">
              <template slot-scope="scope">
                <el-tag v-if="scope.row.required" type="danger">是</el-tag>
                <el-tag v-else type="info">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="column" label="Excel列">
              <template slot-scope="scope">
                <el-select v-model="form.columnMapping[scope.row.field]" placeholder="请选择列" clearable>
                  <el-option
                    v-for="col in columnOptions"
                    :key="col"
                    :label="col"
                    :value="col"
                  />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleImport" :loading="loading">开始导入</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { importStockData } from '@/api/inventory/stock'

export default {
  name: 'StockImport',
  data() {
    return {
      loading: false,
      fileList: [],
      form: {
        file: null,
        sheetName: '',
        updateSupport: false,
        columnMapping: {}
      },
      columnFields: [
        { field: 'productCode', name: '料号', required: true },
        { field: 'stockDate', name: '库存日期', required: true },
        { field: 'productName', name: '物料详情', required: false },
        { field: 'price', name: '单价', required: false },
        { field: 'quantity', name: '数量', required: false },
        { field: 'deliveryTime', name: '交期', required: false },
        { field: 'remark', name: '备注', required: false },
        { field: 'supplierName', name: '供应商', required: false },
        { field: 'brand', name: '品牌', required: false }
      ],
      columnOptions: ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z']
    }
  },
  methods: {
    handleFileChange(file) {
      this.form.file = file.raw
    },
    
    async handleImport() {
      if (!this.form.file) {
        this.$message.error('请选择Excel文件')
        return
      }

      // 检查必填字段映射
      const requiredFields = this.columnFields.filter(f => f.required)
      for (const field of requiredFields) {
        if (!this.form.columnMapping[field.field]) {
          this.$message.error(`请选择${field.name}对应的列`)
          return
        }
      }

      this.loading = true
      try {
        const formData = new FormData()
        formData.append('file', this.form.file)
        formData.append('updateSupport', this.form.updateSupport)
        formData.append('sheetName', this.form.sheetName)
        
        // 添加列映射参数（使用正确的参数名）
        Object.keys(this.form.columnMapping).forEach(key => {
          if (this.form.columnMapping[key]) {
            formData.append(`columnMapping.${key}`, this.form.columnMapping[key])
          }
        })

        const response = await importStockData(formData)
        this.$message.success(response.msg || '导入成功')
        this.$router.back()
      } catch (error) {
        this.$message.error(error.message || '导入失败')
      } finally {
        this.loading = false
      }
    },
    
    handleCancel() {
      this.$router.back()
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 20px;
}
</style>