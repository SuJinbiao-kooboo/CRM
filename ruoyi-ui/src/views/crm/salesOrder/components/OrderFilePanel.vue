<template>
  <div class="order-file-panel" v-loading="loading">
    <div class="file-grid">
      <div v-for="slot in fileSlots" :key="slot.prop" class="file-field">
        <div class="file-field-title">
          <span>{{ slot.label }}</span>
          <span v-hasPermi="['crm:salesOrder:edit']">
            <el-upload class="upload-trigger" action="#" :show-file-list="false" :accept="accept" multiple
              :disabled="!!uploading[slot.prop]" :http-request="options => handleUpload(options, slot.prop)">
              <el-button type="text" size="mini" icon="el-icon-upload2" :loading="!!uploading[slot.prop]">上传并保存</el-button>
            </el-upload>
          </span>
        </div>

        <div v-if="filesOf(slot.prop).length === 0 && legacyList(slot.prop).length === 0" class="empty-tip">暂无文件</div>

        <div v-for="file in filesOf(slot.prop)" :key="file.id" class="file-item">
          <i :class="fileIcon(file.fileName)" class="file-icon"></i>
          <span class="file-name" :title="file.fileName">{{ file.fileName }}</span>
          <span class="file-size">{{ formatSize(file.fileSize) }}</span>
          <div class="file-actions">
            <el-button type="text" size="mini" @click="previewFile(file)">预览</el-button>
            <el-button type="text" size="mini" @click="downloadFile(file)">下载</el-button>
            <span v-hasPermi="['crm:salesOrder:edit']">
              <el-button type="text" size="mini" class="danger-text" @click="removeFile(file)">删除</el-button>
            </span>
          </div>
        </div>

        <div v-for="(path, index) in legacyList(slot.prop)" :key="'legacy-' + index" class="file-item legacy-item">
          <i class="el-icon-document file-icon"></i>
          <a :href="legacyHref(path)" target="_blank" rel="noopener" class="file-name" :title="path">{{ legacyName(path) }}</a>
          <span class="legacy-tag">历史</span>
          <div class="file-actions">
            <a :href="legacyHref(path)" target="_blank" rel="noopener" class="legacy-link">打开</a>
          </div>
        </div>
      </div>
    </div>

    <el-dialog :title="previewTitle" :visible.sync="previewOpen" width="90%" top="4vh" append-to-body @closed="closePreview">
      <div v-if="previewType === 'image'" class="preview-image-wrap">
        <img :src="previewUrl" :alt="previewTitle" class="preview-image">
      </div>
      <iframe v-else-if="previewType === 'pdf'" :src="previewUrl" class="preview-frame" title="PDF预览"></iframe>
      <div v-else-if="previewType === 'html'" class="preview-html" v-html="previewHtml"></div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listSalesOrderFiles,
  uploadSalesOrderFile,
  downloadSalesOrderFile,
  previewSalesOrderFile,
  delSalesOrderFile
} from '@/api/crm/salesOrder'
import { ORDER_FILE_SLOTS } from '../fileFields'

const MAX_FILE_SIZE = 50 * 1024 * 1024
const IMAGE_TYPES = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  bmp: 'image/bmp',
  webp: 'image/webp',
  tif: 'image/tiff',
  tiff: 'image/tiff'
}

export default {
  name: 'OrderFilePanel',
  props: {
    orderId: { type: [Number, String], required: true },
    legacyFiles: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      fileSlots: ORDER_FILE_SLOTS,
      files: [],
      loading: false,
      uploading: {},
      accept: 'image/*,.pdf,.xls,.xlsx,.csv,.doc,.docx',
      previewOpen: false,
      previewTitle: '',
      previewType: '',
      previewUrl: '',
      previewHtml: ''
    }
  },
  watch: {
    orderId: {
      immediate: true,
      handler() {
        this.loadFiles()
      }
    }
  },
  beforeDestroy() {
    this.revokePreviewUrl()
  },
  methods: {
    loadFiles() {
      this.files = []
      if (!this.orderId) {
        return
      }
      this.loading = true
      listSalesOrderFiles(this.orderId).then(response => {
        this.files = response.data || []
      }).catch(error => {
        this.$modal.msgError((error && error.msg) || '附件加载失败')
      }).finally(() => {
        this.loading = false
      })
    },
    filesOf(fieldKey) {
      return this.files.filter(file => file.fieldKey === fieldKey)
    },
    legacyList(fieldKey) {
      const value = this.legacyFiles ? this.legacyFiles[fieldKey] : ''
      if (!value) {
        return []
      }
      return String(value).split(',').map(item => item.trim()).filter(Boolean)
    },
    legacyName(path) {
      const name = String(path).split('/').pop() || path
      try {
        return decodeURIComponent(name)
      } catch (e) {
        return name
      }
    },
    legacyHref(path) {
      if (/^https?:\/\//i.test(path)) {
        return path
      }
      const normalized = String(path).charAt(0) === '/' ? path : '/' + path
      return (process.env.VUE_APP_BASE_API || '') + normalized
    },
    formatSize(size) {
      const bytes = Number(size) || 0
      if (bytes <= 0) {
        return ''
      }
      if (bytes < 1024) {
        return bytes + ' B'
      }
      if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(1) + ' KB'
      }
      return (bytes / 1024 / 1024).toFixed(1) + ' MB'
    },
    fileIcon(fileName) {
      const ext = this.fileExt(fileName)
      if (IMAGE_TYPES[ext]) {
        return 'el-icon-picture-outline'
      }
      if (['xls', 'xlsx', 'csv'].includes(ext)) {
        return 'el-icon-s-grid'
      }
      if (ext === 'pdf') {
        return 'el-icon-document'
      }
      return 'el-icon-paperclip'
    },
    fileExt(fileName) {
      const parts = String(fileName || '').toLowerCase().split('.')
      return parts.length > 1 ? parts.pop() : ''
    },
    handleUpload(options, fieldKey) {
      const file = options.file
      if (!file) {
        return
      }
      if (file.size > MAX_FILE_SIZE) {
        this.$modal.msgError('单个附件不能超过 50MB')
        if (options.onError) {
          options.onError(new Error('单个附件不能超过 50MB'))
        }
        return
      }
      const formData = new FormData()
      formData.append('file', file)
      formData.append('orderId', this.orderId)
      formData.append('fieldKey', fieldKey)
      this.$set(this.uploading, fieldKey, true)
      uploadSalesOrderFile(formData).then(response => {
        if (options.onSuccess) {
          options.onSuccess(response)
        }
        const data = response.data || {}
        data.createTime = data.createTime || new Date().toISOString()
        this.files.push(data)
        this.$modal.msgSuccess('附件已上传并保存')
        this.$emit('changed', this.files.length)
      }).catch(error => {
        if (options.onError) {
          options.onError(error)
        }
        this.$modal.msgError((error && error.msg) || '附件上传失败')
      }).finally(() => {
        this.$set(this.uploading, fieldKey, false)
      })
    },
    previewFile(file) {
      const ext = this.fileExt(file.fileName)
      if (IMAGE_TYPES[ext]) {
        this.loadBlobPreview(file, IMAGE_TYPES[ext], 'image')
        return
      }
      if (ext === 'pdf') {
        this.loadBlobPreview(file, 'application/pdf', 'pdf')
        return
      }
      if (['xls', 'xlsx', 'csv', 'docx'].includes(ext)) {
        previewSalesOrderFile(file.id).then(response => {
          const html = response.data || ''
          if (!html) {
            this.$modal.msgError('附件预览失败')
            return
          }
          this.previewTitle = file.fileName
          this.previewHtml = html
          this.previewType = 'html'
          this.previewOpen = true
        }).catch(error => {
          this.$modal.msgError((error && error.msg) || '附件预览失败')
        })
        return
      }
      this.$modal.confirm('该文件格式暂不支持在线预览，是否下载查看？').then(() => {
        this.downloadFile(file)
      }).catch(() => {})
    },
    loadBlobPreview(file, mime, type) {
      downloadSalesOrderFile(file.id).then(data => {
        if (data.type && data.type.indexOf('application/json') !== -1) {
          data.text().then(text => {
            let message = '附件预览失败'
            try {
              message = JSON.parse(text).msg || message
            } catch (e) {}
            this.$modal.msgError(message)
          })
          return
        }
        this.revokePreviewUrl()
        const blob = new Blob([data], { type: mime })
        this.previewUrl = window.URL.createObjectURL(blob)
        this.previewTitle = file.fileName
        this.previewType = type
        this.previewOpen = true
      }).catch(() => {
        this.$modal.msgError('附件预览失败')
      })
    },
    closePreview() {
      this.revokePreviewUrl()
      this.previewType = ''
      this.previewHtml = ''
    },
    revokePreviewUrl() {
      if (this.previewUrl) {
        window.URL.revokeObjectURL(this.previewUrl)
        this.previewUrl = ''
      }
    },
    downloadFile(file) {
      this.download('/crm/salesOrder/attachment/download/' + file.id, {}, file.fileName)
    },
    removeFile(file) {
      this.$modal.confirm('是否确认删除附件“' + file.fileName + '”？').then(() => {
        return delSalesOrderFile(file.id)
      }).then(() => {
        this.files = this.files.filter(item => item.id !== file.id)
        this.$modal.msgSuccess('附件已删除')
        this.$emit('changed', this.files.length)
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.file-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.file-field { padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; min-height: 88px; }
.file-field-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; color: #303133; font-size: 13px; font-weight: 600; }
.upload-trigger { display: inline-block; }
.empty-tip { color: #c0c4cc; font-size: 12px; line-height: 24px; }
.file-item { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 12px; border-top: 1px dashed #ebeef5; }
.file-icon { color: #909399; }
.file-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #303133; text-decoration: none; }
.file-size { color: #909399; white-space: nowrap; }
.file-actions { display: flex; align-items: center; gap: 2px; white-space: nowrap; }
.file-actions >>> .el-button + .el-button { margin-left: 2px; }
.legacy-item { color: #909399; }
.legacy-tag { padding: 0 4px; border-radius: 2px; background: #f4f4f5; color: #909399; font-size: 11px; }
.legacy-link { color: #409eff; font-size: 12px; text-decoration: none; }
.danger-text { color: #f56c6c; }
.preview-image-wrap { display: flex; justify-content: center; align-items: center; min-height: 200px; max-height: 76vh; overflow: auto; background: #f5f7fa; }
.preview-image { max-width: 100%; max-height: 76vh; object-fit: contain; }
.preview-frame { width: 100%; height: 76vh; border: 0; }
.preview-html { max-height: 76vh; overflow: auto; }
.preview-html >>> table { border-collapse: collapse; font-size: 12px; }
.preview-html >>> td, .preview-html >>> th { padding: 3px 6px; border: 1px solid #dcdfe6; }
@media (max-width: 1100px) {
  .file-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
