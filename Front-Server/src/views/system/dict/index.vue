<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="字典名称" prop="dictName">
        <el-input
          v-model="queryParams.dictName"
          placeholder="请输入字典名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="字典类型" prop="dictKey">
        <el-input
          v-model="queryParams.dictKey"
          placeholder="请输入字典类型"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="字典状态" clearable style="width: 240px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
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
          v-hasPermi="['system:dict:add']"
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
          v-hasPermi="['system:dict:edit']"
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
          v-hasPermi="['system:dict:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:dict:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-refresh"
          size="mini"
          @click="handleRefreshCache"
          v-hasPermi="['system:dict:remove']"
        >刷新缓存</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <div class="list-layout">
      <div class="table-container">
        <el-table v-loading="loading" :data="typeList" @selection-change="handleSelectionChange" height="100%">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="字典编号" align="center" prop="id" />
          <el-table-column label="字典名称" align="center" prop="dictName" :show-overflow-tooltip="true" />
          <el-table-column label="字典类型" align="center" :show-overflow-tooltip="true">
            <template slot-scope="scope">
              <el-button type="text" @click="handleDictData(scope.row)" class="link-type">
                <span>{{ scope.row.dictKey }}</span>
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" prop="status">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
                {{ scope.row.status === 1 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
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
                v-hasPermi="['system:dict:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleDelete(scope.row)"
                v-hasPermi="['system:dict:remove']"
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

    <!-- 添加或修改参数配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="form.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictKey">
          <el-input v-model="form.dictKey" placeholder="请输入字典类型" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 字典数据管理弹窗 -->
    <el-dialog :title="dictDataTitle" :visible.sync="dictDataOpen" width="800px" append-to-body>
      <div class="dict-data-container">
        <div class="dict-data-header">
          <el-button
            type="primary"
            plain
            icon="el-icon-plus"
            size="mini"
            @click="handleAddDictData"
            v-hasPermi="['system:dict:add']"
          >新增字典项</el-button>
        </div>
        
        <el-table v-loading="dictDataLoading" :data="dictDataList" height="400" style="margin-top: 10px;">
          <el-table-column label="字典标签" align="center" prop="itemLabel" :show-overflow-tooltip="true" />
          <el-table-column label="字典键值" align="center" prop="itemKey" />
          <el-table-column label="字典排序" align="center" prop="sortOrder" />
          <el-table-column label="状态" align="center" prop="status">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
                {{ scope.row.status === 1 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-edit"
                @click="handleUpdateDictData(scope.row)"
                v-hasPermi="['system:dict:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleDeleteDictData(scope.row)"
                v-hasPermi="['system:dict:remove']"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <!-- 字典项添加/修改弹窗 -->
      <el-dialog :title="dictDataFormTitle" :visible.sync="dictDataFormOpen" width="500px" append-to-body>
        <el-form ref="dictDataForm" :model="dictDataForm" :rules="dictDataRules" label-width="80px">
          <el-form-item label="字典标签" prop="itemLabel">
            <el-input v-model="dictDataForm.itemLabel" placeholder="请输入字典标签" />
          </el-form-item>
          <el-form-item label="字典键值" prop="itemKey">
            <el-input v-model="dictDataForm.itemKey" placeholder="请输入字典键值" />
          </el-form-item>
          <el-form-item label="字典排序" prop="sortOrder">
            <el-input-number v-model="dictDataForm.sortOrder" :min="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="dictDataForm.status">
              <el-radio :label="1">正常</el-radio>
              <el-radio :label="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="dictDataForm.remark" type="textarea" placeholder="请输入内容"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" @click="submitDictDataForm">确 定</el-button>
          <el-button @click="cancelDictDataForm">取 消</el-button>
        </div>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script>
import { listType, getType, delType, addType, updateType, refreshCache } from "@/api/system/dict/type";
import { listData, getData, delData, addData, updateData } from "@/api/system/dict/data";

export default {
  name: "Dict",
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
      // 字典表格数据
      typeList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 日期范围
      dateRange: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        dictName: undefined,
        dictKey: undefined,
        status: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        dictName: [
          { required: true, message: "字典名称不能为空", trigger: "blur" }
        ],
        dictKey: [
          { required: true, message: "字典类型不能为空", trigger: "blur" }
        ]
      },
      // 字典数据弹窗相关
      dictDataTitle: "",
      dictDataOpen: false,
      dictDataLoading: false,
      dictDataList: [],
      currentDict: null,
      // 字典项表单弹窗
      dictDataFormOpen: false,
      dictDataFormTitle: "",
      dictDataForm: {
        id: undefined,
        dictId: undefined,
        itemLabel: undefined,
        itemKey: undefined,
        sortOrder: 0,
        status: 1,
        remark: undefined
      },
      dictDataRules: {
        itemLabel: [
          { required: true, message: "字典标签不能为空", trigger: "blur" }
        ],
        itemKey: [
          { required: true, message: "字典键值不能为空", trigger: "blur" }
        ],
        sortOrder: [
          { required: true, message: "字典排序不能为空", trigger: "blur" }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    // 日期格式化函数
    parseTime(time, pattern) {
      if (arguments.length === 0 || !time) {
        return null
      }
      const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
      let date
      if (typeof time === 'object') {
        date = time
      } else {
        if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
          time = parseInt(time)
        } else if (typeof time === 'string') {
          time = time.replace(new RegExp(/-/gm), '/').replace('T', ' ').replace(new RegExp(/\.[\d]{3}/gm), '');
        }
        if ((typeof time === 'number') && (time.toString().length === 10)) {
          time = time * 1000
        }
        date = new Date(time)
      }
      const formatObj = {
        y: date.getFullYear(),
        m: date.getMonth() + 1,
        d: date.getDate(),
        h: date.getHours(),
        i: date.getMinutes(),
        s: date.getSeconds(),
        a: date.getDay()
      }
      const time_str = format.replace(/{(y|m|d|h|i|s|a)+}/g, (result, key) => {
        let value = formatObj[key]
        // Note: getDay() returns 0 on Sunday
        if (key === 'a') { return ['日', '一', '二', '三', '四', '五', '六'][value] }
        if (result.length > 0 && value < 10) {
          value = '0' + value
        }
        return value || 0
      })
      return time_str
    },
    
    /** 查询字典类型列表 */
    getList() {
      this.loading = true;
      // 合并日期范围参数
      const params = {
        ...this.queryParams,
        beginTime: this.dateRange && this.dateRange[0] ? this.dateRange[0] : undefined,
        endTime: this.dateRange && this.dateRange[1] ? this.dateRange[1] : undefined
      };
      
      listType(params).then(response => {
        this.typeList = response.rows;
        this.total = response.total;
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
    },
    
    /** 打开字典数据弹窗 */
    handleDictData(row) {
      this.currentDict = row;
      this.dictDataTitle = `字典数据管理 - ${row.dictName} (${row.dictKey})`;
      this.dictDataOpen = true;
      this.getDictDataList();
    },
    
    /** 查询字典数据列表 */
    getDictDataList() {
      this.dictDataLoading = true;
      const params = {
        dictId: this.currentDict.id || this.currentDict.dictId
      };
      
      listData(params).then(response => {
        this.dictDataList = response.rows;
        this.dictDataLoading = false;
      }).catch(error => {
        console.error('查询字典数据失败:', error);
        this.$message.error('查询字典数据失败');
        this.dictDataLoading = false;
      });
    },
    
    /** 打开新增字典项弹窗 */
    handleAddDictData() {
      this.dictDataFormTitle = "新增字典项";
      this.dictDataFormOpen = true;
      this.resetDictDataForm();
      // 确保传递正确的字典主表ID - 从当前选中的字典类型获取
      if (this.currentDict) {
        // 尝试从不同字段获取字典主表ID
        const dictId = this.currentDict.id || this.currentDict.dictId;
        if (dictId) {
          this.dictDataForm.dictId = dictId;
          console.log('设置dictId:', dictId);
        } else {
          console.error('无法获取字典主表ID，currentDict:', this.currentDict);
          this.$message.error('无法获取字典主表ID，请重新选择字典类型');
          return;
        }
      } else {
        console.error('currentDict is null');
        this.$message.error('请先选择字典类型');
        return;
      }
    },
    
    /** 打开修改字典项弹窗 */
    handleUpdateDictData(row) {
      this.dictDataFormTitle = "修改字典项";
      this.dictDataFormOpen = true;
      this.dictDataForm = Object.assign({}, row);
    },
    
    /** 删除字典项 */
    handleDeleteDictData(row) {
      const id = row.id;
      this.$confirm('是否确认删除字典项编号为"' + id + '"的数据项？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delData(id);
      }).then(() => {
        this.getDictDataList();
        this.$message.success("删除成功");
      }).catch(() => {});
    },
    
    /** 提交字典项表单 */
    submitDictDataForm() {
      this.$refs["dictDataForm"].validate(valid => {
        if (valid) {
          // 强制设置dictId，确保一定传递
          if (this.currentDict) {
            const dictId = this.currentDict.id || this.currentDict.dictId;
            if (dictId) {
              this.dictDataForm.dictId = dictId;
            } else {
              this.$message.error('无法获取字典主表ID');
              return;
            }
          } else {
            this.$message.error('请先选择字典类型');
            return;
          }
          
          // 调试：打印提交的数据
          console.log('提交字典项数据:', this.dictDataForm);
          
          if (this.dictDataForm.id != null) {
            updateData(this.dictDataForm).then(response => {
              this.$message.success("修改成功");
              this.dictDataFormOpen = false;
              this.getDictDataList();
            });
          } else {
            addData(this.dictDataForm).then(response => {
              this.$message.success("新增成功");
              this.dictDataFormOpen = false;
              this.getDictDataList();
            });
          }
        }
      });
    },
    
    /** 取消字典项表单 */
    cancelDictDataForm() {
      this.dictDataFormOpen = false;
      this.resetDictDataForm();
    },
    
    /** 重置字典项表单 */
    resetDictDataForm() {
      this.dictDataForm = {
        id: undefined,
        dictId: undefined,
        itemLabel: undefined,
        itemKey: undefined,
        sortOrder: 0,
        status: 1,
        remark: undefined
      };
      this.resetForm("dictDataForm");
    },
    
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: undefined,
        dictName: undefined,
        dictKey: undefined,
        status: 1,
        remark: undefined
      };
      if (this.$refs.form) {
        this.$refs.form.resetFields();
      }
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = [];
      if (this.$refs.queryForm) {
        this.$refs.queryForm.resetFields();
      }
      this.handleQuery();
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加字典类型";
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const dictId = row.id || this.ids
      getType(dictId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改字典类型";
      });
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != undefined) {
            updateType(this.form).then(response => {
              this.$message.success("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addType(this.form).then(response => {
              this.$message.success("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const dictIds = row.id || this.ids;
      this.$confirm('是否确认删除字典编号为"' + dictIds + '"的数据项？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delType(dictIds);
      }).then(() => {
        this.getList();
        this.$message.success("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/dict/export', {
        ...this.queryParams
      }, `dict_${new Date().getTime()}.xlsx`)
    },
    /** 刷新缓存按钮操作 */
    handleRefreshCache() {
      refreshCache().then(() => {
        this.$message.success("刷新成功");
      });
    }
  }
};
</script>