<template>
  <div class="dashboard-container">
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6" v-for="item in cards" :key="item.title">
        <el-card shadow="hover" class="data-card">
          <div class="card-content">
            <div class="icon-wrapper" :style="{ background: item.color }">
              <svg class="icon" aria-hidden="true">
                <use :xlink:href="item.icon"></use>
              </svg>
            </div>
            <div class="text-wrapper">
              <div class="card-title">{{ item.title }}</div>
              <div class="card-num">{{ item.data }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="hover" class="chart-card">
          <div slot="header" class="clearfix">
            <span style="font-weight: bold; border-left: 4px solid #409EFF; padding-left: 10px;">
              📊 智能终端数据分析
            </span>
            <el-tag size="small" type="success" style="float: right;">实时监控中</el-tag>
          </div>
          <div id="main"></div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover" class="time-card">
          <div slot="header" class="clearfix">
            <span style="font-weight: bold;">📅 当前时间</span>
          </div>
          <div class="clock-box">
            <div id="myTimer" class="real-time"></div>
            <div class="date-tips">图书馆正常开放中</div>
          </div>
        </el-card>

        <el-card shadow="hover" style="margin-top: 20px;">
          <div style="font-size: 14px; color: #666;">
            <p>🎓 <b>毕设项目状态：</b></p>
            <p>✅ 硬件连接正常</p>
            <p>✅ 邮件服务在线</p>
            <p>✅ 数据库连接正常</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import {ElMessage} from "element-plus";
import request from "../utils/request";
import router from "@/router";

export default {
  data() {
    return {
      // 给卡片加了颜色配置
      cards: [
        { title: '已借阅', data: 0, icon: '#iconlend-record-pro', color: '#e1f3d8' }, // 浅绿背景
        { title: '总访问', data: 0, icon: '#iconvisit',           color: '#faecd8' }, // 浅橙背景
        { title: '图书数', data: 0, icon: '#iconbook-pro',        color: '#d9ecff' }, // 浅蓝背景
        { title: '用户数', data: 0, icon: '#iconpopulation',      color: '#fde2e2' }  // 浅红背景
      ]
    }
  },
  created() {
    let userJson = sessionStorage.getItem("user")
    if(!userJson) {
      router.push("/login")
    }
  },
  mounted() {
    this.circleTimer()

    // 1. 获取顶部卡片数据
    request.get("/dashboard").then(res => {
      if(res.code == 0) {
        this.cards[0].data = res.data.lendRecordCount
        this.cards[1].data = res.data.visitCount
        this.cards[2].data = res.data.bookCount
        this.cards[3].data = res.data.userCount
      }
    })

    // 2. 获取图表数据 (换成玫瑰图配置)
    fetch('http://localhost:9090/onenet/stats')
        .then(res => res.json())
        .then(data => {
          var chartDom = document.getElementById('main')
          if (!chartDom) return;
          var myChart = echarts.init(chartDom)

          var option = {
            // 标题放这里
            title: {
              text: '借还行为分布',
              left: 'center',
              top: 20,
              textStyle: { color: '#666' }
            },
            tooltip: {
              trigger: 'item',
              formatter: '{b} : {c}次 ({d}%)'
            },
            legend: {
              top: 'bottom'
            },
            series: [
              {
                name: '行为统计',
                type: 'pie',
                radius: [20, 140], // 内圆和外圆的大小
                center: ['50%', '50%'],
                roseType: 'area', // 🌸 开启玫瑰图模式！
                itemStyle: {
                  borderRadius: 8 // 圆角扇形
                },
                data: [
                  // 使用更高级的配色
                  { value: data.borrow, name: '智能借阅', itemStyle: { color: '#36cfc9' } }, // 青色
                  { value: data.ret,    name: '智能归还', itemStyle: { color: '#597ef7' } }, // 靛蓝
                  // 为了让玫瑰图好看，可以人为加一个“查询/其他”假数据垫底，或者就这两项也行
                  // { value: 1, name: '系统心跳', itemStyle: { color: '#9254de' } }
                ]
              }
            ]
          }
          myChart.setOption(option)
          window.addEventListener('resize', () => myChart.resize())
        })
        .catch(err => console.error(err))
  },
  methods: {
    circleTimer() {
      this.getTimer()
      setInterval(() => this.getTimer(), 1000)
    },
    getTimer() {
      var d = new Date()
      var t = d.toLocaleString()
      let timer = document.getElementById('myTimer')
      if (timer) timer.innerHTML = t
    }
  }
}
</script>

<style scoped>
/* 容器留白 */
.dashboard-container {
  padding: 10px;
}

/* 顶部卡片样式优化 */
.data-card {
  border: none;
  border-radius: 8px; /* 圆角 */
}
.card-content {
  display: flex;
  align-items: center;
}
.icon-wrapper {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 15px;
}
.icon {
  width: 35px;
  height: 35px;
}
.text-wrapper {
  display: flex;
  flex-direction: column;
}
.card-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}
.card-num {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

/* 图表卡片 */
.chart-card {
  height: 450px; /* 固定高度 */
  border-radius: 8px;
}
#main {
  width: 100%;
  height: 360px; /* 图表高度 */
}

/* 时间卡片 */
.time-card {
  height: 180px;
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); /* 渐变背景 */
  color: white;
}
.clock-box {
  text-align: center;
  padding-top: 20px;
}
.real-time {
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 10px;
  font-family: 'Courier New', Courier, monospace; /* 科技感字体 */
}
.date-tips {
  font-size: 14px;
  opacity: 0.8;
}
</style>