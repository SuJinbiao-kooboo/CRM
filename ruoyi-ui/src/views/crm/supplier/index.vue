<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="88px">
      <el-form-item label="类型">
        <el-select v-model="queryParams.supplierTypeArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictSupplierType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商名称">
        <el-input v-model="queryParams.supplierName" placeholder="请输入供应商名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="联系人名称">
        <el-input v-model="queryParams.contactName" placeholder="请输入联系人名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="品牌" v-if="false">
        <el-select v-model="queryParams.brandsArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictProductBrand" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="主营产品">
        <el-select v-model="queryParams.mainProductsArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictMainProducts" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="合作等级" v-if="false">
        <el-select v-model="queryParams.cooperationLevelArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictCooperationLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="风险等级" v-if="false">
        <el-select v-model="queryParams.riskLevelArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictRiskLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="付款条件" v-if="false">
        <el-select v-model="queryParams.paymentTermsArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictPaymentTerms" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="合作等级" prop="cooperationLevel">
        <el-input v-model="queryParams.cooperationLevel" placeholder="合作等级(模糊)" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-input v-model="queryParams.riskLevel" placeholder="风险等级(模糊)" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="付款条件" prop="paymentTerms">
        <el-input v-model="queryParams.paymentTerms" placeholder="付款条件(模糊)" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <!-- 其他查询条件移除，根据需求精简到字典多选 -->
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-button type="text" size="mini" @click="showMoreQuery = true">更多查询</el-button>
      </el-form-item>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-form>

    <el-dialog title="更多查询" :visible.sync="showMoreQuery" width="600px" append-to-body>
      <el-form :model="queryParams" label-width="88px">
        <el-form-item label="品牌">
          <el-select v-model="queryParams.brandsArr" multiple collapse-tags filterable placeholder="请选择">
            <el-option v-for="d in dictProductBrand" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="合作状态">
          <el-select v-model="queryParams.cooperationStatus" filterable clearable placeholder="请选择">
            <el-option v-for="d in dictCooperationStatus" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="合作等级">
          <el-select v-model="queryParams.cooperationLevelArr" multiple collapse-tags filterable placeholder="请选择">
            <el-option v-for="d in dictCooperationLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="queryParams.riskLevelArr" multiple collapse-tags filterable placeholder="请选择">
            <el-option v-for="d in dictRiskLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款条件">
          <el-select v-model="queryParams.paymentTermsArr" multiple collapse-tags filterable placeholder="请选择">
            <el-option v-for="d in dictPaymentTerms" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签1">
          <el-select v-model="queryParams.tagsFirst" clearable filterable placeholder="请选择">
            <el-option v-for="d in dictTagsFirst" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签2">
          <el-select v-model="queryParams.tagsSecond" clearable filterable placeholder="请选择">
            <el-option v-for="d in dictTagsSecond" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签3">
          <el-select v-model="queryParams.tagsThird" clearable filterable placeholder="请选择">
            <el-option v-for="d in dictTagsThird" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签4">
          <el-select v-model="queryParams.tagsSi" clearable filterable placeholder="请选择">
            <el-option v-for="d in dictTagsSi" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="showMoreQuery=false; handleQuery()">确 定</el-button>
        <el-button @click="showMoreQuery=false">取 消</el-button>
      </div>
    </el-dialog>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['crm:supplier:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate" v-hasPermi="['crm:supplier:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['crm:supplier:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['crm:supplier:export']">导出</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="supplierList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="供应商名称" align="center" prop="supplierName" />
      <el-table-column label="类型" align="center" prop="supplierType" />
      <el-table-column label="品牌" align="center" prop="brands" />
      <el-table-column label="国家" align="center" prop="country" />
      <el-table-column label="主营产品" align="center" prop="mainProducts" />
      <el-table-column label="合作等级" align="center" prop="cooperationLevel" />
      <el-table-column label="风险等级" align="center" prop="riskLevel" />
      <el-table-column label="付款条件" align="center" prop="paymentTerms" />
      <el-table-column label="合作状态" align="center" prop="cooperationStatus">
        <template slot-scope="scope">
          <el-select v-model="scope.row.cooperationStatus" placeholder="请选择" size="mini" @change="updateCooperationStatus(scope.row)">
            <el-option v-for="d in dictCooperationStatus" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="跟进人" align="center" prop="followUpBy" />
      <el-table-column label="联系人" align="center" prop="contactName" />
      <el-table-column label="手机号" align="center" prop="phone" />
      <el-table-column label="邮箱" align="center" prop="email" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['crm:supplier:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['crm:supplier:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" :page-sizes="[10,20,50,100,200,300,500]" layout="total, sizes, prev, pager, next, jumper" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="900px" append-to-body>
      <el-form ref="form" :model="form" label-width="120px">
        <div class="form-header">基本信息</div>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商编号" prop="supplierCode">
              <el-input v-model="form.supplierCode" placeholder="请输入供应商编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商简称" prop="supplierShortName">
              <el-input v-model="form.supplierShortName" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="supplierType">
              <el-select v-model="form.supplierTypeArr" multiple collapse-tags filterable placeholder="请选择类型">
                <el-option v-for="d in dictSupplierType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="国家" prop="country">
              <el-input v-model="form.country" placeholder="请输入国家" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌" prop="brands">
              <el-select v-model="form.brandsArr" multiple collapse-tags filterable placeholder="请选择品牌">
                <el-option v-for="d in dictProductBrand" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="官网地址" prop="website">
              <el-input v-model="form.website" placeholder="请输入官网地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主营产品" prop="mainProducts">
              <el-select v-model="form.mainProductsArr" multiple collapse-tags filterable placeholder="请选择主营产品">
                <el-option v-for="d in dictMainProducts" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作等级" prop="cooperationLevel">
              <el-select v-model="form.cooperationLevelArr" multiple collapse-tags filterable placeholder="请选择合作等级">
                <el-option v-for="d in dictCooperationLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风险等级" prop="riskLevel">
              <el-select v-model="form.riskLevelArr" multiple collapse-tags filterable placeholder="请选择风险等级">
                <el-option v-for="d in dictRiskLevel" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款条件" prop="paymentTerms">
              <el-select v-model="form.paymentTermsArr" multiple collapse-tags filterable placeholder="请选择付款条件">
                <el-option v-for="d in dictPaymentTerms" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作状态" prop="cooperationStatus">
              <el-select v-model="form.cooperationStatus" filterable placeholder="请选择合作状态">
                <el-option v-for="d in dictCooperationStatus" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业执照号" prop="businessLicense">
              <el-input v-model="form.businessLicense" placeholder="请输入营业执照号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税号" prop="taxNumber">
              <el-input v-model="form.taxNumber" placeholder="请输入税号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行信息" prop="bankInfo">
              <el-input v-model="form.bankInfo" placeholder="请输入银行信息" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行账号" prop="bankAccount">
              <el-input v-model="form.bankAccount" placeholder="请输入银行账号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="介绍信息" prop="introduction">
              <el-input v-model="form.introduction" type="textarea" placeholder="请输入介绍信息" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注1" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注2" prop="remarkSecond">
              <el-input v-model="form.remarkSecond" type="textarea" placeholder="请输入备注2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签1" prop="tagsFirst">
              <el-select v-model="form.tagsFirstArr" multiple collapse-tags filterable placeholder="请选择标签1"><el-option v-for="d in dictTagsFirst" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签2" prop="tagsSecond">
              <el-select v-model="form.tagsSecondArr" multiple collapse-tags filterable placeholder="请选择标签2"><el-option v-for="d in dictTagsSecond" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签3" prop="tagsThird">
              <el-select v-model="form.tagsThirdArr" multiple collapse-tags filterable placeholder="请选择标签3"><el-option v-for="d in dictTagsThird" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签4" prop="tagsSi">
              <el-select v-model="form.tagsSiArr" multiple collapse-tags filterable placeholder="请选择标签4"><el-option v-for="d in dictTagsSi" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跟进人" prop="followUpBy">
              <el-select v-model="form.followUpBy" filterable remote reserve-keyword placeholder="选择跟进人" :remote-method="remoteUsers" value-key="userName">
                <el-option v-for="u in userOptions" :key="u.userId" :label="u.userName" :value="u.userName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-header">联系人</div>
        <el-button type="primary" size="mini" icon="el-icon-plus" @click="addContact">新增联系人</el-button>
        <el-table :data="form.contacts" size="mini" class="mt10">
          <el-table-column label="姓名" prop="contactName">
            <template slot-scope="scope">
              <el-input v-model="scope.row.contactName" placeholder="姓名" />
            </template>
          </el-table-column>
          <el-table-column label="岗位" prop="post">
            <template slot-scope="scope">
              <el-input v-model="scope.row.post" placeholder="岗位" />
            </template>
          </el-table-column>
          <el-table-column label="职位" prop="position">
            <template slot-scope="scope">
              <el-input v-model="scope.row.position" placeholder="职位" />
            </template>
          </el-table-column>
          <el-table-column label="手机号" prop="phone">
            <template slot-scope="scope">
              <el-input v-model="scope.row.phone" placeholder="手机号" />
            </template>
          </el-table-column>
          <el-table-column label="邮箱" prop="email">
            <template slot-scope="scope">
              <el-input v-model="scope.row.email" placeholder="邮箱" />
            </template>
          </el-table-column>
          <el-table-column label="WhatsApp" prop="whatsapp">
            <template slot-scope="scope">
              <el-input v-model="scope.row.whatsapp" placeholder="WhatsApp" />
            </template>
          </el-table-column>
          <el-table-column label="微信" prop="wechat">
            <template slot-scope="scope">
              <el-input v-model="scope.row.wechat" placeholder="微信" />
            </template>
          </el-table-column>
          <el-table-column label="QQ" prop="qq">
            <template slot-scope="scope">
              <el-input v-model="scope.row.qq" placeholder="QQ" />
            </template>
          </el-table-column>
          <el-table-column label="其他1" prop="other_contact_first">
            <template slot-scope="scope">
              <el-input v-model="scope.row.otherContactFirst" placeholder="其他联系方式1" />
            </template>
          </el-table-column>
          <el-table-column label="其他2" prop="other_contact_second">
            <template slot-scope="scope">
              <el-input v-model="scope.row.otherContactSecond" placeholder="其他联系方式2" />
            </template>
          </el-table-column>
          <el-table-column label="备注1" prop="remark_first">
            <template slot-scope="scope">
              <el-input v-model="scope.row.remarkFirst" placeholder="备注1" />
            </template>
          </el-table-column>
          <el-table-column label="备注2" prop="remark_second">
            <template slot-scope="scope">
              <el-input v-model="scope.row.remarkSecond" placeholder="备注2" />
            </template>
          </el-table-column>
          <el-table-column label="主要联系人" prop="isPrimary" width="100">
            <template slot-scope="scope">
              <el-switch v-model="scope.row.isPrimary" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template slot-scope="scope">
              <el-button type="text" size="mini" @click="removeContact(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="form-header">附件</div>
        <el-upload :action="uploadUrl" :headers="uploadHeaders" :on-success="onUploadSuccess" :file-list="uploadFiles" :limit="10" :show-file-list="true">
          <el-button size="small" type="primary">上传附件</el-button>
        </el-upload>
        <el-table :data="form.attachments" size="mini" class="mt10">
          <el-table-column label="附件名称" prop="fileName" />
          <el-table-column label="地址" prop="fileUrl" />
          <el-table-column label="备注" prop="remark">
            <template slot-scope="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template slot-scope="scope">
              <el-button type="text" size="mini" @click="removeAttachment(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSupplier, getSupplier, addSupplier, updateSupplier, delSupplier } from '@/api/crm/supplier'
import { listUser } from '@/api/system/user'
import { getDicts } from '@/api/system/dict/data'
import { getToken } from '@/utils/auth'

export default {
  name: 'CrmSupplier',
  data() {
    return {
      loading: false,
      showSearch: true,
      showMoreQuery: false,
      total: 0,
      supplierList: [],
      ids: [],
      single: true,
      multiple: true,
      title: '',
      open: false,
      uploadUrl: process.env.VUE_APP_BASE_API + '/common/upload',
      uploadHeaders: { Authorization: 'Bearer ' + getToken() },
      uploadFiles: [],
      queryParams: { pageNum: 1, pageSize: 10, supplierName: '', contactName: '', cooperationStatus: '', supplierTypeArr: [], brandsArr: [], mainProductsArr: [], cooperationLevelArr: [], riskLevelArr: [], paymentTermsArr: [], params: {} },
      form: { id: undefined, supplierName: '', supplierCode: '', supplierShortName: '', supplierType: '', supplierTypeArr: [], brands: '', brandsArr: [], country: '', address: '', website: '', mainProducts: '', mainProductsArr: [], cooperationLevel: '', cooperationLevelArr: [], riskLevel: '', riskLevelArr: [], paymentTerms: '', paymentTermsArr: [], cooperationStatus: '', businessLicense: '', taxNumber: '', bankInfo: '', bankAccount: '', introduction: '', remark: '', remarkSecond: '', status: 1, followUpBy: '', tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], contacts: [], attachments: [] },
      dictSupplierType: [], dictMainProducts: [], dictCooperationLevel: [], dictRiskLevel: [], dictPaymentTerms: [], dictProductBrand: [], dictCooperationStatus: [], dictTagsFirst: [], dictTagsSecond: [], dictTagsThird: [], dictTagsSi: [],
      userOptions: []
    }
  },
  created() { this.getList() },
  methods: {
    getList() {
      this.loading = true
      this.queryParams.params.supplierTypeList = this.queryParams.supplierTypeArr
      this.queryParams.params.brandsList = this.queryParams.brandsArr
      this.queryParams.params.mainProductsList = this.queryParams.mainProductsArr
      this.queryParams.params.cooperationLevelList = this.queryParams.cooperationLevelArr
      this.queryParams.params.riskLevelList = this.queryParams.riskLevelArr
      this.queryParams.params.paymentTermsList = this.queryParams.paymentTermsArr
      listSupplier(this.queryParams).then(res => {
        this.supplierList = res.rows
        this.total = res.total
        this.loading = false
      })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.queryParams.supplierTypeArr = []; this.queryParams.brandsArr = []; this.queryParams.mainProductsArr = []; this.queryParams.cooperationLevelArr = []; this.queryParams.riskLevelArr = []; this.queryParams.paymentTermsArr = []; this.handleQuery() },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    handleAdd() { this.resetFormData(); this.open = true; this.title = '新增供应商'; this.loadDicts() },
    handleUpdate(row) {
      const id = row.id || this.ids[0]
      getSupplier(id).then(res => { this.form = res.data || {}; if (!this.form.contacts) this.form.contacts = []; if (!this.form.attachments) this.form.attachments = []; this.splitToArrays(); this.open = true; this.title = '修改供应商'; this.loadDicts() })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$modal.confirm('是否确认删除选中数据项？').then(() => {
        return delSupplier(ids)
      }).then(() => { this.getList(); this.$modal.msgSuccess('删除成功') }).catch(() => {})
    },
    handleExport() {
      this.download('/crm/supplier/export', { ids: this.ids }, `supplier_${new Date().getTime()}.xlsx`)
    },
    cancel() { this.open = false },
    resetFormData() { this.form = { id: undefined, supplierName: '', supplierCode: '', supplierShortName: '', supplierType: '', supplierTypeArr: [], brands: '', brandsArr: [], country: '', address: '', website: '', mainProducts: '', mainProductsArr: [], cooperationLevel: '', cooperationLevelArr: [], riskLevel: '', riskLevelArr: [], paymentTerms: '', paymentTermsArr: [], businessLicense: '', taxNumber: '', bankInfo: '', bankAccount: '', introduction: '', remark: '', remarkSecond: '', status: 1, followUpBy: '', tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], contacts: [], attachments: [] } },
    addContact() { this.form.contacts.push({ contactName: '', post: '', phone: '', email: '', isPrimary: 0 }) },
    removeContact(index) { this.form.contacts.splice(index, 1) },
    onUploadSuccess(resp, file) {
      if (resp && resp.url) {
        this.form.attachments.push({ fileName: resp.originalFilename || file.name, fileUrl: resp.url, remark: '' })
      }
    },
    removeAttachment(index) { this.form.attachments.splice(index, 1) },
    submitForm() {
      if (!this.form.supplierName) { this.$modal.msgError('供应商名称不能为空'); return }
      this.joinFromArrays()
      const data = Object.assign({}, this.form)
      ;(data.id ? updateSupplier(data) : addSupplier(data)).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '提交失败') })
    }
    ,loadDicts() {
      getDicts('crm_supplier_type').then(res => { this.dictSupplierType = res.data })
      getDicts('crm_supplier_main_products').then(res => { this.dictMainProducts = res.data })
      getDicts('crm_supplier_cooperation_level').then(res => { this.dictCooperationLevel = res.data })
      getDicts('crm_supplier_risk_level').then(res => { this.dictRiskLevel = res.data })
      getDicts('crm_supplier_payment_terms').then(res => { this.dictPaymentTerms = res.data })
      getDicts('crm_product_brand').then(res => { this.dictProductBrand = res.data })
      getDicts('crm_cooperation_status').then(res => { this.dictCooperationStatus = res.data })
      getDicts('crm_tags_first').then(res => { this.dictTagsFirst = res.data })
      getDicts('crm_tags_second').then(res => { this.dictTagsSecond = res.data })
      getDicts('crm_tags_third').then(res => { this.dictTagsThird = res.data })
      getDicts('crm_tags_si').then(res => { this.dictTagsSi = res.data })
    }
    ,splitToArrays() {
      this.form.supplierTypeArr = this.split(this.form.supplierType)
      this.form.brandsArr = this.split(this.form.brands)
      this.form.mainProductsArr = this.split(this.form.mainProducts)
      this.form.cooperationLevelArr = this.split(this.form.cooperationLevel)
      this.form.riskLevelArr = this.split(this.form.riskLevel)
      this.form.paymentTermsArr = this.split(this.form.paymentTerms)
      this.form.tagsFirstArr = this.split(this.form.tagsFirst)
      this.form.tagsSecondArr = this.split(this.form.tagsSecond)
      this.form.tagsThirdArr = this.split(this.form.tagsThird)
      this.form.tagsSiArr = this.split(this.form.tagsSi)
    }
    ,joinFromArrays() {
      this.form.supplierType = (this.form.supplierTypeArr || []).join(',')
      this.form.brands = (this.form.brandsArr || []).join(',')
      this.form.mainProducts = (this.form.mainProductsArr || []).join(',')
      this.form.cooperationLevel = (this.form.cooperationLevelArr || []).join(',')
      this.form.riskLevel = (this.form.riskLevelArr || []).join(',')
      this.form.paymentTerms = (this.form.paymentTermsArr || []).join(',')
      this.form.tagsFirst = (this.form.tagsFirstArr || []).join(',')
      this.form.tagsSecond = (this.form.tagsSecondArr || []).join(',')
      this.form.tagsThird = (this.form.tagsThirdArr || []).join(',')
      this.form.tagsSi = (this.form.tagsSiArr || []).join(',')
    }
    ,updateCooperationStatus(row) {
      const data = { id: row.id, cooperationStatus: row.cooperationStatus }
      updateSupplier(data).then(() => { this.$modal.msgSuccess('合作状态已更新') }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '更新失败') })
    }
    ,split(s) { return s ? s.split(',') : [] }
    ,remoteUsers(query) { listUser({ userName: query, pageNum: 1, pageSize: 20 }).then(res => { this.userOptions = res.rows || [] }) }
  }
}
</script>

<style scoped>
.mt10 { margin-top: 10px; }
</style>
