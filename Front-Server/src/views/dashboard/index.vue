<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <div class="card-panel">
          <div class="card-panel-icon-wrapper icon-people">
            <i class="el-icon-user-solid card-panel-icon"></i>
          </div>
          <div class="card-panel-description">
            <div class="card-panel-text">线索数量</div>
            <div class="card-panel-num">{{ dashboardData.leadCount }}</div>
          </div>
        </div>
      </el-col>
      
      <el-col :span="6">
        <div class="card-panel">
          <div class="card-panel-icon-wrapper icon-message">
            <i class="el-icon-s-custom card-panel-icon"></i>
          </div>
          <div class="card-panel-description">
            <div class="card-panel-text">客户数量</div>
            <div class="card-panel-num">{{ dashboardData.customerCount }}</div>
          </div>
        </div>
      </el-col>
      
      <el-col :span="6">
        <div class="card-panel">
          <div class="card-panel-icon-wrapper icon-money">
            <i class="el-icon-s-order card-panel-icon"></i>
          </div>
          <div class="card-panel-description">
            <div class="card-panel-text">订单数量</div>
            <div class="card-panel-num">{{ dashboardData.orderCount }}</div>
          </div>
        </div>
      </el-col>
      
      <el-col :span="6">
        <div class="card-panel">
          <div class="card-panel-icon-wrapper icon-shopping">
            <i class="el-icon-goods card-panel-icon"></i>
          </div>
          <div class="card-panel-description">
            <div class="card-panel-text">库存数量</div>
            <div class="card-panel-num">{{ dashboardData.stockCount }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>最近线索</span>
          </div>
          <el-table :data="recentLeads" style="width: 100%">
            <el-table-column prop="leadName" label="线索名称" />
            <el-table-column prop="companyName" label="公司名称" />
            <el-table-column prop="status" label="状态" />
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>最近订单</span>
          </div>
          <el-table :data="recentOrders" style="width: 100%">
            <el-table-column prop="orderNo" label="订单号" />
            <el-table-column prop="customerName" label="客户名称" />
            <el-table-column prop="totalAmount" label="金额" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: 'Dashboard',
  data() {
    return {
      dashboardData: {
        leadCount: 0,
        customerCount: 0,
        orderCount: 0,
        stockCount: 0
      },
      recentLeads: [],
      recentOrders: []
    }
  },
  created() {
    this.fetchDashboardData()
  },
  methods: {
    fetchDashboardData() {
      // 模拟数据，实际项目中应该调用API
      this.dashboardData = {
        leadCount: 156,
        customerCount: 89,
        orderCount: 234,
        stockCount: 1567
      }
      
      this.recentLeads = [
        { leadName: '张三', companyName: '阿里巴巴', status: '跟进中' },
        { leadName: '李四', companyName: '腾讯科技', status: '已转化' },
        { leadName: '王五', companyName: '百度公司', status: '跟进中' }
      ]
      
      this.recentOrders = [
        { orderNo: 'ORD001', customerName: '阿里巴巴', totalAmount: '¥50,000' },
        { orderNo: 'ORD002', customerName: '腾讯科技', totalAmount: '¥30,000' },
        { orderNo: 'ORD003', customerName: '百度公司', totalAmount: '¥80,000' }
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 20px;
  
  .card-panel {
    height: 108px;
    cursor: pointer;
    font-size: 12px;
    position: relative;
    overflow: hidden;
    color: #666;
    background: #fff;
    box-shadow: 4px 4px 40px rgba(0, 0, 0, .05);
    border-color: rgba(0, 0, 0, .05);
    
    &:hover {
      .card-panel-icon-wrapper {
        color: #fff;
      }
      
      .icon-people {
        background: #40c9c6;
      }
      
      .icon-message {
        background: #36a3f7;
      }
      
      .icon-money {
        background: #f4516c;
      }
      
      .icon-shopping {
        background: #34bfa3;
      }
    }
    
    .card-panel-icon-wrapper {
      float: left;
      margin: 14px 0 0 14px;
      padding: 16px;
      transition: all 0.38s ease-out;
      border-radius: 6px;
    }
    
    .card-panel-icon {
      float: left;
      font-size: 48px;
    }
    
    .card-panel-description {
      float: right;
      font-weight: bold;
      margin: 26px;
      margin-left: 0px;
      
      .card-panel-text {
        line-height: 18px;
        color: rgba(0, 0, 0, 0.45);
        font-size: 16px;
        margin-bottom: 12px;
      }
      
      .card-panel-num {
        font-size: 20px;
      }
    }
  }
  
  .icon-people {
    color: #40c9c6;
  }
  
  .icon-message {
    color: #36a3f7;
  }
  
  .icon-money {
    color: #f4516c;
  }
  
  .icon-shopping {
    color: #34bfa3;
  }
}
</style>