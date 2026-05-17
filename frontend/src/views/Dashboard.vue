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

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">回撤水下图</div>
          <div ref="drawdownChartRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">盈亏分布直方图</div>
          <div ref="pnlDistributionRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">手续费侵蚀</div>
          <div ref="feeImpactRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">交易质量矩阵</div>
          <div ref="qualityMatrixRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">连胜连败压力</div>
          <div ref="streakChartRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>
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
const drawdownChartRef = ref(null)
const pnlDistributionRef = ref(null)
const feeImpactRef = ref(null)
const qualityMatrixRef = ref(null)
const streakChartRef = ref(null)
let charts = {}

const pnl = v => `${v >= 0 ? '+' : ''}${Number(v || 0).toFixed(2)} U`
const pct = v => `${(Number(v || 0) * 100).toFixed(1)}%`
const fmt = v => v != null ? `${Number(v).toFixed(2)} U` : '-'
const fmtHolding = min => !min ? '-' : min < 60 ? `${Number(min).toFixed(0)} 分钟` : `${(Number(min) / 60).toFixed(1)} 小时`
const round = (v, n = 2) => Number(Number(v || 0).toFixed(n))

const statCards = computed(() => [
  { label: '总交易次数', value: data.value.totalTrades ?? '-', sub: `赢 ${data.value.winCount ?? 0} 亏 ${data.value.lossCount ?? 0}` },
  { label: '胜率', value: pct(data.value.winRate), sub: '已关仓订单', color: (data.value.winRate ?? 0) >= 0.5 ? '#3fb950' : '#f85149' },
  { label: '总盈亏', value: pnl(data.value.totalPnl), sub: `净盈亏 ${pnl(data.value.netPnl)}`, color: (data.value.totalPnl ?? 0) >= 0 ? '#3fb950' : '#f85149' },
  { label: '总余额', value: fmt(balance.value.totalBalance), sub: '交易账户 + 资金账户', color: '#d29922' },
])

const riskCards = computed(() => [
  { label: '最大回撤', value: pnl(-Math.abs(data.value.maxDrawdown ?? 0)), sub: `${data.value.maxDrawdownPct?.toFixed(1) ?? 0}%`, color: '#f85149' },
  { label: '盈亏比', value: data.value.profitFactor?.toFixed(2) ?? '-', sub: '总盈利 / |总亏损|', color: '#58a6ff' },
  { label: '平均持仓', value: fmtHolding(data.value.avgHoldingMinutes), sub: `赢 ${fmtHolding(data.value.avgWinHoldingMinutes)} / 亏 ${fmtHolding(data.value.avgLossHoldingMinutes)}`, color: '#d29922' },
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
  renderDrawdownChart()
  renderPnlDistribution()
  renderFeeImpact()
  renderQualityMatrix()
  renderStreakChart()
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
      markLine: { data: [{ type: 'average', name: '均线' }], lineStyle: { color: '#d29922' } }
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
      lineStyle: { color: '#d29922', width: 2 },
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

function renderDrawdownChart() {
  const c = chart('drawdown', drawdownChartRef)
  const d = data.value
  const dates = d.pnlDates || []
  const curve = d.pnlCumulative || []
  let peak = -Infinity
  const drawdown = curve.map(value => {
    peak = Math.max(peak, Number(value || 0))
    return round(Number(value || 0) - peak)
  })
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>回撤: ${pnl(p[0].value)}` },
    grid: { left: 70, right: 24, top: 22, bottom: 45 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', max: 0, axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      name: '回撤',
      type: 'line',
      data: drawdown,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#f85149', width: 2 },
      areaStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(248,81,73,0.08)' }, { offset: 1, color: 'rgba(248,81,73,0.28)' }] },
      },
    }]
  })
}

function renderPnlDistribution() {
  const c = chart('pnlDistribution', pnlDistributionRef)
  const labels = data.value.pnlRangeLabels?.length ? data.value.pnlRangeLabels : histogramFromValues(data.value.dailyPnl || []).labels
  const counts = data.value.pnlRangeCounts?.length ? data.value.pnlRangeCounts : histogramFromValues(data.value.dailyPnl || []).counts
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>交易数: ${p[0].value}` },
    grid: { left: 54, right: 18, top: 22, bottom: 54 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: counts,
      barMaxWidth: 22,
      itemStyle: { color: p => rangeMid(labels[p.dataIndex]) >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] },
    }]
  })
}

function renderFeeImpact() {
  const c = chart('feeImpact', feeImpactRef)
  const d = data.value
  const gross = round(d.totalPnl)
  const fee = round(d.totalFee)
  const net = round(d.netPnl ?? gross + fee)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => p.map(i => `${i.marker}${i.name}: ${pnl(i.value)}`).join('<br/>') },
    grid: { left: 64, right: 18, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: ['毛盈亏', '手续费', '净盈亏'] },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: [gross, fee, net],
      barMaxWidth: 34,
      label: { show: true, position: 'top', formatter: p => pnl(p.value), color: cs.value.labelColor, fontSize: 10 },
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] },
    }]
  })
}

function renderQualityMatrix() {
  const c = chart('qualityMatrix', qualityMatrixRef)
  const d = data.value
  const evBase = Math.max(Math.abs(d.avgWin || 0), Math.abs(d.avgLoss || 0), 1)
  const metrics = [
    { name: '胜率', score: clamp((d.winRate || 0) * 100, 0, 100), raw: pct(d.winRate) },
    { name: '盈亏比', score: clamp(((d.profitFactor || 0) / 2.2) * 100, 0, 100), raw: (d.profitFactor || 0).toFixed(2) },
    { name: '期望值', score: clamp(50 + (d.expectedValue || 0) / evBase * 50, 0, 100), raw: pnl(d.expectedValue) },
    { name: '夏普', score: clamp(((d.sharpeRatio || 0) + 1) / 3 * 100, 0, 100), raw: (d.sharpeRatio || 0).toFixed(2) },
    { name: '卡玛', score: clamp(((d.calmarRatio || 0) + 1) / 3 * 100, 0, 100), raw: (d.calmarRatio || 0).toFixed(2) },
    { name: '回撤', score: clamp(100 - (d.maxDrawdownPct || 0), 0, 100), raw: `${(d.maxDrawdownPct || 0).toFixed(1)}%` },
  ]
  c.setOption({
    tooltip: { formatter: p => `${metrics[p.value[0]].name}<br/>原始值: ${p.value[3]}<br/>评分: ${p.value[2].toFixed(0)}` },
    grid: { left: 18, right: 18, top: 26, bottom: 42 },
    xAxis: { type: 'category', data: metrics.map(i => i.name), axisLabel: { color: cs.value.legendColor, fontSize: 10 } },
    yAxis: { type: 'category', data: ['质量'], axisLabel: { color: cs.value.legendColor } },
    visualMap: {
      show: false,
      min: 0,
      max: 100,
      inRange: { color: ['#f85149', '#d29922', '#3fb950'] },
    },
    series: [{
      type: 'heatmap',
      data: metrics.map((item, index) => [index, 0, round(item.score, 1), item.raw]),
      label: { show: true, formatter: p => p.value[2].toFixed(0), color: '#fff', fontWeight: 700 },
      itemStyle: { borderRadius: 6, borderColor: isDark.value ? '#0d1117' : '#ffffff', borderWidth: 3 },
    }]
  })
}

function renderStreakChart() {
  const c = chart('streak', streakChartRef)
  const d = data.value
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 52, right: 48, top: 36, bottom: 38 },
    xAxis: { type: 'category', data: ['连胜', '连败'] },
    yAxis: [
      { type: 'value', minInterval: 1, axisLabel: { formatter: v => `${v}单` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { show: false } },
    ],
    series: [
      { name: '连续单数', type: 'bar', data: [d.maxConsecWins || 0, d.maxConsecLosses || 0], barMaxWidth: 34, itemStyle: { color: p => p.dataIndex === 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] } },
      { name: '累计金额', type: 'line', yAxisIndex: 1, data: [round(d.maxConsecWinAmount), -Math.abs(round(d.maxConsecLossAmount))], symbolSize: 8, lineStyle: { color: '#58a6ff', width: 2 }, itemStyle: { color: '#58a6ff' } },
    ]
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
        { value: +((d.winRate || 0) * 100).toFixed(1), itemStyle: { color: '#d29922' } },
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

function histogramFromValues(values) {
  if (!values.length) return { labels: [], counts: [] }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const step = Math.max(1, Math.ceil((max - min) / 8))
  const labels = []
  const counts = []
  for (let start = min; start <= max; start += step) {
    const end = start + step
    labels.push(`${Math.round(start)}~${Math.round(end)}`)
    counts.push(values.filter(v => v >= start && v < end).length)
  }
  return { labels, counts }
}

function rangeMid(label = '') {
  const nums = String(label).match(/-?\d+(\.\d+)?/g)?.map(Number) || [0]
  if (nums.length === 1) return nums[0]
  return (nums[0] + nums[1]) / 2
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number(value || 0)))
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


