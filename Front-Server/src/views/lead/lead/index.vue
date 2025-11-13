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
      <el-form-item label="线索状态" prop="leadStatus">
        <el-select v-model="queryParams.leadStatus" placeholder="请选择线索状态" clearable>
          <el-option
            v-for="dict in dict.type.lead_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="跟进人" prop="followBy">
        <el-input
          v-model="queryParams.followBy"
          placeholder="请输入跟进人"
          clearable
          @keyup.enter.native="handleQuery"
        />
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
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="leadList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="线索编号" align="center" prop="leadCode" />
      <el-table-column label="线索名称" align="center" prop="leadName" />
      <el-table-column label="所属国家" align="center" prop="country" />
      <el-table-column label="线索类型" align="center" prop="leadType">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.lead_type" :value="scope.row.leadType"/>
        </template>
      </el-table-column>
      <el-table-column label="线索状态" align="center" prop="leadStatus">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.lead_status" :value="scope.row.leadStatus"/>
        </template>
      </el-table-column>
      <el-table-column label="跟进人" align="center" prop="followBy">
        <template slot-scope="scope">
          <span>{{ getUserName(scope.row.followBy) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="预估金额" align="center" prop="estimatedAmount" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
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
          <el-button
            size="mini"
            type="text"
            icon="el-icon-user"
            @click="handleConvert(scope.row)"
            v-if="scope.row.leadStatus === '1'"
          >转客户</el-button>
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

    <!-- 添加或修改线索信息对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="线索名称" prop="leadName">
          <el-input v-model="form.leadName" placeholder="请输入线索名称" />
        </el-form-item>
        <el-form-item label="所属国家" prop="country">
          <el-input v-model="form.country" placeholder="请输入所属国家" />
        </el-form-item>
        <el-form-item label="线索类型" prop="leadType">
          <el-select v-model="form.leadType" placeholder="请选择线索类型">
            <el-option
              v-for="dict in dict.type.lead_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="官网地址" prop="website">
          <el-input v-model="form.website" placeholder="请输入官网地址" />
        </el-form-item>
        <el-form-item label="预估金额" prop="estimatedAmount">
          <el-input v-model="form.estimatedAmount" placeholder="请输入预估金额" />
        </el-form-item>
        <el-form-item label="介绍信息" prop="introduction">
          <el-input v-model="form.introduction" type="textarea" placeholder="请输入介绍信息" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listLead, getLead, delLead, addLead, updateLead, convertToCustomer } from "@/api/lead/lead";
import { listUser } from "@/api/system/user";

export default {
  name: "Lead",
  dicts: ['lead_type', 'lead_status'],
  data() {
    return {
      // 用户选项
      userOptions: [],
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
      // 线索信息表格数据
      leadList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        leadName: null,
        leadStatus: null,
        followBy: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        leadName: [
          { required: true, message: "线索名称不能为空", trigger: "blur" }
        ],
        leadType: [
          { required: true, message: "线索类型不能为空", trigger: "change" }
        ],
      }
    };
  },
  created() {
    this.getList();
    this.getUsers();
  },
  methods: {
    /** 查询用户列表 */
    getUsers() {
      listUser().then(response => {
        this.userOptions = response.rows;
      });
    },
    /** 查询线索信息列表 */
    getList() {
      this.loading = true;
      listLead(this.queryParams).then(response => {
        this.leadList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        leadCode: null,
        leadName: null,
        country: null,
        leadType: null,
        website: null,
        estimatedAmount: null,
        introduction: null,
        leadStatus: "1"
      };
      this.resetForm("form");
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
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加线索信息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getLead(id).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改线索信息";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateLead(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addLead(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除线索信息编号为"' + ids + '"的数据项？').then(function() {
        return delLead(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 获取用户名称 */
    getUserName(userId) {
      if (!userId) return '';
      const user = this.userOptions.find(item => item.userId === userId);
      return user ? user.userName : '';
    },
    
    /** 转客户操作 */
    handleConvert(row) {
      this.$modal.confirm('是否确认将线索"' + row.leadName + '"转为客户？').then(function() {
        return convertToCustomer(row.id);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("转换成功");
      }).catch(() => {});
    }
  }
};
</script>