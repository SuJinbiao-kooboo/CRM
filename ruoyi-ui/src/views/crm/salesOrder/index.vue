<template>
  <div class="app-container sales-order-page">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="PN号" prop="pn">
        <el-input v-model="queryParams.pn" placeholder="请输入PN号" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="订单日期">
        <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 170px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['crm:salesOrder:add']">新增订单</el-button>
      </el-col>
      <el-col :span="18">
        <span class="page-tip">新增后自动生成编号；点击编号查看详情；除编号和附件外所有字段可直接编辑，修改后自动保存。</span>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="orderList" border size="mini" row-key="id" class="inline-table">
      <el-table-column label="ID" prop="id" width="72" align="center" fixed="left" />
      <el-table-column label="编号" prop="orderNo" width="175" fixed="left" show-overflow-tooltip>
        <template slot-scope="scope">
          <el-link type="primary" :underline="false" @click="goDetail(scope.row)">{{ scope.row.orderNo || '-' }}</el-link>
          <i v-if="scope.row._saving" class="el-icon-loading save-icon" title="自动保存中"></i>
        </template>
      </el-table-column>
      <el-table-column v-for="col in editableColumns" :key="col.prop" :label="col.label" :width="col.width" :align="col.align || 'left'">
        <template slot-scope="scope">
          <el-input v-if="col.type === 'text'" v-model="scope.row[col.prop]" size="mini" clearable class="inline-editor"
            @change="scheduleSave(scope.row)" @keyup.enter.native="$event.target.blur()" />
          <el-date-picker v-else-if="col.type === 'date'" v-model="scope.row[col.prop]" type="date" size="mini"
            value-format="yyyy-MM-dd" placeholder="选择日期" class="inline-editor" @change="onDateChange(scope.row, col.prop)" />
          <el-date-picker v-else-if="col.type === 'month'" v-model="scope.row[col.prop]" type="month" size="mini"
            value-format="yyyy-MM" placeholder="入账月份" class="inline-editor" @change="scheduleSave(scope.row)" />
          <el-select v-else-if="col.type === 'select'" v-model="scope.row[col.prop]" size="mini" clearable filterable class="inline-editor"
            @change="scheduleSave(scope.row)">
            <el-option v-for="item in col.options" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-else-if="col.type === 'multi'" v-model="scope.row[col.prop]" size="mini" multiple collapse-tags filterable
            class="inline-editor" @change="scheduleSave(scope.row)">
            <el-option v-for="item in dict.type.crm_product_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input-number v-else-if="col.type === 'number'" v-model="scope.row[col.prop]" size="mini" :controls="false"
            :precision="col.precision" class="inline-editor number-editor" @change="onNumberChange(scope.row, col.prop)" />
          <el-select v-else-if="col.type === 'partner'" v-model="scope.row[col.prop]" size="mini" clearable filterable
            class="inline-editor" @change="onPartnerChange(scope.row, col.partner)">
            <el-option v-for="item in partnerOptions" :key="item.id" :label="partnerLabel(item)" :value="item.id" />
          </el-select>
          <el-select v-else-if="col.type === 'contact'" v-model="scope.row[col.prop]" size="mini" clearable filterable
            class="inline-editor" @change="scheduleSave(scope.row)" @visible-change="onContactVisible(scope.row, col.partner, $event)">
            <el-option v-for="item in contactOptions(scope.row, col.partner)" :key="item.id" :label="item.contactName || ('联系人#' + item.id)" :value="item.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="文件" width="108" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-paperclip" @click="openFiles(scope.row)">
            文件{{ fileCount(scope.row) ? ' (' + fileCount(scope.row) + ')' : '' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" class="danger-text" @click="handleDelete(scope.row)" v-hasPermi="['crm:salesOrder:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="订单文件" :visible.sync="fileOpen" width="1100px" append-to-body @closed="fileRow = null">
      <div v-if="fileRow" class="file-order-no">订单：{{ fileRow.orderNo }}　PN：{{ fileRow.pn || '-' }}</div>
      <order-file-panel v-if="fileRow" :order-id="fileRow.id" :legacy-files="legacyFilesOf(fileRow)" @changed="onFilesChanged" />
    </el-dialog>
  </div>
</template>

<script>
import { listSalesOrder, addSalesOrder, updateSalesOrder, delSalesOrder, listSalesOrderContacts } from '@/api/crm/salesOrder'
import { listSupplierOptions } from '@/api/crm/supplier'
import OrderFilePanel from './components/OrderFilePanel'
import { ORDER_FILE_SLOTS } from './fileFields'

const STATUS_OPTIONS = [
  { value: 'UNPAID', label: '未付款' },
  { value: 'PAID_WAITING_RECEIPT', label: '已付款待收货' },
  { value: 'RECEIVED_WAITING_DELIVERY', label: '已收货待交付' },
  { value: 'DELIVERED', label: '已交付' },
  { value: 'CANCELLED', label: '已取消' }
]

const COOPERATION_OPTIONS = [
  { value: 'NEW_NEW', label: '新客新供' },
  { value: 'NEW_OLD', label: '新客老供' },
  { value: 'OLD_NEW', label: '老客新供' },
  { value: 'OLD_OLD', label: '老客老供' }
]

function splitCsv(value) {
  if (!value) {
    return []
  }
  return String(value).split(',').map(item => item.trim()).filter(item => item.length > 0)
}

function toNumber(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function round(value, precision) {
  if (value === null || value === undefined) {
    return null
  }
  const factor = Math.pow(10, precision)
  return Math.round((Number(value) + Number.EPSILON) * factor) / factor
}

export default {
  name: 'CrmSalesOrder',
  components: { OrderFilePanel },
  dicts: ['crm_product_type'],
  data() {
    return {
      loading: false,
      showSearch: true,
      orderList: [],
      total: 0,
      dateRange: [],
      queryParams: { pageNum: 1, pageSize: 10, pn: '', status: '', params: {} },
      partnerOptions: [],
      statusOptions: STATUS_OPTIONS,
      cooperationOptions: COOPERATION_OPTIONS,
      editableColumns: [
        { prop: 'orderDate', label: '订单日期', width: 148, type: 'date' },
        { prop: 'bookingDate', label: '入账日期', width: 148, type: 'date' },
        { prop: 'bookingMonth', label: '入账月份', width: 138, type: 'month' },
        { prop: 'cooperationStatus', label: '合作状态', width: 140, type: 'select', options: COOPERATION_OPTIONS },
        { prop: 'pn', label: 'PN', width: 170, type: 'text' },
        { prop: 'desp', label: 'Desp.', width: 220, type: 'text' },
        { prop: 'productTypeArr', label: '产品类型', width: 230, type: 'multi' },
        { prop: 'status', label: '状态', width: 175, type: 'select', options: STATUS_OPTIONS },
        { prop: 'quantity', label: '数量', width: 108, type: 'number', precision: 2, align: 'right' },
        { prop: 'customerId', label: '客户（供应商列表）', width: 250, type: 'partner', partner: 'customer' },
        { prop: 'customerPoNo', label: 'PO号-客', width: 150, type: 'text' },
        { prop: 'piMeNo', label: 'PI号-M&E', width: 150, type: 'text' },
        { prop: 'customerPiNo', label: 'PI号-客户', width: 150, type: 'text' },
        { prop: 'salePrice', label: '售价', width: 118, type: 'number', precision: 4, align: 'right' },
        { prop: 'salesAmount', label: '销售额', width: 125, type: 'number', precision: 2, align: 'right' },
        { prop: 'paymentReceivedDate', label: '付款到账日期', width: 148, type: 'date' },
        { prop: 'supplierId', label: '供应商（供应商列表）', width: 250, type: 'partner', partner: 'supplier' },
        { prop: 'poMeNo', label: 'PO号-M&E', width: 150, type: 'text' },
        { prop: 'supplierPiNo', label: 'PI号-供', width: 150, type: 'text' },
        { prop: 'costPrice', label: '成本单价', width: 125, type: 'number', precision: 4, align: 'right' },
        { prop: 'purchaseAmount', label: '总采购额', width: 130, type: 'number', precision: 2, align: 'right' },
        { prop: 'priceDiff', label: '价差', width: 118, type: 'number', precision: 4, align: 'right' },
        { prop: 'gp', label: 'GP', width: 125, type: 'number', precision: 2, align: 'right' },
        { prop: 'profitRate', label: '利润率(%)', width: 125, type: 'number', precision: 2, align: 'right' },
        { prop: 'paymentDate', label: '打款日期', width: 148, type: 'date' },
        { prop: 'arrivalDate', label: '到货日期', width: 148, type: 'date' },
        { prop: 'logisticsFee', label: '物流费', width: 120, type: 'number', precision: 2, align: 'right' },
        { prop: 'cartonSpec', label: '箱规', width: 150, type: 'text' },
        { prop: 'grossWeight', label: '毛重KG', width: 110, type: 'number', precision: 3, align: 'right' },
        { prop: 'remark', label: '备注', width: 220, type: 'text' },
        { prop: 'logisticsReconciliation', label: '物流对账', width: 170, type: 'text' },
        { prop: 'customerContactId', label: '客户联系人', width: 180, type: 'contact', partner: 'customer' },
        { prop: 'supplierContactId', label: '供应商联系人', width: 180, type: 'contact', partner: 'supplier' }
      ],
      fileOpen: false,
      fileRow: null,
      _saveTimers: {}
    }
  },
  created() {
    this.getList()
    this.loadPartners()
  },
  beforeDestroy() {
    Object.keys(this._saveTimers).forEach(key => clearTimeout(this._saveTimers[key]))
  },
  methods: {
    getQueryParams() {
      const query = JSON.parse(JSON.stringify(this.queryParams))
      query.params = query.params || {}
      query.params.beginTime = this.dateRange && this.dateRange[0] ? this.dateRange[0] : undefined
      query.params.endTime = this.dateRange && this.dateRange[1] ? this.dateRange[1] : undefined
      return query
    },
    getList() {
      this.loading = true
      listSalesOrder(this.getQueryParams()).then(response => {
        this.orderList = (response.rows || []).map(row => this.normalizeRow(row))
        this.total = response.total || 0
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || '查询订单失败')
      }).finally(() => {
        this.loading = false
      })
    },
    loadPartners() {
      listSupplierOptions({}).then(response => {
        this.partnerOptions = response.data || []
      }).catch(() => {
        this.partnerOptions = []
      })
    },
    partnerLabel(item) {
      return (item.supplierCode ? item.supplierCode + ' ' : '') + (item.supplierName || '')
    },
    normalizeRow(row) {
      row.productTypeArr = splitCsv(row.productTypes)
      row._customerContacts = []
      row._supplierContacts = []
      row._saving = false
      row._lastSaved = JSON.stringify(this.buildPayload(row))
      return row
    },
    hydrateRow(row) {
      row.productTypeArr = splitCsv(row.productTypes)
      if (!row._customerContacts) {
        row._customerContacts = []
      }
      if (!row._supplierContacts) {
        row._supplierContacts = []
      }
    },
    buildPayload(row) {
      const payload = {}
      Object.keys(row).forEach(key => {
        if (key.charAt(0) === '_' || key === 'productTypeArr') {
          return
        }
        payload[key] = row[key]
      })
      payload.productTypes = (row.productTypeArr || []).join(',')
      return payload
    },
    scheduleSave(row) {
      if (!row || !row.id) {
        return
      }
      clearTimeout(this._saveTimers[row.id])
      this._saveTimers[row.id] = setTimeout(() => {
        delete this._saveTimers[row.id]
        this.saveRow(row)
      }, 400)
    },
    saveRow(row, force) {
      if (!row || !row.id) {
        return
      }
      const payload = this.buildPayload(row)
      const serialized = JSON.stringify(payload)
      if (!force && serialized === row._lastSaved) {
        return
      }
      if (row._saving) {
        row._saveAgain = true
        return
      }
      row._saving = true
      const customerContacts = row._customerContacts
      const supplierContacts = row._supplierContacts
      updateSalesOrder(payload).then(response => {
        const data = response.data || payload
        Object.assign(row, data)
        row._customerContacts = customerContacts
        row._supplierContacts = supplierContacts
        this.hydrateRow(row)
        row._lastSaved = JSON.stringify(this.buildPayload(row))
      }).catch(error => {
        if (row._lastSaved) {
          const saved = JSON.parse(row._lastSaved)
          Object.assign(row, saved)
          row._customerContacts = customerContacts
          row._supplierContacts = supplierContacts
          this.hydrateRow(row)
        }
        this.$modal.msgError((error && error.msg) || '保存失败')
      }).finally(() => {
        row._saving = false
        if (row._saveAgain) {
          row._saveAgain = false
          this.scheduleSave(row)
        }
      })
    },
    onDateChange(row, prop) {
      if (prop === 'bookingDate' && row.bookingDate) {
        row.bookingMonth = String(row.bookingDate).substring(0, 7)
      }
      this.scheduleSave(row)
    },
    onNumberChange(row, prop) {
      if (prop === 'quantity' || prop === 'salePrice' || prop === 'costPrice') {
        const quantity = toNumber(row.quantity)
        const salePrice = toNumber(row.salePrice)
        const costPrice = toNumber(row.costPrice)
        if (quantity !== null && salePrice !== null) {
          row.salesAmount = round(quantity * salePrice, 2)
        }
        if (quantity !== null && costPrice !== null) {
          row.purchaseAmount = round(quantity * costPrice, 2)
        }
        if (salePrice !== null && costPrice !== null) {
          row.priceDiff = round(salePrice - costPrice, 4)
        }
        const salesAmount = toNumber(row.salesAmount)
        const purchaseAmount = toNumber(row.purchaseAmount)
        if (salesAmount !== null && purchaseAmount !== null) {
          row.gp = round(salesAmount - purchaseAmount, 2)
          row.profitRate = salesAmount === 0 ? null : round(((salesAmount - purchaseAmount) / salesAmount) * 100, 2)
        }
      }
      this.scheduleSave(row)
    },
    onPartnerChange(row, partner) {
      const idProp = partner + 'Id'
      const contactProp = partner + 'ContactId'
      const nameProp = partner + 'ContactName'
      row[contactProp] = null
      row[nameProp] = null
      row['_' + partner + 'Contacts'] = []
      this.scheduleSave(row)
      if (row[idProp]) {
        this.loadContacts(row, partner, true)
      }
    },
    onContactVisible(row, partner, visible) {
      if (!visible) {
        return
      }
      const list = row['_' + partner + 'Contacts'] || []
      if (list.length === 0 && row[partner + 'Id']) {
        this.loadContacts(row, partner, false)
      }
    },
    loadContacts(row, partner, autoSelect) {
      const idProp = partner + 'Id'
      const partnerId = row[idProp]
      if (!partnerId) {
        row['_' + partner + 'Contacts'] = []
        return Promise.resolve([])
      }
      return listSalesOrderContacts(partnerId).then(response => {
        if (row[idProp] !== partnerId) {
          return []
        }
        const contacts = response.data || []
        row['_' + partner + 'Contacts'] = contacts
        if (autoSelect && contacts.length > 0 && !row[partner + 'ContactId']) {
          row[partner + 'ContactId'] = contacts[0].id
          this.scheduleSave(row)
        }
        return contacts
      }).catch(() => {
        row['_' + partner + 'Contacts'] = []
        return []
      })
    },
    contactOptions(row, partner) {
      const contacts = (row['_' + partner + 'Contacts'] || []).slice()
      const contactId = row[partner + 'ContactId']
      const contactName = row[partner + 'ContactName']
      if (contactId && !contacts.some(item => item.id === contactId)) {
        contacts.unshift({ id: contactId, contactName: contactName || ('联系人#' + contactId) })
      }
      return contacts
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm('queryForm')
      this.queryParams.pn = ''
      this.queryParams.status = ''
      this.handleQuery()
    },
    handleAdd() {
      this.loading = true
      addSalesOrder({}).then(response => {
        const row = this.normalizeRow(response.data || {})
        this.orderList.unshift(row)
        this.total++
        this.$modal.msgSuccess('已新增订单，编号：' + (row.orderNo || '-'))
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || '新增订单失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除订单“' + (row.orderNo || row.id) + '”？关联的SN也会一并删除。').then(() => {
        return delSalesOrder(row.id)
      }).then(() => {
        this.orderList = this.orderList.filter(item => item.id !== row.id)
        this.total = Math.max(0, this.total - 1)
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    openFiles(row) {
      this.fileRow = row
      this.fileOpen = true
    },
    legacyFilesOf(row) {
      const legacy = {}
      ORDER_FILE_SLOTS.forEach(slot => {
        if (row[slot.prop]) {
          legacy[slot.prop] = row[slot.prop]
        }
      })
      return legacy
    },
    onFilesChanged(count) {
      if (this.fileRow) {
        this.fileRow.attachmentCount = count
      }
    },
    fileCount(row) {
      const legacyCount = ORDER_FILE_SLOTS.reduce((count, slot) => count + (row[slot.prop] ? String(row[slot.prop]).split(',').filter(Boolean).length : 0), 0)
      return (Number(row.attachmentCount) || 0) + legacyCount
    },
    goDetail(row) {
      this.$router.push('/crm/sales-order-detail/index/' + row.id)
    }
  }
}
</script>

<style scoped>
.page-tip { color: #909399; font-size: 12px; }
.inline-table >>> .el-table__cell { padding: 3px 0; }
.inline-table >>> .cell { padding: 0 4px; }
.inline-editor >>> .el-input__inner,
.inline-editor >>> .el-range-input,
.inline-editor >>> .el-input__prefix {
  border-color: transparent;
  background: transparent;
}
.inline-editor >>> .el-input__inner:hover,
.inline-editor >>> .el-input__inner:focus,
.inline-editor >>> .el-input__inner:active {
  border-color: #409eff;
  background: #fff;
}
.number-editor { width: 100%; }
.number-editor >>> .el-input__inner { text-align: right; }
.danger-text { color: #f56c6c; }
.file-order-no { margin-bottom: 12px; color: #606266; font-weight: 600; }
.save-icon { margin-left: 6px; color: #409eff; }
</style>
