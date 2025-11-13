<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="50px" style="margin-bottom: 5px;">
      <el-form-item label="编码" prop="supplierCode">
        <el-input
          v-model="queryParams.supplierCode"
          placeholder="编码"
          clearable
          @keyup.enter.native="handleQuery"
          style="width: 100px;"
        />
      </el-form-item>
      <el-form-item label="名称" prop="supplierName">
        <el-input
          v-model="queryParams.supplierName"
          placeholder="名称"
          clearable
          @keyup.enter.native="handleQuery"
          style="width: 120px;"
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input
          v-model="queryParams.contactName"
          placeholder="联系人"
          clearable
          @keyup.enter.native="handleQuery"
          style="width: 80px;"
        />
      </el-form-item>
      <el-form-item label="类型" prop="supplierType">
        <el-select v-model="queryParams.supplierType" placeholder="类型" clearable style="width: 80px;">
          <el-option label="原材料" value="1" />
          <el-option label="设备" value="2" />
          <el-option label="服务" value="3" />
          <el-option label="其他" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 70px;">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
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
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
        >导出</el-button>
      </el-col>
    </el-row>

    <div class="list-layout">
      <div class="table-container">
        <el-table v-loading="loading" :data="supplierList" @selection-change="handleSelectionChange" height="100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="供应商编码" align="center" prop="supplierCode" :show-overflow-tooltip="true" />
      <el-table-column label="供应商名称" align="center" prop="supplierName" :show-overflow-tooltip="true" />
      <el-table-column label="简称" align="center" prop="supplierShortName" :show-overflow-tooltip="true" />
      <el-table-column label="联系人" align="center" prop="contactName" :show-overflow-tooltip="true" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" :show-overflow-tooltip="true" />
      <el-table-column label="邮箱" align="center" prop="contactEmail" :show-overflow-tooltip="true" />
      <el-table-column label="职位" align="center" prop="contactPosition" :show-overflow-tooltip="true" />
      <el-table-column label="微信" align="center" prop="wechat" :show-overflow-tooltip="true" />
      <el-table-column label="WhatsApp" align="center" prop="whatsapp" :show-overflow-tooltip="true" />
      <el-table-column label="QQ" align="center" prop="qq" :show-overflow-tooltip="true" />
      <el-table-column label="供应商类型" align="center" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          {{ getSupplierTypeText(scope.row.supplierType) }}
        </template>
      </el-table-column>
      <el-table-column label="合作级别" align="center" prop="cooperationLevel" :show-overflow-tooltip="true" />
      <el-table-column label="信用等级" align="center" prop="creditRating" :show-overflow-tooltip="true" />
      <el-table-column label="标签" align="center" prop="tags" width="120" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.tags" type="info" size="small">{{ scope.row.tags }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="1"
            :inactive-value="0"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <span>{{ formatDateTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
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

    <!-- 标签编辑对话框 -->
    <el-dialog title="编辑标签" :visible.sync="tagsDialogVisible" width="400px" append-to-body>
      <el-form :model="tagsForm" ref="tagsForm" label-width="80px">
        <el-form-item label="标签内容">
          <el-input
            v-model="tagsForm.tags"
            placeholder="请输入标签内容，多个标签用逗号分隔"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="tagsDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitTags">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 供应商编辑对话框 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="800px" append-to-body>
      <el-form :model="form" ref="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="supplierCode">
              <el-input v-model="form.supplierCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row>
          <el-col :span="12">
            <el-form-item label="供应商简称">
              <el-input v-model="form.supplierShortName" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商类型" prop="supplierType">
              <el-select v-model="form.supplierType" placeholder="请选择供应商类型" style="width: 100%">
                <el-option
                  v-for="item in supplierTypeOptions"
                  :key="item.itemKey"
                  :label="item.itemLabel"
                  :value="item.itemKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item label="所属国家">
              <el-input v-model="form.country" placeholder="请输入所属国家" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="官网地址">
              <el-input v-model="form.website" placeholder="请输入官网地址" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="主营产品">
          <el-input type="textarea" v-model="form.mainProducts" placeholder="请输入主营产品" />
        </el-form-item>

        <el-row>
          <el-col :span="12">
            <el-form-item label="合作等级">
              <el-select v-model="form.cooperationLevel" placeholder="请选择合作等级" style="width: 100%">
                <el-option
                  v-for="item in cooperationLevelOptions"
                  :key="item.itemKey"
                  :label="item.itemLabel"
                  :value="item.itemKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="信用评级">
              <el-select v-model="form.creditRating" placeholder="请选择信用评级" style="width: 100%">
                <el-option
                  v-for="item in creditRatingOptions"
                  :key="item.itemKey"
                  :label="item.itemLabel"
                  :value="item.itemKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标签">
          <el-select 
            v-model="form.tags" 
            multiple 
            filterable 
            placeholder="请选择标签" 
            style="width: 100%"
          >
            <el-option
              v-for="item in supplierTagsOptions"
              :key="item.itemKey"
              :label="item.itemLabel"
              :value="item.itemKey"
            />
          </el-select>
        </el-form-item>

        <!-- 联系人管理 -->
        <el-form-item label="联系人信息">
          <div style="border: 1px solid #e6e6e6; border-radius: 4px; padding: 10px;">
            <div style="margin-bottom: 10px;">
              <el-button type="primary" size="mini" @click="addContact">添加联系人</el-button>
            </div>
            <el-table :data="form.contacts" size="mini" border>
              <el-table-column label="姓名" prop="contactName" width="100">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.contactName" placeholder="姓名" />
                </template>
              </el-table-column>
              <el-table-column label="职位" prop="position" width="100">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.position" placeholder="职位" />
                </template>
              </el-table-column>
              <el-table-column label="手机" prop="phone" width="120">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.phone" placeholder="手机" />
                </template>
              </el-table-column>
              <el-table-column label="邮箱" prop="email" width="150">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.email" placeholder="邮箱" />
                </template>
              </el-table-column>
              <el-table-column label="微信" prop="wechat" width="100">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.wechat" placeholder="微信" />
                </template>
              </el-table-column>
              <el-table-column label="WhatsApp" prop="whatsapp" width="100">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.whatsapp" placeholder="WhatsApp" />
                </template>
              </el-table-column>
              <el-table-column label="QQ" prop="qq" width="100">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.qq" placeholder="QQ" />
                </template>
              </el-table-column>
              <el-table-column label="主要联系人" width="100">
                <template slot-scope="scope">
                  <el-radio v-model="primaryContact" :label="scope.$index">是</el-radio>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template slot-scope="scope">
                  <el-button type="text" size="mini" @click="removeContact(scope.$index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>

        <el-form-item label="地址">
          <el-input type="textarea" v-model="form.address" placeholder="请输入地址" />
        </el-form-item>

        <el-form-item label="介绍信息">
          <el-input type="textarea" v-model="form.introduction" placeholder="请输入介绍信息" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSupplier, getSupplier, delSupplier, addSupplier, updateSupplier, exportSupplier } from "@/api/supplier/supplier";
import { getDicts } from "@/api/system/dict/data";
import { parseTime } from "@/utils/ruoyi";

export default {
  name: "SupplierIndex",
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
      // 供应商表格数据
      supplierList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        supplierCode: undefined,
        supplierName: undefined,
        supplierType: undefined,
        status: undefined,
        contactName: undefined
      },
      // 标签编辑对话框
      tagsDialogVisible: false,
      // 标签表单
      tagsForm: {
        id: undefined,
        tags: ''
      },
      // 供应商编辑对话框
      dialogVisible: false,
      dialogTitle: "",
      // 表单数据
      form: {
        id: null,
        supplierCode: null,
        supplierName: null,
        supplierShortName: null,
        country: null,
        supplierType: null,
        website: null,
        mainProducts: null,
        cooperationLevel: null,
        creditRating: null,
        paymentTerms: null,
        businessLicense: null,
        taxNumber: null,
        bankName: null,
        bankAccount: null,
        address: null,
        introduction: null,
        status: 1,
        remark: null,
        tags: null,
        contacts: []
      },
      // 主要联系人索引
      primaryContact: 0,
      // 字典数据
      supplierTagsOptions: [], // 供应商标签选项
      cooperationLevelOptions: [], // 合作等级选项
      creditRatingOptions: [], // 信用评级选项
      supplierTypeOptions: [], // 供应商类型选项
      // 表单验证规则
      rules: {
        supplierCode: [
          { required: true, message: "供应商编码不能为空", trigger: "blur" }
        ],
        supplierName: [
          { required: true, message: "供应商名称不能为空", trigger: "blur" }
        ],
        supplierType: [
          { required: true, message: "供应商类型不能为空", trigger: "change" }
        ]
      }
    };
  },
  created() {
    this.getList();
    this.loadDictData();
  },
  methods: {
    /** 加载字典数据 */
    loadDictData() {
      // 加载供应商标签字典
      getDicts('supplier_tags').then(response => {
        this.supplierTagsOptions = response.data || [];
      }).catch(() => {
        this.supplierTagsOptions = [];
        console.error('加载供应商标签字典失败');
      });
      
      // 加载合作等级字典
      getDicts('supplier_cop_level').then(response => {
        this.cooperationLevelOptions = response.data || [];
      }).catch(() => {
        this.cooperationLevelOptions = [];
        console.error('加载合作等级字典失败');
      });
      
      // 加载信用评级字典
      getDicts('supplier_believe_level').then(response => {
        this.creditRatingOptions = response.data || [];
      }).catch(() => {
        this.creditRatingOptions = [];
        console.error('加载信用评级字典失败');
      });
      
      // 加载供应商类型字典
      getDicts('supplier_type').then(response => {
        this.supplierTypeOptions = response.data || [];
      }).catch(() => {
        this.supplierTypeOptions = [];
        console.error('加载供应商类型字典失败');
      });
    },
    
    /** 查询供应商列表 */
    getList() {
      this.loading = true;
      listSupplier(this.queryParams).then(response => {
        this.supplierList = response.rows;
        this.total = response.total;
        this.loading = false;
      }).catch(() => {
        this.loading = false;
        this.$message.error('获取供应商列表失败');
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      // 安全地重置查询表单
      if (this.$refs.queryForm) {
        this.$refs.queryForm.resetFields();
      }
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.dialogVisible = true;
      this.dialogTitle = "添加供应商";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids;
      getSupplier(id).then(response => {
        const responseData = response.data;
        if (responseData) {
          const supplierData = responseData.supplier;
          const contacts = responseData.contacts;
          
          // 设置供应商基本信息
          this.form.id = supplierData.id;
          this.form.supplierCode = supplierData.supplierCode;
          this.form.supplierName = supplierData.supplierName;
          this.form.supplierShortName = supplierData.supplierShortName;
          this.form.country = supplierData.country;
          this.form.supplierType = supplierData.supplierType;
          this.form.website = supplierData.website;
          this.form.mainProducts = supplierData.mainProducts;
          this.form.cooperationLevel = supplierData.cooperationLevel;
          this.form.creditRating = supplierData.creditRating;
          this.form.paymentTerms = supplierData.paymentTerms;
          this.form.businessLicense = supplierData.businessLicense;
          this.form.taxNumber = supplierData.taxNumber;
          this.form.bankName = supplierData.bankName;
          this.form.bankAccount = supplierData.bankAccount;
          this.form.address = supplierData.address;
          this.form.introduction = supplierData.introduction;
          this.form.status = supplierData.status;
          this.form.remark = supplierData.remark;
          this.form.tags = supplierData.tags;
          
          // 设置联系人信息
          if (contacts && contacts.length > 0) {
            this.form.contacts = contacts.map(contact => ({
              contactName: contact.contactName || '',
              position: contact.position || '',
              phone: contact.phone || '',
              email: contact.email || '',
              wechat: contact.wechat || '',
              whatsapp: contact.whatsapp || '',
              qq: contact.qq || '',
              otherContact: contact.otherContact || '',
              isPrimary: contact.isPrimary || 0
            }));
            
            // 设置主要联系人索引
            const primaryIndex = contacts.findIndex(contact => contact.isPrimary === 1);
            this.primaryContact = primaryIndex >= 0 ? primaryIndex : 0;
          } else {
            this.form.contacts = [];
            this.primaryContact = 0;
          }
          
          this.dialogVisible = true;
          this.dialogTitle = "修改供应商";
        }
      }).catch(() => {
        this.$message.error("获取供应商信息失败");
      });
    },
    /** 加载联系人信息 */
    loadContacts(supplierId) {
      // 此方法已不再需要，联系人信息已在getInfo接口中返回
      // 保留方法以保持代码完整性
    },
    /** 添加联系人 */
    addContact() {
      this.form.contacts.push({
        contactName: '',
        position: '',
        phone: '',
        email: '',
        wechat: '',
        whatsapp: '',
        qq: '',
        otherContact: ''
      });
    },
    /** 删除联系人 */
    removeContact(index) {
      this.form.contacts.splice(index, 1);
      if (this.primaryContact === index) {
        this.primaryContact = 0;
      } else if (this.primaryContact > index) {
        this.primaryContact--;
      }
    },
    /** 表单重置 */
    reset() {
      this.form = {
        id: null,
        supplierCode: null,
        supplierName: null,
        supplierShortName: null,
        country: null,
        supplierType: null,
        website: null,
        mainProducts: null,
        cooperationLevel: null,
        creditRating: null,
        paymentTerms: null,
        businessLicense: null,
        taxNumber: null,
        bankName: null,
        bankAccount: null,
        address: null,
        introduction: null,
        status: 1,
        remark: null,
        tags: null,
        contacts: []
      };
      this.primaryContact = 0;
      // 安全地重置表单验证
      if (this.$refs.form) {
        this.$refs.form.resetFields();
      }
    },
    /** 提交表单 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          // 设置主要联系人
          if (this.form.contacts.length > 0) {
            this.form.contacts.forEach((contact, index) => {
              contact.isPrimary = index === this.primaryContact ? 1 : 0;
            });
          }

          // 处理标签字段：将数组转换为逗号分隔的字符串
          let tagsValue = '';
          if (this.form.tags && Array.isArray(this.form.tags)) {
            tagsValue = this.form.tags.join(',');
          } else if (this.form.tags) {
            tagsValue = this.form.tags;
          }

          // 构建供应商对象
          const supplier = {
            id: this.form.id,
            supplierCode: this.form.supplierCode,
            supplierName: this.form.supplierName,
            supplierShortName: this.form.supplierShortName,
            country: this.form.country,
            supplierType: this.form.supplierType,
            website: this.form.website,
            mainProducts: this.form.mainProducts,
            cooperationLevel: this.form.cooperationLevel,
            creditRating: this.form.creditRating,
            paymentTerms: this.form.paymentTerms,
            businessLicense: this.form.businessLicense,
            taxNumber: this.form.taxNumber,
            bankName: this.form.bankName,
            bankAccount: this.form.bankAccount,
            address: this.form.address,
            introduction: this.form.introduction,
            status: this.form.status,
            remark: this.form.remark,
            tags: tagsValue
          };

          // 构建请求数据
          const requestData = {
            supplier: supplier,
            contacts: this.form.contacts
          };

          if (this.form.id != null) {
            updateSupplier(requestData).then(response => {
              if (response.code === 200) {
                this.$message.success("修改成功");
                this.dialogVisible = false;
                this.getList();
              } else {
                this.$message.error(response.msg || "修改失败");
              }
            }).catch((error) => {
              this.$message.error("修改失败：" + (error.response?.data?.msg || error.message));
            });
          } else {
            addSupplier(requestData).then(response => {
              if (response.code === 200) {
                this.$message.success("新增成功");
                this.dialogVisible = false;
                this.getList();
              } else {
                this.$message.error(response.msg || "新增失败");
              }
            }).catch((error) => {
              this.$message.error("新增失败：" + (error.response?.data?.msg || error.message));
            });
          }
        }
      });
    },
    /** 标签按钮操作 */
    handleTags(row) {
      this.tagsForm = {
        id: row.id,
        tags: row.tags || ''
      };
      this.tagsDialogVisible = true;
    },
    /** 提交标签 */
    submitTags() {
      // 处理标签字段：将数组转换为逗号分隔的字符串
      let tagsValue = '';
      if (this.tagsForm.tags && Array.isArray(this.tagsForm.tags)) {
        tagsValue = this.tagsForm.tags.join(',');
      } else if (this.tagsForm.tags) {
        tagsValue = this.tagsForm.tags;
      }
      
      const requestData = {
        supplier: {
          id: this.tagsForm.id,
          tags: tagsValue
        },
        contacts: []
      };
      updateSupplier(requestData).then(response => {
        this.$message.success("标签更新成功");
        this.tagsDialogVisible = false;
        this.getList();
      }).catch(() => {
        this.$message.error("标签更新失败");
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const supplierIds = row.id || this.ids;
      this.$confirm('是否确认删除供应商编号为"' + supplierIds + '"的数据项？', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(function() {
        return delSupplier(supplierIds);
      }).then(() => {
        this.getList();
        this.$message.success("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      const queryParams = this.queryParams;
      this.$confirm('是否确认导出所有供应商数据项？', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(function() {
        return exportSupplier(queryParams);
      }).then(response => {
        this.download(response.msg);
      }).catch(() => {});
    },
    /** 供应商状态修改 */
    handleStatusChange(row) {
      let text = row.status === 1 ? "启用" : "停用";
      this.$confirm('确认要"' + text + '""' + row.supplierName + '"供应商吗？', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        const requestData = {
          supplier: {
            id: row.id,
            status: row.status
          },
          contacts: []
        };
        return updateSupplier(requestData);
      }).then(() => {
        this.$message.success(text + "成功");
      }).catch(() => {
        row.status = row.status === 1 ? 0 : 1;
      });
    },
    /** 获取供应商类型文本 */
    getSupplierTypeText(type) {
      const typeMap = {
        '1': '原材料供应商',
        '2': '设备供应商',
        '3': '服务供应商',
        '4': '其他'
      };
      return typeMap[type] || '未知';
    },
    /** 格式化时间显示 */
    formatDateTime(time) {
      return parseTime(time, '{y}-{m}-{d} {h}:{i}:{s}');
    }
  }
};
</script>