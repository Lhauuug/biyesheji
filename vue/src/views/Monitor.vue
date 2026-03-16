<template>
  <div class="dashboard-container">
    <div class="left-panel">
      <div class="monitor-card">
        <div class="card-header">
          <span class="title">📡 实时监控终端</span>
          <span class="status-badge">🟢 在线</span>
        </div>

        <div class="icon-wrapper" :class="actionClass + '-bg'">
          <div class="dynamic-icon">{{ currentIcon }}</div>
        </div>

        <div class="status-content">
          <div class="action-title">当前动作</div>
          <div class="action-value" :class="actionClass">{{ actionText }}</div>
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="label">交互学号</span>
            <span class="val">{{ studentId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">上报时间</span>
            <span class="val time-val">{{ deviceTime }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="right-panel">
      <div class="history-card">
        <div class="card-header border-bottom" style="display: flex; align-items: center; justify-content: space-between;">
          <span class="title" style="line-height: 1;">📜 操作日志记录 (最近20条)</span>

          <el-button
              type="primary"
              size="small"
              round
              icon="el-icon-refresh"
              @click="fetchHistory"
              style="display: flex; align-items: center;"
          >
            刷新列表
          </el-button>

        </div>
        <el-table :data="historyList" stripe style="width: 100%" height="500">
          <el-table-column prop="id" label="#" width="60" align="center"></el-table-column>

          <el-table-column label="动作类型" width="140" align="center">
            <template #default="scope">
              <el-tag :type="getActionTagType(scope.row.actionType)" effect="dark" size="small" round>
                {{ getActionText(scope.row.actionType) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="stuNum" label="学号" width="100" align="center">
            <template #default="scope"><b>{{ scope.row.stuNum }}</b></template>
          </el-table-column>

          <el-table-column label="发生时间" align="left">
            <template #default="scope">
              <i class="el-icon-time"></i> {{ formatTime(scope.row.deviceTime) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: "Monitor",
  data() {
    return {
      timer: null,
      rawData: [],    // 实时数据
      historyList: [], // 历史记录列表
      actionMap: {
        "1": "借阅书籍001", "2": "借阅书籍002",
        "3": "归还书籍001", "4": "归还书籍002",
        "5": "借阅被拒 (账号冻结)"
      }
    };
  },
  computed: {
    // === 实时卡片的逻辑 (保持不变) ===
    actionText() {
      const item = this.rawData.find(i => i.identifier === 'send');
      if (!item) return "等待数据...";
      return this.actionMap[item.value] || `未知操作(${item.value})`;
    },
    actionClass() {
      const text = this.actionText;
      if (text.includes("借阅被拒")) return "text-danger";
      if (text.includes("借阅")) return "text-orange";
      if (text.includes("归还")) return "text-blue";
      return "text-gray";
    },
    currentIcon() {
      const text = this.actionText;
      if (text.includes("借阅被拒")) return "🚫";
      if (text.includes("借阅")) return "📖";
      if (text.includes("归还")) return "📘";
      return "📡";
    },
    studentId() {
      const item = this.rawData.find(i => i.identifier === 'stu_num');
      return item ? `No.${item.value.toString().padStart(3, '0')}` : "--";
    },
    deviceTime() {
      const item = this.rawData.find(i => i.identifier === 'send');
      if (!item || !item.time) return "--";
      return new Date(Number(item.time)).toLocaleString();
    }
  },
  mounted() {
    this.refreshAll();
    // 每3秒同时刷新“实时卡片”和“历史表格”
    this.timer = setInterval(this.refreshAll, 3000);
  },
  beforeUnmount() {
    clearInterval(this.timer);
  },
  methods: {
    refreshAll() {
      this.fetchData();    // 查实时
      this.fetchHistory(); // 查历史
    },
    // 1. 获取实时数据
    fetchData() {
      axios.get('http://localhost:9090/onenet/data')
          .then(res => {
            // 注意：现在后端直接返回 JSON 字符串，axios 会自动解析
            // 如果后端直接返回的是对象，这里 res.data 就是对象
            const data = res.data;
            if (data && data.code === 0 && data.data) {
              this.rawData = data.data;
            }
          })
          .catch(e => console.error("实时数据获取失败", e));
    },
    // 2. 获取历史列表 (新接口)
    fetchHistory() {
      axios.get('http://localhost:9090/onenet/history')
          .then(res => {
            this.historyList = res.data; // 后端直接返回 List<DeviceLog>
          })
          .catch(e => console.error("历史记录获取失败", e));
    },
    // 辅助：把数字转中文 (给表格用)
    getActionText(val) {
      return this.actionMap[val] || val;
    },
    // 辅助：根据动作变Tag颜色
    getActionTagType(val) {
      const text = this.actionMap[val] || "";
      if (text.includes("借阅")) return "warning"; // 橙色
      if (text.includes("归还")) return "success"; // 绿色
      if (val === '5') return 'danger';//红色
      return "info";
    },
    // 辅助：格式化时间戳
    formatTime(ts) {
      return ts ? new Date(Number(ts)).toLocaleString() : "--";
    }
  }
};
</script>

<style scoped>
/* 整体布局：左右分栏 */
.dashboard-container {
  display: flex;
  gap: 20px; /* 左右间距 */
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 80px);
  align-items: flex-start; /* 顶部对齐 */
}

/* 左侧面板：固定宽度 */
.left-panel {
  flex: 0 0 380px; /* 宽度固定 380px */
}

/* 右侧面板：自动填满剩余空间 */
.right-panel {
  flex: 1;
}

/* 通用卡片样式 */
.monitor-card, .history-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  padding: 25px;
  transition: transform 0.3s;
  border: 1px solid #ebeef5;
}

.monitor-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
}

/* 标题栏 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
}
.border-bottom {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 15px;
  margin-bottom: 15px;
}
.title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.status-badge {
  background: #f0f9eb;
  color: #67c23a;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

/* 核心图标 */
.icon-wrapper {
  width: 100px;
  height: 100px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 50px;
}
.text-orange-bg { background-color: #fdf6ec; }
.text-blue-bg { background-color: #ecf5ff; }
.text-gray-bg { background-color: #f4f4f5; }

.dynamic-icon {
  animation: float 3s ease-in-out infinite;
}

/* 状态文字 */
.status-content {
  text-align: center;
  margin-bottom: 30px;
}
.action-title {
  color: #909399;
  font-size: 14px;
  margin-bottom: 5px;
}
.action-value {
  font-size: 24px;
  font-weight: 800;
}

/* 底部细节网格 */
.detail-grid {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 15px;
  display: flex;
  justify-content: space-between;
}
.detail-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 48%;
}
.label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.val { font-size: 15px; font-weight: 600; color: #606266; }
.time-val { font-size: 13px; }

/* 颜色工具类 */
.text-blue { color: #409eff; }
.text-orange { color: #e6a23c; }
.text-gray { color: #909399; }

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-5px); }
  100% { transform: translateY(0px); }
}
</style>