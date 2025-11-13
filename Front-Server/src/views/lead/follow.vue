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
      <el-form-item label="跟进类型" prop="followType">
        <el-select v-model="queryParams.followType" placeholder="跟进类型" clearable>
          <el-option label="电话跟进" value="1" />
          <el-option label="邮件跟进" value="2" />
          <el-option label="上门拜访" value="3" />
          <el-option label="其他" value="4" />
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
        >新增跟进</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="followList">
      <el-table-column label="跟进编号" align="center" prop="followId" />
      <el-table-column label="线索名称" align="center" prop="leadName" />
      <el-table-column label="跟进类型" align="center">
        <template slot-scope="scope">
          {{ getFollowTypeText(scope.row.followType) }}
        </template>
      </el-table-column>
      <el-table-column label="跟进内容" align="center" prop="followContent" show-overflow-tooltip />
      <el-table-column label="跟进人" align="center" prop="followUser" />
      <el-table-column label="跟进时间" align="center" prop="followTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.followTime) }}</span>
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
export default {
  name: "LeadFollow",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 跟进记录表格数据
      followList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        leadName: undefined,
        followType: undefined
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询跟进记录列表 */
    getList() {
      this.loading = true;
      // 模拟数据
      setTimeout(() => {
        this.followList = [
          {
            followId: 1,
            leadName: '张三',
            followType: '1',
            followContent: '电话联系客户，了解需求情况',
            followUser: '销售员A',
            followTime: new Date()
          },
          {
            followId: 2,
            leadName: '李四',
            followType: '3',
            followContent: '上门拜访，详细介绍产品',
            followUser: '销售员B',
            followTime: new Date()
          }
        ];
        this.total = this.followList.length;
        this.loading = false;
      }, 1000);
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
    /** 新增按钮操作 */
    handleAdd() {
      this.$message.info('新增功能开发中...');
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.$message.info('修改功能开发中...');
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      this.$message.info('删除功能开发中...');
    },
    /** 获取跟进类型文本 */
    getFollowTypeText(type) {
      const typeMap = {
        '1': '电话跟进',
        '2': '邮件跟进',
        '3': '上门拜访',
        '4': '其他'
      };
      return typeMap[type] || '未知';
    }
  }
};
</script>