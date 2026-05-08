<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <span class="logo-icon">OKX</span>
        <span class="logo-text">交易分析</span>
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
        <el-menu-item index="/current-market">
          <el-icon><TrendCharts /></el-icon><span>当前行情</span>
        </el-menu-item>
        <el-menu-item index="/positions">
          <el-icon><Clock /></el-icon><span>当前持仓</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><TrendCharts /></el-icon><span>深度分析</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon><span>交易记录</span>
        </el-menu-item>
        <el-menu-item index="/liquidation">
          <el-icon><Warning /></el-icon><span>爆仓分析</span>
        </el-menu-item>
        <el-menu-item index="/market">
          <el-icon><Coin /></el-icon><span>主力大户</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><EditPen /></el-icon><span>交易复盘</span>
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
import { useTheme } from '@/composables/useTheme'

const { isDark, toggle } = useTheme()

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
  --bg-main: #0d1117;
  --bg-card: #161b22;
  --bg-hover: #1c2128;
  --bg-active: #1f2937;
  --bg-input: #0d1117;
  --border-color: #21262d;
  --text-primary: #c9d1d9;
  --text-secondary: #8b949e;
  --text-dim: #6e7681;
  --text-heading: #f0f6fc;
}

html:not(.dark) {
  --bg-main: #f5f7fa;
  --bg-card: #ffffff;
  --bg-hover: #f3f4f6;
  --bg-active: #dbeafe;
  --bg-input: #f8f9fa;
  --border-color: #d0d7de;
  --text-primary: #24292f;
  --text-secondary: #57606a;
  --text-dim: #6b7280;
  --text-heading: #24292f;
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
  background: var(--bg-main);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: background .2s, border-color .2s;
}

.logo {
  padding: 20px 16px;
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
  border-radius: 6px;
  background: #58a6ff;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.logo-text { font-size: 16px; font-weight: 700; color: var(--text-heading); }

.sidebar-menu { border-right: none; flex: 1; }
.sidebar-menu .el-menu-item { border-radius: 6px; margin: 2px 8px; }
.sidebar-menu .el-menu-item.is-active { background: var(--bg-active) !important; }

.sync-area { padding: 16px; border-top: 1px solid var(--border-color); }
.sync-btn { width: 100%; }
.sync-result { text-align: center; margin-top: 8px; font-size: 12px; color: #3fb950; }

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
  background: var(--bg-main);
  overflow-y: auto;
  padding: 24px;
  transition: background .2s;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  transition: background .2s, border-color .2s;
}

.chart-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  transition: background .2s, border-color .2s;
}

.card-title {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-heading);
  margin-bottom: 24px;
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
</style>
