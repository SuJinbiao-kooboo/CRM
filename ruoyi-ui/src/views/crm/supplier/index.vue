<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="88px">
      <!-- 快捷筛选：供应商编码，置于第一个位置 -->
      <el-form-item label="供应商编码">
        <el-input v-model="queryParams.supplierCode" placeholder="请输入供应商编码" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.supplierTypeArr" multiple collapse-tags filterable placeholder="请选择">
          <el-option v-for="d in dictSupplierType" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商名称">
        <el-input v-model="queryParams.supplierName" placeholder="请输入供应商名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="公司别名">
        <el-input v-model="queryParams.supplierAlias" placeholder="请输入公司别名" clearable @keyup.enter.native="handleQuery" />
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
        <el-form-item label="合作状态">
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
    <el-dialog :title="detailTitle" :visible.sync="detailOpen" width="1000px" append-to-body>
      <div>
        <div class="form-header">基本信息</div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="供应商名称">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="供应商编号">{{ detailData.supplierCode }}</el-descriptions-item>
          <el-descriptions-item label="供应商简称">{{ detailData.supplierShortName }}</el-descriptions-item>
          <el-descriptions-item label="公司别名">{{ detailData.supplierAlias }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailData.supplierType }}</el-descriptions-item>
          <el-descriptions-item label="国家">{{ detailData.country }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="3">{{ detailData.address }}</el-descriptions-item>
          <el-descriptions-item label="官网地址" :span="3">
            <el-link v-if="detailData.website" :href="detailData.website" target="_blank">{{ detailData.website }}</el-link>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="品牌" :span="3">{{ detailData.brands }}</el-descriptions-item>
          <el-descriptions-item label="主营产品" :span="3">{{ detailData.mainProducts }}</el-descriptions-item>
        </el-descriptions>

        <div class="form-header mt10">合作信息</div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="合作等级">{{ detailData.cooperationLevel }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ detailData.riskLevel }}</el-descriptions-item>
          <el-descriptions-item label="付款条件">{{ detailData.paymentTerms }}</el-descriptions-item>
          <el-descriptions-item label="合作状态">{{ detailData.cooperationStatus }}</el-descriptions-item>
          <el-descriptions-item label="跟进人">{{ detailData.followUpByNames || detailData.followUpBy }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailData.status === 1 ? '正常' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ parseTime(detailData.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ parseTime(detailData.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="form-header mt10">财务信息</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="银行信息" :span="2"><div style="white-space: pre-wrap;">{{ detailData.bankInfo }}</div></el-descriptions-item>
          <el-descriptions-item label="银行账号" :span="2"><div style="white-space: pre-wrap;">{{ detailData.bankAccount }}</div></el-descriptions-item>
        </el-descriptions>

        <div class="form-header mt10">地址信息</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="Bill to" :span="2"><div style="white-space: pre-wrap;">{{ detailData.billTo }}</div></el-descriptions-item>
          <el-descriptions-item label="Ship to" :span="2"><div style="white-space: pre-wrap;">{{ detailData.shipTo }}</div></el-descriptions-item>
        </el-descriptions>

        <div class="form-header mt10">其他信息</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="介绍信息" :span="2">{{ detailData.introduction }}</el-descriptions-item>
          <el-descriptions-item label="备注1" :span="2">{{ detailData.remark }}</el-descriptions-item>
          <el-descriptions-item label="备注2" :span="2">{{ detailData.remarkSecond }}</el-descriptions-item>
          <el-descriptions-item label="标签1">{{ detailData.tagsFirst }}</el-descriptions-item>
          <el-descriptions-item label="标签2">{{ detailData.tagsSecond }}</el-descriptions-item>
          <el-descriptions-item label="标签3">{{ detailData.tagsThird }}</el-descriptions-item>
          <el-descriptions-item label="标签4">{{ detailData.tagsSi }}</el-descriptions-item>
        </el-descriptions>

        <div class="form-header mt10">联系人</div>
        <el-table :data="detailData.contacts || []" size="mini" stripe>
          <el-table-column label="姓名" prop="contactName" width="120" />
          <el-table-column label="岗位" prop="post" width="120" />
          <el-table-column label="职位" prop="position" width="120" />
          <el-table-column label="手机号" prop="phone" width="140" />
          <el-table-column label="邮箱" prop="email" width="200" />
          <el-table-column label="WhatsApp" prop="whatsapp" width="120" />
          <el-table-column label="微信" prop="wechat" width="120" />
          <el-table-column label="Teams" prop="teams" width="120" />
          <el-table-column label="发送组合" prop="otherContactFirst" width="160" />
          <el-table-column label="其他2" prop="otherContactSecond" width="160" />
          <el-table-column label="备注1" prop="remarkFirst" width="160" />
          <el-table-column label="备注2" prop="remarkSecond" width="160" />
          <el-table-column label="主要联系人" prop="isPrimary" width="100">
            <template slot-scope="scope">
              <el-tag :type="scope.row.isPrimary === 1 ? 'success' : 'info'">{{ scope.row.isPrimary === 1 ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="form-header mt10">附件</div>
        <el-table :data="detailData.attachments || []" size="mini" stripe>
          <el-table-column label="附件名称" prop="fileName">
            <template slot-scope="scope">
              <el-link type="primary" @click="previewAttachment(scope.row)">{{ scope.row.fileName }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template slot-scope="scope">
              <el-button type="text" size="mini" @click="downloadAttachment(scope.row)">下载</el-button>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" prop="createTime" width="180">
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="detailOpen=false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 附件预览弹窗（Excel/Word/文本等由后端解析为 HTML 展示） -->
    <el-dialog :title="previewTitle" :visible.sync="previewOpen" width="80%" top="5vh" append-to-body>
      <div class="preview-content" v-html="previewHtml" />
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
      <!-- 供应商编码：唯一标识，置于第一个数据列，与快捷筛选对应 -->
      <el-table-column label="供应商编码" align="center" prop="supplierCode" width="160" show-overflow-tooltip />
      <el-table-column label="供应商名称" align="center" prop="supplierName">
        <template slot-scope="scope">
          <el-link type="primary" @click="openDetail(scope.row)">{{ scope.row.supplierName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="公司别名" align="center" prop="supplierAlias" show-overflow-tooltip />
      <el-table-column label="类型" align="center" prop="supplierType" />
      <el-table-column label="品牌" align="center" prop="brands" />
      <el-table-column label="国家" align="center" prop="country" />
      <el-table-column label="跟进人" align="center">
        <template slot-scope="scope">{{ scope.row.followUpByNames || scope.row.followUpBy }}</template>
      </el-table-column>
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
      <el-table-column label="联系人" align="center" prop="contactName" width="200" />
      <el-table-column label="手机号" align="center" prop="phone" width="200" />
      <el-table-column label="邮箱" align="center" prop="email" width="240" />
      <el-table-column label="Teams" align="center" prop="teams" width="200" />
      <el-table-column label="WhatsApp" align="center" prop="whatsapp" width="200" />
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
              <el-input v-model="form.supplierCode" placeholder="保存时自动生成（VC+月日+随机3位）" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商简称" prop="supplierShortName">
              <el-input v-model="form.supplierShortName" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司别名" prop="supplierAlias">
              <el-input v-model="form.supplierAlias" placeholder="请输入公司别名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跟进人" prop="followUpByArr">
              <el-select v-model="form.followUpByArr" multiple collapse-tags filterable remote reserve-keyword placeholder="请选择跟进人" :remote-method="remoteUsers" :loading="userLoading" @focus="remoteUsers('')">
                <el-option v-for="u in userOptions" :key="u.userId" :label="u.nickName" :value="u.userName" />
              </el-select>
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
          <el-col :span="24">
            <el-form-item label="银行信息" prop="bankInfo">
              <Editor v-model="form.bankInfo" :minHeight="120" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="银行账号" prop="bankAccount">
              <Editor v-model="form.bankAccount" :minHeight="120" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Bill to" prop="billTo">
              <Editor v-model="form.billTo" :minHeight="120" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Ship to" prop="shipTo">
              <Editor v-model="form.shipTo" :minHeight="120" />
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
            <el-form-item label="合作状态" prop="tagsFirst">
              <el-select v-model="form.tagsFirstArr" multiple collapse-tags filterable placeholder="请选择合作状态"><el-option v-for="d in dictTagsFirst" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" /></el-select>
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
        </el-row>

        <div class="form-header">联系人</div>
        <el-button type="primary" size="mini" icon="el-icon-plus" @click="addContact">新增联系人</el-button>
        <el-table :data="form.contacts" size="mini" class="mt10">
          <el-table-column label="姓名" prop="contactName" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.contactName" placeholder="姓名" />
            </template>
          </el-table-column>
          <el-table-column label="岗位" prop="post" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.post" placeholder="岗位" />
            </template>
          </el-table-column>
          <el-table-column label="职位" prop="position" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.position" placeholder="职位" />
            </template>
          </el-table-column>
          <el-table-column label="手机号" prop="phone" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.phone" placeholder="手机号" />
            </template>
          </el-table-column>
          <el-table-column label="邮箱" prop="email" width="240">
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
          <el-table-column label="Teams" prop="teams" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.teams" placeholder="Teams" />
            </template>
          </el-table-column>
          <el-table-column label="其他1" prop="other_contact_first" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.otherContactFirst" placeholder="其他联系方式1" />
            </template>
          </el-table-column>
          <el-table-column label="其他2" prop="other_contact_second" width="200">
            <template slot-scope="scope">
              <el-input v-model="scope.row.otherContactSecond" placeholder="其他联系方式2" />
            </template>
          </el-table-column>
          <el-table-column label="备注1" prop="remark_first" width="240">
            <template slot-scope="scope">
              <el-input v-model="scope.row.remarkFirst" placeholder="备注1" />
            </template>
          </el-table-column>
          <el-table-column label="备注2" prop="remark_second" width="240">
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
        <!-- 附件上传：文件内容直接入库（crm_supplier_attachment 表），编辑已有供应商时携带供应商ID -->
        <el-upload :action="uploadUrl" :headers="uploadHeaders" :data="uploadAttachmentData" :on-success="onUploadSuccess" :file-list="uploadFiles" :limit="10" :show-file-list="true">
          <el-button size="small" type="primary">上传附件</el-button>
        </el-upload>
        <el-table :data="form.attachments" size="mini" class="mt10">
          <el-table-column label="附件名称" prop="fileName" />
          <el-table-column label="上传时间" prop="createTime" width="160">
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template slot-scope="scope">
              <el-button type="text" size="mini" @click="previewAttachment(scope.row)">预览</el-button>
              <el-button type="text" size="mini" @click="downloadAttachment(scope.row)">下载</el-button>
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
import { listSupplier, getSupplier, addSupplier, updateSupplier, delSupplier, getSupplierDetail, delSupplierAttachment, listSupplierUsers } from '@/api/crm/supplier'
import request from '@/utils/request'
import { getDicts } from '@/api/system/dict/data'
import { getToken } from '@/utils/auth'

export default {
  name: 'CrmSupplier',
  components: { Editor: () => import('@/components/Editor') },
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
      detailTitle: '供应商详情',
      detailOpen: false,
      detailData: {},
      previewOpen: false,
      previewTitle: '',
      previewHtml: '',
      uploadUrl: process.env.VUE_APP_BASE_API + '/crm/supplier/attachment/upload',
      uploadHeaders: { Authorization: 'Bearer ' + getToken() },
      uploadFiles: [],
      queryParams: { pageNum: 1, pageSize: 10, supplierCode: '', supplierName: '', supplierAlias: '', contactName: '', cooperationStatus: '', supplierTypeArr: [], brandsArr: [], mainProductsArr: [], cooperationLevelArr: [], riskLevelArr: [], paymentTermsArr: [], params: {} },
      form: { id: undefined, supplierName: '', supplierCode: '', supplierShortName: '', supplierAlias: '', supplierType: '', supplierTypeArr: [], brands: '', brandsArr: [], country: '', address: '', website: '', mainProducts: '', mainProductsArr: [], cooperationLevel: '', cooperationLevelArr: [], riskLevel: '', riskLevelArr: [], paymentTerms: '', paymentTermsArr: [], cooperationStatus: '', followUpByArr: [], businessLicense: '', taxNumber: '', bankInfo: '', bankAccount: '', billTo: '', shipTo: '', introduction: '', remark: '', remarkSecond: '', status: 1, followUpBy: '', tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], contacts: [], attachments: [] },
      dictSupplierType: [], dictMainProducts: [], dictCooperationLevel: [], dictRiskLevel: [], dictPaymentTerms: [], dictProductBrand: [], dictCooperationStatus: [], dictTagsFirst: [], dictTagsSecond: [], dictTagsThird: [], dictTagsSi: [],
      userOptions: [],
      userLoading: false
    }
  },
  created() { this.getList() },
  methods: {
    openDetail(row) {
      const id = row.id
      getSupplierDetail(id).then(res => {
        this.detailData = res.data || {}
        if (!this.detailData.contacts) this.detailData.contacts = []
        if (!this.detailData.attachments) this.detailData.attachments = []
        this.detailOpen = true
      }).catch(err => {
        this.$modal.msgError(err && err.msg ? err.msg : '获取详情失败')
      })
    },
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
    handleAdd() { this.resetFormData(); this.open = true; this.title = '新增供应商'; this.loadDicts(); this.remoteUsers('') },
    handleUpdate(row) {
      const id = row.id || this.ids[0]
      getSupplier(id).then(res => { this.form = res.data || {}; if (!this.form.contacts) this.form.contacts = []; if (!this.form.attachments) this.form.attachments = []; this.uploadFiles = []; this.splitToArrays(); this.remoteUsers(''); this.open = true; this.title = '修改供应商'; this.loadDicts() }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '获取供应商失败') })
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
    resetFormData() { this.form = { id: undefined, supplierName: '', supplierCode: '', supplierShortName: '', supplierAlias: '', supplierType: '', supplierTypeArr: [], brands: '', brandsArr: [], country: '', address: '', website: '', mainProducts: '', mainProductsArr: [], cooperationLevel: '', cooperationLevelArr: [], riskLevel: '', riskLevelArr: [], paymentTerms: '', paymentTermsArr: [], followUpByArr: [], businessLicense: '', taxNumber: '', bankInfo: '', bankAccount: '', billTo: '', shipTo: '', introduction: '', remark: '', remarkSecond: '', status: 1, followUpBy: '', tagsFirst: '', tagsSecond: '', tagsThird: '', tagsSi: '', tagsFirstArr: [], tagsSecondArr: [], tagsThirdArr: [], tagsSiArr: [], contacts: [], attachments: [] }; this.uploadFiles = [] },
    addContact() { this.form.contacts.push({ contactName: '', post: '', phone: '', email: '', isPrimary: 0 }) },
    removeContact(index) { this.form.contacts.splice(index, 1) },
    onUploadSuccess(resp, file) {
      // 附件已入库（crm_supplier_attachment），后端返回 {id, fileName, createTime}
      const data = resp && resp.data ? resp.data : null
      if (data && data.id) {
        this.form.attachments.push({ id: data.id, fileName: data.fileName || file.name, createTime: data.createTime })
      }
    },
    uploadAttachmentData() {
      // 编辑已有供应商时携带供应商ID，新增时为空（保存供应商后由后端回填）
      return this.form.id ? { supplierId: this.form.id } : {}
    },
    downloadAttachment(row) {
      this.download('/crm/supplier/attachment/download/' + row.id, {}, row.fileName)
    },
    previewAttachment(row) {
      const ext = (row.fileName || '').split('.').pop().toLowerCase()
      // PDF/图片：blob 临时链接 + 新窗口浏览器原生预览
      const nativeMap = { pdf: 'application/pdf', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', bmp: 'image/bmp', webp: 'image/webp' }
      if (nativeMap[ext]) {
        request({ url: '/crm/supplier/attachment/download/' + row.id, method: 'post', responseType: 'blob' }).then(data => {
          if (data.type && data.type.indexOf('application/json') !== -1) {
            data.text().then(text => { const rspObj = JSON.parse(text); this.$modal.msgError(rspObj.msg || '附件不存在或已删除') })
            return
          }
          const blob = new Blob([data], { type: nativeMap[ext] })
          const url = window.URL.createObjectURL(blob)
          window.open(url, '_blank')
          // 延迟释放临时链接，避免新页面尚未加载完成即失效
          setTimeout(() => window.URL.revokeObjectURL(url), 60 * 1000)
        }).catch(() => { this.$modal.msgError('附件预览失败') })
        return
      }
      // 文本类：拉取文本内容在弹窗中展示
      if (ext === 'txt' || ext === 'md') {
        request({ url: '/crm/supplier/attachment/download/' + row.id, method: 'post', responseType: 'blob' }).then(data => {
          if (data.type && data.type.indexOf('application/json') !== -1) {
            data.text().then(text => { const rspObj = JSON.parse(text); this.$modal.msgError(rspObj.msg || '附件不存在或已删除') })
            return
          }
          data.text().then(text => { this.openPreviewDialog(row.fileName, '<pre>' + text.replace(/</g, '&lt;') + '</pre>') })
        }).catch(() => { this.$modal.msgError('附件预览失败') })
        return
      }
      // Excel/Word：后端解析为 HTML 表格，弹窗展示
      if (['xlsx', 'xls', 'csv', 'docx'].includes(ext)) {
        request({ url: '/crm/supplier/attachment/preview/' + row.id, method: 'post' }).then(res => {
          // 兼容旧后端：旧版接口把 String 预览 HTML 塞进 msg 而非 data（重载歧义），此处兜底取 msg；data 有值时优先用 data
          const html = res.data || (res.msg && res.msg !== '操作成功' ? res.msg : '') || '';
          if (res.code === 200 && html) {
            this.openPreviewDialog(row.fileName, html)
          } else {
            this.$modal.msgError((res.msg && res.msg !== '操作成功') ? res.msg : '附件预览失败')
          }
        }).catch(() => { this.$modal.msgError('附件预览失败') })
        return
      }
      // 其他格式（doc/zip 等）：不支持在线预览，引导下载查看
      this.$modal.confirm('该文件格式暂不支持在线预览，是否下载查看？').then(() => { this.downloadAttachment(row) }).catch(() => {})
    },
    openPreviewDialog(title, html) {
      this.previewTitle = title
      this.previewHtml = html
      this.previewOpen = true
    },
    removeAttachment(index) {
      const row = this.form.attachments[index]
      if (!row.id) { this.form.attachments.splice(index, 1); return }
      // 软删除：调后端接口将删除标记置为 1，成功后从页面列表移除
      delSupplierAttachment(row.id).then(() => {
        this.form.attachments.splice(index, 1)
        this.$modal.msgSuccess('附件已删除')
      }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '删除附件失败') })
    },
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
      this.form.followUpByArr = this.split(this.form.followUpBy)
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
      this.form.followUpBy = (this.form.followUpByArr || []).join(',')
    }
    ,updateCooperationStatus(row) {
      const data = { id: row.id, cooperationStatus: row.cooperationStatus }
      updateSupplier(data).then(() => { this.$modal.msgSuccess('合作状态已更新') }).catch(err => { this.$modal.msgError(err && err.msg ? err.msg : '更新失败') })
    }
    ,split(s) { return s ? s.split(',') : [] }
    ,remoteUsers(query) {
      this.userLoading = true
      listSupplierUsers({ keyword: query }).then(res => {
        this.userOptions = res.data || []
        this.userLoading = false
      }).catch(() => { this.userLoading = false })
    }
  }
}
</script>

<style scoped>
.mt10 { margin-top: 10px; }
.preview-content { max-height: 70vh; overflow: auto; padding: 4px; }
.preview-content >>> table { border-collapse: collapse; }
.preview-content >>> td, .preview-content >>> th { border: 1px solid #dcdfe6; padding: 4px 8px; white-space: nowrap; }
.preview-content >>> h4 { margin: 8px 0 4px; }
.preview-content >>> p { margin: 4px 0; }
</style>
