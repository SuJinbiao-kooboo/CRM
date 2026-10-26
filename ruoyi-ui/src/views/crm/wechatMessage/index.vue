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
      <el-table-column label="正文" min-width="300">
        <template slot-scope="scope">
          <div class="message-content">
            <el-link v-if="isImage(scope.row)" type="primary" :underline="false" @click="showDetail(scope.row)">
              <i class="el-icon-picture-outline"></i> 查看图片
            </el-link>
            <el-link v-else-if="hasFile(scope.row)" type="primary" :underline="false" @click="showDetail(scope.row)">
              <i class="el-icon-document"></i> {{ cardTitle(scope.row.content) || '查看文件' }}
            </el-link>
            <span v-else>{{ displayContent(scope.row) }}</span>
          </div>
          <div v-if="scope.row.quote" class="quote-preview">引用：{{ scope.row.quote }}</div>
        </template>
      </el-table-column>
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

    <el-dialog title="微信消息详情" :visible.sync="detailOpen" width="960px" append-to-body @closed="closeDetail">
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
        <el-form-item label="正文"><div class="detail-text">{{ displayContent(detail) }}</div></el-form-item>
        <el-form-item v-if="detail.quote" label="引用内容">
          <div class="quote-box">
            <span class="quote-kind">{{ quoteKindLabel(detail.quoteKind) }}</span>
            <div class="detail-text">{{ detail.quote }}</div>
          </div>
        </el-form-item>
        <el-form-item v-if="isImage(detail)" label="图片">
          <div v-loading="detailMediaLoading" class="media-panel">
            <el-image v-if="detailMediaUrl" :src="detailMediaUrl" :preview-src-list="[detailMediaUrl]" fit="contain" class="image-preview" />
            <div v-else-if="detailMediaError" class="media-error">
              {{ detailMediaError }}
              <el-button type="text" @click="loadImagePreview(detail)">重试</el-button>
            </div>
            <div class="media-actions">
              <el-button size="mini" type="primary" icon="el-icon-download" :disabled="!detailMediaUrl" :loading="mediaDownloading" @click="downloadMedia(detail)">下载图片</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item v-if="hasFile(detail)" label="文件">
          <div class="file-card">
            <i class="el-icon-document file-icon"></i>
            <span class="file-name">{{ cardTitle(detail.content) || '微信文件' }}</span>
            <el-button size="mini" type="primary" plain icon="el-icon-view" @click="openFilePreview(detail)">打开</el-button>
            <el-button size="mini" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadMedia(detail)">下载</el-button>
          </div>
        </el-form-item>
        <el-form-item label="分类说明"><div class="detail-text">{{ detail.categoryReason }}</div></el-form-item>
        <el-form-item label="录入说明"><div class="detail-text">{{ detail.entryMessage }}</div></el-form-item>
        <el-form-item label="任务说明"><div class="detail-text">{{ detail.taskMessage }}</div></el-form-item>
        <el-form-item v-if="detail.relatInq" label="关联 INQ"><pre class="json-box">{{ pretty(detail.relatInq) }}</pre></el-form-item>
        <el-form-item v-if="detail.relatOffer" label="关联 OFFER"><pre class="json-box">{{ pretty(detail.relatOffer) }}</pre></el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog :title="filePreviewName ? ('文件预览 - ' + filePreviewName) : '文件预览'"
               :visible.sync="filePreviewOpen" width="82%" top="5vh" append-to-body @closed="clearFilePreview">
      <div v-loading="filePreviewLoading" class="file-preview">
        <div v-if="filePreviewError" class="media-error">{{ filePreviewError }}</div>
        <iframe v-else-if="filePreviewKind === 'pdf'" :src="filePreviewUrl" class="pdf-preview" />
        <pre v-else-if="filePreviewKind === 'text'" class="text-preview">{{ filePreviewText }}</pre>
        <div v-else-if="filePreviewKind === 'excel'" class="excel-preview">
          <div v-if="filePreviewNote" class="sheet-note">{{ filePreviewNote }}</div>
          <table class="excel-table">
            <tbody>
              <tr v-for="(row, rowIndex) in filePreviewRows" :key="rowIndex">
                <th>{{ rowIndex + 1 }}</th>
                <td v-for="(cell, colIndex) in row" :key="colIndex">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div slot="footer">
        <el-button size="small" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadMedia(filePreviewRow)">下载原文件</el-button>
        <el-button size="small" @click="filePreviewOpen = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import * as XLSX from 'xlsx'
import { listWechatMessages, getWechatMessageStats, getWechatMedia, runWechatClassify, clearWechatClassifyCache, scanWechatTrace, runWechatEntry, requeueWechatEntry, runWechatPoll } from '@/api/crm/wechatMessage'

const DOCUMENT_EXTS = ['pdf', 'xls', 'xlsx', 'csv', 'txt', 'md', 'json', 'xml', 'log', 'doc', 'docx', 'ppt', 'pptx', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
const TEXT_EXTS = ['txt', 'md', 'json', 'xml', 'log']
const IMAGE_MIME_EXTS = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/bmp': 'bmp'
}

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
      detailMediaUrl: '',
      detailMediaLoading: false,
      detailMediaError: '',
      mediaDownloading: false,
      filePreviewOpen: false,
      filePreviewRow: {},
      filePreviewName: '',
      filePreviewLoading: false,
      filePreviewError: '',
      filePreviewKind: '',
      filePreviewUrl: '',
      filePreviewText: '',
      filePreviewRows: [],
      filePreviewNote: '',
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
      this.releaseDetailMedia()
      this.detail = row
      this.detailMediaError = ''
      this.detailOpen = true
      if (this.isImage(row)) {
        this.loadImagePreview(row)
      }
    },
    closeDetail() {
      this.releaseDetailMedia()
      this.detail = {}
      this.detailMediaError = ''
    },
    releaseDetailMedia() {
      if (this.detailMediaUrl) {
        URL.revokeObjectURL(this.detailMediaUrl)
        this.detailMediaUrl = ''
      }
    },
    isImage(row) {
      return !!row && (row.type === '图片' || String(row.type || '').toUpperCase() === 'IMAGE')
    },
    hasFile(row) {
      if (!row) return false
      const type = String(row.type || '')
      const content = String(row.content || '')
      if (type === '文件' || type.toUpperCase() === 'FILE') {
        const ext = this.fileExtension(row)
        return !!ext && DOCUMENT_EXTS.indexOf(ext) >= 0
      }
      if (type !== '文件/链接/卡片' || !/<fileext(?:\s[^>]*)?>/i.test(content)) return false
      const ext = this.fileExtension(row)
      return !!ext && DOCUMENT_EXTS.indexOf(ext) >= 0
    },
    cardMeta(content) {
      const text = String(content || '')
      const readTag = tag => {
        const match = text.match(new RegExp('<' + tag + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/' + tag + '>', 'i'))
        if (!match) return ''
        const value = match[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
        return this.decodeXml(value).trim()
      }
      return { title: readTag('title'), ext: readTag('fileext').replace(/^\./, '').toLowerCase() }
    },
    decodeXml(value) {
      const area = document.createElement('textarea')
      area.innerHTML = String(value || '')
      return area.value
    },
    cardTitle(content) {
      return this.cardMeta(content).title
    },
    fileExtension(row) {
      if (!row) return ''
      const meta = this.cardMeta(row.content)
      if (meta.ext) return meta.ext
      const name = meta.title || String(row.content || '')
      const dot = name.lastIndexOf('.')
      return dot >= 0 ? name.substring(dot + 1).replace(/[^a-zA-Z0-9]/g, '').toLowerCase() : ''
    },
    displayContent(row) {
      if (!row) return ''
      if (this.isImage(row)) return '【图片】'
      if (this.hasFile(row)) return this.cardTitle(row.content) || '微信文件'
      const type = String(row.type || '')
      if (type === '文件/链接/卡片' || type === '链接' || type === '卡片') {
        return this.cardTitle(row.content) || '【卡片消息】'
      }
      const content = String(row.content || '')
      if (type && type !== '文本' && type.toUpperCase() !== 'TEXT' && content.trim().indexOf('<') === 0) {
        return '【' + type + '】'
      }
      return content
    },
    quoteKindLabel(kind) {
      const labels = {
        text: '文本',
        图片: '图片',
        image: '图片',
        表情: '表情',
        emoji: '表情',
        语音: '语音',
        voice: '语音',
        视频: '视频',
        video: '视频',
        卡片: '卡片',
        card: '卡片',
        other: '其他'
      }
      return labels[kind] || kind || '内容'
    },
    isJsonBlob(blob) {
      return !!blob && String(blob.type || '').toLowerCase().indexOf('json') >= 0
    },
    async blobMessage(blob, fallback) {
      try {
        const data = JSON.parse(await blob.text())
        return data.msg || data.message || fallback
      } catch (e) {
        return fallback
      }
    },
    async errorMessage(error, fallback) {
      const data = error && error.response ? error.response.data : null
      if (data instanceof Blob) {
        return this.blobMessage(data, fallback)
      }
      if (data && (data.msg || data.message)) {
        return data.msg || data.message
      }
      const message = error && error.message ? error.message : ''
      if (message && message.indexOf('Request failed with status code') < 0) {
        return message
      }
      return fallback
    },
    async getMediaBlob(row, download, fallback) {
      let blob
      try {
        blob = await getWechatMedia({
          username: row.username,
          localId: row.localId,
          download: !!download
        })
      } catch (error) {
        throw new Error(await this.errorMessage(error, fallback))
      }
      if (!(blob instanceof Blob) || blob.size === 0) {
        throw new Error(fallback)
      }
      if (this.isJsonBlob(blob)) {
        throw new Error(await this.blobMessage(blob, fallback))
      }
      return blob
    },
    async loadImagePreview(row) {
      this.releaseDetailMedia()
      this.detailMediaError = ''
      if (!row || !row.username) {
        this.detailMediaError = '消息缺少会话信息，无法读取图片'
        return
      }
      this.detailMediaLoading = true
      try {
        const blob = await this.getMediaBlob(row, false, '图片读取失败')
        this.detailMediaUrl = URL.createObjectURL(blob)
      } catch (error) {
        this.detailMediaError = error.message || '图片读取失败'
      } finally {
        this.detailMediaLoading = false
      }
    },
    downloadName(row, blob) {
      let name = this.hasFile(row) ? this.cardTitle(row.content) : ''
      if (!name && this.isImage(row)) {
        const mime = blob && blob.type ? String(blob.type).toLowerCase() : ''
        name = '微信图片_' + row.localId + '.' + (IMAGE_MIME_EXTS[mime] || 'jpg')
      }
      if (!name && this.hasFile(row)) {
        const ext = this.fileExtension(row)
        name = '微信文件_' + row.localId + (ext ? '.' + ext : '')
      }
      name = name || ('微信文件_' + row.localId)
      return name.replace(/[\\/:*?"<>|]/g, '_')
    },
    async downloadMedia(row) {
      if (!row || !row.username || this.mediaDownloading) return
      this.mediaDownloading = true
      try {
        const blob = await this.getMediaBlob(row, true, '文件下载失败')
        saveAs(blob, this.downloadName(row, blob))
      } catch (error) {
        this.$modal.msgError(error.message || '文件下载失败')
      } finally {
        this.mediaDownloading = false
      }
    },
    async openFilePreview(row) {
      this.clearFilePreview()
      this.filePreviewRow = row
      this.filePreviewName = this.cardTitle(row.content) || '微信文件'
      this.filePreviewOpen = true
      this.filePreviewLoading = true
      try {
        const blob = await this.getMediaBlob(row, false, '文件读取失败')
        const ext = this.fileExtension(row)
        if (ext === 'pdf') {
          if (blob.size > 30 * 1024 * 1024) {
            throw new Error('PDF 超过 30MB，请下载后查看')
          }
          this.filePreviewKind = 'pdf'
          this.filePreviewUrl = URL.createObjectURL(blob)
          return
        }
        if (ext === 'xls' || ext === 'xlsx' || ext === 'csv') {
          if (blob.size > 10 * 1024 * 1024) {
            throw new Error('表格超过 10MB，请下载后查看')
          }
          let workbook
          if (ext === 'csv') {
            workbook = XLSX.read(await blob.text(), { type: 'string' })
          } else {
            workbook = XLSX.read(new Uint8Array(await blob.arrayBuffer()), { type: 'array' })
          }
          const sheetName = workbook.SheetNames[0]
          if (!sheetName) {
            throw new Error('文件中没有可预览的工作表')
          }
          const sheet = workbook.Sheets[sheetName]
          const range = XLSX.utils.decode_range(sheet['!ref'] || 'A1')
          const totalRows = range.e.r - range.s.r + 1
          const totalCols = range.e.c - range.s.c + 1
          const lastRow = Math.min(range.e.r, range.s.r + 199)
          const lastCol = Math.min(range.e.c, range.s.c + 49)
          const rows = []
          for (let rowIndex = range.s.r; rowIndex <= lastRow; rowIndex++) {
            const row = []
            for (let colIndex = range.s.c; colIndex <= lastCol; colIndex++) {
              const cell = sheet[XLSX.utils.encode_cell({ r: rowIndex, c: colIndex })]
              row.push(cell ? XLSX.utils.format_cell(cell) : '')
            }
            rows.push(row)
          }
          this.filePreviewKind = 'excel'
          this.filePreviewRows = rows
          this.filePreviewNote = '工作表：' + sheetName +
            (totalRows > 200 || totalCols > 50 ? '（仅预览前 200 行、50 列，完整内容请下载）' : '')
          return
        }
        if (TEXT_EXTS.indexOf(ext) >= 0) {
          if (blob.size > 2 * 1024 * 1024) {
            throw new Error('文本超过 2MB，请下载后查看')
          }
          this.filePreviewKind = 'text'
          this.filePreviewText = await blob.text()
          return
        }
        throw new Error('该文件类型暂不支持在线预览，请下载原文件')
      } catch (error) {
        this.filePreviewError = error.message || '文件读取失败'
      } finally {
        this.filePreviewLoading = false
      }
    },
    clearFilePreview() {
      if (this.filePreviewUrl) {
        URL.revokeObjectURL(this.filePreviewUrl)
      }
      this.filePreviewOpen = false
      this.filePreviewRow = {}
      this.filePreviewName = ''
      this.filePreviewError = ''
      this.filePreviewKind = ''
      this.filePreviewUrl = ''
      this.filePreviewText = ''
      this.filePreviewRows = []
      this.filePreviewNote = ''
      this.filePreviewLoading = false
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
.message-content { line-height: 1.5; word-break: break-all; }
.quote-preview { margin-top: 3px; color: #909399; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-text { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
.quote-box { border-left: 3px solid #dcdfe6; padding: 6px 10px; background: #f5f7fa; }
.quote-kind { display: inline-block; margin-bottom: 4px; color: #909399; font-size: 12px; }
.media-panel { min-height: 80px; }
.image-preview { display: block; width: 100%; max-width: 860px; max-height: 520px; }
.media-actions { margin-top: 10px; }
.media-error { color: #f56c6c; line-height: 1.6; }
.file-card { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; }
.file-icon { color: #409eff; font-size: 22px; }
.file-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-preview { min-height: 180px; }
.pdf-preview { display: block; width: 100%; height: 72vh; border: 0; }
.text-preview { margin: 0; padding: 12px; max-height: 70vh; overflow: auto; white-space: pre-wrap; word-break: break-all; background: #f5f7fa; }
.excel-preview { max-height: 70vh; overflow: auto; }
.sheet-note { margin-bottom: 8px; color: #909399; font-size: 12px; }
.excel-table { border-collapse: collapse; font-size: 12px; }
.excel-table td, .excel-table th { border: 1px solid #dcdfe6; padding: 4px 8px; white-space: nowrap; }
.excel-table th { position: sticky; left: 0; background: #f5f7fa; color: #909399; font-weight: normal; }
.json-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 8px; margin: 0; max-height: 240px; overflow: auto; }
.text-danger { color: #f56c6c; }
</style>
