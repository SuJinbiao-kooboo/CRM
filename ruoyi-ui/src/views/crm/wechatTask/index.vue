<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="料号" prop="pnKeyword">
        <el-input v-model="queryParams.pnKeyword" placeholder="料号" clearable style="width:150px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="聊天对象" prop="oriTarget">
        <el-input v-model="queryParams.oriTarget" placeholder="群名或好友名" clearable style="width:160px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="内容" prop="queryKeyword">
        <el-input v-model="queryParams.queryKeyword" placeholder="原文或发送内容" clearable style="width:170px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="任务类型" prop="taskType">
        <el-select v-model="queryParams.taskType" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in taskTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="handleStatus">
        <el-select v-model="queryParams.handleStatus" clearable placeholder="全部" style="width:165px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="仅异常">
        <el-switch v-model="queryParams.exceptionOnly" />
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" style="width:250px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-magic-stick" size="mini" :loading="generating" @click="handleGenerate" v-hasPermi="['wechat:task:generate']">生成任务</el-button></el-col>
      <el-col :span="1.5"><el-button type="success" plain icon="el-icon-s-promotion" size="mini" :loading="sending" @click="handleSend" v-hasPermi="['wechat:task:send']">发送扫描</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="taskList">
      <el-table-column label="创建时间" prop="createTime" width="158" />
      <el-table-column label="来源对象" prop="oriTarget" min-width="160" show-overflow-tooltip />
      <el-table-column label="来源分类" prop="sourceCategory" width="90" align="center" />
      <el-table-column label="任务类型" width="125" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="taskTypeTag(scope.row.taskType)">{{ taskTypeLabel(scope.row.taskType) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="料号" prop="pnOriginal" width="170" show-overflow-tooltip />
      <el-table-column label="数量" prop="qty" width="75" align="right" />
      <el-table-column label="成本价" prop="oriPrice" width="90" align="right" />
      <el-table-column label="对外报价" prop="offerPrice" width="100" align="right" />
      <el-table-column label="发送目标" width="105" align="center">
        <template slot-scope="scope">{{ (scope.row.targets || []).length }} / 成功 {{ (scope.row.successTargets || []).length }}</template>
      </el-table-column>
      <el-table-column label="发送次数" prop="sendAttempts" width="85" align="center" />
      <el-table-column label="状态" width="165" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="statusTag(scope.row.handleStatus)">{{ statusLabel(scope.row.handleStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="异常" prop="lastError" min-width="150" show-overflow-tooltip>
        <template slot-scope="scope"><span class="text-danger">{{ scope.row.lastError || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-view" @click="openTask(scope.row.id)" v-hasPermi="['wechat:task:query']">详情/审批</el-button>
          <el-button v-if="isUnknownResult(scope.row)" type="text" size="mini" icon="el-icon-warning-outline" @click="confirmNotSent(scope.row)" v-hasPermi="['wechat:task:send']">确认未发送</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="'任务 #' + form.id + ' 详情与审批'" :visible.sync="open" width="980px" append-to-body>
      <el-descriptions v-if="form.id" :column="2" border size="small" class="mb8">
        <el-descriptions-item label="创建时间">{{ form.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源对象">{{ form.oriTarget || '-' }}</el-descriptions-item>
        <el-descriptions-item label="消息时间">{{ form.sourceMessageTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源分类">{{ form.sourceCategory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ taskTypeLabel(form.taskType) }}</el-descriptions-item>
        <el-descriptions-item label="料号">{{ form.pnOriginal || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ form.qty == null ? '-' : form.qty }}</el-descriptions-item>
        <el-descriptions-item label="成本价">{{ form.oriPrice == null ? '-' : form.oriPrice }}</el-descriptions-item>
        <el-descriptions-item label="对外报价">{{ form.offerPrice == null ? '-' : form.offerPrice }}</el-descriptions-item>
        <el-descriptions-item label="发送状态">{{ statusLabel(form.handleStatus) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ form.approvedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ form.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发送次数">{{ form.sendAttempts == null ? 0 : form.sendAttempts }}</el-descriptions-item>
        <el-descriptions-item label="最近发送时间">{{ form.lastAttemptAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近异常" :span="2"><span class="text-danger">{{ form.lastError || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="审批原因" :span="2">{{ form.approvalReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源消息" :span="2"><div class="detail-text">{{ form.oriMsg || '-' }}</div></el-descriptions-item>
        <el-descriptions-item label="OFFER 快照" :span="2"><pre class="json-box">{{ pretty(form.offerJson) }}</pre></el-descriptions-item>
        <el-descriptions-item label="关联 INQ" :span="2"><pre class="json-box">{{ pretty(form.matchedInqJson) }}</pre></el-descriptions-item>
      </el-descriptions>

      <el-form ref="form" :model="form" label-width="105px" size="small">
        <el-form-item label="任务状态">
          <el-select v-model="form.handleStatus" style="width:240px">
            <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对外报价">
          <el-input-number v-model="form.offerPrice" :controls="false" :precision="2" :min="0" style="width:240px" />
        </el-form-item>
        <el-form-item label="发送内容">
          <el-input v-model="form.sendContent" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="审批原因">
          <el-input v-model="form.approvalReason" maxlength="500" placeholder="可选" />
        </el-form-item>
        <el-form-item label="发送目标">
          <el-button size="mini" icon="el-icon-plus" @click="addTarget">添加目标</el-button>
          <el-table :data="form.targets || []" size="mini" border class="target-table">
            <el-table-column label="群名" min-width="220"><template slot-scope="scope"><el-input v-model="scope.row.name" size="mini" /></template></el-table-column>
            <el-table-column label="伙伴编码" width="150"><template slot-scope="scope"><el-input v-model="scope.row.partyCode" size="mini" /></template></el-table-column>
            <el-table-column label="伙伴名称" min-width="160"><template slot-scope="scope"><el-input v-model="scope.row.partyName" size="mini" /></template></el-table-column>
            <el-table-column label="操作" width="70" align="center"><template slot-scope="scope"><el-button type="text" class="text-danger" @click="removeTarget(scope.$index)">移除</el-button></template></el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="成功目标">
          <el-tag v-for="item in form.successTargets || []" :key="item.name" size="small" type="success" class="mr5">{{ item.name }}</el-tag>
          <span v-if="!(form.successTargets || []).length">-</span>
        </el-form-item>
        <el-form-item label="发送预览">
          <el-table :data="form.previews || []" size="mini" border class="target-table">
            <el-table-column label="目标" min-width="150">
              <template slot-scope="scope">
                <div>{{ scope.row.targetName }}</div>
                <div class="preview-party">{{ scope.row.partyName || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="最终合并文本" min-width="520">
              <template slot-scope="scope"><pre class="preview-box">{{ scope.row.text || '暂无已审批内容' }}</pre></template>
            </el-table-column>
          </el-table>
          <span v-if="!(form.previews || []).length" class="text-muted">暂无发送目标</span>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitApproval" v-hasPermi="['wechat:task:approve']">保存审批</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listWechatTasks, getWechatTask, approveWechatTask, generateWechatTasks, sendWechatTasks, confirmWechatTaskNotSent } from '@/api/crm/wechatTask'

export default {
  name: 'CrmWechatTask',
  data() {
    return {
      loading: false,
      saving: false,
      generating: false,
      sending: false,
      showSearch: true,
      total: 0,
      taskList: [],
      dateRange: [],
      open: false,
      form: {},
      statuses: [
        { value: 'PENDING_APPROVAL', label: '待审批' },
        { value: 'APPROVED_PENDING_SEND', label: '已审批待发送' },
        { value: 'SENT', label: '已发送' },
        { value: 'NO_ACTION', label: '无需处理' }
      ],
      taskTypes: [
        { value: 'SUPPLIER_INQUIRY', label: '供应商询价' },
        { value: 'CUSTOMER_REPLY', label: '客户回复' },
        { value: 'CUSTOMER_PUSH', label: '客户推送' },
        { value: 'UNMAPPED_CHAT', label: '未映射异常' }
      ],
      queryParams: { pageNum: 1, pageSize: 20, pnKeyword: undefined, oriTarget: undefined, queryKeyword: undefined, taskType: undefined, handleStatus: undefined, exceptionOnly: false, beginTime: undefined, endTime: undefined }
    }
  },
  created() {
    this.dateRange = this.last30Days()
    this.handleQuery()
  },
  methods: {
    last30Days() {
      const fmt = date => date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
      const now = new Date()
      return [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
    },
    getList() {
      this.loading = true
      const query = Object.assign({}, this.queryParams)
      if (!query.exceptionOnly) delete query.exceptionOnly
      if (this.dateRange && this.dateRange.length === 2) {
        query.beginTime = this.dateRange[0] + ' 00:00:00'
        query.endTime = this.dateRange[1] + ' 23:59:59'
      }
      listWechatTasks(query).then(res => {
        this.taskList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.dateRange = this.last30Days()
      this.queryParams.exceptionOnly = false
      this.handleQuery()
    },
    handleGenerate() {
      this.generating = true
      generateWechatTasks({}).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '任务生成完成')
        this.getList()
      }).finally(() => { this.generating = false })
    },
    handleSend() {
      this.$confirm('确认扫描并发送所有已审批任务？', '提示', { type: 'warning' }).then(() => {
        this.sending = true
        return sendWechatTasks().then(res => {
          this.$modal.msgSuccess((res.data && res.data.message) || '发送扫描完成')
          this.getList()
        }).finally(() => { this.sending = false })
      }).catch(() => {})
    },
    openTask(id) {
      getWechatTask(id).then(res => {
        this.form = Object.assign({}, res.data || {})
        this.form.targets = (this.form.targets || []).map(item => Object.assign({}, item))
        this.open = true
      })
    },
    addTarget() {
      if (!this.form.targets) this.$set(this.form, 'targets', [])
      this.form.targets.push({ name: '', partyCode: '', partyName: '' })
    },
    removeTarget(index) {
      this.form.targets.splice(index, 1)
    },
    submitApproval() {
      const targets = (this.form.targets || []).filter(item => String(item.name || '').trim())
      if (this.form.handleStatus === 'APPROVED_PENDING_SEND' && targets.length === 0) {
        this.$modal.msgError('已审批待发送任务必须至少有一个发送目标')
        return
      }
      this.saving = true
      approveWechatTask(this.form.id, Object.assign({}, this.form, { targets: targets })).then(() => {
        this.$modal.msgSuccess('审批已保存')
        this.open = false
        this.getList()
      }).finally(() => { this.saving = false })
    },
    confirmNotSent(row) {
      this.$confirm('确认网关确实未发送，恢复该任务为待发送？', '风险确认', { type: 'warning' }).then(() => {
        return confirmWechatTaskNotSent(row.id).then(() => {
          this.$modal.msgSuccess('已恢复为待发送')
          this.getList()
        })
      }).catch(() => {})
    },
    isUnknownResult(row) {
      return String(row.lastError || '').indexOf('GATEWAY_RESULT_UNKNOWN') === 0
    },
    taskTypeLabel(value) {
      const item = this.taskTypes.find(x => x.value === value)
      return item ? item.label : (value || '-')
    },
    taskTypeTag(value) {
      if (value === 'CUSTOMER_REPLY') return 'success'
      if (value === 'CUSTOMER_PUSH') return 'warning'
      if (value === 'UNMAPPED_CHAT') return 'danger'
      return 'info'
    },
    statusLabel(value) {
      const item = this.statuses.find(x => x.value === value)
      return item ? item.label : (value || '-')
    },
    statusTag(value) {
      if (value === 'SENT') return 'success'
      if (value === 'APPROVED_PENDING_SEND') return 'warning'
      if (value === 'NO_ACTION') return 'info'
      return ''
    },
    pretty(value) {
      try { return JSON.stringify(JSON.parse(value), null, 2) } catch (e) { return value || '-' }
    }
  }
}
</script>

<style scoped>
.detail-text { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
.json-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 8px; margin: 0; max-height: 220px; overflow: auto; }
.preview-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 6px; margin: 0; max-height: 240px; overflow: auto; }
.preview-party { color: #909399; font-size: 12px; }
.target-table { width: 100%; margin-top: 8px; }
.text-danger { color: #f56c6c; }
.text-muted { color: #909399; }
.mr5 { margin-right: 5px; }
</style>
