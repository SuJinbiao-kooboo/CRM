<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="询价单号" prop="inquiryCode">
        <el-input
          v-model="queryParams.inquiryCode"
          placeholder="请输入询价单号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户名称" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="询价状态" clearable>
          <el-option label="待报价" value="0" />
          <el-option label="已报价" value="1" />
          <el-option label="已确认" value="2" />
          <el-option label="已拒绝" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="inquiryList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="询价编号" align="center" prop="inquiryId" />
      <el-table-column label="询价单号" align="center" prop="inquiryCode" />
      <el-table-column label="客户名称" align="center" prop="customerName" />
      <el-table-column label="联系人" align="center" prop="contactName" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" />
      <el-table-column label="询价状态" align="center">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="询价日期" align="center" prop="inquiryDate" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.inquiryDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script>
import { listInquiry, getInquiry, delInquiry, addInquiry, updateInquiry } from "@/api/inquiry/inquiry";

export default {
  name: "InquiryIndex",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 询价表格数据
      inquiryList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        inquiryCode: undefined,
        customerName: undefined,
        status: undefined
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询询价列表 */
    getList() {
      this.loading = true;
      // 调用真实API接口
      listInquiry(this.queryParams).then(response => {
        this.inquiryList = response.rows;
        this.total = response.total;
        this.loading = false;
      }).catch(error => {
        console.error('获取询价列表失败:', error);
        this.loading = false;
        this.$modal.msgError("获取询价列表失败");
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.inquiryId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.$router.push("/inquiry/inquiry-add");
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.$router.push("/inquiry/inquiry-edit/" + row.inquiryId);
    },
    /** 详情按钮操作 */
    handleDetail(row) {
      this.$router.push("/inquiry/inquiry-detail/" + row.inquiryId);
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const inquiryIds = row.inquiryId || this.ids;
      this.$modal.confirm('是否确认删除询价编号为"' + inquiryIds + '"的数据项？').then(function() {
        return delInquiry(inquiryIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 获取状态类型 */
    getStatusType(status) {
      const statusMap = {
        '0': 'info',
        '1': 'warning',
        '2': 'success',
        '3': 'danger'
      };
      return statusMap[status] || '';
    },
    /** 获取状态文本 */
    getStatusText(status) {
      const statusMap = {
        '0': '待报价',
        '1': '已报价',
        '2': '已确认',
        '3': '已拒绝'
      };
      return statusMap[status] || '未知';
    }
  }
};
</script>