<template>
  <div class="app-container">
    <el-card shadow="never">
      <div slot="header" class="clearfix">
        <span>订单单据生成</span>
        <span class="sub-tip">选择单据类型 -&gt; 填写字段 -&gt; 生成 Excel</span>
      </div>

      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="单据类型" prop="__docType">
          <el-radio-group v-model="docType" @change="onTypeChange">
            <el-radio-button v-for="t in types" :key="t.key" :label="t.key">{{ t.name }}</el-radio-button>
          </el-radio-group>
          <div class="tip" v-if="currentType && currentType.fileNameHint">文件名规则：{{ currentType.fileNameHint }}</div>
        </el-form-item>

        <template v-if="currentType">
          <el-row :gutter="16">
            <el-col v-for="f in currentType.fields" :key="f.key" :span="f.type === 'textarea' ? 24 : 12">
              <el-form-item :label="f.label" :prop="f.key">
                <el-input v-if="f.type === 'text'" v-model="form[f.key]" :placeholder="f.placeholder || ('请输入' + f.label)" clearable />
                <el-input v-else-if="f.type === 'number'" v-model="form[f.key]" :placeholder="f.placeholder || ('请输入' + f.label)" clearable />
                <el-input v-else-if="f.type === 'textarea'" type="textarea" :rows="f.key === 'snText' ? 12 : 3" v-model="form[f.key]" :placeholder="f.placeholder || ('请输入' + f.label)" />
                <el-date-picker v-else-if="f.type === 'date'" v-model="form[f.key]" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" style="width:100%" />
                <el-select v-else-if="f.type === 'select'" v-model="form[f.key]" :placeholder="f.placeholder || '请选择'" style="width:100%">
                  <el-option v-for="op in f.options" :key="op" :label="op" :value="op" />
                </el-select>
                <el-select v-else-if="f.type === 'supplier'" v-model="form[f.key]" filterable clearable placeholder="请选择" style="width:100%" @change="val => onSupplierChange(f, val)">
                  <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name || s.supplierName" :value="s.id" />
                </el-select>
                <el-input v-else v-model="form[f.key]" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16" v-if="currentType.uploads && currentType.uploads.length">
            <el-col v-for="u in currentType.uploads" :key="u.key" :span="12">
              <el-form-item :label="u.label">
                <el-upload :auto-upload="false" :limit="1" :file-list="uploadList[u.key] || []" accept=".xlsx,.xls"
                           :on-change="(file, list) => onFileChange(u.key, file, list)"
                           :on-remove="() => onFileRemove(u.key)">
                  <el-button size="small" type="primary" icon="el-icon-upload2">选择文件</el-button>
                  <div slot="tip" class="el-upload__tip">{{ u.hint }}</div>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item v-if="currentType.parseItems">
            <el-button type="warning" icon="el-icon-magic-stick" :loading="parseLoading" @click="handleParse">解析明细并回填（可编辑）</el-button>
            <span class="tip">上传 PO / PL 后点击解析，明细会显示到下方表格，可自行修改后再生成</span>
          </el-form-item>

          <el-form-item v-if="currentType.items" label="物料明细">
            <el-table :data="form.items" size="mini" border style="width:100%">
              <el-table-column type="index" label="序号" width="55" align="center" />
              <el-table-column v-for="c in currentType.itemColumns" :key="c.key" :label="c.label" min-width="100">
                <template slot-scope="scope">
                  <span v-if="c.computed">{{ itemAmount(scope.row) }}</span>
                  <el-input v-else v-model="scope.row[c.key]" size="mini" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template slot-scope="scope">
                  <el-button type="text" @click="removeItem(scope.$index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" plain icon="el-icon-plus" size="mini" style="margin-top:8px" @click="addItem">添加行</el-button>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" icon="el-icon-download" :loading="excelLoading" @click="handleGenerate">生成 Excel</el-button>
            <el-button icon="el-icon-refresh-left" @click="resetFormAll">重置</el-button>
          </el-form-item>
        </template>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import { docTypes, parseDocItems, generateDoc, resolveSeller } from '@/api/crm/order'
import { listSupplierOptions } from '@/api/crm/supplier'

const TOP_KEYS = ['orderNo', 'dateText', 'sellerId', 'sellerText', 'buyerId', 'buyerText', 'snText', 'shipTo', 'customerPoNo']

export default {
  name: 'OrderGenerate',
  data() {
    return {
      types: [],
      docType: undefined,
      form: { items: [] },
      supplierOptions: [],
      uploadFiles: {},
      uploadList: {},
      parseLoading: false,
      excelLoading: false
    }
  },
  computed: {
    currentType() {
      return this.types.find(t => t.key === this.docType) || null
    },
    rules() {
      const r = {}
      if (this.currentType) {
        this.currentType.fields.forEach(f => {
          if (f.required) {
            const trigger = (f.type === 'select' || f.type === 'supplier' || f.type === 'date') ? 'change' : 'blur'
            r[f.key] = [{ required: true, message: '请输入' + f.label, trigger: trigger }]
          }
        })
      }
      return r
    }
  },
  created() {
    this.loadTypes()
    this.loadSuppliers()
  },
  methods: {
    loadTypes() {
      docTypes().then(res => {
        this.types = res.data || []
        if (this.types.length) {
          this.docType = this.types[0].key
          this.onTypeChange(this.docType)
        }
      })
    },
    loadSuppliers() {
      listSupplierOptions({}).then(res => {
        this.supplierOptions = res.data || []
      })
    },
    onTypeChange(key) {
      const t = this.types.find(x => x.key === key)
      const form = { items: [] }
      if (t) {
        t.fields.forEach(f => {
          form[f.key] = f.defaultValue != null ? f.defaultValue : undefined
        })
        if (t.items) {
          form.items = [this.blankItem(t)]
        }
      }
      this.form = form
      this.uploadFiles = {}
      this.uploadList = {}
      this.$nextTick(() => {
        if (this.$refs.form) {
          this.$refs.form.clearValidate()
        }
      })
    },
    blankItem(t) {
      const row = {}
      if (t) {
        t.itemColumns.forEach(c => { row[c.key] = undefined })
      }
      return row
    },
    addItem() {
      this.form.items = this.form.items || []
      this.form.items.push(this.blankItem(this.currentType))
    },
    removeItem(index) {
      this.form.items.splice(index, 1)
    },
    itemAmount(row) {
      const amount = (Number(row.qty) || 0) * (Number(row.unitPrice) || 0)
      return amount.toFixed(2)
    },
    onSupplierChange(field, val) {
      if (!val) {
        return
      }
      resolveSeller(val).then(res => {
        const data = res.data || {}
        if (!data.text) {
          return
        }
        const textKey = field.key === 'sellerId' ? 'sellerText' : (field.key === 'buyerId' ? 'buyerText' : null)
        if (textKey) {
          this.$set(this.form, textKey, data.text)
        }
      })
    },
    onFileChange(key, file, list) {
      this.$set(this.uploadFiles, key, file.raw)
      this.$set(this.uploadList, key, list)
    },
    onFileRemove(key) {
      this.$set(this.uploadFiles, key, null)
      this.$set(this.uploadList, key, [])
    },
    buildPayload() {
      const payload = { docType: this.docType, extras: {}, items: [] }
      const fields = this.currentType ? this.currentType.fields : []
      fields.forEach(f => {
        const v = this.form[f.key]
        if (TOP_KEYS.indexOf(f.key) >= 0) {
          payload[f.key] = v
        } else {
          payload.extras[f.key] = v
        }
      })
      const cols = this.currentType ? this.currentType.itemColumns : []
      const itemKeys = cols.filter(c => !c.computed).map(c => c.key)
      payload.items = (this.form.items || []).filter(row => itemKeys.some(k => row[k] !== undefined && row[k] !== null && String(row[k]).trim() !== ''))
      return payload
    },
    handleParse() {
      const hasFile = Object.keys(this.uploadFiles).some(k => this.uploadFiles[k])
      if (!hasFile) {
        this.$modal.msgError('请先上传 PO 或 PL 文件')
        return
      }
      this.parseLoading = true
      parseDocItems({ docType: this.docType }, this.uploadFiles).then(res => {
        const rows = (res.data || []).map(r => {
          const row = {}
          this.currentType.itemColumns.forEach(c => { row[c.key] = r[c.key] !== undefined ? r[c.key] : undefined })
          return row
        })
        this.form.items = rows.length ? rows : [this.blankItem(this.currentType)]
        this.$modal.msgSuccess('已解析 ' + rows.length + ' 条明细，可直接编辑')
        this.parseLoading = false
      }).catch(() => {
        this.parseLoading = false
      })
    },
    fileName(ext) {
      const t = this.docType
      if (t === 'PL') {
        return 'PL-Packing List ' + (this.form.customerPoNo || '') + '.' + ext
      }
      if (t === 'SN') {
        return 'SN List.' + ext
      }
      if (t === 'ENTRUST') {
        return 'Copy of QR-KF-005-A2香港仓库交货委托单' + (this.form.warehouseNo || '') + '.' + ext
      }
      return (this.form.orderNo || 'document') + '.' + ext
    },
    handleGenerate() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        const required = (this.currentType.uploads || []).filter(u => u.required && !this.uploadFiles[u.key])
        if (required.length) {
          this.$modal.msgError('请上传：' + required.map(u => u.label).join('、'))
          return
        }
        this.excelLoading = true
        generateDoc(this.buildPayload(), this.uploadFiles).then(blob => {
          saveAs(new Blob([blob]), this.fileName('xlsx'))
          this.excelLoading = false
        }).catch(() => {
          this.excelLoading = false
        })
      })
    },
    resetFormAll() {
      this.onTypeChange(this.docType)
    }
  }
}
</script>

<style scoped>
.sub-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}
</style>
