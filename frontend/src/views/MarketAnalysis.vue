<template>
  <div class="market-analysis-page">
    <div class="page-shell-head">
      <div>
        <div class="page-title">行情分析</div>
        <div class="page-sub">K线大屏与 BTC/ETH 当前行情结构</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="pro-tabs" @tab-change="onTabChange">
      <el-tab-pane label="当前行情" name="current">
        <CurrentMarket :active="activeTab === 'current'" />
      </el-tab-pane>
      <el-tab-pane label="K线大屏" name="kline">
        <KlineScreen />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import KlineScreen from '@/views/KlineScreen.vue'
import CurrentMarket from '@/views/CurrentMarket.vue'

const activeTab = ref('current')

function onTabChange() {
  window.dispatchEvent(new Event('resize'))
  nextTick(() => window.dispatchEvent(new Event('resize')))
}
</script>

<style scoped>
.market-analysis-page :deep(.page-title) { margin-bottom: 4px; }
.page-shell-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.page-sub { color: var(--text-secondary); font-size: 13px; }
.pro-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 600; }
.pro-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.pro-tabs :deep(.el-tabs__nav-wrap::after) { background: var(--border-color); opacity: .65; }
.pro-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #58a6ff, #3fb950);
}
</style>


