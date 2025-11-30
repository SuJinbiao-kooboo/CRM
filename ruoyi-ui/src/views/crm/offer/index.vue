<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="88px">
      <el-form-item label="产品编码" prop="productCode">
        <el-input v-model="queryParams.productCode" placeholder="请输入产品编码" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商">
        <el-select v-model="queryParams.supplierCodes" multiple collapse-tags filterable remote reserve-keyword placeholder="请选择供应商" :remote-method="remoteSupplierQuery" value-key="supplierCode">
          <el-option v-for="item in supplierOptionsQuery" :key="item.supplierCode" :label="item.supplierName" :value="item.supplierCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="inqOfferType">
        <el-select v-model="queryParams.inqOfferType" placeholder="请选择类型" clearable>
          <el-option label="Inq" value="Inq" />
          <el-option label="Offer" value="Offer" />
        </el-select>
      </el-form-item>
      <el-form-item label="品牌">
        <el-select v-model="queryParams.productBrandArr" multiple collapse-tags filterable placeholder="请选择品牌">
          <el-option v-for="d in dictProductBrand" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="产品类型">
        <el-select v-model="queryParams.productTypeArr" multiple collapse-tags filterable placeholder="请选择类型">
          <el-option v-for="d in dictProductType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签1">
        <el-select v-model="queryParams.tagsFirst" clearable filterable placeholder="标签1">
          <el-option v-for="d in dictTagsFirst" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="text" @click="moreTags=!moreTags">{{ moreTags ? '收起更多标签' : '展开更多标签' }}</el-button>
      </el-form-item>
      <template v-if="moreTags">
        <el-form-item label="标签2">
          <el-select v-model="queryParams.tagsSecond" clearable filterable placeholder="标签2">
            <el-option v-for="d in dictTagsSecond" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签3">
          <el-select v-model="queryParams.tagsThird" clearable filterable placeholder="标签3">
            <el-option v-for="d in dictTagsThird" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签4">
          <el-select v-model="queryParams.tagsSi" clearable filterable placeholder="标签4">
            <el-option v-for="d in dictTagsSi" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item label="库存日期">
        <el-date-picker v-model="stockDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="mini" />
      </el-form-item>
      <!-- 其余筛选条件移除，保留指定条件与日期范围 -->
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['crm:offer:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate" v-hasPermi="['crm:offer:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['crm:offer:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['crm:offer:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-upload2" size="mini" @click="openImport" v-hasPermi="['crm:offer:import']">导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit-outline" size="mini" :disabled="multiple" @click="openBatchEdit" v-hasPermi="['crm:offer:batchEdit']">批量编辑</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="offerList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="产品编码" align="center" prop="productCode" />
      <el-table-column label="供应商名称" align="center" prop="supplierName" />
      <el-table-column label="成本" align="center" prop="priceCost" />
      <el-table-column label="报价" align="center" prop="priceOffer" />
      <el-table-column label="数量" align="center" prop="quantity" />
      <el-table-column label="库存日期" align="center" prop="stockDate" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.stockDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="品牌" align="center" prop="productBrand" />
      <el-table-column label="价格单位" align="center" prop="priceUnit" />
      <el-table-column label="产品类型" align="center" prop="productType" />
      <el-table-column label="类型" align="center" prop="inqOfferType" />
      <el-table-column label="标签1" align="center" prop="tagsFirst" />
      <el-table-column label="标签2" align="center" prop="tagsSecond" />
      <el-table-column label="标签3" align="center" prop="tagsThird" />
      <el-table-column label="标签4" align="center" prop="tagsSi" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="来源表名" align="center" prop="sheetName" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['crm:offer:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['crm:offer:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" :page-sizes="[10,20,50,100,200,300,500]" layout="total, sizes, prev, pager, next, jumper" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="700px" append-to-body>
      <el-form ref="form" :model="form" label-width="120px">
        <el-form-item label="供应商" prop="supplierName">
          <el-select v-model="formSupplier" filterable remote reserve-keyword placeholder="请选择供应商" :remote-method="remoteSupplier" value-key="supplierCode" @change="onSupplierChange">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="请输入产品编码" />
        </el-form-item>
        <el-form-item label="产品品牌" prop="productBrand">
          <el-select v-model="form.productBrandArr" multiple collapse-tags filterable placeholder="请选择产品品牌">
            <el-option v-for="d in dictProductBrand" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="库存日期" prop="stockDate">
          <el-date-picker v-model="form.stockDate" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本" prop="priceCost">
          <el-input-number v-model="form.priceCost" :controls="false" :precision="2" :min="0" placeholder="请输入成本" style="width:100%" />
        </el-form-item>
        <el-form-item label="报价" prop="priceOffer">
          <el-input-number v-model="form.priceOffer" :controls="false" :precision="2" :min="0" placeholder="请输入报价" style="width:100%" />
        </el-form-item>
        <el-form-item label="价格单位" prop="priceUnit">
          <el-select v-model="form.priceUnitArr" multiple collapse-tags filterable placeholder="请选择价格单位">
            <el-option v-for="d in dictPriceUnit" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :controls="false" :precision="0" :min="0" placeholder="请输入数量" style="width:100%" />
        </el-form-item>
        <el-form-item label="交货时间" prop="deliveryTime">
          <el-input v-model="form.deliveryTime" placeholder="请输入交货时间" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="产品类型" prop="productType">
          <el-select v-model="form.productTypeArr" multiple collapse-tags filterable placeholder="请选择产品类型">
            <el-option v-for="d in dictProductType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签1" prop="tagsFirst">
          <el-select v-model="form.tagsFirstArr" multiple collapse-tags filterable placeholder="请选择标签1"><el-option v-for="d in dictTagsFirst" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
        </el-form-item>
        <el-form-item label="标签2" prop="tagsSecond">
          <el-select v-model="form.tagsSecondArr" multiple collapse-tags filterable placeholder="请选择标签2"><el-option v-for="d in dictTagsSecond" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
        </el-form-item>
        <el-form-item label="标签3" prop="tagsThird">
          <el-select v-model="form.tagsThirdArr" multiple collapse-tags filterable placeholder="请选择标签3"><el-option v-for="d in dictTagsThird" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
        </el-form-item>
        <el-form-item label="标签4" prop="tagsSi">
          <el-select v-model="form.tagsSiArr" multiple collapse-tags filterable placeholder="请选择标签4"><el-option v-for="d in dictTagsSi" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
        </el-form-item>
        <el-form-item label="类型" prop="inqOfferType">
          <el-select v-model="form.inqOfferType" placeholder="请选择类型">
            <el-option label="Inq" value="Inq" />
            <el-option label="Offer" value="Offer" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="导入Offer" :visible.sync="openImportDialog" width="700px" append-to-body>
      <el-form :model="importForm" label-width="120px">
        <el-form-item label="供应商" prop="supplier">
          <el-select v-model="importSupplier" filterable remote reserve-keyword placeholder="请选择供应商" :remote-method="remoteSupplier" value-key="supplierCode">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="inqOfferType">
          <el-select v-model="importForm.inqOfferType" placeholder="请选择类型">
            <el-option label="Inq" value="Inq" />
            <el-option label="Offer" value="Offer" />
          </el-select>
        </el-form-item>
        <el-form-item label="Excel文件">
          <el-upload ref="importUpload" :action="uploadAction" :http-request="doImport" :show-file-list="true" :limit="1" :auto-upload="false">
            <el-button size="small" type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
        <div class="form-header">列映射</div>
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="产品编码"><el-select v-model="colMap.productCode" placeholder="列"><el-option v-for="l in letters" :key="l" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品详情"><el-select v-model="colMap.productDetail" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'d'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成本"><el-select v-model="colMap.priceCost" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'c'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报价"><el-select v-model="colMap.priceOffer" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'o'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数量"><el-select v-model="colMap.quantity" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'q'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品品牌"><el-select v-model="colMap.productBrand" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'pb'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品类型"><el-select v-model="colMap.productType" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'pt'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="交货时间"><el-select v-model="colMap.deliveryTime" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'t'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="备注"><el-select v-model="colMap.remark" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'r'" :label="l" :value="l" /></el-select></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitImport">确 定</el-button>
        <el-button @click="openImportDialog=false">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="批量编辑" :visible.sync="openBatchDialog" width="700px" append-to-body>
      <el-form :model="batchForm" label-width="120px">
        <el-form-item label="产品品牌"><el-input v-model="batchForm.productBrand" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="库存日期"><el-date-picker v-model="batchForm.stockDate" type="date" placeholder="不填不更新" style="width: 100%" /></el-form-item>
        <el-form-item label="产品详情"><el-input v-model="batchForm.productDetail" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="成本"><el-input-number v-model="batchForm.priceCost" :controls="false" :precision="2" :min="0" placeholder="填0或不填不更新" style="width:100%" /></el-form-item>
        <el-form-item label="报价"><el-input-number v-model="batchForm.priceOffer" :controls="false" :precision="2" :min="0" placeholder="填0或不填不更新" style="width:100%" /></el-form-item>
        <el-form-item label="价格单位"><el-input v-model="batchForm.priceUnit" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="batchForm.quantity" :controls="false" :precision="0" :min="0" placeholder="填0或不填不更新" style="width:100%" /></el-form-item>
        <el-form-item label="交货时间"><el-input v-model="batchForm.deliveryTime" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="batchForm.remark" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="batchForm.inqOfferType" clearable placeholder="不填不更新"><el-option label="Inq" value="Inq" /><el-option label="Offer" value="Offer" /></el-select></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitBatch">确 定</el-button>
        <el-button @click="openBatchDialog=false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listOffer, getOffer, addOffer, updateOffer, importOffer, batchEditOffer, delOffer } from '@/api/crm/offer'
import { listSupplierOptions } from '@/api/crm/supplier'
import { getDicts } from '@/api/system/dict/data'

export default {
  name: 'CrmOffer',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      offerList: [],
      ids: [],
      single: true,
      multiple: true,
      title: '',
      open: false,
      openImportDialog: false,
      openBatchDialog: false,
      supplierOptions: [],
      supplierOptionsQuery: [],
      formSupplier: null,
      importSupplier: null,
      letters: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
      colMap: { productCode: 'A', productDetail: '', priceCost: '', priceOffer: '', quantity: '', deliveryTime: '', remark: '' },
      queryParams: { pageNum: 1, pageSize: 10, productCode: undefined, supplierCodes: [], productBrandArr: [], productTypeArr: [], inqOfferType: undefined, tagsFirst: undefined, tagsSecond: undefined, tagsThird: undefined, tagsSi: undefined, params: {} },
      moreTags: false,
      stockDateRange: [],
      form: { id: undefined, supplierCode: '', supplierName: '', productCode: '', productBrand: '', productBrandArr: [], stockDate: undefined, priceCost: undefined, priceOffer: undefined, priceUnit: '', priceUnitArr: [], quantity: undefined, deliveryTime: '', remark: '', productType: '', productTypeArr: [], tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], inqOfferType: 'Offer' },
      dictProductBrand: [], dictPriceUnit: [], dictProductType: [], dictTagsFirst: [], dictTagsSecond: [], dictTagsThird: [], dictTagsSi: [],
      importForm: { inqOfferType: 'Offer' },
      uploadAction: process.env.VUE_APP_BASE_API + '/crm/offer/import',
      batchForm: { productBrand: '', stockDate: undefined, productDetail: '', priceCost: 0, priceOffer: 0, priceUnit: '', quantity: 0, deliveryTime: '', remark: '', inqOfferType: '' }
    }
  },
  created() { this.getList(); this.loadDicts() },
  methods: {
    getList() { this.loading = true; this.queryParams.params.beginStockDate = this.stockDateRange && this.stockDateRange.length ? this.stockDateRange[0] : undefined; this.queryParams.params.endStockDate = this.stockDateRange && this.stockDateRange.length ? this.stockDateRange[1] : undefined; this.queryParams.params.supplierCodeList = this.queryParams.supplierCodes || []; this.queryParams.params.productBrandList = this.queryParams.productBrandArr || []; this.queryParams.params.productTypeList = this.queryParams.productTypeArr || []; listOffer(this.queryParams).then(res => { this.offerList = res.rows; this.total = res.total; this.loading = false }) },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.stockDateRange = []; this.queryParams.supplierCodes = []; this.queryParams.productBrandArr = []; this.queryParams.productTypeArr = []; this.resetForm('queryForm'); this.handleQuery() },
    handleSelectionChange(selection) { this.ids = selection.map(item => item.id); this.single = selection.length != 1; this.multiple = !selection.length },
    remoteSupplier(query) { listSupplierOptions({ supplierName: query, pageNum: 1, pageSize: 20 }).then(res => { this.supplierOptions = res.data }) },
    remoteSupplierQuery(query) { listSupplierOptions({ supplierName: query, pageNum: 1, pageSize: 20 }).then(res => { this.supplierOptionsQuery = res.data }) },
    onSupplierChange(val) { if (val) { this.form.supplierCode = val.supplierCode; this.form.supplierName = val.supplierName; this.formSupplier = val } },
    handleAdd() { this.resetFormData(); this.open = true; this.title = '新增Offer' },
    handleUpdate(row) { const id = row.id || this.ids[0]; getOffer(id).then(res => { this.form = res.data || {}; this.splitToArrays(); this.open = true; this.title = '修改Offer' }) },
    handleDelete(row) { const ids = row.id ? [row.id] : this.ids; this.$modal.confirm('是否确认删除选中数据项？').then(() => { return delOffer(ids) }).then(() => { this.getList(); this.$modal.msgSuccess('删除成功') }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '删除失败') }) },
    handleExport() { this.download('/crm/offer/export', { ids: this.ids }, `offer_${new Date().getTime()}.xlsx`) },
    openImport() { this.openImportDialog = true; this.remoteSupplier('') },
    submitImport() {
      if (!this.importSupplier || !this.importForm.inqOfferType) { this.$modal.msgError('请选择供应商和类型'); return }
      if (!this.colMap.productCode) { this.$modal.msgError('请选择产品编码列'); return }
      const up = this.$refs.importUpload
      if (!up || !up.uploadFiles || up.uploadFiles.length === 0) { this.$modal.msgError('请选择Excel文件'); return }
      up.submit()
    },
    doImport(param) {
      if (!this.importSupplier || !this.importForm.inqOfferType) { this.$modal.msgError('请选择供应商和类型'); return }
      if (!this.colMap.productCode) { this.$modal.msgError('请选择产品编码列'); return }
      const fd = new FormData()
      fd.append('file', param.file)
      fd.append('supplierCode', this.importSupplier.supplierCode || '')
      fd.append('supplierName', this.importSupplier.supplierName || '')
      fd.append('inqOfferType', this.importForm.inqOfferType)
      fd.append('colMapJson', JSON.stringify(this.colMap))
      importOffer(fd).then(res => {
        const d = res.data || {}
        const fails = d.failDetails || []
        const lines = fails.map(x => `第${x.row}行：${x.reason}`)
        const text = `${res.msg || ''}\n成功：${d.successCount || 0}，失败：${d.failCount || 0}${lines.length ? '\n失败详情：\n' + lines.join('\n') : ''}`
        this.$alert(text.replace(/\n/g,'<br/>'), '导入结果', { dangerouslyUseHTMLString: true })
        this.openImportDialog = false
        this.getList()
      }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '导入失败') })
    },
    openBatchEdit() { this.openBatchDialog = true },
    submitBatch() { batchEditOffer(this.ids, this.batchForm).then(() => { this.$modal.msgSuccess('批量编辑成功'); this.openBatchDialog = false; this.getList() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '批量编辑失败') }) },
    cancel() { this.open = false },
    resetFormData() { this.form = { id: undefined, supplierCode: '', supplierName: '', productCode: '', productBrand: '', productBrandArr: [], stockDate: new Date(), priceCost: undefined, priceOffer: undefined, priceUnit: '', priceUnitArr: [], quantity: undefined, deliveryTime: '', remark: '', productType: '', productTypeArr: [], tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], inqOfferType: 'Offer' } },
    submitForm() { if (!this.form.productCode) { this.$modal.msgError('产品编码不能为空'); return } this.joinFromArrays(); const data = Object.assign({}, this.form); (data.id ? updateOffer(data) : addOffer(data)).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '提交失败') }) }
    ,loadDicts() {
      getDicts('crm_product_brand').then(res => { this.dictProductBrand = res.data })
      getDicts('crm_price_unit').then(res => { this.dictPriceUnit = res.data })
      getDicts('crm_product_type').then(res => { this.dictProductType = res.data })
      getDicts('crm_tags_first').then(res => { this.dictTagsFirst = res.data })
      getDicts('crm_tags_second').then(res => { this.dictTagsSecond = res.data })
      getDicts('crm_tags_third').then(res => { this.dictTagsThird = res.data })
      getDicts('crm_tags_si').then(res => { this.dictTagsSi = res.data })
    }
    ,split(s) { return s ? s.split(',') : [] }
    ,splitToArrays() {
      this.form.productBrandArr = this.split(this.form.productBrand)
      this.form.priceUnitArr = this.split(this.form.priceUnit)
      this.form.productTypeArr = this.split(this.form.productType)
      this.form.tagsFirstArr = this.split(this.form.tagsFirst)
      this.form.tagsSecondArr = this.split(this.form.tagsSecond)
      this.form.tagsThirdArr = this.split(this.form.tagsThird)
      this.form.tagsSiArr = this.split(this.form.tagsSi)
    }
    ,joinFromArrays() {
      this.form.productBrand = (this.form.productBrandArr || []).join(',')
      this.form.priceUnit = (this.form.priceUnitArr || []).join(',')
      this.form.productType = (this.form.productTypeArr || []).join(',')
      this.form.tagsFirst = (this.form.tagsFirstArr || []).join(',')
      this.form.tagsSecond = (this.form.tagsSecondArr || []).join(',')
      this.form.tagsThird = (this.form.tagsThirdArr || []).join(',')
      this.form.tagsSi = (this.form.tagsSiArr || []).join(',')
    }
  }
}
</script>

<style scoped>
.form-header { font-size: 15px; color: #6379bb; border-bottom: 1px solid #ddd; margin: 8px 10px 25px 10px; padding-bottom: 5px }
</style>
