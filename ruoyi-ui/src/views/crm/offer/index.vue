<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="88px">
      <el-form-item label="产品编码" prop="productCode">
        <el-input v-model="queryParams.productCode" placeholder="请输入产品编码" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="来源表名" prop="sheetName">
        <el-input v-model="queryParams.sheetName" placeholder="请输入来源表名" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商">
        <el-select v-model="queryParams.supplierCodes" multiple collapse-tags filterable remote :remote-method="searchSupplier" :loading="supplierLoading" placeholder="请选择供应商编码" @focus="searchSupplier('')">
          <el-option v-for="item in supplierListOptions" :key="item.id" :label="item.supplierCode" :value="item.supplierCode">
            <span style="float: left">{{ item.supplierCode }}</span>
            <span style="float: right; color: #8492a6; font-size: 13px; margin-left: 10px">{{ item.supplierName }}</span>
          </el-option>
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
        <el-button type="primary" plain icon="el-icon-magic-stick" size="mini" @click="openAiEntry" v-hasPermi="['crm:offer:add']">AI录入</el-button>
        <el-button type="primary" plain icon="el-icon-aim" size="mini" @click="openAiQuery" v-hasPermi="['crm:offer:list']">AI查询</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-copy-document" size="mini" @click="openCopyOffer" v-hasPermi="['crm:offer:list']">复制Offer</el-button>
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
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="openBatchAdd" v-hasPermi="['crm:offer:add']">批量新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-s-promotion" size="mini" @click="handleSendOffer" v-hasPermi="['crm:offer:list']">发送Offer</el-button>
      </el-col>
      <el-col :span="1.8">
        <el-button type="info" plain icon="el-icon-view" size="mini" @click="openOfferProgress" v-hasPermi="['crm:offer:list']">查看Offer进度</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="offerList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="产品编码" align="center" prop="productCode" width="180" show-overflow-tooltip />
      <el-table-column label="供应商编号" align="center" prop="supplierCode" width="180" show-overflow-tooltip />
      <el-table-column label="成本" align="center" prop="priceCost" show-overflow-tooltip />
      <el-table-column label="报价" align="center" prop="priceOffer" show-overflow-tooltip />
      <el-table-column label="数量" align="center" prop="quantity" show-overflow-tooltip />
      <el-table-column label="库存日期" align="center" prop="stockDate" width="180" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.stockDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="品牌" align="center" prop="productBrand" show-overflow-tooltip />
      <el-table-column label="产品详情" align="center" prop="productDetail" min-width="220" show-overflow-tooltip />
      <el-table-column label="价格单位" align="center" prop="priceUnit" show-overflow-tooltip />
      <el-table-column label="产品类型" align="center" prop="productType" show-overflow-tooltip />
      <el-table-column label="DC" align="center" prop="dc" show-overflow-tooltip />
      <el-table-column label="类型" align="center" prop="inqOfferType" show-overflow-tooltip />
      <el-table-column label="标签1" align="center" prop="tagsFirst" show-overflow-tooltip />
      <el-table-column label="标签2" align="center" prop="tagsSecond" show-overflow-tooltip />
      <el-table-column label="标签3" align="center" prop="tagsThird" show-overflow-tooltip />
      <el-table-column label="标签4" align="center" prop="tagsSi" show-overflow-tooltip />
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="MOQ数量" align="center" prop="moqQuantity" show-overflow-tooltip />
      <el-table-column label="质保详情" align="center" prop="warrantyDetail" show-overflow-tooltip />
      <el-table-column label="来源表名" align="center" prop="sheetName" min-width="300" show-overflow-tooltip />
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
          <el-select v-model="formSupplier" filterable remote reserve-keyword placeholder="请选择供应商编码" :remote-method="remoteSupplier" value-key="supplierCode" @change="onSupplierChange">
            <el-option v-for="item in supplierOptions" :key="item.supplierCode" :label="item.supplierCode" :value="item">
              <span style="float: left">{{ item.supplierCode }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px; margin-left: 10px">{{ item.supplierName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="请输入产品编码" />
        </el-form-item>
        <el-form-item label="明细编号" prop="productDetailCode">
          <el-input v-model="form.productDetailCode" placeholder="请输入产品明细编号" />
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
        <el-form-item label="产品详情" prop="productDetail">
          <el-input type="textarea" :rows="3" v-model="form.productDetail" placeholder="请输入产品详情（规格型号等）" />
        </el-form-item>
        <el-form-item label="交货时间" prop="deliveryTime">
          <el-input v-model="form.deliveryTime" placeholder="请输入交货时间" />
        </el-form-item>
        <el-form-item label="MOQ数量" prop="moqQuantity">
          <el-input-number v-model="form.moqQuantity" :controls="false" :precision="0" :min="0" placeholder="请输入MOQ数量" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="来源表名" prop="sheetName">
          <el-input v-model="form.sheetName" placeholder="请输入来源表名" />
        </el-form-item>
        <el-form-item label="质保详情" prop="warrantyDetail">
          <el-input v-model="form.warrantyDetail" placeholder="请输入质保详情" />
        </el-form-item>
        <el-form-item label="DC" prop="dc">
          <el-input v-model="form.dc" placeholder="请输入DC" maxlength="32" />
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
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">有效</el-radio>
            <el-radio :label="0">无效</el-radio>
          </el-radio-group>
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
          <el-select v-model="importSupplier" filterable remote reserve-keyword placeholder="请选择供应商编码" :remote-method="remoteSupplier" value-key="supplierCode">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierCode + (item.supplierName ? ' - ' + item.supplierName : '')" :value="item">
              <span style="float: left">{{ item.supplierCode }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px; margin-left: 10px">{{ item.supplierName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="inqOfferType">
          <el-select v-model="importForm.inqOfferType" placeholder="请选择类型">
            <el-option label="Inq" value="Inq" />
            <el-option label="Offer" value="Offer" />
          </el-select>
        </el-form-item>
        <el-form-item label="利润比例(%)" prop="profitRatio">
          <el-input-number v-model="importForm.profitRatio" :controls="false" :precision="2" :min="0" placeholder="默认2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Excel文件">
          <el-upload ref="importUpload" :action="uploadAction" :http-request="doImport" :show-file-list="true" :limit="1" :auto-upload="false">
            <el-button size="small" type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
        <div class="form-header">列映射</div>
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="产品编码"><el-select v-model="colMap.productCode" placeholder="列"><el-option v-for="l in letters" :key="l" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品详情"><el-select v-model="colMap.productDetailArr" multiple collapse-tags clearable placeholder="列"><el-option v-for="l in letters" :key="l+'d'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成本"><el-select v-model="colMap.priceCost" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'c'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报价"><el-select v-model="colMap.priceOffer" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'o'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数量"><el-select v-model="colMap.quantity" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'q'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品品牌"><el-select v-model="colMap.productBrand" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'pb'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品类型"><el-select v-model="colMap.productType" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'pt'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="交货时间"><el-select v-model="colMap.deliveryTime" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'t'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="备注"><el-select v-model="colMap.remark" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'r'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="MOQ数量"><el-select v-model="colMap.moqQuantity" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'mq'" :label="l" :value="l" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="质保详情"><el-select v-model="colMap.warrantyDetail" clearable placeholder="列"><el-option v-for="l in letters" :key="l+'wd'" :label="l" :value="l" /></el-select></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitImport">确 定</el-button>
        <el-button @click="openImportDialog=false">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="AI智能录入" :visible.sync="openAiEntryDialog" width="700px" append-to-body>
      <el-form :model="aiEntryForm" label-width="120px">
        <el-form-item label="供应商">
          <el-select v-model="aiSupplier" filterable remote reserve-keyword placeholder="请输入供应商编码、名称或别名搜索" :remote-method="remoteSupplier" value-key="supplierCode" style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierCode + (item.supplierName ? ' - ' + item.supplierName : '')" :value="item">
              <span style="float: left">{{ item.supplierCode }}</span>
              <span v-if="item.supplierName" style="float: right; color: #8492a6; font-size: 13px">{{ item.supplierName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="aiEntryForm.inqOfferType">
            <el-radio label="Offer">Offer</el-radio>
            <el-radio label="Inq">Inq</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="利润比例(%)">
          <el-input-number v-model="aiEntryForm.profitRatio" :controls="false" :precision="0" :min="1" :max="100" placeholder="1-100" style="width: 100%" />
          <div style="color: #909399; font-size: 12px; line-height: 1.5">类型为Offer时必填（默认2%，可修改），报价价 = 供应商价格 × (1 + 利润比例/100)</div>
        </el-form-item>
        <el-form-item label="物料内容">
          <el-input type="textarea" v-model="aiEntryForm.content" :rows="10" placeholder="粘贴供应商的物料信息（品牌、料号、型号、规格、数量、报价等），AI将自动整理入库" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="aiEntryLoading" @click="submitAiEntry">确 定</el-button>
        <el-button @click="openAiEntryDialog=false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- AI料号查询：粘贴内容，AI提取完整料号后查询最近1个月INQ/OFFER历史，按料号分组展示（组内按Offer日期倒排），可一键复制 -->
    <el-dialog title="AI料号查询" :visible.sync="openAiQueryDialog" width="90%" top="5vh" append-to-body>
      <!-- 复制工具栏（与AI录入比价共用）：最近1/2/3天取每料号最低Offer价格，默认输出料号/数量/Offer价格，可勾选追加字段 -->
      <div class="copy-toolbar">
        <el-radio-group v-model="copyRecentDays" size="mini">
          <el-radio-button :label="1">最近1天</el-radio-button>
          <el-radio-button :label="2">最近2天</el-radio-button>
          <el-radio-button :label="3">最近3天</el-radio-button>
        </el-radio-group>
        <el-checkbox-group v-model="copyExtraFields" size="mini" class="copy-extra-fields">
          <el-checkbox label="supplierCode">供应商编号</el-checkbox>
          <el-checkbox label="productDetail">详情</el-checkbox>
          <el-checkbox label="deliveryTime">交期</el-checkbox>
          <el-checkbox label="dc">DC</el-checkbox>
        </el-checkbox-group>
        <el-button size="mini" type="primary" icon="el-icon-document-copy" :loading="aiCopyLoading" :disabled="aiQueryGroups.length === 0" @click="submitCopyOffersOfQuery">一键复制Offer</el-button>
        <el-button size="mini" icon="el-icon-document-copy" v-clipboard="aiQueryCopyText" v-clipboard:success="onCopyOk" :disabled="aiQueryGroups.length === 0">一键复制全部信息</el-button>
      </div>
      <el-input type="textarea" v-model="aiQueryContent" :rows="5" placeholder="粘贴物料信息，AI将提取完整料号并查询最近1个月的INQ/OFFER历史" style="margin-bottom: 10px" />
      <div style="text-align: center; margin-bottom: 12px">
        <el-button type="primary" :loading="aiQueryLoading" @click="submitAiQuery">查 询</el-button>
      </div>
      <div v-if="aiQueryGroups.length === 0" style="color:#909399; text-align:center; padding: 30px 0">{{ aiQueryLoading ? 'AI解析中，请耐心等待...' : '暂无查询结果，粘贴物料内容后点击查询' }}</div>
      <div v-for="(group, gi) in aiQueryGroups" :key="gi" class="ai-query-group">
        <div class="ai-query-part-number">{{ group.partNumber }}（{{ (group.offers || []).length }}条记录）</div>
        <div v-if="!group.offers || group.offers.length === 0" class="ai-query-empty">最近1个月无INQ/OFFER记录</div>
        <!-- row-style：组内最近3天Offer最低价行淡蓝色底，其余白底 -->
        <el-table v-else :data="group.offers" size="mini" border :row-style="groupRowStyle">
          <el-table-column label="供应商编号" prop="supplierName" min-width="140" show-overflow-tooltip />
          <el-table-column label="INQ/OFFER" prop="inqOfferType" align="center" width="110" />
          <el-table-column label="数量" prop="quantity" align="center" width="80" />
          <el-table-column label="OFFER价格" prop="priceOffer" align="center" width="100" />
          <el-table-column label="Offer日期" prop="offerDate" align="center" width="145" />
          <el-table-column label="交期" prop="deliveryTime" align="center" min-width="100" show-overflow-tooltip />
          <el-table-column label="详情" prop="productDetail" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      <!-- 自动复制失败时的兜底内容：完整文本展示在此，供手动全选复制，保证数据不丢失 -->
      <div v-if="aiQueryFallbackText" style="margin-top: 10px">
        <div style="color:#909399; font-size: 12px; margin-bottom: 4px">浏览器自动复制失败，请手动全选下方文本复制：</div>
        <el-input type="textarea" :rows="8" readonly v-model="aiQueryFallbackText" />
      </div>
    </el-dialog>

    <!-- AI录入比价：本次AI录入的料号与系统内近1个月同料号报价对比（按料号分组，组内按Offer日期倒排，最近3天最低价为淡蓝色底），右上角可选天数/字段一键复制 -->
    <el-dialog :visible.sync="openAiCompareDialog" width="92%" top="5vh" append-to-body>
      <div slot="title">AI录入比价（近1个月同料号报价，组内按Offer日期倒排）</div>
      <!-- 复制工具栏（与AI查询共用）：最近1/2/3天取每料号最低Offer价格，默认输出料号/数量/Offer价格，可勾选追加字段 -->
      <div class="copy-toolbar">
        <el-radio-group v-model="copyRecentDays" size="mini">
          <el-radio-button :label="1">最近1天</el-radio-button>
          <el-radio-button :label="2">最近2天</el-radio-button>
          <el-radio-button :label="3">最近3天</el-radio-button>
        </el-radio-group>
        <el-checkbox-group v-model="copyExtraFields" size="mini" class="copy-extra-fields">
          <el-checkbox label="supplierCode">供应商编号</el-checkbox>
          <el-checkbox label="productDetail">详情</el-checkbox>
          <el-checkbox label="deliveryTime">交期</el-checkbox>
          <el-checkbox label="dc">DC</el-checkbox>
        </el-checkbox-group>
        <el-button size="mini" type="primary" icon="el-icon-document-copy" :loading="aiCopyLoading" :disabled="aiCompareGroups.length === 0" @click="submitCopyOffersOfCompare">一键复制Offer</el-button>
      </div>
      <div v-if="aiCompareGroups.length === 0" style="color:#909399; text-align:center; padding: 30px 0">近1个月内没有相同物料的报价记录</div>
      <div v-for="(group, gi) in aiCompareGroups" :key="gi" class="ai-query-group">
        <div class="ai-query-part-number">{{ group.partNumber }}（{{ (group.offers || []).length }}条记录）</div>
        <el-table :data="group.offers" size="mini" border :row-style="groupRowStyle">
          <el-table-column label="供应商编号" prop="supplierCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="成本价格" prop="priceCost" align="center" width="100" />
          <el-table-column label="Offer价格" prop="priceOffer" align="center" width="100" />
          <el-table-column label="Offer日期" prop="offerDate" align="center" width="120" />
          <el-table-column label="INQ/OFFER" prop="inqOfferType" align="center" width="110" />
          <el-table-column label="数量" prop="quantity" align="center" width="80" />
          <el-table-column label="交期" prop="deliveryTime" align="center" min-width="110" show-overflow-tooltip />
          <el-table-column label="DC" prop="dc" align="center" width="90" />
          <el-table-column label="详情" prop="productDetail" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      <!-- 自动复制失败时的兜底内容：完整文本展示在此，供手动全选复制，保证数据不丢失 -->
      <div v-if="aiCompareFallbackText" style="margin-top: 10px">
        <div style="color:#909399; font-size: 12px; margin-bottom: 4px">浏览器自动复制失败，请手动全选下方文本复制：</div>
        <el-input type="textarea" :rows="8" readonly v-model="aiCompareFallbackText" />
      </div>
    </el-dialog>

    <!-- 复制Offer：填N天=取N-1天前0点至当前时间（1=今天0点至当前，2=昨天0点至当前），仅Offer记录，按品牌排序、相同料号取成本最低（无价格也保留），制表符分隔复制到剪贴板 -->
    <el-dialog title="复制Offer" :visible.sync="openCopyOfferDialog" width="480px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="最近天数">
          <el-input-number v-model="copyOfferDays" :min="1" :max="365" :precision="0" controls-position="right" style="width: 160px" />
          <span style="margin-left: 8px; color: #909399">天</span>
        </el-form-item>
        <el-form-item label="说明">
          <div style="color: #909399; font-size: 12px; line-height: 1.7">
            按库存日期查询范围：填1=今天0点至当前时间，填2=昨天0点至当前时间，填N=从N-1天前0点至当前时间，取全部Offer记录：
            按品牌排序，相同料号取成本最低（无价格记录也保留），
            以"料号、数量、品牌、DC、交期、详情、报价"制表符分隔复制到剪贴板，首行为英文表头（不包含成本，内容用于发给客户）。
          </div>
        </el-form-item>
        <!-- 自动复制失败时的兜底内容：完整文本展示在此，供手动全选复制，保证接口数据不丢失 -->
        <el-form-item v-if="copyOfferFallbackText" label="复制内容">
          <el-input type="textarea" :rows="10" readonly v-model="copyOfferFallbackText" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="copyOfferLoading" @click="submitCopyOffer">复 制</el-button>
        <el-button @click="openCopyOfferDialog=false">取 消</el-button>
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
        <el-form-item label="MOQ数量"><el-input-number v-model="batchForm.moqQuantity" :controls="false" :precision="0" :min="0" placeholder="填0或不填不更新" style="width:100%" /></el-form-item>
        <el-form-item label="质保详情"><el-input v-model="batchForm.warrantyDetail" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="batchForm.productType" clearable placeholder="不填不更新">
            <el-option v-for="d in dictProductType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="batchForm.remark" placeholder="不填不更新" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="batchForm.inqOfferType" clearable placeholder="不填不更新"><el-option label="Inq" value="Inq" /><el-option label="Offer" value="Offer" /></el-select></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitBatch">确 定</el-button>
        <el-button @click="openBatchDialog=false">取 消</el-button>
      </div>
    </el-dialog>
    <el-dialog title="批量新增" :visible.sync="openBatchAddDialog" width="900px" append-to-body>
      <el-form :model="{}" label-width="120px">
        <el-form-item label="供应商">
          <el-select v-model="batchAddSupplier" filterable remote reserve-keyword placeholder="请选择供应商编码" :remote-method="remoteSupplier" value-key="supplierCode">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierCode" :value="item">
              <span style="float: left">{{ item.supplierCode }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px; margin-left: 10px">{{ item.supplierName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="batchAddInqOfferType" placeholder="请选择类型">
            <el-option label="Inq" value="Inq" />
            <el-option label="Offer" value="Offer" />
          </el-select>
        </el-form-item>
        <div class="form-header">询报价解析表达式</div>
        <!-- 移除模板选择 -->
        <el-row :gutter="10">
          <el-col :span="12">
            <div>字段：</div>
            <div>
              <el-tag v-for="d in dictInqFields" :key="d.dictValue" style="margin:4px;cursor:pointer" @click="addFieldToken(d.dictValue)">{{ d.dictLabel }}</el-tag>
            </div>
          </el-col>
          <el-col :span="12">
            <div>分隔符：</div>
            <div>
              <el-tag v-for="d in dictSeps" :key="d.dictValue" type="success" style="margin:4px;cursor:pointer" @click="addSepToken(d.dictValue)">{{ d.dictLabel }}</el-tag>
            </div>
          </el-col>
        </el-row>
        <el-form-item label="表达式">
          <div>
            <el-tag v-for="(t,i) in formatSequence" :key="i" closable @close="removeToken(i)" style="margin:4px">{{ t.value }}</el-tag>
            <el-button size="mini" @click="clearTokens">清空</el-button>
          </div>
        </el-form-item>
        <el-form-item label="利润比例(%)">
          <el-input-number v-model="batchProfitRatio" :controls="false" :precision="2" :min="0" placeholder="默认2" style="width: 100%" />
        </el-form-item>
        <div class="form-header">粘贴文本</div>
        <el-form-item label="文本">
          <el-input type="textarea" v-model="pasteText" :rows="8" placeholder="粘贴询报价文本" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="parsePasted">解析</el-button>
          <el-button @click="pasteText=''; parsedRows=[]">清空</el-button>
        </el-form-item>
        <el-table :data="parsedRows" height="260" border style="width:100%">
          <el-table-column type="index" width="60" label="#" />
          <el-table-column label="产品编码" prop="productCode" width="140"><template slot-scope="scope"><el-input v-model="scope.row.productCode" size="mini" /></template></el-table-column>
          <el-table-column label="数量" prop="quantity" width="100"><template slot-scope="scope"><el-input-number v-model="scope.row.quantity" :controls="false" :precision="0" :min="0" size="mini" style="width: 100%" /></template></el-table-column>
          <el-table-column label="成本" prop="priceCost" width="100"><template slot-scope="scope"><el-input-number v-model="scope.row.priceCost" :controls="false" :precision="2" :min="0" size="mini" style="width: 100%" /></template></el-table-column>
          <el-table-column label="单价" prop="priceOffer" width="100"><template slot-scope="scope"><el-input-number v-model="scope.row.priceOffer" :controls="false" :precision="2" :min="0" size="mini" style="width: 100%" /></template></el-table-column>
          <el-table-column label="单位" prop="priceUnit" width="80"><template slot-scope="scope"><el-input v-model="scope.row.priceUnit" size="mini" /></template></el-table-column>
          <el-table-column label="交货时间" prop="deliveryTime" width="120"><template slot-scope="scope"><el-input v-model="scope.row.deliveryTime" size="mini" /></template></el-table-column>
          <el-table-column label="产品详情" prop="productDetail" min-width="150"><template slot-scope="scope"><el-input v-model="scope.row.productDetail" size="mini" /></template></el-table-column>
          <el-table-column label="品牌" prop="productBrand" width="100"><template slot-scope="scope"><el-input v-model="scope.row.productBrand" size="mini" /></template></el-table-column>
          <el-table-column label="MOQ" prop="moqQuantity" width="80"><template slot-scope="scope"><el-input-number v-model="scope.row.moqQuantity" :controls="false" :precision="0" :min="0" size="mini" style="width: 100%" /></template></el-table-column>
          <el-table-column label="质保" prop="warrantyDetail" width="100"><template slot-scope="scope"><el-input v-model="scope.row.warrantyDetail" size="mini" /></template></el-table-column>
          <el-table-column label="DC" prop="dc" width="80"><template slot-scope="scope"><el-input v-model="scope.row.dc" maxlength="32" size="mini" /></template></el-table-column>
          <el-table-column label="产品类型" prop="productType" width="100"><template slot-scope="scope"><el-input v-model="scope.row.productType" size="mini" /></template></el-table-column>
          <el-table-column label="备注" prop="remark" min-width="120"><template slot-scope="scope"><el-input v-model="scope.row.remark" size="mini" /></template></el-table-column>
        </el-table>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="confirmBatchAdd">确 定</el-button>
        <el-button @click="openBatchAddDialog=false">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="选择导出字段" :visible.sync="exportDialogVisible" width="600px" append-to-body>
      <div>
        <el-checkbox-group v-model="selectedExportFields">
          <el-checkbox v-for="f in exportFieldsDict" :key="f.value" :label="f.value">{{ f.label }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="exportDialogVisible=false">取 消</el-button>
        <el-button type="primary" @click="confirmExport">确 定</el-button>
      </div>
    </el-dialog>

    <el-dialog title="发送Offer" :visible.sync="openSendOfferDialog" width="560px" append-to-body>
      <el-form label-width="130px">
        <el-form-item label="发送模式">
          <el-radio-group v-model="sendOfferForm.testSend">
            <el-radio :label="true">测试发送（发到字典配置的测试邮箱）</el-radio>
            <el-radio :label="false">正式发送（发到订阅邮箱/供应商邮箱）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否带价格">
          <el-radio-group v-model="sendOfferForm.withPrice">
            <el-radio :label="false">不带价格</el-radio>
            <el-radio :label="true">带价格（报价随邮件下发）</el-radio>
          </el-radio-group>
        </el-form-item>
        <div style="color:#909399;font-size:12px;padding-left:130px;line-height:1.6">
          发送范围：最近录入且符合当前筛选条件的Offer；若勾选了表格行则只发送勾选行。发送后可在"查看Offer进度"确认结果
        </div>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="sendOfferLoading" @click="submitSendOffer">发 送</el-button>
        <el-button @click="openSendOfferDialog=false">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="Offer进度" :visible.sync="offerProgressDialogVisible" width="900px" append-to-body>
      <div style="margin-bottom:10px; display:flex; justify-content:space-between; align-items:center;">
        <div>成功：{{ progressSuccessCount }}，失败：{{ progressFailCount }}</div>
        <div>
          <el-button size="mini" type="primary" @click="refreshOfferProgress">查询</el-button>
          <el-button size="mini" type="primary" v-clipboard="copyAllText" v-clipboard:success="onCopyOk">复制全部</el-button>
        </div>
      </div>
      <el-table :data="emailResultList" height="420" border style="width:100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="batchNo" label="批次号" width="180" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" width="220" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="120" />
        <el-table-column prop="msg" label="失败原因" min-width="240" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="180">
          <template slot-scope="scope"><span>{{ parseTime(scope.row.updateTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template slot-scope="scope">
            <el-button type="text" size="mini" v-clipboard="getRowCopyText(scope.row)" v-clipboard:success="onCopyOk">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer" class="dialog-footer">
        <el-button @click="offerProgressDialogVisible=false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listOffer, getOffer, addOffer, updateOffer, importOffer, batchEditOffer, delOffer, parseOffer, sendOffer, listEmailResults, aiEntry, aiQuery, copyOfferText, copyAiQueryOffers } from '@/api/crm/offer'
import { listSupplierOptions, listSupplier, listSupplierSimple } from '@/api/crm/supplier'
import { getDicts } from '@/api/system/dict/data'
import { parseTime } from "@/utils/ruoyi"
import { mapGetters } from 'vuex'

export default {
  name: 'CrmOffer',
  computed: {
    ...mapGetters(['name'])
  },
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
      openAiEntryDialog: false,
      aiEntryLoading: false,
      aiSupplier: null,
      aiEntryForm: { inqOfferType: 'Offer', profitRatio: 2, content: '' },
      // AI料号查询弹窗状态：内容、加载中、分组结果、待复制文本、复制失败时的兜底文本
      openAiQueryDialog: false,
      aiQueryLoading: false,
      aiQueryContent: '',
      aiQueryGroups: [],
      aiQueryCopyText: '',
      aiQueryFallbackText: '',
      // AI录入比价弹窗状态：比价分组（近1个月同料号，按料号分组、组内按Offer日期倒排）、复制失败兜底文本
      openAiCompareDialog: false,
      aiCompareGroups: [],
      aiCompareFallbackText: '',
      // 复制工具栏状态（AI录入比价/AI查询共用）：最近天数（1=当天/2=最近两天/3=最近三天，默认1天）、勾选的额外字段（默认都不勾选）、加载中
      copyRecentDays: 1,
      copyExtraFields: [],
      aiCopyLoading: false,
      // 复制Offer弹窗状态：最近天数、加载中、自动复制失败时的兜底文本（展示供手动复制）
      openCopyOfferDialog: false,
      copyOfferDays: 7,
      copyOfferLoading: false,
      copyOfferFallbackText: '',
      openBatchDialog: false,
      supplierLoading: false,
      supplierOptions: [],
      supplierListOptions: [],
      supplierOptionsQuery: [],
      formSupplier: null,
      importSupplier: null,
      letters: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
      colMap: { productCode: 'A', productDetail: '', productDetailArr: [], priceCost: '', priceOffer: '', quantity: '', deliveryTime: '', remark: '', moqQuantity: '', warrantyDetail: '', dc: '' },
      queryParams: { pageNum: 1, pageSize: 10, productCode: undefined, supplierCodes: [], productBrandArr: [], productTypeArr: [], inqOfferType: undefined, tagsFirst: undefined, tagsSecond: undefined, tagsThird: undefined, tagsSi: undefined, params: {} },
      moreTags: false,
      stockDateRange: [],
      form: { id: undefined, supplierCode: '', supplierName: '', productCode: '', productDetailCode: '', productBrand: '', productBrandArr: [], stockDate: undefined, priceCost: undefined, priceOffer: undefined, priceUnit: '', priceUnitArr: [], quantity: undefined, productDetail: '', deliveryTime: '', remark: '', sheetName: '', warrantyDetail: '', moqQuantity: undefined, dc: '', productType: '', productTypeArr: [], tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], inqOfferType: 'Offer', status: 1 },
      dictProductBrand: [], dictPriceUnit: [], dictProductType: [], dictTagsFirst: [], dictTagsSecond: [], dictTagsThird: [], dictTagsSi: [],
      importForm: { inqOfferType: 'Offer', profitRatio: 2 },
      uploadAction: process.env.VUE_APP_BASE_API + '/crm/offer/import',
      batchForm: { productBrand: '', stockDate: undefined, productDetail: '', priceCost: undefined, priceOffer: undefined, priceUnit: '', quantity: undefined, deliveryTime: '', moqQuantity: undefined, warrantyDetail: '', productType: '', remark: '', inqOfferType: '' },
      tmpField: '',
      tmpSep: '',
      openBatchAddDialog: false,
      batchAddSupplier: null,
      batchAddInqOfferType: 'Offer',
      batchProfitRatio: 2,
      batchProfitRatio: 2,
      dictFormatTemplates: [],
      dictInqFields: [],
      dictSeps: [],
      selectedTemplate: '',
      formatSequence: [],
      pasteText: '',
      parsedRows: []
      ,exportDialogVisible: false
      ,exportFieldsDict: []
      ,selectedExportFields: []
      ,offerProgressDialogVisible: false
      ,emailResultList: []
      ,progressSuccessCount: 0
      ,progressFailCount: 0
      ,copyAllText: ''
      // 发送Offer弹窗状态：发送模式（true=测试/false=正式，默认测试）、是否带价格（默认不带）
      ,openSendOfferDialog: false
      ,sendOfferLoading: false
      ,sendOfferForm: { testSend: true, withPrice: false }
    }
  },
  created() { this.getList(); this.loadDicts() },
  methods: {
    getQueryParams() {
      const qp = JSON.parse(JSON.stringify(this.queryParams));
      qp.params = qp.params || {};

      qp.params.beginStockDate = this.stockDateRange && this.stockDateRange.length ? parseTime(this.stockDateRange[0], '{y}-{m}-{d}') : undefined;
      qp.params.endStockDate = this.stockDateRange && this.stockDateRange.length ? parseTime(this.stockDateRange[1], '{y}-{m}-{d}') : undefined;

      if (this.queryParams.supplierCodes && this.queryParams.supplierCodes.length > 0) {
        qp.params.supplierCodeList = this.queryParams.supplierCodes.join(',');
      }
      if (this.queryParams.productBrandArr && this.queryParams.productBrandArr.length > 0) {
        qp.params.productBrandList = this.queryParams.productBrandArr.join(',');
      }
      if (this.queryParams.productTypeArr && this.queryParams.productTypeArr.length > 0) {
        qp.params.productTypeList = this.queryParams.productTypeArr.join(',');
      }

      if (this.queryParams.productCode) {
        const codes = this.queryParams.productCode.split(/[\n, ]+/).map(s => s.trim()).filter(s => s.length > 0);
        if (codes.length > 0) {
          qp.params.productCodeList = codes.join(',');
          qp.productCode = undefined;
        }
      }
      return qp;
    },
    /** 查询Offer列表 */
    getList() {
      this.loading = true;
      const qp = JSON.parse(JSON.stringify(this.queryParams));
      qp.params = qp.params || {};

      qp.params.beginStockDate = this.stockDateRange && this.stockDateRange.length ? parseTime(this.stockDateRange[0], '{y}-{m}-{d}') : undefined;
      qp.params.endStockDate = this.stockDateRange && this.stockDateRange.length ? parseTime(this.stockDateRange[1], '{y}-{m}-{d}') : undefined;

      if (this.queryParams.supplierCodes && this.queryParams.supplierCodes.length > 0) {
        qp.params["supplierCodeList"] = this.queryParams.supplierCodes.join(",");
      }
      if (this.queryParams.productBrandArr && this.queryParams.productBrandArr.length > 0) {
        qp.params["productBrandList"] = this.queryParams.productBrandArr.join(",");
      }
      if (this.queryParams.productTypeArr && this.queryParams.productTypeArr.length > 0) {
        qp.params["productTypeList"] = this.queryParams.productTypeArr.join(",");
      }

      if (this.queryParams.productCode) {
        const codes = this.queryParams.productCode.split(/[\n, ]+/).map(s => s.trim()).filter(s => s.length > 0);
        if (codes.length > 0) {
          qp.params["productCodeList"] = codes.join(",");
          qp.productCode = undefined;
        }
      }

      listOffer(qp).then(response => {
        this.offerList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() { this.stockDateRange = []; this.queryParams.supplierCodes = []; this.queryParams.productBrandArr = []; this.queryParams.productTypeArr = []; this.resetForm('queryForm'); this.handleQuery() },
    handleSelectionChange(selection) { this.ids = selection.map(item => item.id); this.single = selection.length != 1; this.multiple = !selection.length },
    remoteSupplier(query) { listSupplierOptions({ supplierName: query, pageNum: 1, pageSize: 20 }).then(res => { this.supplierOptions = res.data }) },
    remoteSupplierQuery(query) { listSupplierOptions({ supplierName: query, pageNum: 1, pageSize: 20 }).then(res => { this.supplierOptionsQuery = res.data }) },
    onSupplierChange(val) { if (val) { this.form.supplierCode = val.supplierCode; this.form.supplierName = val.supplierName; this.formSupplier = val } },
    handleAdd() { this.resetFormData(); this.formSupplier = null; this.open = true; this.title = '新增Offer' },
    /** 编辑Offer：回显全部字段；供应商下拉直接显示编码（库里只存编码，不再查名称展示） */
    handleUpdate(row) {
      const id = row.id || this.ids[0];
      getOffer(id).then(res => {
        this.form = res.data || {};
        this.splitToArrays();
        this.formSupplier = null;
        if (this.form.supplierCode) {
          this.formSupplier = { id: null, supplierCode: this.form.supplierCode, supplierName: this.form.supplierCode, supplierAlias: '' };
          if (!this.supplierOptions.some(o => o.supplierCode === this.form.supplierCode)) {
            this.supplierOptions.unshift(this.formSupplier);
          }
        }
        this.open = true;
        this.title = '修改Offer';
      });
    },
    handleDelete(row) { const ids = row.id ? [row.id] : this.ids; this.$modal.confirm('是否确认删除选中数据项？').then(() => { return delOffer(ids) }).then(() => { this.getList(); this.$modal.msgSuccess('删除成功') }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '删除失败') }) },
    handleExport() {
      getDicts('offer_export_dict').then(res => {
        this.exportFieldsDict = (res.data || []).map(d => ({ label: d.dictLabel, value: d.dictValue }))
        this.selectedExportFields = this.exportFieldsDict.map(x => x.value)
        this.exportDialogVisible = true
      })
    },
    confirmExport() {
      const params = this.getQueryParams();
      if (this.ids && this.ids.length > 0) {
        params.ids = this.ids.join(',');
      }
      if (this.selectedExportFields && this.selectedExportFields.length > 0) {
        params.exportFields = this.selectedExportFields.join(',')
      }
      this.download('/crm/offer/export', params, `offer_${new Date().getTime()}.xlsx`)
      this.exportDialogVisible = false
    },
    /** 打开发送Offer弹窗：测试/正式、是否带价格均为单选（默认测试、不带价格，避免误发正式邮件） */
    handleSendOffer() {
      this.sendOfferForm = { testSend: true, withPrice: false };
      this.openSendOfferDialog = true;
    },
    /** 提交发送Offer：按当前筛选条件（勾选行优先）组装参数，连同发送选项一起调后端发送邮件 */
    submitSendOffer() {
      const params = this.getQueryParams();
      if (this.ids && this.ids.length > 0) {
        params.params = params.params || {};
        params.params.ids = this.ids.join(',');
      }
      params.params = params.params || {};
      params.params.testSend = this.sendOfferForm.testSend;
      params.params.withPrice = this.sendOfferForm.withPrice;
      this.sendOfferLoading = true;
      const modeText = this.sendOfferForm.testSend ? '测试' : '正式';
      sendOffer(params).then(res => {
        this.$modal.msgSuccess('发送成功（' + modeText + '发送），可到"查看Offer进度"确认结果');
        this.openSendOfferDialog = false;
      }).catch(err => {
        this.$modal.msgError(err && err.msg ? err.msg : '发送失败');
      }).finally(() => {
        this.sendOfferLoading = false;
      })
    },
    openImport() { this.openImportDialog = true; this.remoteSupplier('') },
    /** 打开AI智能录入弹窗（类型默认Offer，可切换Inq） */
    /** 打开AI智能录入弹窗：类型默认Offer，利润比例默认2%（可修改），供应商与内容每次重新选择 */
    openAiEntry() { this.aiSupplier = null; this.aiEntryForm = { inqOfferType: 'Offer', profitRatio: 2, content: '' }; this.openAiEntryDialog = true; this.remoteSupplier('') },
    /** 提交AI智能录入：粘贴内容交给后端DeepSeek整理后批量入库 */
    submitAiEntry() {
      if (!this.aiSupplier) { this.$modal.msgError('请选择供应商'); return }
      if (!this.aiEntryForm.inqOfferType) { this.$modal.msgError('请选择类型'); return }
      if (!this.aiEntryForm.content || !this.aiEntryForm.content.trim()) { this.$modal.msgError('请粘贴物料内容'); return }
      const ratio = this.aiEntryForm.profitRatio;
      if (this.aiEntryForm.inqOfferType === 'Offer' && (ratio == null || ratio < 1 || ratio > 100)) { this.$modal.msgError('类型为Offer时请输入1-100的利润比例'); return }
      this.aiEntryLoading = true;
      aiEntry({
        supplierCode: this.aiSupplier.supplierCode || '',
        supplierName: this.aiSupplier.supplierName || '',
        inqOfferType: this.aiEntryForm.inqOfferType,
        profitRatio: ratio,
        content: this.aiEntryForm.content
      }).then(res => {
        this.$modal.msgSuccess(res.msg || 'AI录入成功');
        this.openAiEntryDialog = false;
        this.getList();
        // 录入成功后展示近1个月同料号比价弹窗（无数据时只警告提示，不弹空窗）
        const d = res.data || {};
        const compare = d.compare || [];
        if (d.count > 0 && compare.length) {
          // 按料号分组（后端已按Offer日期倒序返回，组内即为日期倒排），并计算最近3天最低价行的淡蓝底色
          this.aiCompareGroups = this.decorateOfferGroups(this.groupCompareRows(compare));
          this.aiCompareFallbackText = '';
          this.openAiCompareDialog = true;
        } else if (d.count > 0) {
          this.$modal.msgWarning('本次录入的料号近1个月内没有相同物料的报价记录');
        }
      }).catch(err => {
        // AI录入为长耗时请求（最长8分钟，与后端sys.ai.timeout.ms一致），错误提示在此分类给出：超时/连接失败/业务错误
        const e = err || {};
        const msg = String(e.message || '');
        if (e.code === 'ECONNABORTED' || msg.includes('timeout')) {
          this.$modal.msgError('AI解析请求超时（已等待8分钟），DeepSeek接口响应较慢，请稍后重试；内容较多时可分多次录入');
        } else if (msg.includes('Network Error')) {
          this.$modal.msgError('无法连接后端接口，请确认后端服务（8081端口）已正常启动');
        } else {
          this.$modal.msgError(e.msg || msg || 'AI录入失败');
        }
      })
      .finally(() => { this.aiEntryLoading = false })
    },
    /** 打开AI料号查询弹窗 */
    openAiQuery() { this.aiQueryContent = ''; this.aiQueryGroups = []; this.aiQueryCopyText = ''; this.aiQueryFallbackText = ''; this.openAiQueryDialog = true },
    /** 提交AI料号查询：AI提取完整料号后查询最近1个月INQ/OFFER历史记录（组内按Offer日期倒排） */
    submitAiQuery() {
      if (!this.aiQueryContent || !this.aiQueryContent.trim()) { this.$modal.msgError('请粘贴物料内容'); return }
      this.aiQueryLoading = true;
      aiQuery({ content: this.aiQueryContent }).then(res => {
        // 预计算组内最近3天最低价行的淡蓝底色并写入每行 bgColor
        this.aiQueryGroups = this.decorateOfferGroups(res.data || []);
        this.aiQueryCopyText = this.buildAiQueryCopyText();
        this.aiQueryFallbackText = '';
        if (!this.aiQueryGroups.length) this.$modal.msgWarning('查询完成，近1个月内无匹配的INQ/OFFER记录');
        else this.$modal.msgSuccess('查询完成，共匹配' + this.aiQueryGroups.length + '个料号');
      }).catch(err => {
        // 与AI录入相同的错误分类提示：超时/连接失败/业务错误
        const e = err || {};
        const msg = String(e.message || '');
        if (e.code === 'ECONNABORTED' || msg.includes('timeout')) {
          this.$modal.msgError('AI解析请求超时（已等待8分钟），DeepSeek接口响应较慢，请稍后重试');
        } else if (msg.includes('Network Error')) {
          this.$modal.msgError('无法连接后端接口，请确认后端服务（8081端口）已正常启动');
        } else {
          this.$modal.msgError(e.msg || msg || 'AI查询失败');
        }
      })
      .finally(() => { this.aiQueryLoading = false })
    },
    /** AI录入比价/AI查询表格行样式：读取预处理写入的 bgColor（组内最近3天最低价为淡蓝底，其余白底），无颜色时返回空对象 */
    groupRowStyle({ row }) {
      return row && row.bgColor ? { background: row.bgColor } : {}
    },
    /** 为比价/查询分组预计算底色（AI录入比价与AI查询共用）：
     *  每个料号组内取"最近3天"（当天/昨天/前天）内Offer价格最低的一行标记淡蓝底，其余白底 */
    decorateOfferGroups(groups) {
      const dayKeys = this.recentDayKeys();
      (groups || []).forEach(g => {
        const offers = g.offers || [];
        let bestIndex = -1, bestPrice = null;
        offers.forEach((o, i) => {
          // 仅最近3天内的记录参与最低价比较，范围外的行不参与
          if (!this.isRecentOffer(o.offerDate, dayKeys)) return;
          const p = o.priceOffer == null ? null : Number(o.priceOffer);
          if (p == null || isNaN(p)) return;
          // 严格小于才替换：同价时保留日期更近的一条（组内已按Offer日期倒排）
          if (bestPrice == null || p < bestPrice) { bestPrice = p; bestIndex = i }
        });
        offers.forEach((o, i) => { o.bgColor = i === bestIndex ? 'rgb(217,236,255)' : '' });
      });
      return groups || []
    },
    /** 最近3天的日期键（第1个=今天、第2个=昨天、第3个=前天），格式 yyyy-MM-dd，
     *  与后端Offer日期前10位（yyyy-MM-dd）一致，按字符串比较避免时区/解析差异 */
    recentDayKeys() {
      const fmt = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
      const now = Date.now();
      return [fmt(new Date(now)), fmt(new Date(now - 86400000)), fmt(new Date(now - 2 * 86400000))]
    },
    /** 判断Offer日期是否属于最近3天（当天/昨天/前天） */
    isRecentOffer(offerDate, dayKeys) {
      const d = String(offerDate || '').slice(0, 10);
      return !!d && dayKeys.indexOf(d) > -1
    },
    /** AI录入比价结果按料号分组：后端已按Offer日期倒序返回，分组后即组内日期倒排、组间按料号升序 */
    groupCompareRows(rows) {
      const map = {};
      (rows || []).forEach(r => {
        const key = String(r.partNumber || '').trim().toUpperCase();
        if (!key) return;
        if (!map[key]) map[key] = { partNumber: key, offers: [] };
        map[key].offers.push(r);
      });
      return Object.keys(map).sort().map(k => map[k]);
    },
    /** 打开复制Offer弹窗：默认最近1天，清空上次的兜底文本 */
    openCopyOffer() { this.copyOfferDays = 1; this.copyOfferFallbackText = ''; this.openCopyOfferDialog = true },
    /** 提交复制Offer：查询最近N天记录，组装制表符文本后写入剪贴板，失败时文本展示在弹窗内供手动复制 */
    submitCopyOffer() {
      if (!this.copyOfferDays || this.copyOfferDays < 1) { this.$modal.msgError('请选择最近天数'); return }
      this.copyOfferLoading = true;
      copyOfferText(this.copyOfferDays).then(res => {
        // 兜底兼容旧后端：旧版接口重载歧义把文本放进了msg而非data（data为null），此处取msg；新后端data有值时优先用data
        const text = res.data || (res.msg && res.msg !== '操作成功' ? res.msg : '') || '';
        if (!text) { this.$modal.msgWarning('最近' + this.copyOfferDays + '天内没有Offer记录'); return }
        // 剪贴板写入为异步 Promise，成功/失败各自提示
        this.copyToClipboard(text).then(ok => {
          if (ok) {
            this.copyOfferFallbackText = '';
            this.$modal.msgSuccess('已复制 ' + text.split('\n').length + ' 条物料信息到剪贴板');
          } else {
            // 自动复制失败：把完整文本展示在弹窗内供手动复制，保证数据不丢失
            this.copyOfferFallbackText = text;
            this.$modal.msgError('浏览器自动复制失败，请手动全选下方"复制内容"框中的文本复制');
          }
        });
      }).catch(err => {
        const e = err || {};
        const msg = String(e.message || '');
        if (msg.includes('timeout')) {
          this.$modal.msgError('查询超时，请减小最近天数后重试');
        } else if (msg.includes('Network Error')) {
          this.$modal.msgError('无法连接后端接口，请确认后端服务（8081端口）已正常启动');
        } else {
          this.$modal.msgError(e.msg || msg || '复制Offer失败');
        }
      }).finally(() => { this.copyOfferLoading = false })
    },
    /** 通用剪贴板写入：优先 navigator.clipboard API（安全上下文下可靠），
     *  带 3 秒超时兜底（Chrome 在无用户激活的异步回调中 writeText 可能永久 pending 导致界面无反馈），
     *  失败自动降级 textarea + execCommand 方案（兼容 http 环境） */
    copyToClipboard(text) {
      // 方案1：Clipboard API，writeText 返回 Promise；超时或失败则降级
      if (navigator.clipboard && navigator.clipboard.writeText) {
        const timeout = new Promise(resolve => setTimeout(() => resolve(false), 3000));
        return Promise.race([
          navigator.clipboard.writeText(text).then(() => true).catch(() => false),
          timeout
        ]).then(ok => ok ? true : this.fallbackCopy(text));
      }
      // 方案2：textarea + execCommand 兜底
      return Promise.resolve(this.fallbackCopy(text));
    },
    /** 兜底剪贴板写入：隐藏 textarea + document.execCommand('copy')，兼容 http 环境 */
    fallbackCopy(text) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      let ok = false;
      try { ok = document.execCommand('copy') } catch (e) { ok = false }
      document.body.removeChild(ta);
      return ok;
    },
    /** 构建一键复制文本：料号独占一行，其下记录以制表符缩进分隔，方便粘贴到微信；每组记录首行为英文表头，数量带pcs、价格带USD单位 */
    buildAiQueryCopyText() {
      const lines = [];
      (this.aiQueryGroups || []).forEach(g => {
        lines.push(g.partNumber);
        const offers = g.offers || [];
        if (offers.length === 0) {
          lines.push('\t最近1个月无INQ/OFFER记录');
        } else {
          lines.push('\tSupplier\tType\tQty\tPrice\tCreated\tDelivery\tDetail');
          offers.forEach(o => {
            lines.push(['', o.supplierName || '', o.inqOfferType || '', o.quantity == null ? '' : o.quantity + 'pcs', o.priceOffer == null ? '' : o.priceOffer + 'USD', o.createTime || '', o.deliveryTime || '', o.productDetail || ''].join('\t'));
          });
        }
      });
      return lines.join('\n');
    },
    /** 一键复制Offer（AI录入比价弹窗按钮）：按当前比价分组的料号复制最近N天最低报价 */
    submitCopyOffersOfCompare() { this.doCopyOffers(this.aiCompareGroups, 'aiCompareFallbackText') },
    /** 一键复制Offer（AI查询弹窗按钮）：按当前查询分组的料号复制最近N天最低报价 */
    submitCopyOffersOfQuery() { this.doCopyOffers(this.aiQueryGroups, 'aiQueryFallbackText') },
    /** 一键复制Offer（AI录入比价与AI查询共用）：
     *  取最近copyRecentDays天（1=当天、2=今天+昨天、3=今天+昨天+前天，按Offer日期口径）内每个料号价格最低的Offer（同价取日期最新），
     *  默认输出 料号/数量/Offer价格，勾选 copyExtraFields（供应商编号/详情/交期/DC）时由后端按固定顺序追加对应列；
     *  文本由后端组装为制表符分隔（首行英文表头），前端写入剪贴板，自动复制失败时把文本展示在弹窗内供手动复制 */
    doCopyOffers(groups, fallbackField) {
      const partNumbers = (groups || []).map(g => g.partNumber).filter(Boolean);
      if (partNumbers.length === 0) { this.$modal.msgError('请先执行查询'); return }
      const days = this.copyRecentDays || 1;
      this.aiCopyLoading = true;
      copyAiQueryOffers({ days: days, partNumbers: partNumbers, extraFields: this.copyExtraFields }).then(res => {
        // 兜底兼容：data为空时尝试从msg取（后端双参重载返回，data恒有值，此分支仅防御性保留）
        const text = res.data || (res.msg && res.msg !== '操作成功' ? res.msg : '') || '';
        if (!text) { this.$modal.msgWarning('最近' + days + '天内这些物料没有Offer记录'); return }
        this.copyToClipboard(text).then(ok => {
          if (ok) {
            this[fallbackField] = '';
            // 首行为表头，条数=总行数-1
            this.$modal.msgSuccess('已复制 ' + (text.split('\n').length - 1) + ' 条最低报价到剪贴板');
          } else {
            // 自动复制失败：把完整文本展示在弹窗内供手动复制，保证数据不丢失
            this[fallbackField] = text;
            this.$modal.msgError('浏览器自动复制失败，请手动全选下方文本复制');
          }
        });
      }).catch(err => {
        const e = err || {};
        const msg = String(e.message || '');
        if (msg.includes('timeout')) {
          this.$modal.msgError('查询超时，请稍后重试');
        } else if (msg.includes('Network Error')) {
          this.$modal.msgError('无法连接后端接口，请确认后端服务（8081端口）已正常启动');
        } else {
          this.$modal.msgError(e.msg || msg || '复制失败');
        }
      }).finally(() => { this.aiCopyLoading = false })
    },
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
      if (this.colMap.productDetailArr && this.colMap.productDetailArr.length) { this.colMap.productDetail = this.colMap.productDetailArr.join(',') }
      const fd = new FormData()
      fd.append('file', param.file)
      fd.append('supplierCode', this.importSupplier.supplierCode || '')
      fd.append('supplierName', this.importSupplier.supplierName || '')
      fd.append('inqOfferType', this.importForm.inqOfferType)
      fd.append('profitRatio', String(this.importForm.profitRatio == null ? 2 : this.importForm.profitRatio))
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
    openBatchAdd() { this.openBatchAddDialog = true; this.remoteSupplier(''); this.loadParseDicts() },
    loadParseDicts() { getDicts('Offer_formate_text').then(res => { this.dictFormatTemplates = res.data || [] }); getDicts('Offer_formate_Inq_fields').then(res => { this.dictInqFields = res.data || []; if (!this.dictInqFields.some(x => (x.dictValue || x.dictLabel) === 'DC')) { this.dictInqFields.push({ dictValue: 'DC', dictLabel: 'DC' }) } }); getDicts('Offer_formate_Inq_seperate_tag').then(res => { this.dictSeps = res.data || [] }) },
    addFieldToken(val) { 
      if (!val) return; 
      this.formatSequence.push({ type: 'field', value: val });
      // 自动拼接一个空格作为分隔符
      this.formatSequence.push({ type: 'sep', value: '空格' });
    },
    addSepToken(val) { if (!val) return; this.formatSequence.push({ type: 'sep', value: val }) },
    removeToken(i) { this.formatSequence.splice(i, 1) },
    removeLastToken() { if (this.formatSequence.length) this.formatSequence.pop() },
    clearTokens() { this.formatSequence = [] },
    loadTemplate(val) { this.selectedTemplate = val; if (!val) { this.clearTokens(); return } const raw = val.split('+'); this.clearTokens(); raw.forEach(t => { const v = t.trim(); if (this.dictInqFields.some(d => d.dictLabel === v || d.dictValue === v)) { this.formatSequence.push({ type: 'field', value: v }) } else if (this.dictSeps.some(d => d.dictLabel === v || d.dictValue === v)) { this.formatSequence.push({ type: 'sep', value: v }) } }) },
    parsePasted() {
      if (!this.formatSequence.length || !this.pasteText) {
        this.$modal.msgError('请先定义解析表达式并粘贴文本');
        return;
      }
      const data = {
        text: this.pasteText,
        sequence: this.formatSequence,
        profitRatio: this.batchProfitRatio == null ? 2 : this.batchProfitRatio
      };
      parseOffer(data).then(res => {
        this.parsedRows = res.data || [];
        if (this.parsedRows.length === 0) {
          this.$modal.msgWarning('解析结果为空，请检查文本和表达式');
        } else {
          // 默认单位 USD
          this.parsedRows.forEach(row => {
            if (!row.priceUnit) {
              row.priceUnit = 'USD';
            }
          });
          this.$modal.msgSuccess(`成功解析 ${this.parsedRows.length} 条数据`);
        }
      }).catch(err => {
        this.$modal.msgError('解析失败: ' + (err.msg || '未知错误'));
      });
    },
    confirmBatchAdd() {
      if (!this.batchAddSupplier || !this.batchAddInqOfferType) {
        this.$modal.msgError('请选择供应商和类型');
        return;
      }
      if (!this.parsedRows.length) {
        this.$modal.msgError('没有可新增的数据');
        return;
      }
      const sup = this.batchAddSupplier;
      const now = new Date();
      // 拼接来源表名: 年月日时分+供应商名称+用户名称
      const timeStr = parseTime(now, '{y}{m}{d}{h}{i}');
      const userName = this.name || '';
      const sheetName = `${timeStr}${sup.supplierName}${userName}`;

      const tasks = this.parsedRows.map(r => {
        const data = {
          supplierCode: sup.supplierCode,
          supplierName: sup.supplierName,
          productCode: r.productCode || '',
          productBrand: r.productBrand || '',
          productDetail: r.productDetail || '',
          priceOffer: this.toNumberOrNull(r.priceOffer),
          priceCost: this.toNumberOrNull(r.priceCost), // Added priceCost mapping
          quantity: this.toNumberOrNull(r.quantity),
          deliveryTime: r.deliveryTime || '',
          moqQuantity: this.toNumberOrNull(r.moqQuantity),
          warrantyDetail: r.warrantyDetail || '',
          dc: r.dc || '',
          productType: r.productType || '',
          remark: r.remark || '',
          priceUnit: r.priceUnit || '',
          stockDate: now,
          inqOfferType: this.batchAddInqOfferType,
          sheetName: sheetName
        };
        return addOffer(data);
      });

      Promise.all(tasks).then(() => {
        this.$modal.msgSuccess('批量新增成功');
        this.openBatchAddDialog = false;
        this.parsedRows = [];
        this.getList();
      }).catch(err => {
        this.$modal.msgError(err && err.msg ? err.msg : '批量新增失败');
      });
    },
    toNumberOrNull(v) { if (v == null) return null; const s = String(v).match(/\d+(\.\d+)?/); return s ? Number(s[0]) : null },
    submitBatch() { batchEditOffer(this.ids, this.batchForm).then(() => { this.$modal.msgSuccess('批量编辑成功'); this.openBatchDialog = false; this.getList() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '批量编辑失败') }) },
    cancel() { this.open = false },
    resetFormData() { this.form = { id: undefined, supplierCode: '', supplierName: '', productCode: '', productDetailCode: '', productBrand: '', productBrandArr: [], stockDate: new Date(), priceCost: undefined, priceOffer: undefined, priceUnit: '', priceUnitArr: [], quantity: undefined, productDetail: '', deliveryTime: '', remark: '', sheetName: '', warrantyDetail: '', moqQuantity: undefined, dc: '', productType: '', productTypeArr: [], tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], inqOfferType: 'Offer', status: 1 } },
    submitForm() { if (!this.form.productCode) { this.$modal.msgError('产品编码不能为空'); return } this.joinFromArrays(); const data = Object.assign({}, this.form); (data.id ? updateOffer(data) : addOffer(data)).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '提交失败') }) }
    ,searchSupplier(query) {
      this.supplierLoading = true;
      listSupplierSimple({ pageNum: 1, pageSize: 1000, params: { keyword: query } }).then(res => {
        this.supplierListOptions = res.rows;
        this.supplierLoading = false;
      })
    }
    ,loadDicts() {
      this.searchSupplier('')
      getDicts('crm_product_brand').then(res => { this.dictProductBrand = res.data })
      getDicts('crm_price_unit').then(res => { this.dictPriceUnit = res.data })
      getDicts('crm_product_type').then(res => { this.dictProductType = res.data })
      getDicts('crm_tags_first').then(res => { this.dictTagsFirst = res.data })
      getDicts('crm_tags_second').then(res => { this.dictTagsSecond = res.data })
      getDicts('crm_tags_third').then(res => { this.dictTagsThird = res.data })
      getDicts('crm_tags_si').then(res => { this.dictTagsSi = res.data })
      getDicts('offer_export_dict').then(res => { this.exportFieldsDict = (res.data || []).map(d => ({ label: d.dictLabel, value: d.dictValue })) })
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
    ,openOfferProgress() {
      listEmailResults().then(res => {
        const list = res.data || []
        this.emailResultList = list
        this.progressSuccessCount = list.filter(x => String(x.result).indexOf('成功') !== -1).length
        this.progressFailCount = list.filter(x => String(x.result).indexOf('失败') !== -1).length
        this.copyAllText = this.buildAllCopyText(list)
        this.offerProgressDialogVisible = true
      })
    }
    ,refreshOfferProgress() {
      listEmailResults().then(res => {
        const list = res.data || []
        this.emailResultList = list
        this.progressSuccessCount = list.filter(x => String(x.result).indexOf('成功') !== -1).length
        this.progressFailCount = list.filter(x => String(x.result).indexOf('失败') !== -1).length
        this.copyAllText = this.buildAllCopyText(list)
      })
    }
    ,buildAllCopyText(list) {
      const lines = []
      lines.push(`成功：${this.progressSuccessCount}，失败：${this.progressFailCount}`)
      list.forEach(r => {
        const t = [
          `ID=${r.id}`,
          `批次号=${r.batchNo || ''}`,
          `邮箱=${r.email || ''}`,
          `结果=${r.result || ''}`,
          `原因=${r.msg || ''}`,
          `更新时间=${r.updateTime ? this.parseTime(r.updateTime) : ''}`
        ].join(' | ')
        lines.push(t)
      })
      return lines.join('\n')
    }
    ,getRowCopyText(row) {
      return [
        `ID=${row.id}`,
        `批次号=${row.batchNo || ''}`,
        `邮箱=${row.email || ''}`,
        `结果=${row.result || ''}`,
        `原因=${row.msg || ''}`,
        `更新时间=${row.updateTime ? this.parseTime(row.updateTime) : ''}`
      ].join(' | ')
    }
    ,onCopyOk() { this.$modal.msgSuccess('已复制') }
  }
}
</script>

<style scoped>
.form-header { font-size: 15px; color: #6379bb; border-bottom: 1px solid #ddd; margin: 8px 10px 25px 10px; padding-bottom: 5px }
.ai-query-group { margin-bottom: 16px }
.ai-query-part-number { font-weight: bold; font-size: 14px; color: #303133; background: #f0f2f5; padding: 6px 10px; border-radius: 4px 4px 0 0; border-left: 3px solid #1890ff }
.ai-query-empty { color: #909399; padding: 8px 12px; border: 1px solid #ebeef5; border-top: none; border-radius: 0 0 4px 4px; font-size: 13px }
/* 复制工具栏（AI录入比价/AI查询共用）：天数单选、额外字段复选框、一键复制按钮右对齐排列 */
.copy-toolbar { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-bottom: 10px }
.copy-extra-fields { display: inline-flex; align-items: center }
</style>
