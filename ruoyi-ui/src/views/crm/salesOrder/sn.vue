<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="PN号" prop="pn">
        <el-input v-model="queryParams.pn" placeholder="请输入PN号" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="订单编号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入订单编号" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="SN编码" prop="snNo">
        <el-input v-model="queryParams.snNo" placeholder="请输入SN编码" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="openBatch" v-hasPermi="['crm:salesOrderSn:add']">批量录入</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="snList" border size="mini" row-key="id">
      <el-table-column label="ID" prop="id" width="82" align="center" />
      <el-table-column label="订单编号" prop="orderNo" width="170" />
      <el-table-column label="产品P/N号" prop="pn" width="180" show-overflow-tooltip />
      <el-table-column label="PO编号（M&E）" prop="poMeNo" width="170" show-overflow-tooltip />
      <el-table-column label="PI编号（M&E）" prop="piMeNo" width="170" show-overflow-tooltip />
      <el-table-column label="SN编号" prop="snNo" min-width="220">
        <template slot-scope="scope">
          <el-input v-model="scope.row.snNo" size="mini" @change="saveRow(scope.row)" @keyup.enter.native="$event.target.blur()" />
        </template>
      </el-table-column>
      <el-table-column label="箱号" prop="cartonNo" width="160">
        <template slot-scope="scope">
          <el-input v-model="scope.row.cartonNo" size="mini" placeholder="非必填" clearable @change="saveRow(scope.row)" @keyup.enter.native="$event.target.blur()" />
        </template>
      </el-table-column>
      <el-table-column label="录入时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" width="105" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" :loading="scope.row._saving" @click="saveRow(scope.row)">保存</el-button>
          <el-button type="text" size="mini" class="danger-text" @click="handleDelete(scope.row)" v-hasPermi="['crm:salesOrderSn:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="SN批量录入" :visible.sync="batchOpen" width="780px" append-to-body @closed="resetBatch">
      <el-form label-width="110px" size="small">
        <el-form-item label="选择订单" required>
          <el-select v-model="batchForm.orderId" filterable remote clearable reserve-keyword style="width: 100%"
            :remote-method="searchOrders" :loading="orderLoading" placeholder="输入订单编号 / PN / PO / PI 搜索"
            @focus="searchOrders('')" @change="onOrderChange">
            <el-option v-for="item in orderOptions" :key="item.id" :label="orderOptionLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedOrder" label="自动回填">
          <el-descriptions :column="2" border size="small" class="auto-fill-box">
            <el-descriptions-item label="订单编号">{{ selectedOrder.orderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产品P/N号">{{ selectedOrder.pn || '-' }}</el-descriptions-item>
            <el-descriptions-item label="PO编号（M&E）">{{ selectedOrder.poMeNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="PI编号（M&E）">{{ selectedOrder.piMeNo || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-form-item>
        <el-form-item label="箱号">
          <el-input v-model="batchForm.cartonNo" placeholder="非必填，本批次统一箱号" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="SN编号" required>
          <el-input v-model="batchForm.snText" type="textarea" :rows="13"
            placeholder="一行一个SN编号，例如：&#10;SN202611110001&#10;SN202611110002" />
          <div class="batch-tip">系统会自动去掉每行首尾空格、过滤空行，并对重复SN进行去重。</div>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="batchSaving" @click="submitBatch">确认录入</el-button>
        <el-button @click="batchOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSalesOrderSn, batchAddSalesOrderSn, updateSalesOrderSn, delSalesOrderSn, listSalesOrderOptions } from '@/api/crm/salesOrder'

export default {
  name: 'CrmSalesOrderSn',
  data() {
    return {
      loading: false,
      showSearch: true,
      snList: [],
      total: 0,
      queryParams: { pageNum: 1, pageSize: 20, pn: '', orderNo: '', snNo: '' },
      batchOpen: false,
      batchSaving: false,
      orderLoading: false,
      orderOptions: [],
      selectedOrder: null,
      batchForm: { orderId: undefined, cartonNo: '', snText: '' }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listSalesOrderSn(this.queryParams).then(response => {
        this.snList = (response.rows || []).map(row => {
          row._saving = false
          row._lastSaved = this.serializeRow(row)
          return row
        })
        this.total = response.total || 0
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || '查询SN失败')
      }).finally(() => {
        this.loading = false
      })
    },
    serializeRow(row) {
      return JSON.stringify({ id: row.id, snNo: row.snNo, cartonNo: row.cartonNo })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams.pn = ''
      this.queryParams.orderNo = ''
      this.queryParams.snNo = ''
      this.handleQuery()
    },
    saveRow(row) {
      if (!row || !row.id) {
        return
      }
      if (!row.snNo || !String(row.snNo).trim()) {
        this.$modal.msgError('SN编号不能为空')
        return
      }
      const serialized = this.serializeRow(row)
      if (serialized === row._lastSaved) {
        return
      }
      if (row._saving) {
        row._saveAgain = true
        return
      }
      row._saving = true
      updateSalesOrderSn({ id: row.id, snNo: String(row.snNo).trim(), cartonNo: row.cartonNo || null }).then(() => {
        row.snNo = String(row.snNo).trim()
        row.cartonNo = row.cartonNo || null
        row._lastSaved = this.serializeRow(row)
      }).catch(error => {
        const saved = JSON.parse(row._lastSaved)
        Object.assign(row, saved)
        this.$modal.msgError((error && error.msg) || 'SN保存失败')
      }).finally(() => {
        row._saving = false
        if (row._saveAgain) {
          row._saveAgain = false
          this.saveRow(row)
        }
      })
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除SN“' + row.snNo + '”？').then(() => {
        return delSalesOrderSn(row.id)
      }).then(() => {
        this.snList = this.snList.filter(item => item.id !== row.id)
        this.total = Math.max(0, this.total - 1)
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    openBatch() {
      this.batchOpen = true
      this.searchOrders('')
    },
    searchOrders(keyword) {
      this.orderLoading = true
      listSalesOrderOptions(keyword).then(response => {
        this.orderOptions = response.data || []
        if (this.batchForm.orderId) {
          this.selectedOrder = this.orderOptions.find(item => item.id === this.batchForm.orderId) || this.selectedOrder
        }
      }).catch(() => {
        this.orderOptions = []
      }).finally(() => {
        this.orderLoading = false
      })
    },
    orderOptionLabel(item) {
      const parts = [item.orderNo || '-', item.pn || '-', item.poMeNo || '-', item.piMeNo || '-']
      return parts.join(' ｜ ')
    },
    onOrderChange(orderId) {
      this.selectedOrder = this.orderOptions.find(item => item.id === orderId) || null
    },
    submitBatch() {
      if (!this.batchForm.orderId) {
        this.$modal.msgError('请选择订单')
        return
      }
      const seen = new Set()
      const sns = []
      String(this.batchForm.snText || '').split(/\r?\n/).forEach(item => {
        const sn = item.trim()
        if (sn && !seen.has(sn)) {
          seen.add(sn)
          sns.push(sn)
        }
      })
      if (sns.length === 0) {
        this.$modal.msgError('请粘贴至少一个SN编号')
        return
      }
      this.batchSaving = true
      batchAddSalesOrderSn({
        orderId: this.batchForm.orderId,
        cartonNo: this.batchForm.cartonNo,
        sns: sns
      }).then(response => {
        const result = response.data || {}
        const skipped = Number(result.skipped) || 0
        this.$modal.msgSuccess('录入完成：提交 ' + result.total + ' 条，成功 ' + result.inserted + ' 条' + (skipped ? '，重复跳过 ' + skipped + ' 条' : ''))
        this.batchOpen = false
        this.queryParams.pageNum = 1
        this.getList()
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || 'SN批量录入失败')
      }).finally(() => {
        this.batchSaving = false
      })
    },
    resetBatch() {
      this.batchForm = { orderId: undefined, cartonNo: '', snText: '' }
      this.selectedOrder = null
    }
  }
}
</script>

<style scoped>
.danger-text { color: #f56c6c; }
.auto-fill-box { width: 100%; }
.auto-fill-box >>> .el-descriptions-item__content { color: #409eff; font-weight: 600; }
.batch-tip { color: #909399; font-size: 12px; line-height: 20px; }
</style>
