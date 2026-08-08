<template>
  <div class="app-container">
    <el-row :gutter="10">
      <el-col :span="24" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-monitor"></i> 基本信息</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%">
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf"><div class="cell">缓存类型</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">本地缓存 (Hutool LFU)</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">淘汰策略</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">LFU 最不经常使用</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">缓存容量</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">{{ cache.capacity }} 条</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">当前条目数</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.size">{{ cache.size }} 条</div></td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf"><div class="cell">缓存使用率</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">{{ usage }}%</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">过期机制</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">支持按Key设置过期</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">定时清理</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.capacity">后台线程每小时清理过期键</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">命名空间数</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.namespaces">{{ cache.namespaces.length }}</div></td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-pie-chart"></i> 命名空间分布</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <div ref="namespaces" style="height: 420px" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-odometer"></i> 容量使用率</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <div ref="usage" style="height: 420px" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getCache } from "@/api/monitor/cache"
import * as echarts from "echarts"

export default {
  name: "Cache",
  data() {
    return {
      // 命名空间分布图
      namespaceChart: null,
      // 容量使用率图
      usageChart: null,
      // cache信息
      cache: {},
      // 缓存使用率（百分比）
      usage: 0
    }
  },
  created() {
    this.getList()
    this.openLoading()
  },
  methods: {
    /** 查询缓存信息 */
    getList() {
      getCache().then((response) => {
        this.cache = response.data
        this.$modal.closeLoading()
        // 计算缓存使用率（当前条目数 / 总容量 * 100）
        this.usage = this.cache.capacity ? (this.cache.size / this.cache.capacity * 100).toFixed(2) : 0

        // 命名空间分布饼图（按各业务命名空间的条目数占比展示）
        this.namespaceChart = echarts.init(this.$refs.namespaces, "macarons")
        this.namespaceChart.setOption({
          tooltip: {
            trigger: "item",
            formatter: "{a} <br/>{b} : {c} 条 ({d}%)",
          },
          legend: {
            bottom: 0,
            data: this.cache.namespaces.map(item => item.remark)
          },
          series: [
            {
              name: "命名空间",
              type: "pie",
              roseType: "radius",
              radius: [15, 95],
              center: ["50%", "38%"],
              data: this.cache.namespaces.map(item => ({ name: item.remark, value: item.count })),
              animationEasing: "cubicInOut",
              animationDuration: 1000,
            }
          ]
        })
        // 容量使用率仪表盘（百分比）
        this.usageChart = echarts.init(this.$refs.usage, "macarons")
        this.usageChart.setOption({
          tooltip: {
            formatter: "{b} <br/>{a} : " + this.usage + "%",
          },
          series: [
            {
              name: "容量使用率",
              type: "gauge",
              min: 0,
              max: 100,
              detail: {
                formatter: this.usage + "%",
              },
              data: [
                {
                  value: parseFloat(this.usage),
                  name: "缓存占用",
                }
              ]
            }
          ]
        })
        window.addEventListener("resize", () => {
          this.namespaceChart.resize()
          this.usageChart.resize()
        })
      })
    },
    // 打开加载层
    openLoading() {
      this.$modal.loading("正在加载缓存监控数据，请稍候！")
    }
  }
}
</script>
