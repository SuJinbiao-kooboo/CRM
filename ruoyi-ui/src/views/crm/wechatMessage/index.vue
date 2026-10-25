<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="聊天对象" prop="name">
        <el-input v-model="queryParams.name" placeholder="群名或好友名" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="消息内容" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="正文或异常原因" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="msgCategory">
        <el-select v-model="queryParams.msgCategory" clearable placeholder="全部" style="width:110px">
          <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="分类状态" prop="classifyStatus">
        <el-select v-model="queryParams.classifyStatus" clearable placeholder="全部" style="width:120px">
          <el-option v-for="item in classifyStatuses" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="录入状态" prop="entryStatus">
        <el-select v-model="queryParams.entryStatus" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in entryStatuses" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务状态" prop="taskStatus">
        <el-select v-model="queryParams.taskStatus" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in taskStatuses" :key="item" :label="item" :value="item" />
        </el-select>
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
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-download" size="mini" :loading="polling" @click="handlePoll" v-hasPermi="['wechat:message:poll']">拉取</el-button></el-col>
      <el-col :span="1.5"><el-button type="warning" plain icon="el-icon-magic-stick" size="mini" :loading="classifying" @click="handleClassifySelected" v-hasPermi="['wechat:message:classify']">分类选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="success" plain icon="el-icon-document-add" size="mini" :loading="entering" @click="handleEntrySelected" v-hasPermi="['wechat:message:entry']">录入选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="info" plain icon="el-icon-refresh-left" size="mini" @click="handleRequeue" v-hasPermi="['wechat:message:entry']">重排选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="danger" plain icon="el-icon-bell" size="mini" @click="handleTraceScan" v-hasPermi="['wechat:message:classify']">TRACE补扫</el-button></el-col>
      <el-col :span="1.5"><el-button plain icon="el-icon-delete-solid" size="mini" @click="handleClearCache" v-hasPermi="['wechat:message:classify']">清分类缓存</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert v-if="statsText" :title="statsText" type="info" :closable="false" show-icon class="mb8" />

    <el-table ref="messageTable" v-loading="loading" :data="messageList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column label="消息时间" prop="createTime" width="158" />
      <el-table-column label="聊天对象" prop="name" min-width="170" show-overflow-tooltip />
      <el-table-column label="发言人" prop="sender" width="110" show-overflow-tooltip />
      <el-table-column label="分类" width="88" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="categoryType(scope.row.msgCategory)">{{ scope.row.msgCategory || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="正文" prop="content" min-width="260" show-overflow-tooltip />
      <el-table-column label="分类状态" width="105" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="statusType(scope.row.classifyStatus)">{{ scope.row.classifyStatus || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="录入状态" width="120" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.entryStatus)">{{ scope.row.entryStatus || '-' }}</el-tag>
          <el-tooltip v-if="scope.row.entryMessage" :content="scope.row.entryMessage" placement="top"><i class="el-icon-warning-outline" /></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="任务状态" width="118" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.taskStatus)">{{ scope.row.taskStatus || '-' }}</el-tag>
          <el-tooltip v-if="scope.row.taskMessage" :content="scope.row.taskMessage" placement="top"><i class="el-icon-warning-outline" /></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="TRACE预警" width="135" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.traceAlertedAt">{{ scope.row.traceAlertedAt }}</span>
          <span v-else-if="scope.row.traceAlertError" class="text-danger">{{ scope.row.traceAlertError }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-view" @click="showDetail(scope.row)">详情</el-button></template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="微信消息详情" :visible.sync="detailOpen" width="960px" append-to-body>
      <el-form label-width="110px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="聊天对象">{{ detail.name }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发言人">{{ detail.sender }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息时间">{{ detail.createTime }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息键">{{ detail.username }}:{{ detail.localId }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类">{{ detail.msgCategory }} / {{ detail.categorySource }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类状态">{{ detail.classifyStatus }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="录入状态">{{ detail.entryStatus }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="任务状态">{{ detail.taskStatus }}</el-form-item></el-col>
        </el-row>
        <el-form-item label="正文"><div class="detail-text">{{ detail.content }}</div></el-form-item>
        <el-form-item label="分类说明"><div class="detail-text">{{ detail.categoryReason }}</div></el-form-item>
        <el-form-item label="录入说明"><div class="detail-text">{{ detail.entryMessage }}</div></el-form-item>
        <el-form-item label="任务说明"><div class="detail-text">{{ detail.taskMessage }}</div></el-form-item>
        <el-form-item v-if="detail.relatInq" label="关联 INQ"><pre class="json-box">{{ pretty(detail.relatInq) }}</pre></el-form-item>
        <el-form-item v-if="detail.relatOffer" label="关联 OFFER"><pre class="json-box">{{ pretty(detail.relatOffer) }}</pre></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import { listWechatMessages, getWechatMessageStats, runWechatClassify, clearWechatClassifyCache, scanWechatTrace, runWechatEntry, requeueWechatEntry, runWechatPoll } from '@/api/crm/wechatMessage'

export default {
  name: 'CrmWechatMessage',
  data() {
    return {
      loading: false,
      polling: false,
      classifying: false,
      entering: false,
      showSearch: true,
      total: 0,
      messageList: [],
      selected: [],
      dateRange: [],
      statsText: '',
      detailOpen: false,
      detail: {},
      categories: ['INQ', 'OFFER', 'TRACE', 'OTHER'],
      classifyStatuses: ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'SKIPPED'],
      entryStatuses: ['NOT_APPLICABLE', 'PENDING', 'PROCESSING', 'SUCCESS', 'SUCCESS_WARN', 'FAILED', 'DEAD', 'SKIPPED'],
      taskStatuses: ['PENDING', 'PROCESSING', 'GENERATED', 'SKIPPED', 'MANUAL_REVIEW', 'FAILED'],
      queryParams: { pageNum: 1, pageSize: 20, name: undefined, keyword: undefined, msgCategory: undefined, classifyStatus: undefined, entryStatus: undefined, taskStatus: undefined, beginTime: undefined, endTime: undefined }
    }
  },
  created() {
    this.dateRange = this.last30Days()
    this.handleQuery()
    this.loadStats()
  },
  methods: {
    last30Days() {
      const fmt = date => {
        const y = date.getFullYear()
        const m = String(date.getMonth() + 1).padStart(2, '0')
        const d = String(date.getDate()).padStart(2, '0')
        return y + '-' + m + '-' + d
      }
      const now = new Date()
      return [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
    },
    getList() {
      this.loading = true
      const query = Object.assign({}, this.queryParams)
      if (this.dateRange && this.dateRange.length === 2) {
        query.beginTime = this.dateRange[0] + ' 00:00:00'
        query.endTime = this.dateRange[1] + ' 23:59:59'
      }
      listWechatMessages(query).then(res => {
        this.messageList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadStats() {
      getWechatMessageStats().then(res => {
        const data = res.data || {}
        const poll = data.poll || {}
        const entry = data.entry || {}
        this.statsText = '未分类 ' + (poll.unclassifiedCnt || 0) + ' 条，待录入 ' + (entry.pendingCnt || 0) + ' 条，最近拉取 ' + (poll.lastRunAt || '-')
      }).catch(() => {})
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
      this.loadStats()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.dateRange = this.last30Days()
      this.handleQuery()
    },
    handleSelectionChange(rows) {
      this.selected = rows || []
    },
    selectedKeys() {
      return this.selected.map(row => row.username + ':' + row.localId)
    },
    requireSelection() {
      if (!this.selected.length) {
        this.$modal.msgWarning('请先勾选消息')
        return false
      }
      if (this.selected.length > 20) {
        this.$modal.msgWarning('单次最多选择20条消息')
        return false
      }
      return true
    },
    handlePoll() {
      this.polling = true
      runWechatPoll({ chat: this.queryParams.name || null }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '拉取完成')
        this.getList()
      }).finally(() => { this.polling = false })
    },
    handleClassifySelected() {
      if (!this.requireSelection()) return
      this.classifying = true
      runWechatClassify({ keys: this.selectedKeys() }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '分类完成')
        this.getList()
      }).finally(() => { this.classifying = false })
    },
    handleEntrySelected() {
      if (!this.requireSelection()) return
      this.entering = true
      runWechatEntry({ keys: this.selectedKeys() }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '录入完成')
        this.getList()
      }).finally(() => { this.entering = false })
    },
    handleRequeue() {
      if (!this.requireSelection()) return
      this.$modal.confirm('确认将选中的失败/跳过消息重新入队？').then(() => requeueWechatEntry({ keys: this.selectedKeys() })).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '已重排')
        this.getList()
      }).catch(() => {})
    },
    handleTraceScan() {
      scanWechatTrace().then(res => this.$modal.msgSuccess((res.data && res.data.message) || '补扫完成'))
    },
    handleClearCache() {
      this.$modal.confirm('确认清空分类缓存？历史分类结果不会被改写。').then(() => clearWechatClassifyCache()).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '已清空')
      }).catch(() => {})
    },
    showDetail(row) {
      this.detail = row
      this.detailOpen = true
    },
    pretty(value) {
      try { return JSON.stringify(JSON.parse(value), null, 2) } catch (e) { return value || '' }
    },
    categoryType(category) {
      if (category === 'INQ') return 'warning'
      if (category === 'OFFER') return 'success'
      if (category === 'TRACE') return 'danger'
      return 'info'
    },
    statusType(status) {
      if (status === 'SUCCESS' || status === 'SUCCESS_WARN' || status === 'GENERATED') return 'success'
      if (status === 'FAILED' || status === 'DEAD' || status === 'MANUAL_REVIEW') return 'danger'
      if (status === 'PROCESSING') return 'warning'
      return 'info'
    }
  }
}
</script>

<style scoped>
.detail-text { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
.json-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 8px; margin: 0; max-height: 240px; overflow: auto; }
.text-danger { color: #f56c6c; }
</style>
