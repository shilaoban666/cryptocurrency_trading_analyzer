<template>
  <div class="kline-page">
    <div class="screen-head">
      <div>
        <div class="kline-title">K线大屏</div>
        <div class="page-sub">实时行情、趋势结构和专业指标矩阵</div>
      </div>
      <div class="toolbar">
        <el-select v-model="instId" filterable class="symbol-select" @change="load">
          <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
        </el-select>
        <el-radio-group v-model="bar" class="period-group" @change="load">
          <el-radio-button v-for="b in bars" :key="b.value" :value="b.value">{{ b.label }}</el-radio-button>
        </el-radio-group>
        <el-switch v-model="autoRefresh" active-text="自动刷新" />
        <el-button type="primary" :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="14" class="ticker-row">
      <el-col :span="6" v-for="item in tickerCards" :key="item.label">
        <div class="stat-card ticker-card">
          <div class="ticker-label">{{ item.label }}</div>
          <div class="ticker-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="ticker-sub">{{ item.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card control-card">
      <div class="indicator-groups">
        <div class="indicator-group main-toggle">
          <span>主图</span>
          <el-select v-model="mainIndicators" multiple collapse-tags collapse-tags-tooltip :max-collapse-tags="2" class="indicator-select" placeholder="选择主图指标">
            <el-option v-for="item in mainIndicatorOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="indicator-group ema-config">
          <span>EMA</span>
          <el-input v-model="emaInput" size="small" placeholder="5,10,21" @change="applyEmaInput" @keyup.enter="applyEmaInput" />
        </div>
        <div class="indicator-group sub-toggle">
          <span>副图</span>
          <el-select v-model="subIndicators" multiple collapse-tags collapse-tags-tooltip :max-collapse-tags="2" class="indicator-select" placeholder="选择副图指标">
            <el-option v-for="item in subIndicatorOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="indicator-group order-config">
          <span>副图排序</span>
          <div class="order-pills">
            <button v-for="key in subOrder" :key="key" type="button" class="order-pill" :class="{ disabled: !subIndicators.includes(key) }">
              <span>{{ indicatorLabel(key) }}</span>
              <el-button text size="small" :disabled="!subIndicators.includes(key)" @click.stop="moveSub(key, -1)">
                <el-icon><ArrowUp /></el-icon>
              </el-button>
              <el-button text size="small" :disabled="!subIndicators.includes(key)" @click.stop="moveSub(key, 1)">
                <el-icon><ArrowDown /></el-icon>
              </el-button>
            </button>
          </div>
        </div>
        <div class="refresh-info">
          <span v-if="lastUpdate">最后更新 {{ lastUpdate }}</span>
          <span v-if="autoRefresh">刷新倒计时 {{ countdown }}s</span>
        </div>
      </div>
    </div>

    <div class="screen-grid">
      <div class="chart-card kline-card">
          <div class="structure-strip">
            <div>
              <span>当前结构</span>
              <strong>{{ structureSummary.label }}</strong>
              <em>{{ structureSummary.desc }}</em>
            </div>
            <div>
              <span>价格结构</span>
              <strong>{{ structureSummary.range }}</strong>
              <em>{{ structureSummary.bias }}</em>
            </div>
            <div>
              <span>动量状态</span>
              <strong>{{ structureSummary.momentum }}</strong>
              <em>{{ structureSummary.energy }}</em>
            </div>
          </div>
          <div ref="chartRef" class="main-chart" :style="{ height: chartHeight }" />
        </div>

      <div class="side-panel">
        <div class="chart-card signal-card">
          <div class="card-title">指标快照</div>
          <div class="signal-list">
            <div v-for="sig in signals" :key="sig.label" class="signal-item">
              <span>{{ sig.label }}</span>
              <b :style="{ color: sig.color }">{{ sig.value }}</b>
              <em>{{ sig.desc }}</em>
            </div>
          </div>
        </div>

        <div class="chart-card levels-card">
          <div class="card-title">关键价位</div>
          <div class="level-list">
            <div v-for="level in keyLevelRows" :key="level.label" class="level-row">
              <span>{{ level.label }}</span>
              <b :style="{ color: level.color }">{{ level.value }}</b>
              <em>{{ level.desc }}</em>
            </div>
          </div>
        </div>

        <div class="chart-card">
          <div class="card-title">最近K线</div>
          <el-table :data="recentRows" size="small" style="width:100%">
            <el-table-column prop="time" label="时间" width="86" />
            <el-table-column prop="close" label="收盘" />
            <el-table-column prop="change" label="涨跌">
              <template #default="{ row }">
                <span :class="row.change >= 0 ? 'profit' : 'loss'">{{ row.change >= 0 ? '+' : '' }}{{ row.change.toFixed(2) }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getCandles } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const symbols = [
  'BTC-USDT-SWAP', 'ETH-USDT-SWAP', 'SOL-USDT-SWAP', 'XRP-USDT-SWAP',
  'BNB-USDT-SWAP', 'DOGE-USDT-SWAP', 'ADA-USDT-SWAP', 'AVAX-USDT-SWAP',
  'LINK-USDT-SWAP', 'OP-USDT-SWAP',
]
const bars = [
  { label: '1m', value: '1m' },
  { label: '5m', value: '5m' },
  { label: '15m', value: '15m' },
  { label: '1H', value: '1H' },
  { label: '4H', value: '4H' },
  { label: '日线', value: '1D' },
  { label: '周线', value: '1W' },
  { label: '月线', value: '1M' },
  { label: '年线', value: '1Y' },
]

const instId = ref('BTC-USDT-SWAP')
const bar = ref('1m')
const loading = ref(false)
const autoRefresh = ref(true)
const countdown = ref(60)
const lastUpdate = ref('')
const candles = ref([])
const mainIndicators = ref(['EMA', 'SMC'])
const subIndicators = ref(['VOL'])
const subOrder = ref(['VOL', 'VWAP_DEV', 'MACD', 'RSI', 'KDJ'])
const emaInput = ref('5,10,21')
const emaPeriods = ref([5, 10, 21])
const mainIndicatorOptions = [
  { label: 'MA', value: 'MA' },
  { label: 'EMA', value: 'EMA' },
  { label: 'BOLL', value: 'BOLL' },
  { label: 'VWAP', value: 'VWAP' },
  { label: 'SMC结构', value: 'SMC' },
  { label: '支撑压力', value: 'SR' },
  { label: '云图', value: 'CLOUD' },
]
const subIndicatorOptions = [
  { label: '成交量', value: 'VOL' },
  { label: 'VWAP偏离', value: 'VWAP_DEV' },
  { label: 'MACD', value: 'MACD' },
  { label: 'RSI', value: 'RSI' },
  { label: 'KDJ', value: 'KDJ' },
]

const chartRef = ref(null)
let chart = null
let refreshTimer = null
let countdownTimer = null

const rows = computed(() => candles.value.map(c => ({
  ts: +c[0],
  time: formatTime(+c[0]),
  open: +c[1],
  high: +c[2],
  low: +c[3],
  close: +c[4],
  volume: +c[5],
})))

const closePrices = computed(() => rows.value.map(r => r.close))
const typicalPrices = computed(() => rows.value.map(r => (r.high + r.low + r.close) / 3))
const ma7 = computed(() => ma(closePrices.value, 7))
const ma25 = computed(() => ma(closePrices.value, 25))
const ma99 = computed(() => ma(closePrices.value, 99))
const emaLines = computed(() => emaPeriods.value.map((period, index) => ({
  period,
  color: ['#3fb950', '#58a6ff', '#d29922', '#a371f7', '#ff7b72'][index % 5],
  data: ema(closePrices.value, period),
})))
const boll = computed(() => calcBoll(closePrices.value, 20, 2))
const macd = computed(() => calcMacd(closePrices.value))
const rsi6 = computed(() => rsi(closePrices.value, 6))
const rsi12 = computed(() => rsi(closePrices.value, 12))
const kdj = computed(() => calcKdj(rows.value, 9))
const vwap = computed(() => calcVwap(rows.value))
const vwapDev = computed(() => closePrices.value.map((v, i) => vwap.value[i] ? round((v - vwap.value[i]) / vwap.value[i] * 100, 3) : null))
const cloud = computed(() => calcIchimoku(rows.value))
const srLevels = computed(() => calcSupportResistance(rows.value))
const smc = computed(() => calcSmc(rows.value))

const tickerCards = computed(() => {
  const last = rows.value.at(-1)
  const prev = rows.value.at(-2)
  const change = last && prev ? (last.close - prev.close) / prev.close * 100 : 0
  const rangeRows = rows.value.slice(-Math.min(80, rows.value.length))
  const high = rangeRows.length ? Math.max(...rangeRows.map(r => r.high)) : 0
  const low = rangeRows.length ? Math.min(...rangeRows.map(r => r.low)) : 0
  const vol = rangeRows.reduce((sum, r) => sum + r.volume, 0)
  return [
    { label: '最新价', value: last ? price(last.close) : '--', sub: instId.value, color: change >= 0 ? '#3fb950' : '#f85149' },
    { label: '单K涨跌', value: `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`, sub: periodLabel(bar.value), color: change >= 0 ? '#3fb950' : '#f85149' },
    { label: '波动区间', value: `${price(high)} / ${price(low)}`, sub: '最近 80 根K线', color: '#58a6ff' },
    { label: '成交量', value: compact(vol), sub: '近期累计', color: '#d29922' },
  ]
})

const signals = computed(() => {
  const close = closePrices.value.at(-1)
  const m7 = ma7.value.at(-1)
  const m25 = ma25.value.at(-1)
  const r = rsi6.value.at(-1)
  const macdHist = macd.value.hist.at(-1)
  const k = kdj.value.k.at(-1)
  const d = kdj.value.d.at(-1)
  const vw = vwap.value.at(-1)
  const cloudTop = Math.max(cloud.value.senkouA.at(-1) || 0, cloud.value.senkouB.at(-1) || 0)
  return [
    signal('趋势', close > m7 && m7 > m25 ? '多头排列' : close < m7 && m7 < m25 ? '空头排列' : '震荡', close > m7 ? '#3fb950' : '#d29922', `MA7 ${price(m7)} / MA25 ${price(m25)}`),
    signal('云图', close > cloudTop ? '云上强势' : close < cloudTop ? '云内/云下' : '待确认', close > cloudTop ? '#3fb950' : '#8b949e', `云顶 ${price(cloudTop)}`),
    signal('VWAP', close > vw ? '资金均价上方' : '资金均价下方', close > vw ? '#58a6ff' : '#f85149', `VWAP ${price(vw)}`),
    signal('RSI', r > 70 ? '超买' : r < 30 ? '超卖' : '中性', r > 70 ? '#f85149' : r < 30 ? '#3fb950' : '#8b949e', `RSI6 ${num(r)}`),
    signal('MACD', macdHist > 0 ? '动能扩张' : '动能收缩', macdHist > 0 ? '#3fb950' : '#f85149', `柱 ${num(macdHist)}`),
    signal('KDJ', k > d ? '金叉偏多' : '死叉偏空', k > d ? '#3fb950' : '#f85149', `K ${num(k)} / D ${num(d)}`),
  ]
})

const keyLevelRows = computed(() => [
  { label: '压力位', value: price(srLevels.value.resistance), color: '#ffb86b', desc: '近期摆动高点聚合' },
  { label: '支撑位', value: price(srLevels.value.support), color: '#58a6ff', desc: '近期摆动低点聚合' },
  { label: 'VWAP', value: price(vwap.value.at(-1)), color: '#a78bfa', desc: '成交量加权均价' },
  { label: 'BOLL中轨', value: price(boll.value.mid.at(-1)), color: '#8b949e', desc: '20周期均值' },
])

const structureSummary = computed(() => {
  const last = rows.value.at(-1)
  const prev = rows.value.at(-2)
  const close = closePrices.value.at(-1)
  const emaFast = emaLines.value[0]?.data?.at(-1)
  const emaMid = emaLines.value[1]?.data?.at(-1)
  const r = rsi6.value.at(-1)
  const hist = macd.value.hist.at(-1)
  const up = close > emaFast && emaFast > emaMid
  const down = close < emaFast && emaFast < emaMid
  const range = `${price(srLevels.value.support)} - ${price(srLevels.value.resistance)}`
  const change = last && prev ? (last.close - prev.close) / prev.close * 100 : 0
  return {
    label: up ? '多头排列' : down ? '空头排列' : '震荡压缩',
    desc: `EMA ${emaPeriods.value.slice(0, 3).join('/')} 结构`,
    range,
    bias: change >= 0 ? `短线 +${change.toFixed(2)}%` : `短线 ${change.toFixed(2)}%`,
    momentum: r > 70 ? 'RSI 过热' : r < 30 ? 'RSI 冷却' : hist > 0 ? '动能扩张' : '动能收敛',
    energy: `RSI ${num(r)} / MACD ${num(hist)}`,
  }
})

const recentRows = computed(() => rows.value.slice(-8).reverse().map(r => {
  const idx = rows.value.findIndex(item => item.ts === r.ts)
  const prev = rows.value[idx - 1]
  return {
    time: r.time.slice(-5),
    close: price(r.close),
    change: prev ? (r.close - prev.close) / prev.close * 100 : 0,
  }
}))

const chartHeight = computed(() => {
  const subs = activeSubOrder().length
  if (subs >= 5) return '1180px'
  if (subs === 4) return '960px'
  if (subs === 3) return '860px'
  if (subs === 2) return '780px'
  return '720px'
})

async function load() {
  loading.value = true
  try {
    const requestBar = bar.value === '1Y' ? '1M' : bar.value
    const requestLimit = bar.value === '1Y' ? 240 : 300
    const raw = await getCandles(instId.value, requestBar, requestLimit)
    const normalized = (Array.isArray(raw) ? raw : []).reverse()
    candles.value = bar.value === '1Y' ? aggregateYearly(normalized) : normalized
    lastUpdate.value = dayjs().format('HH:mm:ss')
    countdown.value = 60
    await nextTick()
    renderChart()
  } catch (e) {
    ElMessage.warning('K线数据加载失败')
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chart && chartRef.value) chart = initChart(chartRef.value)
  if (!chart) return

  const times = rows.value.map(r => r.time)
  const ohlc = rows.value.map(r => [r.open, r.close, r.low, r.high])
  const volumes = rows.value.map(r => r.volume)
  const volumeMa = ma(volumes, 20)
  const activeSubs = activeSubOrder()
  const grids = buildGrids(activeSubs)
  const xIndexes = grids.map((_, i) => i)
  const axisCommon = { axisLine: { lineStyle: { color: cs.value.gridLine } }, axisTick: { show: false } }
  const xAxis = grids.map((_, i) => ({
    ...axisCommon,
    type: 'category',
    data: times,
    gridIndex: i,
    boundaryGap: true,
    axisLabel: {
      show: i === 0 || i === grids.length - 1,
      fontSize: 10,
      color: cs.value.legendColor,
      margin: 9,
      formatter: value => value,
    },
    axisPointer: {
      label: {
        show: true,
        formatter: p => p.value,
      },
    },
    splitLine: { show: false },
  }))
  const yAxis = grids.map((_, i) => ({
    ...axisCommon,
    type: 'value',
    scale: true,
    gridIndex: i,
    axisLabel: { color: cs.value.legendColor, fontSize: 10 },
    splitLine: { lineStyle: { color: cs.value.gridLine, opacity: .7 } },
  }))
  const priceBounds = calcPriceBounds()
  yAxis[0].min = priceBounds.min
  yAxis[0].max = priceBounds.max
  const series = [{
    name: 'K线',
    type: 'candlestick',
    data: ohlc,
    xAxisIndex: 0,
    yAxisIndex: 0,
    itemStyle: { color: '#3fb950', color0: '#f85149', borderColor: '#3fb950', borderColor0: '#f85149' },
  }]

  if (mainIndicators.value.includes('CLOUD')) {
    series.push(
      lineSeries('云顶A', cloud.value.senkouA, '#31d0aa', 0, { width: 1, opacity: .72, areaStyle: { color: 'rgba(49,208,170,0.10)' } }),
      lineSeries('云底B', cloud.value.senkouB, '#ff7b72', 0, { width: 1, opacity: .72, areaStyle: { color: 'rgba(255,139,107,0.08)' } }),
    )
  }
  if (mainIndicators.value.includes('MA')) {
    series.push(lineSeries('MA7', ma7.value, '#79c0ff', 0), lineSeries('MA25', ma25.value, '#d29922', 0), lineSeries('MA99', ma99.value, '#a371f7', 0))
  }
  if (mainIndicators.value.includes('EMA')) {
    emaLines.value.forEach(line => {
      series.push(lineSeries(`EMA${line.period}`, line.data, line.color, 0))
    })
  }
  if (mainIndicators.value.includes('BOLL')) {
    series.push(lineSeries('BOLL上轨', boll.value.up, '#d29922', 0), lineSeries('BOLL中轨', boll.value.mid, '#8b949e', 0), lineSeries('BOLL下轨', boll.value.down, '#d29922', 0))
  }
  if (mainIndicators.value.includes('VWAP')) {
    series.push(lineSeries('VWAP', vwap.value, '#a78bfa', 0, { width: 2.2, type: 'dashed' }))
  }
  if (mainIndicators.value.includes('SR')) {
    series[0].markLine = {
      silent: true,
      symbol: 'none',
      label: { color: cs.value.legendColor, fontSize: 10 },
      data: [
        { yAxis: srLevels.value.resistance, name: '压力', lineStyle: { color: '#ffb86b', type: 'dashed', width: 1.5 }, label: { formatter: '压力 {c}' } },
        { yAxis: srLevels.value.support, name: '支撑', lineStyle: { color: '#58a6ff', type: 'dashed', width: 1.5 }, label: { formatter: '支撑 {c}' } },
      ]
    }
  }
  if (mainIndicators.value.includes('SMC')) {
    applySmc(series, times)
  }

  activeSubs.forEach((key, offset) => {
    const gridIndex = offset + 1
    applySubAxisTitle(yAxis[gridIndex], key)
    if (key === 'VOL') {
      series.push(
        subTitleSeries('成交量', gridIndex),
        { name: '成交量', type: 'bar', data: volumes, xAxisIndex: gridIndex, yAxisIndex: gridIndex, barMaxWidth: 10, itemStyle: { color: p => rows.value[p.dataIndex]?.close >= rows.value[p.dataIndex]?.open ? 'rgba(43,213,118,.62)' : 'rgba(255,92,122,.62)', borderRadius: [3, 3, 0, 0] } },
        lineSeries('VOL MA20', volumeMa, '#79c0ff', gridIndex, { width: 1.4 }),
      )
    } else if (key === 'VWAP_DEV') {
      series.push(
        subTitleSeries('VWAP偏离%', gridIndex),
        { name: 'VWAP偏离%', type: 'bar', data: vwapDev.value, xAxisIndex: gridIndex, yAxisIndex: gridIndex, barMaxWidth: 10, itemStyle: { color: p => p.value >= 0 ? '#58a6ff' : '#f85149', borderRadius: [3, 3, 0, 0] } },
      )
    } else if (key === 'MACD') {
      series.push(
        subTitleSeries('MACD', gridIndex),
        { name: 'MACD柱', type: 'bar', data: macd.value.hist, xAxisIndex: gridIndex, yAxisIndex: gridIndex, barMaxWidth: 10, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [3, 3, 0, 0] } },
        lineSeries('DIF', macd.value.dif, '#79c0ff', gridIndex),
        lineSeries('DEA', macd.value.dea, '#d29922', gridIndex),
      )
    } else if (key === 'RSI') {
      yAxis[gridIndex].min = -5
      yAxis[gridIndex].max = 105
      series.push(
        subTitleSeries('RSI', gridIndex),
        zoneSeries('RSI超卖区', times, 0, 30, gridIndex, 'rgba(43,213,118,.10)'),
        zoneSeries('RSI超买区', times, 70, 100, gridIndex, 'rgba(255,92,122,.10)'),
        lineSeries('RSI6', rsi6.value, '#79c0ff', gridIndex, { width: 1.8, markLine: oscillatorLines(30, 70) }),
        lineSeries('RSI12', rsi12.value, '#d29922', gridIndex, { width: 1.6 }),
      )
    } else if (key === 'KDJ') {
      const kdjRange = oscillatorRange([kdj.value.k, kdj.value.d, kdj.value.j], 0, 100)
      yAxis[gridIndex].min = kdjRange.min
      yAxis[gridIndex].max = kdjRange.max
      series.push(
        subTitleSeries('KDJ', gridIndex),
        zoneSeries('KDJ超卖区', times, 0, 20, gridIndex, 'rgba(43,213,118,.10)'),
        zoneSeries('KDJ超买区', times, 80, 100, gridIndex, 'rgba(255,92,122,.10)'),
        lineSeries('K', kdj.value.k, '#79c0ff', gridIndex, { width: 1.7, markLine: oscillatorLines(20, 80) }),
        lineSeries('D', kdj.value.d, '#d29922', gridIndex, { width: 1.6 }),
        lineSeries('J', kdj.value.j, '#a371f7', gridIndex, { width: 1.5 }),
      )
    }
  })

  chart.setOption({
    animation: true,
    animationDuration: 720,
    animationEasing: 'cubicOut',
    backgroundColor: 'transparent',
    color: ['#79c0ff', '#d29922', '#3fb950', '#f85149', '#a371f7'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', crossStyle: { color: '#8b949e', opacity: .7 } },
      backgroundColor: isDark.value ? 'rgba(13,17,23,.92)' : 'rgba(255,255,255,.96)',
      borderColor: isDark.value ? '#30363d' : '#d0d7de',
      textStyle: { color: cs.value.labelColor, fontSize: 12 },
    },
    legend: {
      top: 0,
      left: 8,
      type: 'scroll',
      itemWidth: 12,
      itemHeight: 8,
      data: visibleLegendNames(series),
      textStyle: { color: cs.value.legendColor, fontSize: 11 },
    },
    grid: grids,
    xAxis,
    yAxis,
    dataZoom: [
      { type: 'inside', xAxisIndex: xIndexes, start: 54, end: 100 },
      { type: 'slider', xAxisIndex: xIndexes, start: 54, end: 100, height: 24, bottom: 10, borderColor: cs.value.dzBorder, fillerColor: 'rgba(88,166,255,.16)', handleStyle: { color: '#58a6ff' }, textStyle: cs.value.dzText },
    ],
    axisPointer: { link: xIndexes.map(i => ({ xAxisIndex: i })) },
    series,
  }, true)
}

function buildGrids(activeSubs = activeSubOrder()) {
  const height = chartRef.value?.clientHeight || 900
  const top = 48
  const bottom = 58
  const mainSubGap = activeSubs.length ? 34 : 0
  const subGap = 24
  const subHeights = activeSubs.map(key => {
    if (key === 'RSI' || key === 'KDJ') return 134
    if (key === 'MACD') return 118
    return 108
  })
  const usedBySubs = subHeights.reduce((sum, value) => sum + value, 0)
    + Math.max(0, activeSubs.length - 1) * subGap
    + top + bottom + mainSubGap
  const mainHeight = Math.max(activeSubs.length ? 310 : 560, height - usedBySubs)
  const grids = [{ left: 90, right: 32, top, height: mainHeight }]
  let cursor = top + mainHeight + mainSubGap
  subHeights.forEach(height => {
    grids.push({ left: 90, right: 32, top: cursor, height })
    cursor += height + subGap
  })
  return grids
}

function calcPriceBounds() {
  const values = []
  rows.value.forEach(r => values.push(r.high, r.low))
  ;[ma7.value, ma25.value, ma99.value, ...emaLines.value.map(line => line.data), boll.value.up, boll.value.down, vwap.value, cloud.value.senkouA, cloud.value.senkouB]
    .forEach(arr => (arr || []).forEach(v => { if (v != null && !Number.isNaN(v)) values.push(v) }))
  if (srLevels.value.support) values.push(srLevels.value.support)
  if (srLevels.value.resistance) values.push(srLevels.value.resistance)
  if (mainIndicators.value.includes('SMC')) {
    smc.value.liquidity.forEach(line => values.push(line.price))
    smc.value.orderBlocks.forEach(block => values.push(block.low, block.high))
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (!Number.isFinite(min) || !Number.isFinite(max) || min === max) return { min: null, max: null }
  const pad = (max - min) * 0.08
  return { min: round(min - pad, 6), max: round(max + pad, 6) }
}

function visibleLegendNames(series) {
  return series
    .map(item => item.name)
    .filter(name => name && !name.includes('超买区') && !name.includes('超卖区') && !name.startsWith('__title_'))
}

function activeSubOrder() {
  return subOrder.value.filter(key => subIndicators.value.includes(key))
}

function indicatorLabel(key) {
  return ({ VOL: '成交量', VWAP_DEV: 'VWAP偏离', MACD: 'MACD', RSI: 'RSI', KDJ: 'KDJ', SMC: 'SMC结构' })[key] || key
}

function moveSub(key, direction) {
  const arr = [...subOrder.value]
  const index = arr.indexOf(key)
  const target = index + direction
  if (index < 0 || target < 0 || target >= arr.length) return
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
  subOrder.value = arr
}

function applyEmaInput() {
  const values = emaInput.value
    .split(',')
    .map(v => Number.parseInt(v.trim(), 10))
    .filter(v => Number.isFinite(v) && v >= 2 && v <= 300)
  emaPeriods.value = [...new Set(values)].slice(0, 6)
  emaInput.value = emaPeriods.value.join(',')
}

function applySubAxisTitle(axis, key) {
  axis.name = indicatorLabel(key)
  axis.nameLocation = 'middle'
  axis.nameGap = 60
  axis.nameTextStyle = { color: cs.value.legendColor, fontSize: 12, fontWeight: 700 }
}

function subTitleSeries(title, axisIndex) {
  return {
    name: `__title_${title}`,
    type: 'line',
    data: [],
    xAxisIndex: axisIndex,
    yAxisIndex: axisIndex,
    silent: true,
    tooltip: { show: false },
    markPoint: {
      symbol: 'roundRect',
      symbolSize: [86, 22],
      data: [{ coord: [0, 0], value: title }],
      label: { color: '#fff', formatter: title, fontSize: 11, fontWeight: 700 },
      itemStyle: { color: 'rgba(88,166,255,.72)' },
    },
  }
}

function lineSeries(name, data, color, axisIndex, extra = {}) {
  return {
    name,
    type: 'line',
    data,
    xAxisIndex: axisIndex,
    yAxisIndex: axisIndex,
    showSymbol: false,
    symbol: 'none',
    smooth: false,
    lineStyle: { width: extra.width || 1.6, color, type: extra.type || 'solid', opacity: extra.opacity ?? 1 },
    areaStyle: extra.areaStyle,
    markLine: extra.markLine,
    emphasis: { focus: 'series' },
  }
}

function zoneSeries(name, times, low, high, axisIndex, color) {
  return {
    name,
    type: 'line',
    data: times.map(() => low),
    xAxisIndex: axisIndex,
    yAxisIndex: axisIndex,
    symbol: 'none',
    showSymbol: false,
    lineStyle: { opacity: 0 },
    areaStyle: { color, origin: high },
    legendHoverLink: false,
    silent: true,
    markArea: {
      silent: true,
      itemStyle: { color },
      data: [[{ yAxis: low }, { yAxis: high }]],
    },
    tooltip: { show: false },
  }
}

function oscillatorLines(low, high) {
  return {
    silent: true,
    symbol: 'none',
    label: {
      color: cs.value.legendColor,
      fontSize: 10,
      formatter: p => p.value === high ? `超买 ${high}` : `超卖 ${low}`,
    },
    lineStyle: { color: cs.value.gridLine, type: 'dashed', width: 1 },
    data: [
      { yAxis: low, value: low },
      { yAxis: high, value: high },
    ],
  }
}

function oscillatorRange(groups, floor = 0, ceil = 100) {
  const values = groups.flat().filter(v => v != null && Number.isFinite(v))
  const min = Math.min(floor, ...values)
  const max = Math.max(ceil, ...values)
  const pad = Math.max(8, (max - min) * 0.08)
  return { min: Math.floor(min - pad), max: Math.ceil(max + pad) }
}

function ma(values, n) {
  return values.map((_, i) => i < n - 1 ? null : avg(values.slice(i - n + 1, i + 1)))
}

function ema(values, n) {
  const k = 2 / (n + 1)
  const out = []
  values.forEach((v, i) => { out.push(i === 0 ? v : v * k + out[i - 1] * (1 - k)) })
  return out.map(v => round(v, 6))
}

function calcBoll(values, n, k) {
  const mid = ma(values, n)
  const up = values.map((_, i) => {
    if (i < n - 1) return null
    const slice = values.slice(i - n + 1, i + 1)
    const m = mid[i]
    const sd = Math.sqrt(avg(slice.map(v => (v - m) ** 2)))
    return round(m + k * sd, 6)
  })
  const down = values.map((_, i) => {
    if (i < n - 1) return null
    const slice = values.slice(i - n + 1, i + 1)
    const m = mid[i]
    const sd = Math.sqrt(avg(slice.map(v => (v - m) ** 2)))
    return round(m - k * sd, 6)
  })
  return { mid, up, down }
}

function calcMacd(values) {
  const fast = ema(values, 12)
  const slow = ema(values, 26)
  const dif = fast.map((v, i) => round(v - slow[i], 6))
  const dea = ema(dif, 9)
  const hist = dif.map((v, i) => round((v - dea[i]) * 2, 6))
  return { dif, dea, hist }
}

function rsi(values, n) {
  return values.map((_, i) => {
    if (i < n) return null
    let gain = 0
    let loss = 0
    for (let j = i - n + 1; j <= i; j++) {
      const diff = values[j] - values[j - 1]
      if (diff >= 0) gain += diff
      else loss -= diff
    }
    if (loss === 0) return 100
    return round(100 - 100 / (1 + gain / loss), 4)
  })
}

function calcKdj(items, n) {
  const k = []
  const d = []
  const j = []
  items.forEach((row, i) => {
    if (i < n - 1) {
      k.push(null); d.push(null); j.push(null); return
    }
    const slice = items.slice(i - n + 1, i + 1)
    const low = Math.min(...slice.map(r => r.low))
    const high = Math.max(...slice.map(r => r.high))
    const rsv = high === low ? 50 : (row.close - low) / (high - low) * 100
    const prevK = k[i - 1] ?? 50
    const prevD = d[i - 1] ?? 50
    const kv = prevK * 2 / 3 + rsv / 3
    const dv = prevD * 2 / 3 + kv / 3
    k.push(round(kv, 4))
    d.push(round(dv, 4))
    j.push(round(3 * kv - 2 * dv, 4))
  })
  return { k, d, j }
}

function calcVwap(items) {
  let pv = 0
  let vol = 0
  return items.map(row => {
    const tp = (row.high + row.low + row.close) / 3
    pv += tp * row.volume
    vol += row.volume
    return vol ? round(pv / vol, 6) : null
  })
}

function calcIchimoku(items) {
  const conv = midpoint(items, 9)
  const base = midpoint(items, 26)
  const senkouA = conv.map((v, i) => v == null || base[i] == null ? null : round((v + base[i]) / 2, 6))
  const senkouB = midpoint(items, 52)
  return { conv, base, senkouA, senkouB }
}

function midpoint(items, n) {
  return items.map((_, i) => {
    if (i < n - 1) return null
    const slice = items.slice(i - n + 1, i + 1)
    return round((Math.max(...slice.map(r => r.high)) + Math.min(...slice.map(r => r.low))) / 2, 6)
  })
}

function calcSupportResistance(items) {
  const slice = items.slice(-80)
  if (!slice.length) return { support: 0, resistance: 0 }
  const lows = slice.map(r => r.low).sort((a, b) => a - b)
  const highs = slice.map(r => r.high).sort((a, b) => b - a)
  return {
    support: round(avg(lows.slice(0, Math.min(5, lows.length))), 6),
    resistance: round(avg(highs.slice(0, Math.min(5, highs.length))), 6),
  }
}

function calcSmc(items) {
  if (items.length < 20) {
    return { swingHighs: [], swingLows: [], breaks: [], liquidity: [], orderBlocks: [] }
  }
  const lookback = 3
  const swingHighs = []
  const swingLows = []
  for (let i = lookback; i < items.length - lookback; i++) {
    const left = items.slice(i - lookback, i)
    const right = items.slice(i + 1, i + lookback + 1)
    const row = items[i]
    if (left.every(r => row.high > r.high) && right.every(r => row.high >= r.high)) {
      swingHighs.push({ index: i, price: row.high, time: row.time })
    }
    if (left.every(r => row.low < r.low) && right.every(r => row.low <= r.low)) {
      swingLows.push({ index: i, price: row.low, time: row.time })
    }
  }

  const breaks = []
  let lastHigh = null
  let lastLow = null
  let trend = 'neutral'
  items.forEach((row, index) => {
    const high = swingHighs.find(point => point.index === index - 1)
    const low = swingLows.find(point => point.index === index - 1)
    if (high) lastHigh = high
    if (low) lastLow = low
    if (lastHigh && row.close > lastHigh.price && index > lastHigh.index) {
      breaks.push({ index, price: row.close, direction: 'up', type: trend === 'down' ? 'CHOCH' : 'BOS' })
      trend = 'up'
      lastHigh = null
    }
    if (lastLow && row.close < lastLow.price && index > lastLow.index) {
      breaks.push({ index, price: row.close, direction: 'down', type: trend === 'up' ? 'CHOCH' : 'BOS' })
      trend = 'down'
      lastLow = null
    }
  })

  const latestHighs = swingHighs.slice(-3)
  const latestLows = swingLows.slice(-3)
  const liquidity = [
    ...latestHighs.map(point => ({ ...point, type: '买方流动性', color: '#d29922' })),
    ...latestLows.map(point => ({ ...point, type: '卖方流动性', color: '#58a6ff' })),
  ]
  const orderBlocks = breaks.slice(-3).map(signal => {
    const search = items.slice(Math.max(0, signal.index - 12), signal.index)
    const candle = signal.direction === 'up'
      ? [...search].reverse().find(row => row.close < row.open) || search.at(-1)
      : [...search].reverse().find(row => row.close > row.open) || search.at(-1)
    const candleIndex = candle ? items.indexOf(candle) : signal.index
    return {
      start: Math.max(0, candleIndex),
      end: Math.min(items.length - 1, signal.index + 16),
      low: candle ? Math.min(candle.open, candle.close, candle.low) : signal.price,
      high: candle ? Math.max(candle.open, candle.close, candle.high) : signal.price,
      direction: signal.direction,
    }
  })
  return { swingHighs: swingHighs.slice(-16), swingLows: swingLows.slice(-16), breaks: breaks.slice(-10), liquidity, orderBlocks }
}

function applySmc(series, times) {
  const data = smc.value
  const candle = series[0]
  const smcMarkLines = [
    ...(candle.markLine?.data || []),
    ...data.liquidity.map(line => ({
      yAxis: line.price,
      name: line.type,
      lineStyle: { color: line.color, type: 'dashed', width: 1.2, opacity: .72 },
      label: { formatter: `${line.type} ${price(line.price)}`, color: line.color, fontSize: 10 },
    })),
  ]
  candle.markLine = {
    ...(candle.markLine || {}),
    silent: true,
    symbol: 'none',
    data: smcMarkLines,
  }
  if (data.orderBlocks.length) {
    candle.markArea = {
      silent: true,
      itemStyle: { opacity: .18 },
      data: data.orderBlocks.map(block => ([
        { xAxis: times[block.start], yAxis: block.low, itemStyle: { color: block.direction === 'up' ? 'rgba(63,185,80,.24)' : 'rgba(248,81,73,.24)' } },
        { xAxis: times[block.end], yAxis: block.high },
      ])),
    }
  }
  series.push(
    {
      name: 'SMC BOS/CHOCH',
      type: 'scatter',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: data.breaks.map(signal => ({
        name: signal.type,
        value: [times[signal.index], signal.price],
        symbolSize: 18,
        label: {
          show: true,
          formatter: signal.type,
          position: signal.direction === 'up' ? 'top' : 'bottom',
          color: signal.direction === 'up' ? '#3fb950' : '#f85149',
          fontSize: 10,
          fontWeight: 700,
        },
        itemStyle: { color: signal.direction === 'up' ? '#3fb950' : '#f85149', borderColor: '#fff', borderWidth: 1 },
      })),
      tooltip: { formatter: p => `${p.name}<br/>${p.value?.[0]}<br/>价格 ${price(p.value?.[1])}` },
    },
    {
      name: 'SMC摆动点',
      type: 'scatter',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: [
        ...data.swingHighs.map(point => ({ value: [times[point.index], point.price], symbol: 'triangle', symbolRotate: 180, itemStyle: { color: '#d29922' } })),
        ...data.swingLows.map(point => ({ value: [times[point.index], point.price], symbol: 'triangle', itemStyle: { color: '#58a6ff' } })),
      ],
      symbolSize: 9,
      tooltip: { formatter: p => `摆动点<br/>${p.value?.[0]}<br/>${price(p.value?.[1])}` },
    },
  )
}

function aggregateYearly(items) {
  const groups = new Map()
  items.forEach(row => {
    const key = dayjs(+row[0]).format('YYYY')
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(row)
  })
  return [...groups.values()].map(group => {
    const first = group[0]
    const last = group[group.length - 1]
    const high = Math.max(...group.map(r => +r[2]))
    const low = Math.min(...group.map(r => +r[3]))
    const vol = group.reduce((sum, r) => sum + (+r[5] || 0), 0)
    return [first[0], first[1], high, low, last[4], vol]
  })
}

function signal(label, value, color, desc) {
  return { label, value, color, desc }
}

function avg(values) {
  const valid = values.filter(v => v != null && !Number.isNaN(v))
  return valid.reduce((sum, v) => sum + v, 0) / (valid.length || 1)
}

function round(v, digits = 2) {
  return v == null ? null : +Number(v).toFixed(digits)
}

function price(v) {
  if (v == null || Number.isNaN(v)) return '--'
  return Number(v).toLocaleString(undefined, { maximumFractionDigits: v > 100 ? 2 : 5 })
}

function compact(v) {
  if (!v) return '0'
  if (v > 1e9) return `${(v / 1e9).toFixed(2)}B`
  if (v > 1e6) return `${(v / 1e6).toFixed(2)}M`
  if (v > 1e3) return `${(v / 1e3).toFixed(2)}K`
  return Number(v).toFixed(2)
}

function num(v) {
  return v == null ? '--' : Number(v).toFixed(2)
}

function periodLabel(v) {
  return bars.find(b => b.value === v)?.label || v
}

function formatTime(ts) {
  if (bar.value === '1D' || bar.value === '1W' || bar.value === '1M' || bar.value === '1Y') return dayjs(ts).format('YYYY-MM-DD')
  return dayjs(ts).format('MM-DD HH:mm')
}

function startTimers() {
  stopTimers()
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    if (!autoRefresh.value) return
    countdown.value = Math.max(0, countdown.value - 1)
  }, 1000)
  refreshTimer = window.setInterval(() => {
    if (autoRefresh.value) load()
  }, 60000)
}

function stopTimers() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (countdownTimer) window.clearInterval(countdownTimer)
  refreshTimer = null
  countdownTimer = null
}

watch([mainIndicators, subIndicators], async () => {
  await nextTick()
  renderChart()
}, { deep: true })

watch([subOrder, emaPeriods], async () => {
  await nextTick()
  renderChart()
}, { deep: true })

watch(isDark, async () => {
  chart?.dispose()
  chart = null
  await nextTick()
  renderChart()
})

watch(autoRefresh, enabled => {
  if (enabled) startTimers()
  else stopTimers()
})

function onResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  startTimers()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopTimers()
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.kline-page { color: var(--text-primary); animation: pageIn .35s ease both; }
.screen-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.kline-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-heading);
  margin-bottom: 4px;
}
.page-sub { color: var(--text-secondary); font-size: 13px; }
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.symbol-select { width: 182px; }
.period-group { max-width: 100%; }
.ticker-row { margin-bottom: 14px; }
.ticker-card {
  min-height: 108px;
  position: relative;
  overflow: hidden;
}
.ticker-card::after {
  content: "";
  position: absolute;
  inset: auto -30px -60px auto;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(88,166,255,.18), transparent 68%);
  pointer-events: none;
}
.ticker-label { color: var(--text-secondary); font-size: 12px; margin-bottom: 8px; }
.ticker-value { font-size: 25px; font-weight: 800; margin-bottom: 6px; letter-spacing: 0; }
.ticker-sub { color: var(--text-dim); font-size: 12px; }
.control-card { padding: 12px 16px; }
.indicator-groups { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.indicator-group { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.indicator-group > span { color: var(--text-secondary); font-size: 13px; font-weight: 700; }
.indicator-select { width: 232px; }
.ema-config :deep(.el-input) { width: 132px; }
.order-config { flex-basis: 100%; align-items: flex-start; }
.order-pills { display: flex; flex-wrap: wrap; gap: 8px; }
.order-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0 5px 0 10px;
  background: var(--bg-main);
  color: var(--text-secondary);
  font-size: 12px;
}
.order-pill.disabled { opacity: .42; }
.order-pill :deep(.el-button) { padding: 2px 4px; height: 22px; min-height: 22px; color: var(--accent-blue); }
.refresh-info { display: flex; gap: 14px; color: var(--text-dim); font-size: 12px; margin-left: auto; }
.screen-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: 16px;
  align-items: start;
}
.kline-card {
  padding: 12px;
  background:
    linear-gradient(180deg, rgba(88,166,255,.08), transparent 120px),
    var(--bg-card);
}
.structure-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.structure-strip > div {
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(255,255,255,.04), transparent), var(--bg-main);
}
.structure-strip span {
  display: block;
  color: var(--text-dim);
  font-size: 11px;
  margin-bottom: 5px;
}
.structure-strip strong {
  display: block;
  color: var(--text-heading);
  font-size: 16px;
  line-height: 1.15;
}
.structure-strip em {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.main-chart { min-height: 720px; }
.side-panel { min-width: 0; }
.signal-card,
.levels-card { padding-bottom: 16px; }
.signal-card,
.levels-card,
.side-panel .chart-card { padding: 12px; }
.signal-list,
.level-list { display: grid; gap: 8px; }
.signal-item,
.level-row {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 9px;
  background: linear-gradient(180deg, rgba(255,255,255,.035), transparent), var(--bg-main);
  transition: transform .18s ease, border-color .18s ease;
}
.signal-item:hover,
.level-row:hover { transform: translateY(-2px); border-color: rgba(88,166,255,.55); }
.signal-item span,
.level-row span { display: block; color: var(--text-secondary); font-size: 11px; margin-bottom: 4px; }
.signal-item b,
.level-row b { display: block; font-size: 14px; margin-bottom: 2px; }
.signal-item em,
.level-row em { color: var(--text-dim); font-size: 11px; font-style: normal; line-height: 1.35; }
.profit { color: #3fb950; }
.loss { color: #f85149; }
@keyframes pageIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>


