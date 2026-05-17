<template>
  <div class="trade-records-page">
    <div class="page-shell-head">
      <div>
        <div class="page-title">交易分析</div>
        <div class="page-sub">深度交易质量分析与交易订单全字段明细</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="pro-tabs">
      <el-tab-pane label="深度分析" name="analysis">
        <Analysis />
      </el-tab-pane>
      <el-tab-pane label="交易复盘" name="review">
        <Review embedded />
      </el-tab-pane>
      <el-tab-pane label="交易详情" name="orders">
        <Orders />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Orders from '@/views/Orders.vue'
import Analysis from '@/views/Analysis.vue'
import Review from '@/views/Review.vue'

const activeTab = ref('analysis')
</script>

<style scoped>
.trade-records-page :deep(.page-title) { margin-bottom: 4px; }
.page-shell-head { margin-bottom: 12px; }
.page-sub { color: var(--text-secondary); font-size: 13px; }
.pro-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 600; }
.pro-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.pro-tabs :deep(.el-tabs__nav-wrap::after) { background: var(--border-color); opacity: .65; }
.pro-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #58a6ff, #d29922);
}
</style>


