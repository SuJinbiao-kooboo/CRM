<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="产品编码" prop="productCode">
        <el-input v-model="queryParams.productCode" placeholder="请输入产品编码" clearable />
      </el-form-item>
      <el-form-item label="供应商" prop="supplier">
        <el-input v-model="queryParams.supplier" placeholder="请输入供应商" clearable />
      </el-form-item>
      <el-form-item label="品牌" prop="brand">
        <el-input v-model="queryParams.brand" placeholder="请输入品牌" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-upload" size="mini" @click="handleImport">导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport">导出</el-button>
      </el-col>
    </el-row>

    <!-- 表格区域 -->
    <el-table v-loading="loading" :data="stockList">
      <el-table-column label="库存日期" prop="stockDate" width="160">
        <template slot-scope="scope">
          {{ formatDateTime(scope.row.stockDate) }}
        </template>
      </el-table-column>
      <el-table-column label="产品编码" prop="productCode" width="120" />
      <el-table-column label="产品详情" prop="productDetail" />
      <el-table-column label="单价" prop="price" width="100" />
      <el-table-column label="数量" prop="quantity" width="100" />
      <el-table-column label="交货时间" prop="deliveryTime" width="120" />
      <el-table-column label="供应商" prop="supplier" width="120" />
      <el-table-column label="品牌" prop="brand" width="120" />
      <el-table-column label="备注" prop="remark" width="120" />
      <el-table-column label="状态" prop="status" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '有效' : '无效' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template slot-scope="scope">
          <el-button size="mini" type="text" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="库存日期" prop="stockDate">
          <el-date-picker v-model="form.stockDate" type="date" placeholder="选择日期" value-format="yyyy-MM-dd" />
        </el-form-item>
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="请输入产品编码" />
        </el-form-item>
        <el-form-item label="产品详情" prop="productDetail">
          <el-input v-model="form.productDetail" type="textarea" placeholder="请输入产品详情" />
        </el-form-item>
        <el-form-item label="单价" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" placeholder="请输入单价" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0" placeholder="请输入数量" />
        </el-form-item>
        <el-form-item label="交货时间" prop="deliveryTime">
          <el-input v-model="form.deliveryTime" placeholder="请输入交货时间" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplier">
          <el-input v-model="form.supplier" placeholder="请输入供应商" />
        </el-form-item>
        <el-form-item label="品牌" prop="brand">
          <el-input v-model="form.brand" placeholder="请输入品牌" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">有效</el-radio>
            <el-radio :label="0">无效</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog title="库存导入" :visible.sync="importVisible" width="600px">
      <el-form :model="importForm" label-width="120px">
        <el-form-item label="Excel文件" required>
          <el-upload
            class="upload-demo"
            action=""
            :on-change="handleFileChange"
            :auto-upload="false"
            :show-file-list="false"
          >
            <el-button size="small" type="primary">点击上传</el-button>
            <div slot="tip" class="el-upload__tip">请上传Excel文件</div>
          </el-upload>
        </el-form-item>
        
        <el-form-item label="库存日期列" required>
          <el-select v-model="importForm.stockDateCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="产品编码列" required>
          <el-select v-model="importForm.productCodeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <!-- 其他选填列 -->
        <el-form-item label="产品详情列">
          <el-select v-model="importForm.productDetailCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="单价列">
          <el-select v-model="importForm.priceCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="数量列">
          <el-select v-model="importForm.quantityCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="交货时间列">
          <el-select v-model="importForm.deliveryTimeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="备注列">
          <el-select v-model="importForm.remarkCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="供应商列">
          <el-select v-model="importForm.supplierCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="品牌列">
          <el-select v-model="importForm.brandCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="submitImport">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listStock, getStock, addStock, updateStock, delStock, exportStock, importStockData } from '@/api/inventory/stock'
import { resetForm, parseTime } from '@/utils/ruoyi'

export default {
  data() {
    return {
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        productCode: null,
        supplier: null,
        brand: null
      },
      // 加载状态
      loading: false,
      // 库存列表
      stockList: [],
      // 总条数
      total: 0,
      // 显示搜索
      showSearch: true,
      // 新增/编辑相关
      title: '',
      open: false,
      form: {
        id: null,
        stockDate: null,
        productCode: null,
        productDetail: null,
        price: null,
        quantity: null,
        deliveryTime: null,
        supplier: null,
        brand: null,
        remark: null,
        status: 1
      },
      rules: {
        productCode: [
          { required: true, message: '产品编码不能为空', trigger: 'blur' }
        ],
        stockDate: [
          { required: true, message: '库存日期不能为空', trigger: 'change' }
        ]
      },
      // 导入相关
      importVisible: false,
      importForm: {
        file: null,
        stockDateCol: 'A',
        productCodeCol: 'B',
        productDetailCol: '',
        priceCol: '',
        quantityCol: '',
        deliveryTimeCol: '',
        remarkCol: '',
        supplierCol: '',
        brandCol: ''
      },
      columnOptions: ['A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z']
    }
  },
  created() {
    this.getList()
  },
  methods: {
    // 获取库存列表
    getList() {
      this.loading = true
      listStock(this.queryParams).then(response => {
        this.stockList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 搜索
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    // 重置
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        productCode: null,
        supplier: null,
        brand: null
      }
      this.$refs.queryForm.resetFields()
      this.getList()
    },
    // 新增
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '添加库存信息'
    },
    // 编辑
    handleEdit(row) {
      this.reset()
      const id = row.id || this.ids
      getStock(id).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改库存信息'
      })
    },
    // 删除
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$confirm('是否确认删除库存信息编号为"' + ids + '"的数据项?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delStock(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      })
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        stockDate: null,
        productCode: null,
        productDetail: null,
        price: null,
        quantity: null,
        deliveryTime: null,
        supplier: null,
        brand: null,
        remark: null,
        status: 1
      }
      resetForm.call(this, 'form')
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 提交表单
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateStock(this.form).then(response => {
              this.$message.success('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addStock(this.form).then(response => {
              this.$message.success('新增成功')
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    // 导入
    handleImport() {
      this.importVisible = true
    },
    // 文件改变
    handleFileChange(file) {
      this.importForm.file = file.raw
    },
    // 提交导入
    submitImport() {
      if (!this.importForm.file) {
        this.$message.error('请上传文件')
        return
      }
      if (!this.importForm.stockDateCol || !this.importForm.productCodeCol) {
        this.$message.error('请选择必填列')
        return
      }
      
      const formData = new FormData()
      formData.append('file', this.importForm.file)
      formData.append('stockDateCol', this.importForm.stockDateCol)
      formData.append('productCodeCol', this.importForm.productCodeCol)
      formData.append('productDetailCol', this.importForm.productDetailCol)
      formData.append('priceCol', this.importForm.priceCol)
      formData.append('quantityCol', this.importForm.quantityCol)
      formData.append('deliveryTimeCol', this.importForm.deliveryTimeCol)
      formData.append('remarkCol', this.importForm.remarkCol)
      formData.append('supplierCol', this.importForm.supplierCol)
      formData.append('brandCol', this.importForm.brandCol)
      
      importStockData(formData).then(response => {
        this.$message.success('导入成功')
        this.importVisible = false
        this.getList()
      }).catch(error => {
        this.$message.error('导入失败')
      })
    },
    // 导出
    handleExport() {
      exportStock(this.queryParams).then(response => {
        this.download(response, '库存数据.xlsx')
      })
    },
    // 下载文件方法
    download(data, fileName) {
      if (!data) {
        return
      }
      const url = window.URL.createObjectURL(new Blob([data]))
      const link = document.createElement('a')
      link.style.display = 'none'
      link.href = url
      link.setAttribute('download', fileName)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    },
    
    // 格式化日期时间
    formatDateTime(date) {
      if (!date) return ''
      return parseTime(date, '{y}-{m}-{d} {h}:{i}:{s}')
    }
  }
}
</script>

<style scoped>
.upload-demo {
  margin-bottom: 20px;
}
</style>