<template>
  <div class="app-container">
    <el-card shadow="never">
      <div slot="header" class="clearfix">
        <span>订单单据生成</span>
        <span class="sub-tip">选择单据类型 -&gt; 填写字段 -&gt; 生成 Excel / PDF</span>
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

          <el-form-item v-if="currentType.upload" label="来源文件">
            <el-upload :auto-upload="false" :limit="1" :file-list="fileList" accept=".xlsx,.xls"
                       :on-change="onFileChange" :on-remove="onFileRemove">
              <el-button size="small" type="primary" icon="el-icon-upload2">选择文件</el-button>
              <div slot="tip" class="el-upload__tip">{{ currentType.uploadHint }}</div>
            </el-upload>
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
            <el-button type="primary" icon="el-icon-download" :loading="excelLoading" @click="handleGenerate('excel')">生成 Excel</el-button>
            <el-button type="success" icon="el-icon-document" :loading="pdfLoading" @click="handleGenerate('pdf')">生成 PDF</el-button>
            <el-button icon="el-icon-refresh-left" @click="resetFormAll">重置</el-button>
          </el-form-item>
        </template>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import { docTypes, generateDoc, generateDocPdf, resolveSeller } from '@/api/crm/order'
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
      fileList: [],
      uploadFile: null,
      excelLoading: false,
      pdfLoading: false
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
      this.fileList = []
      this.uploadFile = null
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
    onFileChange(file) {
      this.uploadFile = file.raw
    },
    onFileRemove() {
      this.uploadFile = null
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
    fileName(ext) {
      const t = this.docType
      if (t === 'PL') {
        return 'PL-Packing List ' + (this.form.customerPoNo || '') + '.' + ext
      }
      if (t === 'SN') {
        return 'SN List.' + ext
      }
      if (t === 'ENTRUST') {
        return '交货委托单.' + ext
      }
      return (this.form.orderNo || 'document') + '.' + ext
    },
    handleGenerate(kind) {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        if (this.currentType.upload && !this.uploadFile) {
          this.$modal.msgError('请上传来源文件')
          return
        }
        const api = kind === 'pdf' ? generateDocPdf : generateDoc
        const loadingKey = kind === 'pdf' ? 'pdfLoading' : 'excelLoading'
        this[loadingKey] = true
        api(this.buildPayload(), this.uploadFile).then(blob => {
          saveAs(new Blob([blob]), this.fileName(kind === 'pdf' ? 'pdf' : 'xlsx'))
          this[loadingKey] = false
        }).catch(() => {
          this[loadingKey] = false
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