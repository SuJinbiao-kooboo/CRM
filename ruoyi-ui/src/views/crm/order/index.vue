<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="订单编号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入订单编号" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="单据类型" prop="orderType">
        <el-select v-model="queryParams.orderType" placeholder="全部" clearable style="width:120px">
          <el-option v-for="t in orderTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商" prop="sellerName">
        <el-input v-model="queryParams.sellerName" placeholder="供应商名称" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:120px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['crm:order:add']">新增订单</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderList">
      <el-table-column label="订单编号" prop="orderNo" width="180" />
      <el-table-column label="类型" prop="orderType" width="80" align="center" />
      <el-table-column label="订单日期" prop="orderDate" width="110" align="center" />
      <el-table-column label="供应商" prop="sellerName" show-overflow-tooltip />
      <el-table-column label="币种" prop="currency" width="80" align="center" />
      <el-table-column label="合计金额" prop="totalAmount" width="120" align="right" />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === '1' ? 'success' : (scope.row.status === '2' ? 'danger' : 'info')">
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" align="center" width="280" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['crm:order:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-download" @click="handleGenerate(scope.row)" v-hasPermi="['crm:order:generate']">Excel</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['crm:order:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="1080px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="订单编号" prop="orderNo">
              <el-input v-model="form.orderNo" placeholder="留空则保存时自动生成">
                <el-button slot="append" icon="el-icon-magic-stick" @click="handleGenNo">生成</el-button>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单据类型" prop="orderType">
              <el-select v-model="form.orderType" style="width:100%">
                <el-option v-for="t in orderTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="订单日期" prop="orderDate">
              <el-date-picker v-model="form.orderDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供应商" prop="sellerId">
              <el-select v-model="form.sellerId" filterable clearable placeholder="选择供应商" style="width:100%" @change="handleSellerChange">
                <el-option v-for="s in supplierOptions" :key="s.id" :label="s.supplierName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-input v-model="form.currency" placeholder="USD" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width:100%">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="卖方信息">
          <el-input v-model="form.sellerSnapshot" type="textarea" :rows="6" placeholder="选择供应商后自动带出，可手动修改；生成单据时写入 Seller 区域" />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">物料明细</el-divider>
      <el-row class="mb8">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="addItem">添加物料</el-button>
        <span class="total-tip">合计金额：{{ computedTotal }}</span>
      </el-row>
      <el-table :data="form.items" size="mini" border>
        <el-table-column label="NO." type="index" width="50" align="center" />
        <el-table-column label="Brand" width="110">
          <template slot-scope="scope"><el-input v-model="scope.row.brand" size="mini" /></template>
        </el-table-column>
        <el-table-column label="ITEM" width="150">
          <template slot-scope="scope"><el-input v-model="scope.row.itemName" size="mini" /></template>
        </el-table-column>
        <el-table-column label="Spec.">
          <template slot-scope="scope"><el-input v-model="scope.row.spec" size="mini" /></template>
        </el-table-column>
        <el-table-column label="QTY" width="90">
          <template slot-scope="scope"><el-input v-model="scope.row.qty" size="mini" type="number" /></template>
        </el-table-column>
        <el-table-column label="Warranty" width="100">
          <template slot-scope="scope"><el-input v-model="scope.row.warranty" size="mini" /></template>
        </el-table-column>
        <el-table-column label="U/P" width="100">
          <template slot-scope="scope"><el-input v-model="scope.row.unitPrice" size="mini" type="number" /></template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template slot-scope="scope">{{ itemAmount(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="Lead time" width="120">
          <template slot-scope="scope"><el-input v-model="scope.row.leadTime" size="mini" /></template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-delete" @click="removeItem(scope.$index)" /></template>
        </el-table-column>
      </el-table>

      <div slot="footer" class="dialog-footer">
        <el-button type="warning" icon="el-icon-download" :loading="genExcelLoading" @click="genDoc()" v-hasPermi="['crm:order:generate']">生成 Excel</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import { listOrder, getOrder, addOrder, updateOrder, delOrder, nextOrderNo, resolveSeller, generateOrderExcel } from '@/api/crm/order'
import { listSupplierOptions } from '@/api/crm/supplier'

export default {
  name: 'CrmOrder',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      orderList: [],
      supplierOptions: [],
      orderTypes: [
        { label: 'PO', value: 'PO' },
        { label: 'PI', value: 'PI' },
        { label: 'PL', value: 'PL' },
        { label: '委托单', value: 'ENTRUST' },
        { label: 'SN List', value: 'SN' },
        { label: 'CI', value: 'CI' }
      ],
      statusOptions: [
        { label: '草稿', value: '0' },
        { label: '已生成', value: '1' },
        { label: '作废', value: '2' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, orderType: undefined, sellerName: undefined, status: undefined },
      title: '',
      open: false,
      genExcelLoading: false,
      form: {},
      rules: {
        orderType: [{ required: true, message: '单据类型不能为空', trigger: 'change' }],
        orderDate: [{ required: true, message: '订单日期不能为空', trigger: 'change' }],
        sellerId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
      }
    }
  },
  computed: {
    computedTotal() {
      const items = this.form.items || []
      const sum = items.reduce((acc, it) => acc + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0)
      return sum.toFixed(2)
    }
  },
  created() {
    this.getList()
    this.loadSuppliers()
  },
  methods: {
    getList() {
      this.loading = true
      listOrder(this.queryParams).then(response => {
        this.orderList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    loadSuppliers() {
      listSupplierOptions({}).then(res => {
        this.supplierOptions = res.data || []
      })
    },
    statusLabel(status) {
      const hit = this.statusOptions.find(s => s.value === status)
      return hit ? hit.label : status
    },
    itemAmount(row) {
      const amount = (Number(row.qty) || 0) * (Number(row.unitPrice) || 0)
      return amount.toFixed(2)
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: undefined,
        orderNo: undefined,
        orderType: 'PO',
        orderDate: undefined,
        sellerId: undefined,
        sellerSnapshot: undefined,
        currency: 'USD',
        status: '0',
        items: []
      }
      this.resetForm('form')
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.addItem()
      this.open = true
      this.title = '新增订单'
    },
    handleUpdate(row) {
      this.reset()
      getOrder(row.id).then(response => {
        const data = response.data
        data.items = data.items || []
        if (data.items.length === 0) {
          data.items.push(this.blankItem())
        }
        this.form = data
        this.open = true
        this.title = '修改订单'
      })
    },
    blankItem() {
      return { brand: undefined, itemName: undefined, spec: undefined, qty: undefined, warranty: undefined, unitPrice: undefined, leadTime: undefined }
    },
    addItem() {
      this.form.items = this.form.items || []
      this.form.items.push(this.blankItem())
    },
    removeItem(index) {
      this.form.items.splice(index, 1)
    },
    handleGenNo() {
      nextOrderNo(this.form.orderType || 'PO').then(res => {
        this.form.orderNo = res.data
      })
    },
    handleSellerChange(supplierId) {
      if (!supplierId) {
        return
      }
      resolveSeller(supplierId).then(res => {
        const data = res.data || {}
        this.form.sellerSnapshot = data.text
        if (data.sellerContactId) {
          this.form.sellerContactId = data.sellerContactId
        }
      })
    },
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (!valid) {
          return
        }
        const action = this.form.id != null ? updateOrder : addOrder
        action(this.buildPayload()).then(() => {
          this.$modal.msgSuccess(this.form.id != null ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    buildPayload() {
      const items = (this.form.items || []).filter(it => it.itemName || it.brand || it.spec)
      return Object.assign({}, this.form, { items: items })
    },
    genDoc() {
      this.$refs['form'].validate(valid => {
        if (!valid) {
          return
        }
        const payload = this.buildPayload()
        this.genExcelLoading = true
        generateOrderExcel(payload).then(blob => {
          saveAs(new Blob([blob]), (payload.orderNo || 'PO') + '.xlsx')
          this.genExcelLoading = false
        }).catch(() => {
          this.genExcelLoading = false
        })
      })
    },
    handleGenerate(row) {
      getOrder(row.id).then(response => {
        const payload = response.data
        generateOrderExcel(payload).then(blob => {
          saveAs(new Blob([blob]), (payload.orderNo || 'PO') + '.xlsx')
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除订单编号为"' + row.orderNo + '"的数据项？').then(() => {
        return delOrder(row.id)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.total-tip {
  margin-left: 12px;
  color: #f56c6c;
  font-weight: bold;
}
</style>
