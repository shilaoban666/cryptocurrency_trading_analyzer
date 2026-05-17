<template>
  <div class="balance-page">
    <div class="page-head">
      <div>
        <div class="page-title">资金变化</div>
        <div class="page-sub">账户资金曲线、回撤、胜率与交易质量联动分析</div>
      </div>
      <el-radio-group v-model="rangeKey" class="range-group" @change="onRangeChange">
        <el-radio-button v-for="item in rangeOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card balance-stat">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="balance-tabs" @tab-change="onTabChange">
      <el-tab-pane label="资金概览" name="overview">
        <div class="chart-card">
          <div class="card-title">总资金曲线<span class="hint">{{ activeRange.label }}</span></div>
          <div ref="curveRef" style="height:340px" />
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <div class="chart-card">
              <div class="card-title">每日资金变化<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="dailyRef" style="height:300px" />
            </div>
          </el-col>
          <el-col :span="10">
            <div class="chart-card">
              <div class="card-title">账户结构</div>
              <div ref="accountRef" style="height:300px" />
            </div>
          </el-col>
        </el-row>

        <div class="chart-card">
          <div class="card-title">收益日历<span class="hint">{{ activeRange.label }}</span></div>
          <div ref="calendarRef" style="height:240px" />
        </div>

        <div class="chart-card">
          <div class="card-title">月度资金变化<span class="hint">{{ activeRange.label }}</span></div>
          <div ref="monthlyRef" style="height:300px" />
        </div>

        <el-row :gutter="16">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">累计净收益<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="cumPnlRef" style="height:280px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">资金回撤曲线<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="drawdownRef" style="height:280px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">盈利日 / 亏损日占比<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="winLossRef" style="height:260px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">每日盈亏分布<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="pnlDistRef" style="height:260px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">资金波动率<span class="hint">滚动7日</span></div>
              <div ref="volatilityRef" style="height:260px" />
            </div>
          </el-col>
        </el-row>

        <div class="chart-card">
          <div class="card-title">滚动7日盈亏<span class="hint">{{ activeRange.label }}</span></div>
          <div ref="rollingRef" style="height:280px" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="资金分析" name="analysis">
        <el-row :gutter="12">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">资金效率雷达<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="riskRadarRef" style="height:280px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">期望值拆解<span class="hint">均盈 / 均亏 / 期望</span></div>
              <div ref="expectancyRef" style="height:280px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">周内资金贡献 & 胜率<span class="hint">星期维度</span></div>
              <div ref="weekdayRef" style="height:260px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">日内时段资金贡献 & 胜率<span class="hint">小时维度</span></div>
              <div ref="hourlyRef" style="height:260px" />
            </div>
          </el-col>
        </el-row>

        <div class="chart-card">
          <div class="card-title">开单时段热力图<span class="hint">星期 × 小时</span></div>
          <div ref="tradeHeatmapRef" style="height:240px" />
        </div>

        <el-row :gutter="12">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">滚动20单胜率<span class="hint">交易质量趋势</span></div>
              <div ref="rollingQualityRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">日收益风险散点<span class="hint">金额越大圆点越大</span></div>
              <div ref="dailyScatterRef" style="height:250px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">杠杆效率曲线<span class="hint">交易次数 + 胜率</span></div>
              <div ref="leverageRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">持仓时长效率<span class="hint">分桶胜率</span></div>
              <div ref="holdingRef" style="height:250px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="8">
            <div class="chart-card compact-balance-chart">
              <div class="card-title">盈亏金额分布矩阵<span class="hint">{{ activeRange.label }}</span></div>
              <div ref="analysisPnlDistRef" style="height:230px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-balance-chart">
              <div class="card-title">资金贡献归因<span class="hint">盈利日 / 亏损日 / 净值</span></div>
              <div ref="capitalAttributionRef" style="height:230px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-balance-chart">
              <div class="card-title">收益一致性评分<span class="hint">稳定性 / 波动 / 回撤</span></div>
              <div ref="consistencyRef" style="height:230px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <div class="chart-card compact-balance-chart">
              <div class="card-title">回撤修复效率<span class="hint">水下天数 / 修复速度</span></div>
              <div ref="recoveryRef" style="height:240px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card compact-balance-chart">
              <div class="card-title">资金节奏象限<span class="hint">时间胜率 / 盈亏贡献</span></div>
              <div ref="rhythmRef" style="height:240px" />
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { getAnalysis, getBalance } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const rangeOptions = [
  { label: '1日', value: '1d', days: 1 },
  { label: '1周', value: '1w', days: 7 },
  { label: '1月', value: '1m', days: 30 },
  { label: '三月', value: '3m', days: 90 },
  { label: '半年', value: '6m', days: 180 },
]

const activeTab = ref('overview')
const rangeKey = ref('1m')
const balance = ref({ tradingBalance: 0, fundingBalance: 0, totalBalance: 0, dates: [], curve: [] })
const analysis = ref({})

const curveRef = ref(null)
const dailyRef = ref(null)
const accountRef = ref(null)
const calendarRef = ref(null)
const monthlyRef = ref(null)
const cumPnlRef = ref(null)
const drawdownRef = ref(null)
const winLossRef = ref(null)
const pnlDistRef = ref(null)
const volatilityRef = ref(null)
const rollingRef = ref(null)

const riskRadarRef = ref(null)
const expectancyRef = ref(null)
const weekdayRef = ref(null)
const hourlyRef = ref(null)
const tradeHeatmapRef = ref(null)
const rollingQualityRef = ref(null)
const dailyScatterRef = ref(null)
const leverageRef = ref(null)
const holdingRef = ref(null)
const analysisPnlDistRef = ref(null)
const capitalAttributionRef = ref(null)
const consistencyRef = ref(null)
const recoveryRef = ref(null)
const rhythmRef = ref(null)

let charts = {}

const activeRange = computed(() => rangeOptions.find(i => i.value === rangeKey.value) || rangeOptions[2])
const rangeStart = computed(() => dayjs().subtract(activeRange.value.days - 1, 'day').startOf('day'))
const rangeEnd = computed(() => dayjs().endOf('day'))

const balanceRows = computed(() => {
  const dates = balance.value.dates || []
  const curve = balance.value.curve || []
  const rows = dates.map((date, i) => ({ date, value: Number(curve[i] || 0) }))
  const today = dayjs().format('YYYY-MM-DD')
  const total = Number(balance.value.totalBalance || 0)
  if (!rows.length) return total ? [{ date: today, value: total }] : []
  if (rows[rows.length - 1].date !== today && total) rows.push({ date: today, value: total })
  return rows
})

const periodRows = computed(() => {
  const rows = balanceRows.value.filter(row => {
    const d = dayjs(row.date)
    return (d.isAfter(rangeStart.value) || d.isSame(rangeStart.value, 'day')) &&
      (d.isBefore(rangeEnd.value) || d.isSame(rangeEnd.value, 'day'))
  })
  if (rows.length) return rows
  const last = balanceRows.value.at(-1)
  return last ? [last] : []
})

const baselineBalance = computed(() => {
  const previous = balanceRows.value
    .filter(row => dayjs(row.date).isBefore(rangeStart.value, 'day'))
    .at(-1)
  return previous?.value ?? periodRows.value[0]?.value ?? 0
})

const periodDates = computed(() => periodRows.value.map(row => row.date))
const periodCurve = computed(() => periodRows.value.map(row => row.value))
const periodDailyChanges = computed(() => periodCurve.value.map((v, i) => {
  const prev = i === 0 ? baselineBalance.value : periodCurve.value[i - 1]
  return round(v - prev)
}))

const analysisDailyDates = computed(() => analysis.value.dailyDates?.length ? analysis.value.dailyDates : periodDates.value)
const analysisDailyPnl = computed(() => analysis.value.dailyPnl?.length ? analysis.value.dailyPnl : periodDailyChanges.value)

const periodStats = computed(() => {
  const curve = periodCurve.value
  const first = baselineBalance.value || curve[0] || 0
  const last = curve.at(-1) || 0
  const change = last - first
  const ret = first ? change / first * 100 : 0
  const dd = calcDrawdown(curve)
  return { first, last, change: round(change), ret: round(ret), maxDrawdown: dd.amount, maxDrawdownPct: dd.pct }
})

const cards = computed(() => {
  const stats = periodStats.value
  const winRate = Number(analysis.value.winRate || 0) * 100
  return [
    { label: '交易账户', value: money(balance.value.tradingBalance), sub: '统一账户总权益', color: '#58a6ff' },
    { label: '资金账户', value: money(balance.value.fundingBalance), sub: 'USDT 可用余额', color: '#58a6ff' },
    { label: '周期净变化', value: signed(stats.change), sub: `${activeRange.value.label} 收益率 ${stats.ret.toFixed(2)}%`, color: stats.change >= 0 ? '#3fb950' : '#f85149' },
    { label: '周期胜率', value: `${winRate.toFixed(1)}%`, sub: `最大回撤 ${stats.maxDrawdownPct.toFixed(2)}%`, color: winRate >= 50 ? '#3fb950' : '#f85149' },
  ]
})

function chart(key, elRef) {
  if (!elRef.value) return null
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

async function load() {
  const [bal, ana] = await Promise.all([getBalance(), fetchAnalysis()])
  balance.value = bal
  analysis.value = ana || {}
  await nextTick()
  renderAll()
}

async function fetchAnalysis() {
  return getAnalysis({
    start: rangeStart.value.format('YYYY-MM-DDTHH:mm:ss'),
    end: rangeEnd.value.format('YYYY-MM-DDTHH:mm:ss'),
  })
}

async function onRangeChange() {
  analysis.value = await fetchAnalysis()
  await nextTick()
  renderAll()
}

function onTabChange() {
  nextTick(() => {
    renderAll()
    onResize()
  })
}

function renderAll() {
  renderCurve()
  renderDaily()
  renderAccount()
  renderCalendar()
  renderMonthly()
  renderCumPnl()
  renderDrawdown()
  renderWinLoss()
  renderPnlDist()
  renderVolatility()
  renderRolling()
  renderRiskRadar()
  renderExpectancy()
  renderWeekday()
  renderHourly()
  renderTradeHeatmap()
  renderRollingQuality()
  renderDailyScatter()
  renderLeverage()
  renderHolding()
  renderAnalysisPnlDist()
  renderCapitalAttribution()
  renderConsistency()
  renderRecovery()
  renderRhythm()
}

function renderCurve() {
  const c = chart('curve', curveRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>总余额: ${money(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: periodDates.value, boundaryGap: false, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', scale: true, axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: periodCurve.value,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2.4, color: '#d29922' },
      areaStyle: { color: gradient('rgba(210,153,34,.30)', 'rgba(210,153,34,.02)') },
    }]
  }, true)
}

function renderDaily() {
  const c = chart('daily', dailyRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>变化: ${signed(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: periodDates.value, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: periodDailyChanges.value,
      barMaxWidth: 18,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] }
    }]
  }, true)
}

function renderAccount() {
  const c = chart('account', accountRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${money(p.value)} (${p.percent}%)` },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '45%'],
      label: { color: cs.value.labelColor },
      data: [
        { name: '交易账户', value: round(balance.value.tradingBalance), itemStyle: { color: '#58a6ff' } },
        { name: '资金账户', value: round(balance.value.fundingBalance), itemStyle: { color: '#3fb950' } },
      ]
    }]
  }, true)
}

function renderCalendar() {
  const c = chart('calendar', calendarRef)
  if (!c) return
  const data = periodDates.value.map((d, i) => [d, periodDailyChanges.value[i] || 0])
  const maxAbs = Math.max(1, ...data.map(i => Math.abs(i[1])))
  c.setOption({
    tooltip: { formatter: p => `${p.value[0]}<br/>变化: ${signed(p.value[1])}` },
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
      range: [rangeStart.value.format('YYYY-MM-DD'), rangeEnd.value.format('YYYY-MM-DD')],
      itemStyle: { borderColor: cs.value.dzBorder },
      dayLabel: { color: cs.value.legendColor },
      monthLabel: { color: cs.value.legendColor },
      yearLabel: { color: cs.value.legendColor }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data }]
  }, true)
}

function renderMonthly() {
  const c = chart('monthly', monthlyRef)
  if (!c) return
  const monthly = new Map()
  periodDates.value.forEach((date, i) => {
    const month = date.slice(0, 7)
    monthly.set(month, round((monthly.get(month) || 0) + (periodDailyChanges.value[i] || 0)))
  })
  const months = [...monthly.keys()]
  const values = [...monthly.values()]
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>变化: ${signed(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: months, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 42,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', formatter: p => signed(p.value), color: cs.value.labelColor, fontSize: 11 }
    }]
  }, true)
}

function renderCumPnl() {
  const c = chart('cumPnl', cumPnlRef)
  if (!c) return
  let cum = 0
  const values = periodDailyChanges.value.map(v => round(cum += v))
  c.setOption(lineOption(periodDates.value, values, '#58a6ff', '累计净收益', v => signed(v)), true)
}

function renderDrawdown() {
  const c = chart('drawdown', drawdownRef)
  if (!c) return
  const values = drawdownSeries(periodCurve.value)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>回撤: ${p[0].value}%` },
    grid: { left: 60, right: 20, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: periodDates.value, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', max: 0, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { color: '#f85149', width: 2 }, areaStyle: { color: 'rgba(248,81,73,.18)' } }]
  }, true)
}

function renderWinLoss() {
  const c = chart('winLoss', winLossRef)
  if (!c) return
  const wins = periodDailyChanges.value.filter(v => v > 0).length
  const losses = periodDailyChanges.value.filter(v => v < 0).length
  const flats = periodDailyChanges.value.filter(v => v === 0).length
  c.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '43%'],
      label: { color: cs.value.labelColor },
      data: [
        { name: '盈利日', value: wins, itemStyle: { color: '#3fb950' } },
        { name: '亏损日', value: losses, itemStyle: { color: '#f85149' } },
        { name: '持平日', value: flats, itemStyle: { color: '#8b949e' } },
      ]
    }]
  }, true)
}

function renderPnlDist() {
  const c = chart('pnlDist', pnlDistRef)
  if (!c) return
  const { labels, counts } = histogram(periodDailyChanges.value)
  c.setOption(barOption(labels, counts, '#d29922', '天数'), true)
}

function renderVolatility() {
  const c = chart('volatility', volatilityRef)
  if (!c) return
  const values = rollingStd(periodDailyChanges.value, 7)
  c.setOption(lineOption(periodDates.value, values, '#a371f7', '7日波动率', v => `${v}U`), true)
}

function renderRolling() {
  const c = chart('rolling', rollingRef)
  if (!c) return
  const values = rollingSum(periodDailyChanges.value, 7)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>滚动7日: ${signed(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: periodDates.value, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: values, barMaxWidth: 18, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] } }]
  }, true)
}

function renderRiskRadar() {
  const c = chart('riskRadar', riskRadarRef)
  if (!c) return
  const a = analysis.value
  const values = [
    clamp((a.winRate || 0) * 100, 0, 100),
    clamp((a.profitFactor || 0) / 2 * 100, 0, 100),
    clamp((a.sharpeRatio || 0) / 2 * 100, 0, 100),
    clamp((a.calmarRatio || 0) / 2 * 100, 0, 100),
    clamp(((a.expectedValue || 0) + 50) / 100 * 100, 0, 100),
    clamp(100 - Math.abs(periodStats.value.maxDrawdownPct) * 2, 0, 100),
  ]
  c.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '66%',
      indicator: [
        { name: '胜率', max: 100 },
        { name: '盈亏因子', max: 100 },
        { name: '夏普', max: 100 },
        { name: '卡玛', max: 100 },
        { name: '期望', max: 100 },
        { name: '回撤控制', max: 100 },
      ],
      axisName: { color: cs.value.legendColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(88,166,255,.04)'] } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
    },
    series: [{
      type: 'radar',
      data: [{ value: values, name: '资金效率', areaStyle: { color: 'rgba(88,166,255,.20)' }, lineStyle: { color: '#58a6ff', width: 2 } }]
    }]
  }, true)
}

function renderExpectancy() {
  const c = chart('expectancy', expectancyRef)
  if (!c) return
  const a = analysis.value
  const labels = ['平均盈利', '平均亏损', '单笔期望', '单笔均值', '手续费']
  const values = [a.avgWin || 0, a.avgLoss || 0, a.expectedValue || 0, a.avgPnl || 0, a.totalFee || 0].map(round)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${signed(p[0].value)}` },
    grid: { left: 72, right: 20, top: 24, bottom: 42 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: values, barMaxWidth: 34, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', color: cs.value.labelColor, formatter: p => signed(p.value) } }]
  }, true)
}

function renderWeekday() {
  const c = chart('weekday', weekdayRef)
  if (!c) return
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const pnl = analysis.value.weekdayTotalPnl || Array(7).fill(0)
  const win = (analysis.value.weekdayWinRate || Array(7).fill(0)).map(v => round(v * 100))
  c.setOption(comboBarLine(labels, pnl, win, '资金贡献', '胜率', 'U', '%'), true)
}

function renderHourly() {
  const c = chart('hourly', hourlyRef)
  if (!c) return
  const labels = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const pnl = analysis.value.hourlyTotalPnl || Array(24).fill(0)
  const win = (analysis.value.hourlyWinRate || Array(24).fill(0)).map(v => round(v * 100))
  c.setOption(comboBarLine(labels, pnl, win, '资金贡献', '胜率', 'U', '%', true), true)
}

function renderTradeHeatmap() {
  const c = chart('tradeHeatmap', tradeHeatmapRef)
  if (!c) return
  const data = analysis.value.heatmapData || []
  const week = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const maxVal = Math.max(1, ...data.map(i => i[2] || 0))
  c.setOption({
    tooltip: { formatter: p => `${week[p.data[1]]} ${p.data[0]}:00<br/>交易 ${p.data[2]} 次` },
    grid: { left: 64, right: 24, top: 12, bottom: 34 },
    xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, i) => `${i}h`), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: week },
    visualMap: { min: 0, max: maxVal, calculable: true, orient: 'horizontal', right: 10, top: 0, inRange: { color: cs.value.heatmap }, textStyle: { color: cs.value.legendColor } },
    series: [{ type: 'heatmap', data, label: { show: false } }]
  }, true)
}

function renderRollingQuality() {
  const c = chart('rollingQuality', rollingQualityRef)
  if (!c) return
  const rates = (analysis.value.rollingWinRate || []).map(v => round(v * 100))
  const xs = analysis.value.rollingIndex || rates.map((_, i) => i + 1)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `第 ${p[0].name} 单<br/>近20单胜率 ${p[0].value}%` },
    grid: { left: 56, right: 24, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: xs, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'line', data: rates, smooth: true, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 }, areaStyle: { color: 'rgba(88,166,255,.16)' }, markLine: { silent: true, symbol: 'none', data: [{ yAxis: 50, lineStyle: { color: '#d29922', type: 'dashed' }, label: { formatter: '50% 平衡线' } }] } }]
  }, true)
}

function renderDailyScatter() {
  const c = chart('dailyScatter', dailyScatterRef)
  if (!c) return
  const values = periodDailyChanges.value.map((v, i) => [i + 1, v, Math.abs(v), periodDates.value[i]])
  const maxAbs = Math.max(1, ...values.map(i => i[2]))
  c.setOption({
    tooltip: { formatter: p => `${p.data[3]}<br/>资金变化: ${signed(p.data[1])}` },
    grid: { left: 64, right: 24, top: 20, bottom: 42 },
    xAxis: { type: 'value', name: '周期内日期序号', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '资金变化', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'scatter',
      data: values,
      symbolSize: p => 8 + Math.sqrt(p[2] / maxAbs) * 22,
      itemStyle: { color: p => p.data[1] >= 0 ? '#3fb950' : '#f85149', opacity: .72 }
    }]
  }, true)
}

function renderLeverage() {
  const c = chart('leverage', leverageRef)
  if (!c) return
  const rateMap = analysis.value.leverWinRate || {}
  const countMap = analysis.value.leverTradeCount || {}
  const levers = Object.keys({ ...rateMap, ...countMap }).sort((a, b) => Number(a) - Number(b))
  const counts = levers.map(k => countMap[k] || 0)
  const rates = levers.map(k => round((rateMap[k] || 0) * 100))
  c.setOption(comboBarLine(levers.map(k => `${k}x`), counts, rates, '交易次数', '胜率', '单', '%'), true)
}

function renderHolding() {
  const c = chart('holding', holdingRef)
  if (!c) return
  const labels = analysis.value.holdingLabels || []
  const counts = analysis.value.holdingCounts || []
  const rates = (analysis.value.holdingWinRates || []).map(v => round(v * 100))
  c.setOption(comboBarLine(labels, counts, rates, '交易次数', '胜率', '单', '%', true), true)
}

function renderAnalysisPnlDist() {
  const c = chart('analysisPnlDist', analysisPnlDistRef)
  if (!c) return
  const labels = analysis.value.pnlRangeLabels?.length ? analysis.value.pnlRangeLabels : histogram(analysisDailyPnl.value).labels
  const counts = analysis.value.pnlRangeCounts?.length ? analysis.value.pnlRangeCounts : histogram(analysisDailyPnl.value).counts
  c.setOption(barOption(labels, counts, '#58a6ff', '次数'), true)
}

function renderCapitalAttribution() {
  const c = chart('capitalAttribution', capitalAttributionRef)
  if (!c) return
  const values = periodDailyChanges.value
  const win = values.filter(v => v > 0).reduce((s, v) => s + v, 0)
  const loss = values.filter(v => v < 0).reduce((s, v) => s + v, 0)
  const fee = -(analysis.value.totalFee || 0)
  const net = win + loss + fee
  const labels = ['盈利贡献', '亏损侵蚀', '手续费', '净变化']
  const data = [win, loss, fee, net].map(round)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => p.map(i => `${i.marker}${i.name}: ${signed(i.value)}`).join('<br/>') },
    grid: { left: 72, right: 18, top: 24, bottom: 38 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data,
      barMaxWidth: 28,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', color: cs.value.labelColor, formatter: p => signed(p.value) },
    }]
  }, true)
}

function renderConsistency() {
  const c = chart('consistency', consistencyRef)
  if (!c) return
  const values = periodDailyChanges.value
  const positives = values.filter(v => v > 0)
  const negatives = values.filter(v => v < 0)
  const winRate = values.length ? positives.length / values.length * 100 : 0
  const avgWin = positives.length ? positives.reduce((s, v) => s + v, 0) / positives.length : 0
  const avgLoss = negatives.length ? Math.abs(negatives.reduce((s, v) => s + v, 0) / negatives.length) : 0
  const volatility = rollingStd(values, Math.min(7, Math.max(values.length, 1))).at(-1) || 0
  const maxAbs = Math.max(1, ...values.map(v => Math.abs(v)))
  const stats = periodStats.value
  const score = [
    clamp(winRate, 0, 100),
    clamp(avgLoss ? avgWin / avgLoss * 50 : 80, 0, 100),
    clamp(100 - volatility / maxAbs * 100, 0, 100),
    clamp(100 - Math.abs(stats.maxDrawdownPct) * 2, 0, 100),
    clamp((analysis.value.profitFactor || 0) / 2 * 100, 0, 100),
  ]
  c.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '63%',
      indicator: [
        { name: '胜率', max: 100 },
        { name: '盈亏比', max: 100 },
        { name: '低波动', max: 100 },
        { name: '回撤控制', max: 100 },
        { name: '盈利因子', max: 100 },
      ],
      axisName: { color: cs.value.legendColor, fontSize: 11 },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(88,166,255,.045)'] } },
    },
    series: [{ type: 'radar', data: [{ name: '一致性', value: score.map(v => round(v, 1)), areaStyle: { color: 'rgba(63,185,80,.18)' }, lineStyle: { color: '#3fb950', width: 2 } }] }]
  }, true)
}

function renderRecovery() {
  const c = chart('recovery', recoveryRef)
  if (!c) return
  const curve = periodCurve.value
  const dates = periodDates.value
  let peak = baselineBalance.value || curve[0] || 0
  let underwater = 0
  const drawdowns = curve.map(value => {
    peak = Math.max(peak, value)
    const dd = peak ? round((value - peak) / peak * 100, 2) : 0
    underwater = dd < 0 ? underwater + 1 : 0
    return { dd, underwater }
  })
  c.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: p => {
        const idx = p[0]?.dataIndex ?? 0
        return `${dates[idx]}<br/>回撤: ${drawdowns[idx]?.dd || 0}%<br/>水下天数: ${drawdowns[idx]?.underwater || 0}`
      },
    },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 58, right: 54, top: 36, bottom: 42 },
    xAxis: { type: 'category', data: dates, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '回撤%', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', name: '水下天数', splitLine: { show: false } },
    ],
    series: [
      { name: '回撤深度', type: 'line', data: drawdowns.map(i => i.dd), smooth: true, symbol: 'none', lineStyle: { color: '#f85149', width: 2 }, areaStyle: { color: 'rgba(248,81,73,.14)' } },
      { name: '水下天数', type: 'bar', yAxisIndex: 1, data: drawdowns.map(i => i.underwater), barMaxWidth: 14, itemStyle: { color: 'rgba(210,153,34,.55)', borderRadius: [3,3,0,0] } },
    ],
  }, true)
}

function renderRhythm() {
  const c = chart('rhythm', rhythmRef)
  if (!c) return
  const changes = periodDailyChanges.value
  const labels = periodDates.value
  const window = Math.min(7, Math.max(1, changes.length))
  const points = changes.map((value, index) => {
    const slice = changes.slice(Math.max(0, index - window + 1), index + 1)
    const winRate = slice.length ? slice.filter(v => v > 0).length / slice.length * 100 : 0
    const avgPnl = slice.reduce((sum, v) => sum + v, 0) / (slice.length || 1)
    const volatility = std(slice)
    return {
      name: labels[index],
      value: [round(winRate, 1), round(avgPnl), round(volatility), labels[index]],
      symbolSize: Math.min(32, Math.max(9, Math.abs(avgPnl) * 0.09 + 10)),
      itemStyle: { color: avgPnl >= 0 ? 'rgba(63,185,80,.72)' : 'rgba(248,81,73,.72)' },
    }
  })
  c.setOption({
    tooltip: { formatter: p => `${p.value[3]}<br/>滚动胜率: ${p.value[0]}%<br/>滚动均盈亏: ${signed(p.value[1])}<br/>波动: ${p.value[2]} U` },
    grid: { left: 58, right: 26, top: 30, bottom: 44 },
    xAxis: { type: 'value', min: 0, max: 100, name: '滚动胜率', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '滚动均盈亏', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      name: '资金节奏',
      type: 'scatter',
      data: points,
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: cs.value.gridLine, type: 'dashed' },
        data: [{ xAxis: 50 }, { yAxis: 0 }],
      },
    }],
  }, true)
}

function comboBarLine(labels, bars, line, barName, lineName, barUnit, lineUnit, rotate = false) {
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 68, right: 58, top: 38, bottom: rotate ? 62 : 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: rotate ? 45 : 0, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: barUnit, axisLabel: { formatter: v => `${v}${barUnit}` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', name: lineUnit, max: 100, axisLabel: { formatter: v => `${v}${lineUnit}` }, splitLine: { show: false } },
    ],
    series: [
      { name: barName, type: 'bar', data: bars.map(round), barMaxWidth: 24, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] } },
      { name: lineName, type: 'line', yAxisIndex: 1, data: line, smooth: true, symbolSize: 6, lineStyle: { color: '#d29922', width: 2 }, itemStyle: { color: '#d29922' } },
    ]
  }
}

function lineOption(dates, values, color, name, formatter) {
  return {
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${name}: ${formatter(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { width: 2, color }, areaStyle: { color: `${color}22` } }]
  }
}

function barOption(labels, values, color, name) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 52, right: 18, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 10, rotate: labels.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', name, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: values, barMaxWidth: 24, itemStyle: { color, borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', color: cs.value.labelColor } }]
  }
}

function histogram(values) {
  const buckets = [
    { name: '< -100', test: v => v < -100 },
    { name: '-100~-20', test: v => v >= -100 && v < -20 },
    { name: '-20~0', test: v => v >= -20 && v < 0 },
    { name: '0~20', test: v => v >= 0 && v < 20 },
    { name: '20~100', test: v => v >= 20 && v < 100 },
    { name: '> 100', test: v => v >= 100 },
  ]
  return { labels: buckets.map(b => b.name), counts: buckets.map(b => values.filter(b.test).length) }
}

function rollingStd(values, window) {
  return values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - window + 1), i + 1)
    const mean = slice.reduce((s, v) => s + v, 0) / (slice.length || 1)
    const variance = slice.reduce((s, v) => s + (v - mean) ** 2, 0) / (slice.length || 1)
    return round(Math.sqrt(variance))
  })
}

function std(values) {
  const valid = values.filter(v => Number.isFinite(Number(v))).map(Number)
  const mean = valid.reduce((s, v) => s + v, 0) / (valid.length || 1)
  const variance = valid.reduce((s, v) => s + (v - mean) ** 2, 0) / (valid.length || 1)
  return Math.sqrt(variance)
}

function rollingSum(values, window) {
  return values.map((_, i) => round(values.slice(Math.max(0, i - window + 1), i + 1).reduce((s, v) => s + v, 0)))
}

function drawdownSeries(curve) {
  let peak = -Infinity
  return curve.map(v => {
    peak = Math.max(peak, v)
    return peak > 0 ? round((v - peak) / peak * 100) : 0
  })
}

function calcDrawdown(curve) {
  let peak = -Infinity
  let maxAmount = 0
  let maxPct = 0
  curve.forEach(v => {
    peak = Math.max(peak, v)
    const amount = peak - v
    const pct = peak > 0 ? amount / peak * 100 : 0
    maxAmount = Math.max(maxAmount, amount)
    maxPct = Math.max(maxPct, pct)
  })
  return { amount: round(maxAmount), pct: round(maxPct) }
}

function gradient(from, to) {
  return { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: from }, { offset: 1, color: to }] }
}

function money(v) { return `${Number(v || 0).toFixed(2)} U` }
function signed(v) { return `${Number(v || 0) >= 0 ? '+' : ''}${Number(v || 0).toFixed(2)} U` }
function round(v, digits = 2) { return Number.isFinite(Number(v)) ? +Number(v).toFixed(digits) : 0 }
function clamp(v, min, max) { return Math.max(min, Math.min(max, v)) }

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
.balance-page { animation: pageFadeIn .32s ease both; }
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title { margin-bottom: 4px; }
.page-sub,
.hint {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
}
.hint { margin-left: 8px; }
.range-group { flex-shrink: 0; }
.balance-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 700; }
.balance-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.balance-tabs :deep(.el-tabs__nav-wrap::after) { background: var(--border-color); opacity: .65; }
.balance-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #58a6ff, #3fb950);
}
.balance-stat { text-align: center; }
.balance-page :deep(.chart-card) { padding: 12px; margin-bottom: 10px; }
.balance-page :deep(.card-title) { margin-bottom: 8px; }
.compact-balance-chart { padding: 12px !important; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.stat-value { font-size: 23px; font-weight: 700; margin-bottom: 3px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
@media (max-width: 1100px) {
  .page-head { flex-direction: column; }
  .range-group { width: 100%; overflow-x: auto; }
}
</style>
