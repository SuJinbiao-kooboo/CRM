<template>
  <div class="app-container order-detail-page" v-loading="loading">
    <div class="detail-header">
      <div class="title-block">
        <el-button icon="el-icon-back" size="mini" @click="goBack">返回</el-button>
        <span class="order-no">{{ order.orderNo || '订单详情' }}</span>
        <el-tag v-if="order.status" :type="statusTagType(order.status)" size="small">{{ statusLabel(order.status) }}</el-tag>
        <el-tag v-if="order.cooperationStatus" type="info" size="small">{{ cooperationLabel(order.cooperationStatus) }}</el-tag>
      </div>
      <el-button type="primary" size="mini" icon="el-icon-refresh" @click="loadOrder">刷新</el-button>
    </div>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">基本信息</div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="订单ID">{{ order.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="编号">{{ order.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(order.status) }}</el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ order.orderDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入账日期">{{ order.bookingDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入账月份">{{ order.bookingMonth || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合作状态">{{ cooperationLabel(order.cooperationStatus) }}</el-descriptions-item>
        <el-descriptions-item label="PN">{{ order.pn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ formatNumber(order.quantity) }}</el-descriptions-item>
        <el-descriptions-item label="Desp." :span="3">{{ order.desp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品类型" :span="3">{{ productTypeLabel(order.productTypes) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">客户与销售</div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="客户">{{ order.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户联系人">{{ order.customerContactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PO号-客">{{ order.customerPoNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PI号-M&E">{{ order.piMeNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PI号-客户">{{ order.customerPiNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="售价">{{ formatNumber(order.salePrice) }}</el-descriptions-item>
        <el-descriptions-item label="销售额">{{ formatNumber(order.salesAmount) }}</el-descriptions-item>
        <el-descriptions-item label="付款到账日期">{{ order.paymentReceivedDate || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">供应商与采购</div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="供应商">{{ order.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商联系人">{{ order.supplierContactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PO号-M&E">{{ order.poMeNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PI号-供">{{ order.supplierPiNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成本单价">{{ formatNumber(order.costPrice) }}</el-descriptions-item>
        <el-descriptions-item label="总采购额">{{ formatNumber(order.purchaseAmount) }}</el-descriptions-item>
        <el-descriptions-item label="价差">{{ formatNumber(order.priceDiff) }}</el-descriptions-item>
        <el-descriptions-item label="GP">{{ formatNumber(order.gp) }}</el-descriptions-item>
        <el-descriptions-item label="利润率(%)">{{ formatNumber(order.profitRate) }}</el-descriptions-item>
        <el-descriptions-item label="打款日期">{{ order.paymentDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="到货日期">{{ order.arrivalDate || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">物流与备注</div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="物流费">{{ formatNumber(order.logisticsFee) }}</el-descriptions-item>
        <el-descriptions-item label="箱规">{{ order.cartonSpec || '-' }}</el-descriptions-item>
        <el-descriptions-item label="毛重KG">{{ formatNumber(order.grossWeight) }}</el-descriptions-item>
        <el-descriptions-item label="物流对账">{{ order.logisticsReconciliation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ order.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ order.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(order.createTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ parseTime(order.updateTime) || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">订单附件</div>
      <order-file-panel v-if="order.id" :order-id="order.id" :legacy-files="legacyFiles" @changed="loadOrder" />
    </el-card>

    <el-card shadow="never" class="detail-card">
      <div slot="header" class="card-title">关联SN（{{ snTotal }} 条）</div>
      <el-table v-loading="snLoading" :data="snList" border size="mini" max-height="420">
        <el-table-column label="ID" prop="id" width="82" align="center" />
        <el-table-column label="SN编号" prop="snNo" min-width="220" show-overflow-tooltip />
        <el-table-column label="箱号" prop="cartonNo" width="160" show-overflow-tooltip />
        <el-table-column label="PI编号（客户）" prop="customerPiNo" width="170" show-overflow-tooltip />
        <el-table-column label="录入时间" prop="createTime" width="170" align="center" />
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getSalesOrder, listSalesOrderSn } from '@/api/crm/salesOrder'
import OrderFilePanel from './components/OrderFilePanel'
import { ORDER_FILE_SLOTS } from './fileFields'

const STATUS_OPTIONS = [
  { value: 'UNPAID', label: '未付款', tag: 'warning' },
  { value: 'PAID_WAITING_RECEIPT', label: '已付款待收货', tag: '' },
  { value: 'RECEIVED_WAITING_DELIVERY', label: '已收货待交付', tag: 'info' },
  { value: 'DELIVERED', label: '已交付', tag: 'success' },
  { value: 'CANCELLED', label: '已取消', tag: 'info' }
]

const COOPERATION_OPTIONS = [
  { value: 'NEW_NEW', label: '新客新供' },
  { value: 'NEW_OLD', label: '新客老供' },
  { value: 'OLD_NEW', label: '老客新供' },
  { value: 'OLD_OLD', label: '老客老供' }
]

export default {
  name: 'CrmSalesOrderDetail',
  components: { OrderFilePanel },
  dicts: ['crm_product_type'],
  data() {
    return {
      loading: false,
      snLoading: false,
      order: {},
      snList: [],
      snTotal: 0
    }
  },
  computed: {
    orderId() {
      return this.$route.params.id
    },
    legacyFiles() {
      const legacy = {}
      ORDER_FILE_SLOTS.forEach(slot => {
        if (this.order[slot.prop]) {
          legacy[slot.prop] = this.order[slot.prop]
        }
      })
      return legacy
    }
  },
  created() {
    this.loadOrder()
    this.loadSns()
  },
  methods: {
    loadOrder() {
      if (!this.orderId) {
        return
      }
      this.loading = true
      getSalesOrder(this.orderId).then(response => {
        this.order = response.data || {}
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || '订单详情加载失败')
      }).finally(() => {
        this.loading = false
      })
    },
    loadSns() {
      if (!this.orderId) {
        return
      }
      this.snLoading = true
      listSalesOrderSn({ orderId: this.orderId, pageNum: 1, pageSize: 500 }).then(response => {
        this.snList = response.rows || []
        this.snTotal = response.total || 0
      }).catch(() => {
        this.snList = []
        this.snTotal = 0
      }).finally(() => {
        this.snLoading = false
      })
    },
    goBack() {
      if (window.history.length > 1) {
        this.$router.back()
      } else {
        this.$router.push('/crm/orderDemand/salesOrder')
      }
    },
    statusLabel(value) {
      const item = STATUS_OPTIONS.find(option => option.value === value)
      return item ? item.label : (value || '-')
    },
    statusTagType(value) {
      const item = STATUS_OPTIONS.find(option => option.value === value)
      return item ? item.tag : 'info'
    },
    cooperationLabel(value) {
      const item = COOPERATION_OPTIONS.find(option => option.value === value)
      return item ? item.label : (value || '-')
    },
    productTypeLabel(value) {
      if (!value) {
        return '-'
      }
      const dict = this.dict.type.crm_product_type || []
      return String(value).split(',').map(item => {
        const hit = dict.find(option => option.value === item.trim())
        return hit ? hit.label : item.trim()
      }).filter(Boolean).join('、') || '-'
    },
    formatNumber(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      const number = Number(value)
      return Number.isFinite(number) ? number.toLocaleString('zh-CN', { maximumFractionDigits: 4 }) : value
    }
  }
}
</script>

<style scoped>
.order-detail-page { padding-bottom: 24px; }
.detail-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.title-block { display: flex; align-items: center; gap: 10px; }
.order-no { font-size: 20px; font-weight: 600; color: #303133; }
.detail-card { margin-bottom: 14px; }
.card-title { font-size: 14px; font-weight: 600; color: #303133; }
</style>
