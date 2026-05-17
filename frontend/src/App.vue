<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <span class="logo-icon">币</span>
        <span class="logo-text">加密交易分析</span>
      </div>

      <el-menu
        :default-active="$route.path"
        router
        :background-color="isDark ? '#0d1117' : '#ffffff'"
        :text-color="isDark ? '#8b949e' : '#57606a'"
        active-text-color="#58a6ff"
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon><span>总览仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/balance">
          <el-icon><Money /></el-icon><span>资金变化</span>
        </el-menu-item>
        <el-menu-item index="/market-analysis">
          <el-icon><DataLine /></el-icon><span>行情分析</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon><span>交易分析</span>
        </el-menu-item>
        <el-menu-item index="/insights">
          <el-icon><Reading /></el-icon><span>交易心得</span>
        </el-menu-item>
        <el-menu-item index="/liquidation">
          <el-icon><Warning /></el-icon><span>爆仓分析</span>
        </el-menu-item>
        <el-menu-item index="/positions">
          <el-icon><Clock /></el-icon><span>当前持仓</span>
        </el-menu-item>
        <el-menu-item index="/calendar">
          <el-icon><Calendar /></el-icon><span>交易日历</span>
        </el-menu-item>
        <el-menu-item index="/market">
          <el-icon><Coin /></el-icon><span>主力大户</span>
        </el-menu-item>
        <el-menu-item index="/liquidation-maps">
          <el-icon><Histogram /></el-icon><span>清算热图</span>
        </el-menu-item>
      </el-menu>

      <div class="sync-area">
        <el-button type="primary" :loading="syncing" @click="doSync" class="sync-btn">
          <el-icon><Refresh /></el-icon>
          同步数据
        </el-button>
        <div v-if="syncResult" class="sync-result">
          新增 {{ syncResult.swap + syncResult.futures }} 条
        </div>
        <div class="theme-toggle" @click="toggle" :title="isDark ? '切换浅色模式' : '切换深色模式'">
          <span class="theme-icon">{{ isDark ? '☀' : '☾' }}</span>
          <span class="theme-label">{{ isDark ? '浅色模式' : '深色模式' }}</span>
        </div>
      </div>
    </el-aside>

    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { syncOrders } from '@/api'
import { useThemeState } from '@/composables/useThemeState'

const { isDark, toggleTheme } = useThemeState()
const toggle = toggleTheme

const syncing = ref(false)
const syncResult = ref(null)

async function doSync() {
  syncing.value = true
  try {
    syncResult.value = await syncOrders('all')
    ElMessage.success(`同步完成，新增 ${syncResult.value.swap + syncResult.value.futures} 条`)
  } finally {
    syncing.value = false
  }
}
</script>

<style>
:root {
  --bg-main: #090d12;
  --bg-card: #111821;
  --bg-hover: #172231;
  --bg-active: rgba(88, 166, 255, .13);
  --bg-input: #0c1219;
  --border-color: rgba(148, 163, 184, .16);
  --text-primary: #d7dee8;
  --text-secondary: #98a6b8;
  --text-dim: #66758a;
  --text-heading: #f6f9ff;
  --accent-blue: #58a6ff;
  --accent-green: #3fb950;
  --accent-red: #f85149;
  --accent-amber: #d29922;
  --shadow-card: 0 18px 50px rgba(0, 0, 0, .26);
  --el-color-primary: #58a6ff;
  --el-color-success: #3fb950;
  --el-color-danger: #f85149;
  --el-color-warning: #d29922;
}

html:not(.dark) {
  --bg-main: #eef3f8;
  --bg-card: rgba(255, 255, 255, .92);
  --bg-hover: #edf4ff;
  --bg-active: rgba(9, 105, 218, .10);
  --bg-input: #f8fafc;
  --border-color: rgba(100, 116, 139, .18);
  --text-primary: #1f2937;
  --text-secondary: #566274;
  --text-dim: #7b8798;
  --text-heading: #111827;
  --shadow-card: 0 18px 44px rgba(30, 41, 59, .10);
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  background: var(--bg-main);
  color: var(--text-primary);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  transition: background .2s, color .2s;
}

.app-layout { height: 100vh; overflow: hidden; }

.sidebar {
  background:
    linear-gradient(180deg, rgba(88, 166, 255, .06), transparent 220px),
    var(--bg-main);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: background .2s, border-color .2s;
}

.logo {
  padding: 22px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--border-color);
}

.logo-icon {
  width: 34px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: linear-gradient(135deg, #58a6ff, #3fb950);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.logo-text { font-size: 16px; font-weight: 700; color: var(--text-heading); }

.sidebar-menu { border-right: none; flex: 1; }
.sidebar-menu .el-menu-item {
  border-radius: 8px;
  margin: 3px 8px;
  transition: background .18s ease, color .18s ease, transform .18s ease;
}
.sidebar-menu .el-menu-item:hover { transform: translateX(2px); }
.sidebar-menu .el-menu-item.is-active {
  background: var(--bg-active) !important;
  box-shadow: inset 2px 0 0 var(--accent-blue);
}

.sync-area { padding: 16px; border-top: 1px solid var(--border-color); }
.sync-btn { width: 100%; }
.sync-result { text-align: center; margin-top: 8px; font-size: 12px; color: var(--accent-green); }

.theme-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  transition: background .15s;
}

.theme-toggle:hover { background: var(--bg-hover); }
.theme-icon { font-size: 16px; }
.theme-label { font-size: 13px; color: var(--text-secondary); }

.main-content {
  background:
    radial-gradient(circle at 18% 8%, rgba(88, 166, 255, .075), transparent 28%),
    linear-gradient(180deg, rgba(63, 185, 80, .035), transparent 260px),
    var(--bg-main);
  overflow-y: auto;
  padding: 24px;
  transition: background .2s;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(14px);
  transition: transform .18s ease, background .2s, border-color .2s, box-shadow .2s;
}
.stat-card:hover { transform: translateY(-2px); border-color: rgba(88, 166, 255, .28); }

.chart-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(14px);
  transition: transform .18s ease, background .2s, border-color .2s, box-shadow .2s;
}
.chart-card:hover { border-color: rgba(88, 166, 255, .22); }

.card-title {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-heading);
  margin-bottom: 24px;
  letter-spacing: 0;
}

.text-muted { color: var(--text-secondary) !important; }
.text-dim { color: var(--text-dim) !important; }

html:not(.dark) .el-table th.el-table__cell {
  background-color: var(--bg-card) !important;
  color: var(--text-secondary) !important;
  border-color: var(--border-color) !important;
}

html:not(.dark) .el-table td.el-table__cell {
  background-color: var(--bg-card) !important;
  color: var(--text-primary) !important;
  border-color: var(--border-color) !important;
}

html:not(.dark) .el-table tr { background: transparent; }

html:not(.dark) .el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background: var(--bg-hover) !important;
}

html:not(.dark) .el-pagination { --el-pagination-bg-color: var(--bg-card); }

html:not(.dark) .el-input__wrapper,
html:not(.dark) .el-select .el-input__wrapper {
  background: var(--bg-input) !important;
}

.el-button {
  transition: transform .16s ease, box-shadow .16s ease, background .16s ease;
}
.el-button:hover { transform: translateY(-1px); }
.el-button--primary {
  background: linear-gradient(135deg, #58a6ff, #3fb950) !important;
  border-color: transparent !important;
  box-shadow: 0 10px 24px rgba(88, 166, 255, .20);
}
.el-tabs__item { letter-spacing: 0; }
.el-table {
  --el-table-border-color: var(--border-color);
  --el-table-header-bg-color: var(--bg-card);
  --el-table-row-hover-bg-color: var(--bg-hover);
}
@keyframes pageFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.main-content > * { animation: pageFadeIn .32s ease both; }
</style>
