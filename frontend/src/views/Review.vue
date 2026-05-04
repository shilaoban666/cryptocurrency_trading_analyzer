<template>
  <div>
    <div class="page-title">🔍 交易复盘</div>

    <el-tabs v-model="activeTab" class="review-tabs" @tab-change="onTabChange">

      <!-- ══ Tab 1: 复盘记录 ══ -->
      <el-tab-pane label="📋 复盘记录" name="records">

        <!-- 筛选栏 -->
        <div class="chart-card" style="margin-bottom:16px">
          <el-row :gutter="12" align="middle">
            <el-col :span="5">
              <el-select v-model="filter.instId" placeholder="全部品种" clearable @change="load">
                <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-select v-model="filter.isWin" placeholder="全部结果" clearable @change="load">
                <el-option label="盈利" :value="1" />
                <el-option label="亏损" :value="0" />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-select v-model="filter.reviewed" placeholder="全部状态" clearable @change="load">
                <el-option label="已复盘" value="yes" />
                <el-option label="未复盘" value="no" />
              </el-select>
            </el-col>
            <el-col :span="11" style="text-align:right;font-size:13px" class="text-muted">
              共 {{ total }} 条 &nbsp;|&nbsp; 已复盘
              <span style="color:#58a6ff;font-weight:600">{{ stats.reviewedCount }}</span>
              &nbsp;/&nbsp;{{ stats.totalClosedCount }}
            </el-col>
          </el-row>
        </div>

        <!-- 主体：订单列表 + 复盘编辑 -->
        <div style="display:flex;gap:16px;align-items:flex-start">

          <!-- 左侧订单列表 -->
          <div class="chart-card order-list-card" style="width:420px;flex-shrink:0;padding:0">
            <div v-for="order in orders" :key="order.ordId"
                 class="order-row"
                 :class="{ active: selectedOrdId === order.ordId }"
                 @click="selectOrder(order)">
              <div class="order-row-top">
                <span class="order-inst">{{ order.instId }}</span>
                <el-tag :type="order.posSide === 'long' ? 'success' : 'danger'" size="small">
                  {{ order.posSide === 'long' ? '多' : '空' }}
                </el-tag>
                <span :class="['order-pnl', order.pnl > 0 ? 'profit' : 'loss']">
                  {{ order.pnl > 0 ? '+' : '' }}{{ order.pnl?.toFixed(2) }}U
                </span>
                <span :style="{ color: order.hasReview ? '#3fb950' : '#6e7681', fontSize: '11px' }"
                      :title="order.hasReview ? '已复盘' : '未复盘'">
                  {{ order.hasReview ? '●' : '○' }}
                </span>
              </div>
              <div class="order-row-sub text-muted">
                <span>{{ fmtDt(order.createTime) }}</span>
                <span style="margin-left:8px">{{ order.lever }}x</span>
                <span style="margin-left:8px">{{ fmtMin(order.holdingMinutes) }}</span>
              </div>
            </div>
            <div v-if="orders.length === 0" style="padding:32px;text-align:center" class="text-muted">
              暂无数据
            </div>
            <div style="padding:10px 16px;border-top:1px solid var(--border-color)">
              <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                             :total="total" :page-sizes="[20, 50]"
                             layout="sizes, prev, pager, next"
                             small @change="load" />
            </div>
          </div>

          <!-- 右侧复盘编辑 -->
          <div class="chart-card" style="flex:1;min-width:0">
            <div v-if="!selectedOrder" style="text-align:center;padding:80px 0" class="text-muted">
              点击左侧订单开始复盘
            </div>
            <template v-else>
              <!-- 订单信息头 -->
              <div class="review-header">
                <div class="review-order-info">
                  <span class="review-inst">{{ selectedOrder.instId }}</span>
                  <el-tag :type="selectedOrder.posSide === 'long' ? 'success' : 'danger'" size="small">
                    {{ selectedOrder.posSide === 'long' ? '做多' : '做空' }}
                  </el-tag>
                  <el-tag v-if="selectedOrder.isWin === 1" type="success" size="small">盈利</el-tag>
                  <el-tag v-else type="danger" size="small">亏损</el-tag>
                  <span :class="['review-pnl', selectedOrder.pnl > 0 ? 'profit' : 'loss']">
                    {{ selectedOrder.pnl > 0 ? '+' : '' }}{{ selectedOrder.pnl?.toFixed(2) }} USDT
                  </span>
                </div>
                <div class="text-muted" style="font-size:13px;margin-top:6px">
                  {{ fmtDt(selectedOrder.createTime) }} &nbsp;·&nbsp;
                  {{ selectedOrder.lever }}x 杠杆 &nbsp;·&nbsp;
                  持仓 {{ fmtMin(selectedOrder.holdingMinutes) }} &nbsp;·&nbsp;
                  手续费 {{ selectedOrder.fee?.toFixed(4) }} U
                </div>
              </div>

              <!-- 原因标签 -->
              <div class="section-label">
                {{ selectedOrder.isWin === 1 ? '✅ 盈利原因' : '❌ 亏损原因' }}
              </div>
              <el-checkbox-group v-model="form.reasons" class="tag-group">
                <el-checkbox v-for="r in currentReasons" :key="r" :label="r" :value="r" class="sig-tag">
                  {{ r }}
                </el-checkbox>
              </el-checkbox-group>

              <!-- 技术指标区（仅亏损单显示） -->
              <template v-if="selectedOrder.isWin === 0">
                <div class="section-label" style="margin-top:18px">📊 技术指标状态（入场时）</div>
                <div class="indicator-block ema-block">
                  <div class="indicator-title">EMA 均线</div>
                  <el-checkbox-group v-model="form.emaSignals" class="tag-group">
                    <el-checkbox v-for="s in EMA_SIGNALS" :key="s" :label="s" :value="s" class="sig-tag ema-tag">{{ s }}</el-checkbox>
                  </el-checkbox-group>
                </div>
                <div class="indicator-block kdj-block">
                  <div class="indicator-title">KDJ</div>
                  <el-checkbox-group v-model="form.kdjSignals" class="tag-group">
                    <el-checkbox v-for="s in KDJ_SIGNALS" :key="s" :label="s" :value="s" class="sig-tag kdj-tag">{{ s }}</el-checkbox>
                  </el-checkbox-group>
                </div>
                <div class="indicator-block macd-block">
                  <div class="indicator-title">MACD</div>
                  <el-checkbox-group v-model="form.macdSignals" class="tag-group">
                    <el-checkbox v-for="s in MACD_SIGNALS" :key="s" :label="s" :value="s" class="sig-tag macd-tag">{{ s }}</el-checkbox>
                  </el-checkbox-group>
                </div>
              </template>

              <!-- 复盘笔记 -->
              <div class="section-label" style="margin-top:18px">📝 复盘笔记</div>
              <el-input v-model="form.reviewText" type="textarea" :rows="5"
                        placeholder="记录交易思路、执行情况、经验教训..."
                        resize="none" class="review-textarea" />

              <div style="margin-top:14px;display:flex;align-items:center;gap:12px">
                <el-button type="primary" :loading="saving" @click="saveReview">保存复盘</el-button>
                <span v-if="savedAt" class="text-muted" style="font-size:12px">
                  已保存 {{ fmtDt(savedAt) }}
                </span>
              </div>
            </template>
          </div>
        </div>
      </el-tab-pane>

      <!-- ══ Tab 2: 数据统计 ══ -->
      <el-tab-pane label="📈 数据统计" name="stats">

        <!-- 概览卡片 -->
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="6" v-for="c in summaryCards" :key="c.label">
            <div class="stat-mini-card">
              <div class="stat-mini-val" :style="{ color: c.color }">{{ c.value }}</div>
              <div class="stat-mini-label">{{ c.label }}</div>
            </div>
          </el-col>
        </el-row>

        <!-- 行1：进度环 + 盈利原因饼 + 亏损原因饼 -->
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="6">
            <div class="chart-card">
              <div class="card-title">复盘完成进度</div>
              <div ref="progressRef" style="height:240px" />
            </div>
          </el-col>
          <el-col :span="9">
            <div class="chart-card">
              <div class="card-title">✅ 盈利原因分布</div>
              <div ref="winPieRef" style="height:240px" />
            </div>
          </el-col>
          <el-col :span="9">
            <div class="chart-card">
              <div class="card-title">❌ 亏损原因分布</div>
              <div ref="lossPieRef" style="height:240px" />
            </div>
          </el-col>
        </el-row>

        <!-- 行2：EMA / KDJ / MACD 水平柱状图 -->
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">EMA 信号分布（亏损单）</div>
              <div ref="emaChartRef" style="height:280px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">KDJ 信号分布（亏损单）</div>
              <div ref="kdjChartRef" style="height:280px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">MACD 信号分布（亏损单）</div>
              <div ref="macdChartRef" style="height:280px" />
            </div>
          </el-col>
        </el-row>

        <!-- 行3：盈利原因 hbar + 亏损原因 hbar（详细视图） -->
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">✅ 盈利原因明细</div>
              <div ref="winBarRef" style="height:260px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">❌ 亏损原因明细</div>
              <div ref="lossBarRef" style="height:260px" />
            </div>
          </el-col>
        </el-row>

      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { getSymbols, getReviewOrders, saveReview as apiSaveReview, getReviewStats } from '@/api'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme'
import dayjs from 'dayjs'

const { isDark, cs, initChart } = useTheme()

// ── 预设选项 ────────────────────────────────────────────────────────────────
const WIN_REASONS = [
  '趋势判断正确', '入场时机准确', '顺势加仓', '技术形态突破',
  '止盈执行到位', '资金管理得当', '情绪控制良好', '基本面支撑', '其他'
]
const LOSS_REASONS = [
  '逆势操作', '追涨杀跌', '止损执行不及时', '仓位过重',
  '方向判断失误', '情绪化交易', '技术指标失效', '突发行情', '其他'
]
const EMA_SIGNALS = [
  '均线多头排列', '均线空头排列', '均线粘合', '价格在均线上方',
  '价格在均线下方', '短期金叉', '短期死叉', '逆均线方向开仓'
]
const KDJ_SIGNALS = [
  'K超买(>80)', 'K超卖(<20)', 'KD金叉', 'KD死叉',
  '高位钝化', '低位钝化', 'KDJ背离', 'J值极端'
]
const MACD_SIGNALS = [
  'MACD金叉', 'MACD死叉', '红柱扩张', '红柱收缩',
  '绿柱扩张', '绿柱收缩', '零轴以上', '零轴以下',
  '顶背离', '底背离'
]

// ── 状态 ────────────────────────────────────────────────────────────────────
const activeTab  = ref('records')
const symbols    = ref([])
const orders     = ref([])
const total      = ref(0)
const page       = ref(1)
const pageSize   = ref(20)
const filter     = reactive({ instId: null, isWin: null, reviewed: null })
const stats      = ref({ reviewedCount: 0, totalClosedCount: 0 })

const selectedOrdId = ref(null)
const selectedOrder = ref(null)
const form = reactive({
  reviewText: '', reasons: [],
  emaSignals: [], kdjSignals: [], macdSignals: []
})
const saving  = ref(false)
const savedAt = ref(null)
const currentReasons = ref(WIN_REASONS)

// ── 图表 refs ───────────────────────────────────────────────────────────────
const progressRef  = ref(null)
const winPieRef    = ref(null)
const lossPieRef   = ref(null)
const winBarRef    = ref(null)
const lossBarRef   = ref(null)
const emaChartRef  = ref(null)
const kdjChartRef  = ref(null)
const macdChartRef = ref(null)
let charts = {}

// ── 概览卡片 ─────────────────────────────────────────────────────────────────
const summaryCards = computed(() => {
  const s = stats.value
  const reviewed = s.reviewedCount    || 0
  const tot      = s.totalClosedCount || 0
  const pct      = tot > 0 ? (reviewed / tot * 100).toFixed(1) : '0.0'
  return [
    { label: '总关仓单',  value: tot,              color: 'var(--text-heading)' },
    { label: '已复盘',    value: reviewed,          color: '#58a6ff' },
    { label: '待复盘',    value: tot - reviewed,    color: '#f0883e' },
    { label: '复盘完成率', value: pct + '%',
      color: parseFloat(pct) >= 50 ? '#3fb950' : '#f85149' },
  ]
})

// ── 数据加载 ─────────────────────────────────────────────────────────────────
async function load() {
  const res = await getReviewOrders({
    page: page.value - 1, size: pageSize.value,
    instId:   filter.instId   || undefined,
    isWin:    filter.isWin    ?? undefined,
    reviewed: filter.reviewed || undefined,
  })
  orders.value = res.list
  total.value  = res.total
}

async function loadStats() {
  stats.value = await getReviewStats()
  if (activeTab.value === 'stats') {
    await nextTick()
    renderAllCharts()
  }
}

function selectOrder(order) {
  selectedOrdId.value = order.ordId
  selectedOrder.value = order
  form.reviewText  = order.reviewText  || ''
  form.reasons     = [...(order.reasons    || [])]
  form.emaSignals  = [...(order.emaSignals || [])]
  form.kdjSignals  = [...(order.kdjSignals || [])]
  form.macdSignals = [...(order.macdSignals|| [])]
  savedAt.value    = null
  currentReasons.value = order.isWin === 1 ? WIN_REASONS : LOSS_REASONS
}

async function saveReview() {
  if (!selectedOrder.value) return
  saving.value = true
  try {
    const res = await apiSaveReview(selectedOrder.value.ordId, {
      reviewText:  form.reviewText,
      reasons:     form.reasons,
      emaSignals:  form.emaSignals,
      kdjSignals:  form.kdjSignals,
      macdSignals: form.macdSignals,
    })
    savedAt.value = res.updatedAt
    const target = orders.value.find(o => o.ordId === selectedOrder.value.ordId)
    if (target) {
      target.hasReview  = true
      target.reviewText = form.reviewText
      target.reasons    = [...form.reasons]
      target.emaSignals = [...form.emaSignals]
      target.kdjSignals = [...form.kdjSignals]
      target.macdSignals= [...form.macdSignals]
    }
    ElMessage.success('复盘已保存')
    loadStats()
  } finally {
    saving.value = false
  }
}

// ── Tab 切换 ─────────────────────────────────────────────────────────────────
async function onTabChange(tab) {
  if (tab === 'stats') {
    await nextTick()
    renderAllCharts()
  }
}

// ── 图表渲染 ─────────────────────────────────────────────────────────────────
function gi(key, el) {
  if (!charts[key]) charts[key] = initChart(el)
  return charts[key]
}

function renderAllCharts() {
  const s = stats.value
  if (progressRef.value)  gi('progress', progressRef.value).setOption(buildProgress(s))
  if (winPieRef.value)    gi('winPie',   winPieRef.value).setOption(buildPie(s.winReasons  || {}, WIN_PIE_COLORS))
  if (lossPieRef.value)   gi('lossPie',  lossPieRef.value).setOption(buildPie(s.lossReasons || {}, LOSS_PIE_COLORS))
  if (winBarRef.value)    gi('winBar',   winBarRef.value).setOption(buildHBar(s.winReasons  || {}, '#3fb950'))
  if (lossBarRef.value)   gi('lossBar',  lossBarRef.value).setOption(buildHBar(s.lossReasons || {}, '#f85149'))
  if (emaChartRef.value)  gi('ema',      emaChartRef.value).setOption(buildHBar(s.emaSignals  || {}, '#58a6ff'))
  if (kdjChartRef.value)  gi('kdj',      kdjChartRef.value).setOption(buildHBar(s.kdjSignals  || {}, '#f0883e'))
  if (macdChartRef.value) gi('macd',     macdChartRef.value).setOption(buildHBar(s.macdSignals || {}, '#a371f7'))
}

const WIN_PIE_COLORS  = ['#3fb950','#56d364','#7ee787','#a5f3b0','#c9f1d0','#26a641','#196c2e','#0f3d14','#2ea043']
const LOSS_PIE_COLORS = ['#f85149','#ff7b72','#ffa198','#ffb7b0','#da3633','#b91c1c','#991b1b','#7f1d1d','#ef4444']

function buildProgress(s) {
  const reviewed = s.reviewedCount    || 0
  const tot      = s.totalClosedCount || 0
  const pct      = tot > 0 ? Math.round(reviewed / tot * 100) : 0
  return {
    backgroundColor: 'transparent',
    series: [{
      type: 'gauge',
      startAngle: 200, endAngle: -20,
      min: 0, max: 100,
      radius: '88%',
      axisLine: {
        lineStyle: {
          width: 16,
          color: [[pct / 100, '#58a6ff'], [1, cs.value.gridLine]]
        }
      },
      axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false },
      detail: {
        valueAnimation: true,
        formatter: `{value}%\n已复盘 ${reviewed}/${tot}`,
        color: cs.value.labelColor, fontSize: 16, lineHeight: 24,
        offsetCenter: [0, '10%']
      },
      data: [{ value: pct }]
    }]
  }
}

function buildPie(data, colorPalette) {
  const entries = Object.entries(data)
  if (entries.length === 0) return emptyOption()
  return {
    backgroundColor: 'transparent',
    color: colorPalette,
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次  ({d}%)' },
    legend: {
      type: 'scroll', orient: 'vertical', right: 0, top: 'middle',
      textStyle: { color: cs.value.legendColor, fontSize: 11 },
      formatter: name => name.length > 6 ? name.slice(0, 6) + '…' : name
    },
    series: [{
      type: 'pie',
      radius: ['38%', '68%'],
      center: ['40%', '50%'],
      data: entries.map(([name, value]) => ({ name, value })),
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 13, fontWeight: 'bold', color: cs.value.labelColor }
      },
      labelLine: { show: false }
    }]
  }
}

function buildHBar(data, color) {
  const entries = Object.entries(data)
  if (entries.length === 0) return emptyOption()
  const labels  = entries.map(([k]) => k)
  const values  = entries.map(([, v]) => v)
  const leftPad = Math.max(...labels.map(l => l.length)) * 7 + 8
  return {
    backgroundColor: 'transparent',
    grid: { left: leftPad, right: 44, top: 8, bottom: 16 },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLabel: { color: cs.value.legendColor, fontSize: 11 }
    },
    yAxis: {
      type: 'category', data: labels,
      axisLabel: { color: cs.value.labelColor, fontSize: 12 }
    },
    series: [{
      type: 'bar', data: values,
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: cs.value.labelColor, fontSize: 12 }
    }],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } }
  }
}

function emptyOption() {
  return {
    backgroundColor: 'transparent',
    graphic: [{ type: 'text', left: 'center', top: 'middle',
      style: { text: '暂无数据', fill: cs.value.legendColor, fontSize: 13 } }]
  }
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  if (activeTab.value === 'stats') {
    await nextTick()
    renderAllCharts()
  }
})

const fmtDt  = v => v ? dayjs(v).format('MM-DD HH:mm') : '-'
const fmtMin = v => !v ? '-' : v < 60 ? v + 'min' : (v / 60).toFixed(1) + 'h'

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(async () => {
  symbols.value = await getSymbols()
  await load()
  await loadStats()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
})
</script>

<style scoped>
/* ── Tabs ── */
:deep(.review-tabs .el-tabs__header) {
  margin-bottom: 16px;
}
:deep(.review-tabs .el-tabs__nav-wrap::after) {
  background-color: var(--border-color);
}
:deep(.review-tabs .el-tabs__item) {
  color: var(--text-secondary);
  font-size: 14px;
}
:deep(.review-tabs .el-tabs__item.is-active) {
  color: #58a6ff;
}
:deep(.review-tabs .el-tabs__active-bar) {
  background-color: #58a6ff;
}
:deep(.review-tabs .el-tabs__content) {
  overflow: visible;
}

/* ── 概览卡片 ── */
.stat-mini-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px 20px;
  text-align: center;
  transition: background .2s, border-color .2s;
}
.stat-mini-val   { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-mini-label { font-size: 12px; color: var(--text-secondary); }

/* ── 订单列表 ── */
.order-row {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background .15s;
}
.order-row:hover  { background: var(--bg-hover); }
.order-row.active { background: var(--bg-active); border-left: 3px solid #58a6ff; }
.order-row:last-child { border-bottom: none; }

.order-row-top  { display: flex; align-items: center; gap: 8px; }
.order-inst     { font-size: 13px; font-weight: 600; flex: 1; }
.order-pnl      { font-size: 13px; font-weight: 600; }
.order-pnl.profit { color: #3fb950; }
.order-pnl.loss   { color: #f85149; }
.order-row-sub  { font-size: 12px; margin-top: 4px; }

/* ── 复盘区 ── */
.review-header {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 14px;
}
.review-order-info { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.review-inst { font-size: 16px; font-weight: 700; }
.review-pnl  { font-size: 15px; font-weight: 600; }
.review-pnl.profit { color: #3fb950; }
.review-pnl.loss   { color: #f85149; }

.section-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

/* ── 标签选择组 ── */
.tag-group { display: flex; flex-wrap: wrap; gap: 6px; }
.sig-tag {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 3px 10px;
  margin: 0 !important;
  transition: border-color .15s, background .15s;
}
:deep(.sig-tag .el-checkbox__label) { font-size: 12px; }

/* 原因标签选中 */
:deep(.sig-tag.is-checked)                     { background: rgba(88,166,255,.12); border-color: #58a6ff; }
:deep(.sig-tag.is-checked .el-checkbox__label) { color: #58a6ff; font-weight: 500; }

/* EMA 选中蓝 */
:deep(.ema-tag.is-checked)                     { border-color: #58a6ff; background: rgba(88,166,255,.12); }
:deep(.ema-tag.is-checked .el-checkbox__label) { color: #58a6ff; }

/* KDJ 选中橙 */
:deep(.kdj-tag.is-checked)                     { border-color: #f0883e; background: rgba(240,136,62,.12); }
:deep(.kdj-tag.is-checked .el-checkbox__label) { color: #f0883e; }

/* MACD 选中紫 */
:deep(.macd-tag.is-checked)                     { border-color: #a371f7; background: rgba(163,113,247,.12); }
:deep(.macd-tag.is-checked .el-checkbox__label) { color: #a371f7; }

/* ── 指标区块 ── */
.indicator-block {
  border-radius: 8px;
  border-left: 3px solid;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.ema-block  { border-left-color: #58a6ff; background: rgba(88,166,255,.05); }
.kdj-block  { border-left-color: #f0883e; background: rgba(240,136,62,.05); }
.macd-block { border-left-color: #a371f7; background: rgba(163,113,247,.05); }

.indicator-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: .05em;
}

/* ── 复盘文本框 ── */
:deep(.review-textarea .el-textarea__inner) {
  background: var(--bg-input);
  border-color: var(--border-color);
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.6;
}
:deep(.review-textarea .el-textarea__inner:focus) { border-color: #58a6ff; }
</style>
