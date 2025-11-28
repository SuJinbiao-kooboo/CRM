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
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleBatchDelete">批量删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="el-icon-edit" size="mini" :disabled="multiple" @click="handleBatchEdit">批量编辑</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" :disabled="multiple" @click="handleBatchExport">导出选中</el-button>
      </el-col>
    </el-row>

    <!-- 表格区域 -->
    <div class="list-layout">
      <div class="table-container">
        <el-table v-loading="loading" :data="stockList" height="100%" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
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
          <el-table-column label="产品类型" prop="productType" width="120" />
          <el-table-column label="产品明细编号" prop="productDetailCode" width="120" />
          <el-table-column label="Inq/Offer类型" prop="inqOfferType" width="120" />
          <el-table-column label="备注" prop="remark" width="120" />
          <el-table-column label="操作" width="180">
            <template slot-scope="scope">
              <el-button size="mini" type="text" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button size="mini" type="text" @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" />
      </div>
    </div>

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
        
        <!-- 库存日期使用当前时间，不再需要选择列 -->
        
        <el-form-item label="产品编码列" required>
          <el-select v-model="importForm.productCodeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        
        <!-- 供应商列（必填，第二个字段） -->
        <el-form-item label="供应商列" required>
          <el-select v-model="importForm.supplierCol" placeholder="请选择">
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
        
        <!-- SHEET_NAME使用文件名拼接，不再需要选择列 -->
        
        <el-form-item label="品牌列">
          <el-select v-model="importForm.brandCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品类型列">
          <el-select v-model="importForm.productTypeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品明细编号列">
          <el-select v-model="importForm.productDetailCodeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="Inq/Offer类型列">
          <el-select v-model="importForm.inqOfferTypeCol" placeholder="请选择">
            <el-option v-for="item in columnOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="submitImport">确定</el-button>
      </div>
    </el-dialog>

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
        <el-form-item label="产品类型" prop="productType">
          <el-input v-model="form.productType" placeholder="请输入产品类型" />
        </el-form-item>
        <el-form-item label="产品明细编号" prop="productDetailCode">
          <el-input v-model="form.productDetailCode" placeholder="请输入产品明细编号" />
        </el-form-item>
        <el-form-item label="Inq/Offer类型" prop="inqOfferType">
          <el-input v-model="form.inqOfferType" placeholder="请输入Inq/Offer类型" />
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

    <!-- 导出字段选择对话框 -->
    <el-dialog title="选择导出字段" :visible.sync="exportDialogVisible" width="600px" append-to-body>
      <div class="export-fields-dialog">
        <div class="field-selection">
          <div class="field-actions">
            <el-button type="text" @click="selectAllFields">全选</el-button>
            <el-button type="text" @click="clearAllFields">清空</el-button>
            <el-button type="text" @click="resetFields">重置</el-button>
          </div>
          <el-checkbox-group v-model="selectedExportFields">
            <div class="field-list">
              <el-checkbox 
                v-for="field in exportFields" 
                :key="field.fieldName" 
                :label="field.fieldName"
                class="field-item"
              >
                <span class="field-name">{{ field.displayName }}</span>
                <span class="field-type">({{ field.dataType }})</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
        <div class="selected-count">
          已选择 {{ selectedExportFields.length }} 个字段
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="exportDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="confirmExport">导 出</el-button>
      </div>
    </el-dialog>

    <!-- 批量编辑对话框 -->
    <el-dialog title="批量编辑" :visible.sync="batchEditVisible" width="600px" append-to-body>
      <el-form ref="batchForm" :model="batchForm" label-width="120px">
        <el-form-item label="产品详情">
          <el-input v-model="batchForm.productDetail" type="textarea" placeholder="请输入产品详情（留空则不更新）" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="batchForm.price" :min="0" :precision="2" placeholder="请输入单价（留空则不更新）" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="batchForm.quantity" :min="0" placeholder="请输入数量（留空则不更新）" />
        </el-form-item>
        <el-form-item label="交货时间">
          <el-input v-model="batchForm.deliveryTime" placeholder="请输入交货时间（留空则不更新）" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="batchForm.supplier" placeholder="请输入供应商（留空则不更新）" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="batchForm.brand" placeholder="请输入品牌（留空则不更新）" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-input v-model="batchForm.productType" placeholder="请输入产品类型（留空则不更新）" />
        </el-form-item>
        <el-form-item label="产品明细编号">
          <el-input v-model="batchForm.productDetailCode" placeholder="请输入产品明细编号（留空则不更新）" />
        </el-form-item>
        <el-form-item label="Inq/Offer类型">
          <el-input v-model="batchForm.inqOfferType" placeholder="请输入Inq/Offer类型（留空则不更新）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" type="textarea" placeholder="请输入备注（留空则不更新）" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="batchEditVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitBatchEdit">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listStock, getStock, addStock, updateStock, delStock, exportStock, batchUpdateStock, getExportFields } from '@/api/inventory/stock'
import { resetForm } from '@/utils/ruoyi'
import { parseTime } from '@/utils/ruoyi'

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
        productType: null,
        productDetailCode: null,
        inqOfferType: null,
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
        // stockDateCol: 'A',  // 移除，使用当前时间
        productCodeCol: 'A',  // 产品编码列为第一个字段
        supplierCol: 'B',    // 供应商列为第二个字段，必填
        productDetailCol: '',
        priceCol: '',
        quantityCol: '',
        deliveryTimeCol: '',
        remarkCol: '',
        brandCol: '',
        productTypeCol: '',
        productDetailCodeCol: '',
        inqOfferTypeCol: ''
        // sheetNameCol: ''   // 移除，使用文件名拼接
      },
      columnOptions: ['A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'],
      // 批量操作相关
      ids: [],
      multiple: true,
      batchEditVisible: false,
      batchForm: {
        productDetail: null,
        price: null,
        quantity: null,
        deliveryTime: null,
        supplier: null,
        brand: null,
        productType: null,
        productDetailCode: null,
        inqOfferType: null,
        remark: null
      },
      // 导出字段相关
      exportDialogVisible: false,
      exportFields: [],
      selectedExportFields: [],
      exportType: 'all' // 'all' 或 'selected'
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
        productCode: null
      }
      this.$refs.queryForm.resetFields()
      this.getList()
    },
    // 新增
    handleAdd() {
      // 在当前页面打开新增对话框，不需要跳转
      this.reset()
      this.open = true
      this.title = '添加库存信息'
    },
    // 编辑
    handleEdit(row) {
      // 在当前页面打开编辑对话框，不需要跳转
      this.reset()
      const id = row.id
      getStock(id).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改库存信息'
      })
    },
    // 删除
    handleDelete(row) {
      const ids = row.id
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
      if (!this.importForm.productCodeCol || !this.importForm.supplierCol) {
        this.$message.error('请选择必填列（产品编码和供应商）')
        return
      }
      
      const formData = new FormData()
      formData.append('file', this.importForm.file)
      // 移除stockDateCol，使用当前时间
      formData.append('productCodeCol', this.importForm.productCodeCol)
      formData.append('supplierCol', this.importForm.supplierCol)
      formData.append('productDetailCol', this.importForm.productDetailCol)
      formData.append('priceCol', this.importForm.priceCol)
      formData.append('quantityCol', this.importForm.quantityCol)
      formData.append('deliveryTimeCol', this.importForm.deliveryTimeCol)
      formData.append('remarkCol', this.importForm.remarkCol)
      formData.append('brandCol', this.importForm.brandCol)
      // 使用文件名作为sheetName
      const fileName = this.importForm.file.name
      const sheetName = fileName.replace(/\.[^/.]+$/, "") // 移除文件扩展名
      formData.append('sheetName', sheetName)
      
      // 使用正确的API方法
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
      this.exportType = 'all'
      this.openExportDialog()
    },

    // 打开导出字段选择对话框
    async openExportDialog() {
      try {
        // 获取可导出字段列表
        const response = await getExportFields()
        this.exportFields = response.data
        
        // 默认选中所有字段
        this.selectedExportFields = this.exportFields
          .filter(field => field.defaultSelected)
          .map(field => field.fieldName)
        
        this.exportDialogVisible = true
      } catch (error) {
        this.$message.error('获取导出字段失败')
      }
    },

    // 全选字段
    selectAllFields() {
      this.selectedExportFields = this.exportFields.map(field => field.fieldName)
    },

    // 清空字段
    clearAllFields() {
      this.selectedExportFields = []
    },

    // 重置字段选择
    resetFields() {
      this.selectedExportFields = this.exportFields
        .filter(field => field.defaultSelected)
        .map(field => field.fieldName)
    },

    // 确认导出
    confirmExport() {
      if (this.selectedExportFields.length === 0) {
        this.$message.warning('请至少选择一个导出字段')
        return
      }

      const queryParams = {
        ...this.queryParams,
        exportFields: this.selectedExportFields.join(',')
      }

      // 如果是导出选中数据，添加ids参数
      if (this.exportType === 'selected' && this.ids.length > 0) {
        queryParams.ids = this.ids
      }

      exportStock(queryParams).then(response => {
        const fileName = this.exportType === 'selected' ? '选中库存数据.xlsx' : '库存数据.xlsx'
        const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.style.display = 'none'
        link.href = url
        link.setAttribute('download', fileName)
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        this.$message.success('导出成功')
        this.exportDialogVisible = false
      }).catch(error => {
        this.$message.error('导出失败')
      })
    },
    
    // 格式化日期时间
    formatDateTime(date) {
      if (!date) return ''
      return parseTime(date, '{y}-{m}-{d} {h}:{i}:{s}')
    },
    // 选择行变化
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.multiple = !selection.length
    },
    // 批量删除
    handleBatchDelete() {
      const ids = this.ids
      this.$confirm('是否确认删除选中的' + ids.length + '条数据?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delStock(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
        this.ids = []
        this.multiple = true
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    // 批量编辑
    handleBatchEdit() {
      this.batchEditVisible = true
      this.batchForm = {
        productDetail: null,
        price: null,
        quantity: null,
        deliveryTime: null,
        supplier: null,
        brand: null,
        productType: null,
        productDetailCode: null,
        inqOfferType: null,
        remark: null
      }
    },
    // 提交批量编辑
    submitBatchEdit() {
      // 检查至少有一个字段有值
      const hasValue = Object.values(this.batchForm).some(value => 
        value !== null && value !== undefined && value !== ''
      )
      
      if (!hasValue) {
        this.$message.error('请至少填写一个要更新的字段')
        return
      }
      
      // 准备更新数据，过滤掉空值
      const updateFields = {}
      for (const [key, value] of Object.entries(this.batchForm)) {
        if (value !== null && value !== undefined && value !== '') {
          updateFields[key] = value
        }
      }
      
      const updateData = {
        ids: this.ids,
        updateFields: updateFields
      }
      
      // 调用后端批量更新接口
      batchUpdateStock(updateData).then(response => {
        this.$message.success('批量更新成功')
        this.batchEditVisible = false
        this.getList()
        this.ids = []
        this.multiple = true
      }).catch(error => {
        this.$message.error('批量更新失败：' + (error.message || '请检查网络连接'))
      })
    },
    // 导出选中
    handleBatchExport() {
      if (this.ids.length === 0) {
        this.$message.warning('请先选择要导出的数据')
        return
      }
      
      this.exportType = 'selected'
      this.openExportDialog()
    }
  }
}
</script>

<style scoped>
.upload-demo {
  margin-bottom: 20px;
}

.export-fields-dialog {
  max-height: 400px;
  overflow-y: auto;
}

.field-selection {
  margin-bottom: 20px;
}

.field-actions {
  margin-bottom: 15px;
  text-align: right;
}

.field-actions .el-button {
  margin-left: 10px;
}

.field-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 10px;
}

.field-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  transition: all 0.3s;
}

.field-item:hover {
  border-color: #409eff;
  background-color: #f5f7fa;
}

.field-name {
  font-weight: 500;
  margin-right: 8px;
}

.field-type {
  color: #909399;
  font-size: 12px;
}

.selected-count {
  text-align: center;
  font-size: 14px;
  color: #67c23a;
  font-weight: 500;
  margin-top: 10px;
}
</style>