<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="线索名称" prop="leadName">
        <el-input
          v-model="queryParams.leadName"
          placeholder="请输入线索名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="公司名称" prop="companyName">
        <el-input
          v-model="queryParams.companyName"
          placeholder="请输入公司名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="线索状态" clearable>
          <el-option label="新线索" value="0" />
          <el-option label="跟进中" value="1" />
          <el-option label="已转化" value="2" />
          <el-option label="已失效" value="3" />
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

    <div class="list-layout">
      <div class="table-container">
        <el-table v-loading="loading" :data="leadList" @selection-change="handleSelectionChange" height="100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="线索编号" align="center" prop="leadId" />
      <el-table-column label="线索名称" align="center" prop="leadName" />
      <el-table-column label="公司名称" align="center" prop="companyName" />
      <el-table-column label="联系人" align="center" prop="contactName" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" />
      <el-table-column label="状态" align="center">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
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
      </div>

      <div class="pagination-container">
        <pagination
          v-show="total>0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { listLead, getLead, delLead, addLead, updateLead } from "@/api/lead/lead";

export default {
  name: "LeadIndex",
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
      // 线索表格数据
      leadList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        leadName: undefined,
        companyName: undefined,
        status: undefined
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询线索列表 */
    getList() {
      this.loading = true;
      listLead(this.queryParams).then(response => {
        this.leadList = response.rows;
        this.total = response.total;
        this.loading = false;
      }).catch(() => {
        this.loading = false;
        this.$message.error('获取线索列表失败');
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
      this.ids = selection.map(item => item.leadId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.$message.info('新增功能开发中...');
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.$message.info('修改功能开发中...');
    },
    /** 详情按钮操作 */
    handleDetail(row) {
      this.$message.info('详情功能开发中...');
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      this.$message.info('删除功能开发中...');
    },
    /** 获取状态类型 */
    getStatusType(status) {
      const statusMap = {
        '0': '',
        '1': 'warning',
        '2': 'success',
        '3': 'danger'
      };
      return statusMap[status] || '';
    },
    /** 获取状态文本 */
    getStatusText(status) {
      const statusMap = {
        '0': '新线索',
        '1': '跟进中',
        '2': '已转化',
        '3': '已失效'
      };
      return statusMap[status] || '未知';
    }
  }
};
</script>