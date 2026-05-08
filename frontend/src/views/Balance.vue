<template>
  <div>
    <div class="page-title">资金变化</div>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card balance-stat">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">总资金曲线</div>
      <div ref="curveRef" style="height:340px" />
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">每日资金变化</div>
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
      <div class="card-title">收益日历</div>
      <div ref="calendarRef" style="height:240px" />
    </div>

    <div class="chart-card">
      <div class="card-title">月度资金变化</div>
      <div ref="monthlyRef" style="height:300px" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getBalance } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const balance = ref({ tradingBalance: 0, fundingBalance: 0, totalBalance: 0, dates: [], curve: [] })
const curveRef = ref(null)
const dailyRef = ref(null)
const accountRef = ref(null)
const calendarRef = ref(null)
const monthlyRef = ref(null)
let charts = {}

const money = v => `${Number(v || 0).toFixed(2)} U`
const signed = v => `${v >= 0 ? '+' : ''}${Number(v || 0).toFixed(2)} U`

const dailyChanges = computed(() => {
  const curve = balance.value.curve || []
  return curve.map((v, i) => i === 0 ? 0 : +(v - curve[i - 1]).toFixed(2))
})

const cards = computed(() => {
  const changes = dailyChanges.value
  const totalChange = changes.reduce((s, v) => s + v, 0)
  const lastChange = changes.length ? changes[changes.length - 1] : 0
  const curve = balance.value.curve || []
  const maxBalance = curve.length ? Math.max(...curve) : balance.value.totalBalance || 0
  return [
    { label: '交易账户', value: money(balance.value.tradingBalance), sub: '统一账户总权益', color: '#58a6ff' },
    { label: '资金账户', value: money(balance.value.fundingBalance), sub: 'USDT 可用余额', color: '#58a6ff' },
    { label: '总余额', value: money(balance.value.totalBalance), sub: `峰值 ${money(maxBalance)}`, color: '#f0883e' },
    { label: '最近变化', value: signed(lastChange), sub: `区间变化 ${signed(totalChange)}`, color: lastChange >= 0 ? '#3fb950' : '#f85149' },
  ]
})

function chart(key, elRef) {
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

async function load() {
  balance.value = await getBalance()
  await nextTick()
  renderAll()
}

function renderAll() {
  renderCurve()
  renderDaily()
  renderAccount()
  renderCalendar()
  renderMonthly()
}

function renderCurve() {
  const c = chart('curve', curveRef)
  const dates = balance.value.dates || []
  const curve = balance.value.curve || []
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>总余额: ${money(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: curve,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#f0883e' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(240,136,62,0.28)' }, { offset: 1, color: 'rgba(240,136,62,0.02)' }]
        }
      }
    }]
  })
}

function renderDaily() {
  const c = chart('daily', dailyRef)
  const changes = dailyChanges.value
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>变化: ${signed(p[0].value)}` },
    grid: { left: 70, right: 24, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: balance.value.dates || [], axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v} U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: changes,
      barMaxWidth: 18,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

function renderAccount() {
  const c = chart('account', accountRef)
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${money(p.value)} (${p.percent}%)` },
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

function renderCalendar() {
  const c = chart('calendar', calendarRef)
  const dates = balance.value.dates || []
  const data = dates.map((d, i) => [d, dailyChanges.value[i] || 0])
  const year = dates.length ? dates[dates.length - 1].slice(0, 4) : new Date().getFullYear().toString()
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
      range: year,
      itemStyle: { borderColor: cs.value.dzBorder },
      dayLabel: { color: cs.value.legendColor },
      monthLabel: { color: cs.value.legendColor },
      yearLabel: { color: cs.value.legendColor }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data }]
  })
}

function renderMonthly() {
  const c = chart('monthly', monthlyRef)
  const monthly = new Map()
  ;(balance.value.dates || []).forEach((date, i) => {
    const month = date.slice(0, 7)
    monthly.set(month, +(monthly.get(month) || 0) + (dailyChanges.value[i] || 0))
  })
  const months = [...monthly.keys()]
  const values = [...monthly.values()].map(v => +v.toFixed(2))
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
.balance-stat { text-align: center; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
</style>
