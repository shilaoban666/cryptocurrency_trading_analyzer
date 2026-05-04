<template>
  <div>
    <div class="page-title">📊 总览仪表盘</div>

    <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
                    start-placeholder="开始日期" end-placeholder="结束日期"
                    value-format="YYYY-MM-DDTHH:mm:ss" style="margin-bottom:20px"
                    @change="load" />

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <div class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color || 'var(--text-heading)' }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in statCards2" :key="card.label">
        <div class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color || 'var(--text-heading)' }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">📈 累计盈亏曲线（USDT）</div>
      <div ref="pnlChartRef" style="height:320px" />
    </div>

    <div class="chart-card">
      <div class="card-title">📅 每日盈亏</div>
      <div ref="dailyChartRef" style="height:260px" />
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">🧭 多空胜率对比</div>
          <div ref="directionChartRef" style="height:260px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">⚡ 杠杆胜率分布</div>
          <div ref="leverChartRef" style="height:260px" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getAnalysis } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const data      = ref({})
const dateRange = ref(null)

const pnlChartRef       = ref(null)
const dailyChartRef     = ref(null)
const directionChartRef = ref(null)
const leverChartRef     = ref(null)
let charts = {}

const pnl = v => (v >= 0 ? '+' : '') + v?.toFixed(2) + ' U'
const pct = v => (v * 100).toFixed(1) + '%'

const statCards = computed(() => [
  { label: '总交易次数', value: data.value.totalTrades ?? '-',  sub: `胜 ${data.value.winCount ?? 0}  败 ${data.value.lossCount ?? 0}` },
  { label: '胜率',       value: pct(data.value.winRate ?? 0),    sub: '关仓单统计', color: data.value.winRate >= 0.5 ? '#3fb950' : '#f85149' },
  { label: '总盈亏',     value: pnl(data.value.totalPnl),        sub: `净盈亏 ${pnl(data.value.netPnl)}`, color: (data.value.totalPnl ?? 0) >= 0 ? '#3fb950' : '#f85149' },
  { label: '盈亏比',     value: data.value.profitFactor?.toFixed(2) ?? '-', sub: '总盈 / |总亏|', color: '#58a6ff' },
])

const statCards2 = computed(() => [
  { label: '平均盈利', value: pnl(data.value.avgWin),  sub: `单笔最大 ${pnl(data.value.maxWin)}`,  color: '#3fb950' },
  { label: '平均亏损', value: pnl(data.value.avgLoss), sub: `单笔最大 ${pnl(data.value.maxLoss)}`, color: '#f85149' },
  { label: '最大回撤', value: pnl(-Math.abs(data.value.maxDrawdown ?? 0)), sub: `${data.value.maxDrawdownPct?.toFixed(1) ?? 0}%`, color: '#f85149' },
  { label: '夏普比率', value: data.value.sharpeRatio?.toFixed(2) ?? '-', sub: '年化简化版', color: '#58a6ff' },
])

async function load() {
  const params = {}
  if (dateRange.value) { params.start = dateRange.value[0]; params.end = dateRange.value[1] }
  data.value = await getAnalysis(params)
  await nextTick()
  renderAll()
}

function renderAll() {
  renderPnlChart()
  renderDailyChart()
  renderDirectionChart()
  renderLeverChart()
}

function renderPnlChart() {
  if (!charts.pnl) charts.pnl = initChart(pnlChartRef.value)
  const d = data.value
  charts.pnl.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>累计盈亏: ${p[0].value?.toFixed(2)} U` },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: d.pnlDates, axisLabel: { fontSize: 10, interval: Math.floor((d.pnlDates?.length || 0) / 8) } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v + ' U' } },
    series: [{
      type: 'line', data: d.pnlCumulative, smooth: true, symbol: 'none',
      lineStyle: { width: 2, color: '#58a6ff' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: 'rgba(88,166,255,0.3)' }, { offset: 1, color: 'rgba(88,166,255,0.02)' }] } },
      markLine: { data: [{ type: 'average', name: '均线' }], lineStyle: { color: '#f0883e' } }
    }]
  })
}

function renderDailyChart() {
  if (!charts.daily) charts.daily = initChart(dailyChartRef.value)
  const d = data.value
  const colors = (d.dailyPnl || []).map(v => v >= 0 ? '#3fb950' : '#f85149')
  charts.daily.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${p[0].value?.toFixed(2)} U` },
    grid: { left: 60, right: 20, top: 10, bottom: 40 },
    xAxis: { type: 'category', data: d.dailyDates, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v + ' U' } },
    series: [{ type: 'bar', data: d.dailyPnl, itemStyle: { color: p => colors[p.dataIndex] }, barMaxWidth: 20 }]
  })
}

function renderDirectionChart() {
  if (!charts.direction) charts.direction = initChart(directionChartRef.value)
  const d = data.value
  charts.direction.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}: ${(p.value * 100).toFixed(1)}%` },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'],
      label: { show: true, formatter: p => `${p.name}\n${(p.value * 100).toFixed(1)}%` },
      data: [
        { name: '做多胜率', value: d.longWinRate  ?? 0, itemStyle: { color: '#3fb950' } },
        { name: '做空胜率', value: d.shortWinRate ?? 0, itemStyle: { color: '#58a6ff' } },
        { name: '做多败率', value: 1 - (d.longWinRate  ?? 0), itemStyle: { color: isDark.value ? '#2d3f2d' : '#dcfce7' } },
        { name: '做空败率', value: 1 - (d.shortWinRate ?? 0), itemStyle: { color: isDark.value ? '#1e2d3f' : '#dbeafe' } },
      ]
    }]
  })
}

function renderLeverChart() {
  if (!charts.lever) charts.lever = initChart(leverChartRef.value)
  const d = data.value
  const levers = Object.keys(d.leverWinRate || {})
  const rates  = levers.map(k => ((d.leverWinRate[k]) * 100).toFixed(1))
  charts.lever.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}: ${p[0].value}%` },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: levers },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: v => v + '%' } },
    series: [{
      type: 'bar', data: rates, barMaxWidth: 40,
      itemStyle: { color: p => parseFloat(p.value) >= 50 ? '#3fb950' : '#f85149', borderRadius: [4,4,0,0] },
      label: { show: true, position: 'top', formatter: p => p.value + '%', color: cs.value.labelColor }
    }]
  })
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(() => { load(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.stat-card { text-align: center; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; margin-bottom: 4px; }
.stat-sub   { font-size: 11px; color: var(--text-dim); }
</style>
