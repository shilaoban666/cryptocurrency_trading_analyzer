<template>
  <div class="liq-page">
    <div class="liq-header">
      <span class="liq-title">💥 爆仓分析大屏</span>
      <div class="liq-actions">
        <el-button type="danger" :loading="syncing" @click="doSync" size="small">
          <el-icon><Refresh /></el-icon> 同步强平记录
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="liq-tabs" @tab-change="onTabChange">
      <el-tab-pane label="亏损分析" name="loss">
        <div class="loss-toolbar">
          <div class="loss-summary">
            亏损单 {{ lossSummary.total || 0 }} 笔
            <span v-if="lossSummary.totalLoss"> / 累计亏损 {{ lossSummary.totalLoss }} U</span>
          </div>
          <el-button size="small" :loading="lossLoading" @click="refreshLossOrders">
            刷新亏损数据
          </el-button>
        </div>
        <div v-if="lossLoaded && lossSummary.total === 0" class="loss-empty">
          当前没有检测到已关仓亏损单。这里统计的是交易记录中净盈亏（pnl + fee）小于 0 的订单，不依赖强平记录。
        </div>
        <el-row :gutter="16">
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">亏损金额分布</div><div ref="lossDistRef" style="height:280px" /></div></el-col>
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">尾部亏损分位</div><div ref="lossTailRef" style="height:280px" /></div></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">多空亏损结构</div><div ref="lossSideRef" style="height:280px" /></div></el-col>
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">亏损时段分布</div><div ref="lossHourRef" style="height:280px" /></div></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">滚动亏损压力</div><div ref="lossRollingRef" style="height:280px" /></div></el-col>
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">杠杆 × 亏损关系</div><div ref="lossLeverageRef" style="height:280px" /></div></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">持仓时长 × 亏损</div><div ref="lossHoldingRef" style="height:280px" /></div></el-col>
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">亏损风险矩阵</div><div ref="lossMatrixRef" style="height:280px" /></div></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">多空时段亏损矩阵</div><div ref="lossSideHourRef" style="height:280px" /></div></el-col>
          <el-col :span="12"><div class="liq-card"><div class="liq-card-header">亏损结构雷达</div><div ref="lossRadarRef" style="height:280px" /></div></el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="爆仓概览" name="overview">
    <!-- 顶部概览卡片 -->
    <el-row :gutter="16" style="margin-bottom:20px">
      <el-col :span="6" v-for="c in overviewCards" :key="c.label">
        <div class="liq-stat-card">
          <div class="liq-stat-icon">{{ c.icon }}</div>
          <div class="liq-stat-body">
            <div class="liq-stat-label">{{ c.label }}</div>
            <div class="liq-stat-value" :style="{color:c.color}">{{ c.value }}</div>
            <div class="liq-stat-sub">{{ c.sub }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- K线图 -->
    <div class="liq-card" style="margin-bottom:16px">
      <div class="liq-card-header">
        <span>📈 强平 K 线大图</span>
        <div class="kline-controls">
          <el-select v-model="selectedSymbol" size="small" style="width:180px;margin-right:8px"
                     placeholder="选择品种" @change="loadCandles">
            <el-option v-for="s in symbolList" :key="s" :label="s" :value="s" />
          </el-select>
          <el-radio-group v-model="selectedBar" size="small" @change="loadCandles">
            <el-radio-button value="15m">15m</el-radio-button>
            <el-radio-button value="1H">1H</el-radio-button>
            <el-radio-button value="4H">4H</el-radio-button>
            <el-radio-button value="1D">日</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div v-if="!selectedSymbol" class="kline-empty">请选择品种查看 K 线</div>
      <div v-else ref="klineRef" style="height:480px" />
    </div>

    <!-- 三列图表 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="8">
        <div class="liq-card" style="height:340px">
          <div class="liq-card-header">🪙 品种爆仓分布</div>
          <div ref="symbolPieRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="liq-card" style="height:340px">
          <div class="liq-card-header">🕐 爆仓高危时段</div>
          <div ref="hourRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="liq-card" style="height:340px">
          <div class="liq-card-header">📅 爆仓每日趋势</div>
          <div ref="dailyRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <!-- 最近爆仓记录 -->
    <div class="liq-card">
      <div class="liq-card-header">📋 最近强平记录（最多50条）</div>
      <el-table :data="stats.records || []" size="small"
                style="background:transparent;width:100%"
                :header-cell-style="tableHeader"
                :row-style="{background:'transparent'}"
                :cell-style="tableCell">
        <el-table-column prop="time"     label="时间"     width="170" />
        <el-table-column prop="instId"   label="品种"     width="160" />
        <el-table-column prop="posSide"  label="方向"     width="70">
          <template #default="{ row }">
            <el-tag :type="row.posSide === 'long' ? 'success' : 'danger'" size="small">
              {{ row.posSide === 'long' ? '做多' : '做空' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pnl" label="盈亏(U)" width="110">
          <template #default="{ row }">
            <span :style="{color: row.pnl >= 0 ? muted.green : muted.red}">{{ row.pnl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fee"          label="手续费(U)"   width="100" />
        <el-table-column prop="px"           label="强平价"      width="120" />
        <el-table-column prop="sz"           label="仓位(张)"    width="100" />
        <el-table-column prop="balanceAfter" label="余额(U)"     />
      </el-table>
    </div>
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { getLiquidation, syncLiquidation, getCandles, getLossOrders } from '@/api'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()
const muted = {
  blue: '#58a6ff',
  green: '#3fb950',
  red: '#f85149',
  amber: '#d29922',
}

const stats          = ref({})
const lossStats      = ref({ list: [], summary: {} })
const syncing        = ref(false)
const lossLoading    = ref(false)
const lossLoaded     = ref(false)
const selectedSymbol = ref('')
const selectedBar    = ref('1H')
const activeTab      = ref('loss')

const klineRef     = ref(null)
const symbolPieRef = ref(null)
const hourRef      = ref(null)
const dailyRef     = ref(null)
const lossDistRef   = ref(null)
const lossTailRef   = ref(null)
const lossSideRef   = ref(null)
const lossHourRef   = ref(null)
const lossRollingRef = ref(null)
const lossLeverageRef = ref(null)
const lossHoldingRef = ref(null)
const lossMatrixRef = ref(null)
const lossSideHourRef = ref(null)
const lossRadarRef = ref(null)
let charts = {}

const symbolList = computed(() => stats.value.symbolNames || [])
const lossSummary = computed(() => lossStats.value.summary || {})

const tableHeader = computed(() => ({
  background:  isDark.value ? '#161b22' : '#f6f8fa',
  color:       isDark.value ? '#8b949e' : '#57606a',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))
const tableCell = computed(() => ({
  color:       isDark.value ? '#c9d1d9' : '#24292f',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))

const overviewCards = computed(() => {
  const s = stats.value
  return [
    { icon: '💥', label: '总爆仓次数',   value: s.totalCount ?? 0,          color: muted.red, sub: '历史强平单数量' },
    { icon: '📉', label: '总亏损',       value: fmt(s.totalLoss) + ' U',     color: muted.red, sub: '强平累计损失' },
    { icon: '📊', label: '平均单次亏损', value: fmt(s.avgLoss)   + ' U',     color: muted.amber, sub: '每次爆仓平均' },
    { icon: '🔥', label: '最惨单次亏损', value: fmt(s.maxSingleLoss) + ' U', color: muted.red,
      sub: s.mostLiquidatedSymbol ? `最多：${s.mostLiquidatedSymbol}` : '无数据' },
  ]
})

function fmt(v) { return v != null ? (+v).toFixed(2) : '0.00' }

async function loadStats() {
  stats.value = await getLiquidation() || {}
  await nextTick()
  renderAll()
  if (activeTab.value === 'overview' && symbolList.value.length > 0 && !selectedSymbol.value) {
    selectedSymbol.value = symbolList.value[0]
    await loadCandles()
  }
}

async function loadLossOrders() {
  const res = await getLossOrders({ limit: 5000 })
  const rows = (Array.isArray(res?.list) ? res.list : [])
    .map(toLossRow)
    .filter(row => row.lossAmount > 0)
    .sort((a, b) => dateValue(b.time) - dateValue(a.time))
  return {
    list: rows,
    summary: res?.summary || summarizeLossRows(rows),
  }
}

async function refreshLossOrders() {
  if (lossLoading.value) return
  lossLoading.value = true
  try {
    lossStats.value = await loadLossOrders()
    lossLoaded.value = true
  } finally {
    lossLoading.value = false
  }
}

async function ensureLossOrders() {
  if (activeTab.value !== 'loss') return
  await refreshLossOrders()
  await nextTick()
  await waitForPaint()
  renderLossAll()
}

function waitForPaint() {
  return new Promise(resolve => requestAnimationFrame(resolve))
}

function dateValue(value) {
  const time = new Date(String(value || '').replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : 0
}

function hourOf(value) {
  const date = new Date(String(value || '').replace(' ', 'T'))
  const hour = date.getHours()
  return Number.isFinite(hour) ? hour : null
}

function toLossRow(order) {
  const pnl = Number(order.pnl || 0)
  const fee = Number(order.fee || 0)
  const netPnl = pnl + fee
  const time = order.time || order.updateTime || order.createTime || ''
  const loss = Number(order.lossAmount ?? (netPnl < 0 ? Math.abs(netPnl) : 0))
  return {
    ...order,
    time: String(time).replace('T', ' '),
    netPnl: +netPnl.toFixed(2),
    lossAmount: +Math.max(0, loss).toFixed(2),
    lever: order.lever ?? '',
    holdingMinutes: Number(order.holdingMinutes || 0),
  }
}

function summarizeLossRows(rows) {
  const totalLoss = rows.reduce((sum, row) => sum + Number(row.lossAmount || 0), 0)
  const totalFee = rows.reduce((sum, row) => sum + Math.abs(Number(row.fee || 0)), 0)
  return {
    total: rows.length,
    returned: rows.length,
    totalLoss: +totalLoss.toFixed(2),
    totalFee: +totalFee.toFixed(2),
    avgLoss: rows.length ? +(totalLoss / rows.length).toFixed(2) : 0,
  }
}

function renderAll() {
  if (activeTab.value === 'loss') {
    renderLossAll()
    return
  }
  renderSymbolPie()
  renderHour()
  renderDaily()
}

async function doSync() {
  syncing.value = true
  try {
    const res = await syncLiquidation()
    ElMessage.success(`同步完成，新增 ${res.newRecords} 条强平记录`)
    await loadStats()
  } finally {
    syncing.value = false
  }
}

async function loadCandles() {
  if (!selectedSymbol.value) return
  await nextTick()
  if (charts.kline) { charts.kline.dispose(); charts.kline = null }
  charts.kline = initChart(klineRef.value)

  let candleData = []
  try {
    const raw = await getCandles(selectedSymbol.value, selectedBar.value, 300)
    candleData = (Array.isArray(raw) ? raw : []).reverse()
  } catch (e) {
    ElMessage.warning('K线数据加载失败')
  }

  const liqRecords = (stats.value.records || []).filter(r => r.instId === selectedSymbol.value)
  const dates  = candleData.map(c => new Date(+c[0]).toISOString())
  const ohlc   = candleData.map(c => [+c[1], +c[4], +c[3], +c[2]])

  const markPoints = liqRecords.map(r => {
    const ts = new Date(r.time.replace(' ', 'T')).getTime()
    let best = 0, bestDiff = Infinity
    candleData.forEach((c, i) => { const diff = Math.abs(+c[0] - ts); if (diff < bestDiff) { bestDiff = diff; best = i } })
    return {
      name: r.posSide === 'long' ? '多爆' : '空爆',
      coord: [best, +r.px], value: r.pnl,
      label: { formatter: `${r.posSide === 'long' ? '▼多' : '▲空'}\n${(+r.pnl).toFixed(0)}U` },
      itemStyle: { color: muted.red }, symbol: 'pin', symbolSize: 36,
    }
  })

  charts.kline.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'cross' },
      formatter: params => {
        const c = params[0]; if (!c) return ''
        const [o, cl, l, h] = c.value
        return `${dates[c.dataIndex]?.slice(0,16)}<br/>开: ${o} &nbsp; 收: ${cl}<br/>高: ${h} &nbsp; 低: ${l}`
      }
    },
    grid: { left: 70, right: 20, top: 20, bottom: 60 },
    xAxis: {
      type: 'category', data: dates.map(d => d.slice(0,16).replace('T',' ')),
      axisLabel: { fontSize: 9, rotate: 20 }, splitLine: { show: false }
    },
    yAxis: {
      type: 'value', scale: true,
      splitLine: { lineStyle: { color: cs.value.gridLine } }
    },
    dataZoom: [
      { type: 'inside', start: 60, end: 100 },
      { type: 'slider', start: 60, end: 100, height: 24, bottom: 0,
        handleStyle: { color: muted.blue }, fillerColor: 'rgba(88,166,255,0.10)',
        borderColor: cs.value.dzBorder, textStyle: cs.value.dzText }
    ],
    series: [{
      type: 'candlestick', data: ohlc,
      itemStyle: { color: muted.green, color0: muted.red, borderColor: muted.green, borderColor0: muted.red },
      markPoint: { data: markPoints, silent: false }
    }]
  }, true)
}

function renderSymbolPie() {
  if (!charts.symbolPie) charts.symbolPie = initChart(symbolPieRef.value)
  const s = stats.value
  const pieData = (s.symbolNames || []).map((name, i) => ({ name, value: (s.symbolCounts || [])[i] || 0 }))
  charts.symbolPie.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: cs.value.legendColor, fontSize: 10 } },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      data: pieData, label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } }
    }]
  })
}

function renderHour() {
  if (!charts.hour) charts.hour = initChart(hourRef.value)
  const counts = stats.value.hourlyCount || Array(24).fill(0)
  const hours  = Array.from({length:24}, (_,i) => i + ':00')
  const maxC   = Math.max(...counts)
  charts.hour.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name} 爆仓 ${p[0].value} 次` },
    grid: { left: 40, right: 10, top: 10, bottom: 40 },
    xAxis: { type: 'category', data: hours, axisLabel: { fontSize: 9, rotate: 45 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: counts, barMaxWidth: 16,
      itemStyle: {
        color: p => {
          const ratio = counts[p.dataIndex] / (maxC || 1)
          const r = Math.round(217 * ratio + 62 * (1-ratio))
          const g = Math.round(107 * ratio + 132 * (1-ratio))
          const b = Math.round(116 * ratio + 145 * (1-ratio))
          return `rgb(${r},${g},${b})`
        },
        borderRadius: [3, 3, 0, 0]
      }
    }]
  })
}

function renderDaily() {
  if (!charts.daily) charts.daily = initChart(dailyRef.value)
  const s = stats.value
  charts.daily.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor }, data: ['爆仓次数', '亏损(U)'] },
    grid: { left: 50, right: 60, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: s.dailyDates || [], axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: [
      { type: 'value', name: '次数' },
      { type: 'value', name: '亏损(U)', axisLabel: { formatter: v => v + 'U' } }
    ],
    series: [
      { name: '爆仓次数', type: 'bar', data: s.dailyCounts || [], barMaxWidth: 20,
        itemStyle: { color: 'rgba(248,81,73,0.48)', borderRadius: [3,3,0,0] } },
      { name: '亏损(U)', type: 'line', yAxisIndex: 1, data: s.dailyLoss || [],
        smooth: true, symbol: 'none', lineStyle: { color: muted.amber } }
    ]
  })
}

function lossAmount(row) {
  if (row.lossAmount != null) return +Number(row.lossAmount || 0).toFixed(2)
  const pnl = Math.abs(Number(row.netPnl ?? row.pnl ?? 0))
  const fee = Math.abs(Number(row.fee || 0))
  return +(pnl || fee || 0).toFixed(2)
}

function lossRecords() {
  return (lossStats.value.list || [])
    .map(row => ({ ...row, lossAmount: lossAmount(row) }))
    .filter(row => row.lossAmount > 0)
}

function renderLossAll() {
  const rows = lossRecords()
  renderLossDist()
  renderLossTail()
  renderLossSide()
  renderLossHour()
  renderLossRolling()
  renderLossLeverage()
  renderLossHolding()
  renderLossMatrix()
  renderLossSideHour()
  renderLossRadar()
  if (!rows.length) renderLossEmptyCharts()
}

function renderLossEmptyCharts() {
  const refs = [
    ['lossDist', lossDistRef],
    ['lossTail', lossTailRef],
    ['lossSide', lossSideRef],
    ['lossHour', lossHourRef],
    ['lossRolling', lossRollingRef],
    ['lossLeverage', lossLeverageRef],
    ['lossHolding', lossHoldingRef],
    ['lossMatrix', lossMatrixRef],
    ['lossSideHour', lossSideHourRef],
    ['lossRadar', lossRadarRef],
  ]
  refs.forEach(([key, chartRef]) => {
    if (!chartRef.value) return
    if (!charts[key]) charts[key] = initChart(chartRef.value)
    charts[key].setOption(emptyOption(), true)
  })
}

function emptyOption() {
  return {
    title: {
      text: '暂无亏损单',
      subtext: '同步交易记录后会统计 pnl + fee < 0 的已关仓订单',
      left: 'center',
      top: 'middle',
      textStyle: { color: cs.value.labelColor, fontSize: 15 },
      subtextStyle: { color: cs.value.legendColor, fontSize: 11 },
    },
  }
}

function renderLossDist() {
  if (!lossDistRef.value) return
  if (!charts.lossDist) charts.lossDist = initChart(lossDistRef.value)
  const losses = lossRecords().map(r => r.lossAmount)
  const sorted = [...losses].sort((a, b) => a - b)
  const max = Math.max(...sorted, 1)
  const step = Math.max(10, Math.ceil(max / 6 / 10) * 10)
  const buckets = Array.from({ length: 6 }, (_, i) => {
    const start = i * step
    const end = (i + 1) * step
    return i === 5 ? `${start}+` : `${start}-${end}`
  })
  const vals = buckets.map((_, i) => {
    const start = i * step
    const end = (i + 1) * step
    return i === 5 ? sorted.filter(v => v >= start).length : sorted.filter(v => v >= start && v < end).length
  })
  charts.lossDist.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name} U<br/>亏损单数: ${p[0].value}` },
    grid: { left: 54, right: 18, top: 24, bottom: 42 },
    xAxis: { type: 'category', data: buckets, axisLabel: { rotate: 20, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: vals,
      barMaxWidth: 26,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: p => ['#79c0ff', '#58a6ff', '#d29922', '#f0883e', '#ff7b72', '#f85149'][p.dataIndex] || muted.red,
      },
      label: { show: true, position: 'top', color: cs.value.labelColor, fontSize: 10 },
    }],
  }, true)
}

function renderLossSide() {
  if (!lossSideRef.value) return
  if (!charts.lossSide) charts.lossSide = initChart(lossSideRef.value)
  const map = sumBy(
    lossRecords(),
    r => r.posSide === 'long' ? '做多亏损' : r.posSide === 'short' ? '做空亏损' : '净持仓亏损',
    r => r.lossAmount,
  )
  charts.lossSide.setOption(pieOption(Object.entries(map).map(([name, value]) => ({ name, value: +value.toFixed(2) }))), true)
}

function renderLossHour() {
  if (!lossHourRef.value) return
  if (!charts.lossHour) charts.lossHour = initChart(lossHourRef.value)
  const vals = Array(24).fill(0)
  lossRecords().forEach(r => {
    const hour = hourOf(r.time)
    if (hour != null) vals[hour] += r.lossAmount
  })
  charts.lossHour.setOption(barOption(Array.from({ length: 24 }, (_, i) => `${i}:00`), vals.map(v => +v.toFixed(2)), muted.amber), true)
}

function renderLossTail() {
  if (!lossTailRef.value) return
  if (!charts.lossTail) charts.lossTail = initChart(lossTailRef.value)
  const values = lossRecords().map(r => r.lossAmount).sort((a, b) => a - b)
  const pct = p => percentile(values, p)
  const labels = ['P50', 'P75', 'P90', 'P95', '最大']
  const data = [pct(.5), pct(.75), pct(.9), pct(.95), Math.max(...values, 0)].map(v => +v.toFixed(2))
  charts.lossTail.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${p[0].value} U` },
    grid: { left: 58, right: 18, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'line', data, smooth: true, symbolSize: 8, areaStyle: { color: 'rgba(248,81,73,.14)' }, lineStyle: { color: muted.red, width: 2.5 }, itemStyle: { color: muted.red } }],
  }, true)
}

function renderLossRolling() {
  if (!lossRollingRef.value) return
  if (!charts.lossRolling) charts.lossRolling = initChart(lossRollingRef.value)
  const rows = [...lossRecords()].reverse()
  const windowSize = 10
  const rolling = rows.map((_, i) => {
    const slice = rows.slice(Math.max(0, i - windowSize + 1), i + 1)
    return +(slice.reduce((sum, r) => sum + r.lossAmount, 0) / slice.length).toFixed(2)
  })
  charts.lossRolling.setOption(lineOption(rows.map(r => String(r.time).slice(5, 16)), rolling, muted.red), true)
}

function renderLossLeverage() {
  if (!lossLeverageRef.value) return
  if (!charts.lossLeverage) charts.lossLeverage = initChart(lossLeverageRef.value)
  const rows = lossRecords().slice(0, 220)
  const values = rows.map(r => {
    const leverage = parseLeverage(r.lever)
    const holding = Number(r.holdingMinutes || 0)
    const size = Math.min(34, Math.max(9, Math.sqrt(r.lossAmount || 1) * 1.9))
    const risk = leverage * Math.max(1, r.lossAmount)
    return {
      name: `${r.instId || '-'} ${r.posSide || ''}`,
      value: [leverage, r.lossAmount, holding, risk],
      symbolSize: size,
      itemStyle: { color: r.posSide === 'short' ? 'rgba(88,166,255,.72)' : 'rgba(248,81,73,.72)' },
    }
  })
  const maxLoss = Math.max(...values.map(v => v.value[1]), 1)
  charts.lossLeverage.setOption({
    tooltip: {
      formatter: p => `${p.name}<br/>杠杆: ${p.value[0]}x<br/>亏损: ${p.value[1]} U<br/>持仓: ${fmtHolding(p.value[2])}<br/>风险暴露: ${Number(p.value[3]).toFixed(0)}`,
    },
    grid: { left: 58, right: 22, top: 26, bottom: 46 },
    xAxis: { type: 'value', name: '杠杆', min: 0, axisLabel: { formatter: v => `${v}x` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '亏损(U)', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    visualMap: {
      show: false,
      dimension: 1,
      min: 0,
      max: maxLoss,
      inRange: { opacity: [0.45, 0.95] },
    },
    series: [{
      name: '杠杆亏损样本',
      type: 'scatter',
      data: values,
      emphasis: { scale: 1.16, focus: 'self' },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { color: cs.value.legendColor, fontSize: 10 },
        lineStyle: { color: '#d29922', type: 'dashed' },
        data: [{ xAxis: 10, name: '10x警戒' }],
      },
    }],
  }, true)
}

function renderLossHolding() {
  if (!lossHoldingRef.value) return
  if (!charts.lossHolding) charts.lossHolding = initChart(lossHoldingRef.value)
  const buckets = [
    ['<15m', 0, 15],
    ['15m-1h', 15, 60],
    ['1h-4h', 60, 240],
    ['4h-1d', 240, 1440],
    ['1d+', 1440, Infinity],
  ]
  const data = buckets.map(([name, min, max]) => {
    const rows = lossRecords().filter(r => Number(r.holdingMinutes || 0) >= min && Number(r.holdingMinutes || 0) < max)
    const total = rows.reduce((sum, r) => sum + r.lossAmount, 0)
    return { name, count: rows.length, avg: rows.length ? +(total / rows.length).toFixed(2) : 0, total: +total.toFixed(2) }
  })
  charts.lossHolding.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>平均亏损: ${p[0].value} U<br/>累计亏损: ${data[p[0].dataIndex].total} U<br/>单数: ${data[p[0].dataIndex].count}` },
    grid: { left: 58, right: 18, top: 24, bottom: 42 },
    xAxis: { type: 'category', data: data.map(d => d.name) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: data.map(d => d.avg), barMaxWidth: 26, itemStyle: { color: muted.amber, borderRadius: [4,4,0,0] }, label: { show: true, position: 'top', color: cs.value.labelColor } }],
  }, true)
}

function renderLossMatrix() {
  if (!lossMatrixRef.value) return
  if (!charts.lossMatrix) charts.lossMatrix = initChart(lossMatrixRef.value)
  const rows = lossRecords().slice(0, 80)
  charts.lossMatrix.setOption({
    tooltip: { formatter: p => `${p.name}<br/>亏损: ${p.value[1]}U<br/>手续费: ${p.value[0]}U` },
    grid: { left: 58, right: 18, top: 18, bottom: 42 },
    xAxis: { type: 'value', name: '手续费' },
    yAxis: { type: 'value', name: '亏损' },
    series: [{ type: 'scatter', data: rows.map(r => ({ name: r.instId, value: [Math.abs(Number(r.fee || 0)), r.lossAmount] })), symbolSize: 12, itemStyle: { color: muted.red, opacity: .62 } }]
  }, true)
}

function renderLossSideHour() {
  if (!lossSideHourRef.value) return
  if (!charts.lossSideHour) charts.lossSideHour = initChart(lossSideHourRef.value)
  const sides = ['做多亏损', '做空亏损']
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const data = []
  lossRecords().forEach(r => {
    const x = hourOf(r.time)
    if (x == null) return
    const y = r.posSide === 'short' ? 1 : 0
    data.push([x, y, r.lossAmount])
  })
  charts.lossSideHour.setOption({
    tooltip: { formatter: p => `${hours[p.value[0]]}<br/>${sides[p.value[1]]}: ${p.value[2]} U` },
    grid: { left: 72, right: 18, top: 18, bottom: 42 },
    xAxis: { type: 'category', data: hours, axisLabel: { rotate: 45, fontSize: 9 } },
    yAxis: { type: 'category', data: sides },
    visualMap: { min: 0, max: Math.max(...data.map(d => d[2]), 1), show: false, inRange: { color: ['#f8fafc', '#d29922', '#f85149'] } },
    series: [{ type: 'heatmap', data, label: { show: false } }],
  }, true)
}

function renderLossRadar() {
  if (!lossRadarRef.value) return
  if (!charts.lossRadar) charts.lossRadar = initChart(lossRadarRef.value)
  const rows = lossRecords()
  if (!rows.length) return charts.lossRadar.setOption(emptyOption(), true)
  const total = rows.reduce((sum, r) => sum + r.lossAmount, 0) || 1
  const losses = rows.map(r => r.lossAmount)
  const maxLoss = Math.max(...losses, 1)
  const avgLoss = total / rows.length
  const p95 = percentile(losses, .95)
  const feeRatio = rows.reduce((sum, r) => sum + Math.abs(Number(r.fee || 0)), 0) / total * 100
  const longLoss = rows.filter(r => r.posSide === 'long').reduce((sum, r) => sum + r.lossAmount, 0)
  const shortLoss = rows.filter(r => r.posSide === 'short').reduce((sum, r) => sum + r.lossAmount, 0)
  const avgLeverage = avg(rows.map(r => parseLeverage(r.lever)).filter(Boolean))
  const avgHolding = avg(rows.map(r => Number(r.holdingMinutes || 0)).filter(v => v > 0))
  const values = [
    clamp(avgLoss / maxLoss * 100, 8, 100),
    clamp(maxLoss / Math.max(p95, maxLoss * .72) * 76, 8, 100),
    clamp(p95 / maxLoss * 100, 8, 100),
    clamp(Math.max(longLoss, shortLoss) / total * 100, 0, 100),
    clamp(avgLeverage / 20 * 100, 0, 100),
    clamp(avgHolding / 720 * 100, 0, 100),
    clamp(feeRatio * 8, 0, 100),
  ].map(v => +v.toFixed(1))
  charts.lossRadar.setOption({
    tooltip: {},
    radar: {
      indicator: [
        { name: '平均亏损', max: 100 },
        { name: '最大单损', max: 100 },
        { name: '尾部风险', max: 100 },
        { name: '方向集中', max: 100 },
        { name: '杠杆压力', max: 100 },
        { name: '持仓拖延', max: 100 },
        { name: '手续费侵蚀', max: 100 },
      ],
      axisName: { color: cs.value.labelColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent'] } },
    },
    series: [{ type: 'radar', data: [{ value: values, name: '亏损结构', areaStyle: { color: 'rgba(248,81,73,.18)' }, lineStyle: { color: muted.red } }] }],
  }, true)
}

function sumBy(rows, keyFn, valueFn) {
  return rows.reduce((map, row) => {
    const key = keyFn(row)
    map[key] = (map[key] || 0) + valueFn(row)
    return map
  }, {})
}

function percentile(values, p) {
  const sorted = values.filter(v => Number.isFinite(v)).sort((a, b) => a - b)
  if (!sorted.length) return 0
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * p) - 1))
  return sorted[index]
}

function avg(values) {
  const valid = values.filter(v => Number.isFinite(v))
  return valid.reduce((sum, v) => sum + v, 0) / (valid.length || 1)
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, Number(value) || 0))
}

function parseLeverage(value) {
  const match = String(value ?? '').match(/[\d.]+/)
  const parsed = match ? Number(match[0]) : 1
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1
}

function fmtHolding(minutes) {
  const value = Number(minutes || 0)
  if (!value) return '-'
  if (value < 60) return `${Math.round(value)}m`
  if (value < 1440) return `${(value / 60).toFixed(1)}h`
  return `${(value / 1440).toFixed(1)}d`
}

function barOption(labels, values, color) {
  return { tooltip: { trigger: 'axis' }, grid: { left: 54, right: 18, top: 18, bottom: 42 }, xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 10 } }, yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } }, series: [{ type: 'bar', data: values, barMaxWidth: 22, itemStyle: { color, borderRadius: [4,4,0,0] } }] }
}

function horizontalBar(labels, values, color) {
  return { tooltip: { trigger: 'axis' }, grid: { left: 110, right: 24, top: 18, bottom: 24 }, xAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } }, yAxis: { type: 'category', data: labels }, series: [{ type: 'bar', data: values, itemStyle: { color, borderRadius: [0,4,4,0] } }] }
}

function lineOption(labels, values, color, suffix = 'U') {
  return { tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${p[0].value}${suffix}` }, grid: { left: 62, right: 18, top: 18, bottom: 42 }, xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 10 } }, yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } }, series: [{ type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { color, width: 2 }, areaStyle: { color: `${color}22` } }] }
}

function pieOption(data) {
  return { tooltip: { trigger: 'item' }, legend: { bottom: 0, textStyle: { color: cs.value.legendColor } }, series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '43%'], label: { color: cs.value.labelColor }, data }] }
}

async function onTabChange(name) {
  if (name === 'loss') {
    await ensureLossOrders()
  }
}

watch(activeTab, ensureLossOrders)

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
  if (selectedSymbol.value) loadCandles()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(async () => {
  await loadStats()
  await ensureLossOrders()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.liq-page { color: var(--text-primary); }
.liq-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 700; }
.liq-tabs :deep(.el-tabs__item.is-active) { color: var(--accent-blue); }
.loss-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.loss-summary {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.loss-empty {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(210, 153, 34, .28);
  border-radius: 8px;
  background: rgba(210, 153, 34, .08);
  color: var(--text-secondary);
  font-size: 13px;
}

.liq-header {
  display: flex; align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.liq-title { font-size: 24px; font-weight: 700; color: var(--text-heading); }

.liq-stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: background .2s, border-color .2s;
}
.liq-stat-icon  { font-size: 28px; }
.liq-stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; }
.liq-stat-value { font-size: 22px; font-weight: 700; }
.liq-stat-sub   { font-size: 11px; color: var(--text-dim); margin-top: 2px; }

.liq-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  transition: background .2s, border-color .2s;
}
.liq-card-header {
  font-size: 14px; color: var(--text-secondary);
  margin-bottom: 12px;
  display: flex; align-items: center; justify-content: space-between;
}
.kline-controls { display: flex; align-items: center; }
.kline-empty {
  height: 480px; display: flex;
  align-items: center; justify-content: center;
  color: var(--text-dim); font-size: 16px;
}
</style>
