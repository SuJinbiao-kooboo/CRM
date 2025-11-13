<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 统计卡片 -->
      <el-col :span="6">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>线索统计</span>
          </div>
          <div class="text item">
            <div class="stat-number">{{ leadStats.total || 0 }}</div>
            <div class="stat-label">总线索数</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>客户统计</span>
          </div>
          <div class="text item">
            <div class="stat-number">{{ customerStats.total || 0 }}</div>
            <div class="stat-label">总客户数</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>订单统计</span>
          </div>
          <div class="text item">
            <div class="stat-number">{{ orderStats.total || 0 }}</div>
            <div class="stat-label">总订单数</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>库存统计</span>
          </div>
          <div class="text item">
            <div class="stat-number">{{ stockStats.total || 0 }}</div>
            <div class="stat-label">总库存数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 销售趋势图表 -->
      <el-col :span="12">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>销售趋势</span>
          </div>
          <div id="salesChart" style="height: 300px;"></div>
        </el-card>
      </el-col>
      
      <!-- 客户分布图表 -->
      <el-col :span="12">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>客户分布</span>
          </div>
          <div id="customerChart" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 数据表格 -->
      <el-col :span="24">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>详细报表</span>
            <el-button style="float: right; padding: 3px 0" type="text" @click="handleExport">导出</el-button>
          </div>
          
          <el-table v-loading="loading" :data="reportList">
            <el-table-column label="日期" align="center" prop="reportDate" width="120" />
            <el-table-column label="线索数量" align="center" prop="leadCount" />
            <el-table-column label="客户数量" align="center" prop="customerCount" />
            <el-table-column label="订单数量" align="center" prop="orderCount" />
            <el-table-column label="订单金额" align="center" prop="orderAmount">
              <template slot-scope="scope">
                ¥{{ scope.row.orderAmount || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="库存数量" align="center" prop="stockCount" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getReportStatistics, getReportList, exportReport } from "@/api/report/report";

export default {
  name: "Report",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 统计数据
      leadStats: {},
      customerStats: {},
      orderStats: {},
      stockStats: {},
      // 报表列表
      reportList: []
    };
  },
  created() {
    this.getStatistics();
    this.getList();
  },
  methods: {
    /** 获取统计数据 */
    getStatistics() {
      getReportStatistics().then(response => {
        this.leadStats = response.data.leadStats || {};
        this.customerStats = response.data.customerStats || {};
        this.orderStats = response.data.orderStats || {};
        this.stockStats = response.data.stockStats || {};
      }).catch(error => {
        console.error('获取统计数据失败:', error);
        this.$modal.msgError("获取统计数据失败");
      });
    },
    /** 获取报表列表 */
    getList() {
      this.loading = true;
      getReportList().then(response => {
        this.reportList = response.rows || [];
        this.loading = false;
      }).catch(error => {
        console.error('获取报表列表失败:', error);
        this.loading = false;
        this.$modal.msgError("获取报表列表失败");
      });
    },
    /** 导出报表 */
    handleExport() {
      exportReport().then(response => {
        this.$modal.msgSuccess("导出成功");
      }).catch(error => {
        console.error('导出报表失败:', error);
        this.$modal.msgError("导出报表失败");
      });
    }
  }
};
</script>

<style scoped>
.box-card {
  margin-bottom: 20px;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  text-align: center;
}

.stat-label {
  font-size: 14px;
  color: #666;
  text-align: center;
  margin-top: 8px;
}

.text.item {
  padding: 20px 0;
}
</style>