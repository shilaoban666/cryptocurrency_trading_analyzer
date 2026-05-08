<template>
  <div>
    <div class="page-title">总览仪表盘</div>

    <el-date-picker
      v-model="dateRange"
      type="daterange"
      range-separator="至"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      value-format="YYYY-MM-DDTHH:mm:ss"
      style="margin-bottom:20px"
      @change="load"
    />

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <div class="stat-card stat-tile">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color || 'var(--text-heading)' }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in riskCards" :key="card.label">
        <div class="stat-card stat-tile">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color || 'var(--text-heading)' }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">累计盈亏曲线</div>
          <div ref="pnlChartRef" style="height:320px" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="card-title">当前账户资金</div>
          <div ref="balanceChartRef" style="height:320px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">每日盈亏</div>
          <div ref="dailyChartRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">滚动 20 单胜率</div>
          <div ref="rollingChartRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">主力行为雷达</div>
          <div ref="majorRadarRef" style="height:300px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">多空胜率对比</div>
          <div ref="directionChartRef" style="height:300px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">杠杆胜率分布</div>
          <div ref="leverChartRef" style="height:300px" />
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">专业收益日历</div>
      <div ref="calendarChartRef" style="height:240px" />
    </div>

    <div class="chart-card">
      <div class="card-title">品种盈亏与胜率</div>
      <div ref="symbolChartRef" style="height:320px" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getAnalysis, getBalance } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const data = ref({})
const balance = ref({ tradingBalance: 0, fundingBalance: 0, totalBalance: 0, dates: [], curve: [] })
const dateRange = ref(null)

const pnlChartRef = ref(null)
const dailyChartRef = ref(null)
const directionChartRef = ref(null)
const leverChartRef = ref(null)
const balanceChartRef = ref(null)
const rollingChartRef = ref(null)
const majorRadarRef = ref(null)
const calendarChartRef = ref(null)
const symbolChartRef = ref(null)
let charts = {}

const pnl = v => `${v >= 0 ? '+' : ''}${Number(v || 0).toFixed(2)} U`
const pct = v => `${(Number(v || 0) * 100).toFixed(1)}%`
const fmt = v => v != null ? `${Number(v).toFixed(2)} U` : '-'
const fmtHolding = min => !min ? '-' : min < 60 ? `${Number(min).toFixed(0)} 分钟` : `${(Number(min) / 60).toFixed(1)} 小时`

const statCards = computed(() => [
  { label: '总交易次数', value: data.value.totalTrades ?? '-', sub: `赢 ${data.value.winCount ?? 0} 亏 ${data.value.lossCount ?? 0}` },
  { label: '胜率', value: pct(data.value.winRate), sub: '已关仓订单', color: (data.value.winRate ?? 0) >= 0.5 ? '#3fb950' : '#f85149' },
  { label: '总盈亏', value: pnl(data.value.totalPnl), sub: `净盈亏 ${pnl(data.value.netPnl)}`, color: (data.value.totalPnl ?? 0) >= 0 ? '#3fb950' : '#f85149' },
  { label: '总余额', value: fmt(balance.value.totalBalance), sub: '交易账户 + 资金账户', color: '#f0883e' },
])

const riskCards = computed(() => [
  { label: '最大回撤', value: pnl(-Math.abs(data.value.maxDrawdown ?? 0)), sub: `${data.value.maxDrawdownPct?.toFixed(1) ?? 0}%`, color: '#f85149' },
  { label: '盈亏比', value: data.value.profitFactor?.toFixed(2) ?? '-', sub: '总盈利 / |总亏损|', color: '#58a6ff' },
  { label: '平均持仓', value: fmtHolding(data.value.avgHoldingMinutes), sub: `赢 ${fmtHolding(data.value.avgWinHoldingMinutes)} / 亏 ${fmtHolding(data.value.avgLossHoldingMinutes)}`, color: '#f0883e' },
  { label: '夏普比率', value: data.value.sharpeRatio?.toFixed(2) ?? '-', sub: '年化简化版', color: '#58a6ff' },
])

function chart(key, elRef) {
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

async function load() {
  const params = {}
  if (dateRange.value) {
    params.start = dateRange.value[0]
    params.end = dateRange.value[1]
  }
  const [analysis, balData] = await Promise.allSettled([getAnalysis(params), getBalance()])
  if (analysis.status === 'fulfilled') data.value = analysis.value
  if (balData.status === 'fulfilled') balance.value = balData.value
  await nextTick()
  renderAll()
}

function renderAll() {
  renderPnlChart()
  renderDailyChart()
  renderDirectionChart()
  renderLeverChart()
  renderBalanceChart()
  renderRollingChart()
  renderMajorRadar()
  renderCalendarChart()
  renderSymbolChart()
}

function renderBalanceChart() {
  const c = chart('balance', balanceChartRef)
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${fmt(p.value)} (${p.percent}%)` },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '45%'],
      label: { color: cs.value.labelColor },
      data: [
        { name: '交易账户', value: +(balance.value.tradingBalance || 0).toFixed(2), itemStyle: { color: '#58a6ff' } },
        { name: '资金账户', value: +(balance.value.fundingBalance || 0).toFixed(2), itemStyle: { color: '#3fb950' } },
      ]
    }]
  })
}

function renderPnlChart() {
  const c = chart('pnl', pnlChartRef)
  const d = data.value
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>累计盈亏: ${pnl(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: d.pnlDates || [], boundaryGap: false, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: d.pnlCumulative || [],
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#58a6ff' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(88,166,255,0.3)' }, { offset: 1, color: 'rgba(88,166,255,0.02)' }] } },
      markLine: { data: [{ type: 'average', name: '均线' }], lineStyle: { color: '#f0883e' } }
    }]
  })
}

function renderDailyChart() {
  const c = chart('daily', dailyChartRef)
  const d = data.value
  const values = d.dailyPnl || []
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${pnl(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: d.dailyDates || [], axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: values, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] }, barMaxWidth: 18 }]
  })
}

function renderRollingChart() {
  const c = chart('rolling', rollingChartRef)
  const rates = (data.value.rollingWinRate || []).map(v => +(v * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `第 ${p[0].name} 单<br/>近 20 单胜率: ${p[0].value}%` },
    grid: { left: 55, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: data.value.rollingIndex || [], axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: rates,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#f0883e', width: 2 },
      markLine: { silent: true, data: [{ yAxis: 50, lineStyle: { color: '#f85149', type: 'dashed' } }], label: { formatter: '50%', color: '#f85149' } }
    }]
  })
}

function renderMajorRadar() {
  const c = chart('majorRadar', majorRadarRef)
  const d = data.value
  const longEdge = Math.max(0, (d.longWinRate || 0) * 100)
  const shortEdge = Math.max(0, (d.shortWinRate || 0) * 100)
  const profitQuality = Math.min(100, Math.max(0, (d.profitFactor || 0) * 25))
  const riskControl = Math.max(0, 100 - Math.min(100, d.maxDrawdownPct || 0))
  const holdingQuality = Math.max(0, 100 - Math.min(100, (d.avgHoldingMinutes || 0) / 30))
  c.setOption({
    tooltip: {},
    radar: {
      indicator: [
        { name: '多头优势', max: 100 },
        { name: '空头优势', max: 100 },
        { name: '盈利质量', max: 100 },
        { name: '回撤控制', max: 100 },
        { name: '持仓效率', max: 100 },
      ],
      axisName: { color: cs.value.labelColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent'] } },
      axisLine: { lineStyle: { color: cs.value.gridLine } }
    },
    series: [{
      type: 'radar',
      data: [{ value: [longEdge, shortEdge, profitQuality, riskControl, holdingQuality], name: '行为画像', areaStyle: { color: 'rgba(88,166,255,0.22)' }, lineStyle: { color: '#58a6ff' } }]
    }]
  })
}

function renderCalendarChart() {
  const c = chart('calendar', calendarChartRef)
  const dates = data.value.dailyDates || []
  const values = data.value.dailyPnl || []
  const year = dates.length ? dates[dates.length - 1].slice(0, 4) : new Date().getFullYear().toString()
  const seriesData = dates.map((date, i) => [date, values[i] || 0])
  const maxAbs = Math.max(1, ...seriesData.map(i => Math.abs(i[1])))
  c.setOption({
    tooltip: { formatter: p => `${p.value[0]}<br/>盈亏: ${pnl(p.value[1])}` },
    visualMap: {
      min: -maxAbs,
      max: maxAbs,
      calculable: true,
      orient: 'horizontal',
      right: 20,
      top: 0,
      inRange: { color: ['#f85149', isDark.value ? '#161b22' : '#ffffff', '#3fb950'] },
      textStyle: { color: cs.value.legendColor }
    },
    calendar: {
      top: 48,
      left: 40,
      right: 20,
      cellSize: ['auto', 18],
      range: year,
      itemStyle: { borderColor: cs.value.dzBorder },
      dayLabel: { color: cs.value.legendColor },
      monthLabel: { color: cs.value.legendColor },
      yearLabel: { color: cs.value.legendColor }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: seriesData }]
  })
}

function renderDirectionChart() {
  const c = chart('direction', directionChartRef)
  const d = data.value
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 55, right: 20, top: 20, bottom: 35 },
    xAxis: { type: 'category', data: ['做多', '做空', '综合'] },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: [
        { value: +((d.longWinRate || 0) * 100).toFixed(1), itemStyle: { color: '#3fb950' } },
        { value: +((d.shortWinRate || 0) * 100).toFixed(1), itemStyle: { color: '#58a6ff' } },
        { value: +((d.winRate || 0) * 100).toFixed(1), itemStyle: { color: '#f0883e' } },
      ],
      barMaxWidth: 42,
      label: { show: true, position: 'top', formatter: p => `${p.value}%`, color: cs.value.labelColor },
      itemStyle: { borderRadius: [4, 4, 0, 0] }
    }]
  })
}

function renderLeverChart() {
  const c = chart('lever', leverChartRef)
  const leverMap = data.value.leverWinRate || {}
  const levers = Object.keys(leverMap).sort((a, b) => parseFloat(a) - parseFloat(b))
  const rates = levers.map(k => +(leverMap[k] * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}: ${p[0].value}%` },
    grid: { left: 55, right: 20, top: 20, bottom: 35 },
    xAxis: { type: 'category', data: levers },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: rates,
      barMaxWidth: 40,
      itemStyle: { color: p => parseFloat(p.value) >= 50 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', formatter: p => `${p.value}%`, color: cs.value.labelColor }
    }]
  })
}

function renderSymbolChart() {
  const c = chart('symbol', symbolChartRef)
  const d = data.value
  const syms = d.symbolNames || []
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 75, right: 65, top: 36, bottom: 55 },
    xAxis: { type: 'category', data: syms, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '盈亏(U)', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', name: '胜率', max: 100, axisLabel: { formatter: v => `${v}%` } }
    ],
    series: [
      { name: '总盈亏', type: 'bar', data: d.symbolTotalPnl || [], barMaxWidth: 30, itemStyle: { color: p => (d.symbolTotalPnl || [])[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] } },
      { name: '胜率', type: 'line', yAxisIndex: 1, data: (d.symbolWinRate || []).map(v => +(v * 100).toFixed(1)), smooth: true, symbolSize: 6, lineStyle: { color: '#f0883e' }, itemStyle: { color: '#f0883e' } }
    ]
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
.stat-tile { text-align: center; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
</style>
