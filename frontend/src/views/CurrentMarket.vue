<template>
  <div>
    <div class="page-title">BTC / ETH 当前数据</div>

    <div class="chart-card filter-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <div class="filter-item">
            <span class="text-muted">观察标的</span>
            <el-segmented v-model="activeSymbol" :options="['BTC', 'ETH']" />
          </div>
        </el-col>
        <el-col :span="5">
          <div class="filter-item">
            <span class="text-muted">周期</span>
            <el-select v-model="period" style="flex:1" @change="loadAll">
              <el-option label="15分钟" value="15m" />
              <el-option label="1小时" value="1H" />
              <el-option label="4小时" value="4H" />
              <el-option label="1天" value="1D" />
            </el-select>
          </div>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" :loading="loading" @click="loadAll">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </el-col>
        <el-col :span="9" style="text-align:right">
          <span class="text-dim last-update">最后更新：{{ lastUpdate || '未更新' }}</span>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in overviewCards" :key="card.label">
        <div class="stat-card info-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">走势判断<span class="hint">{{ selected?.trendText }}</span></div>
      <el-row :gutter="12">
        <el-col :span="6" v-for="item in trendCards" :key="item.title">
          <div class="trend-item" :class="item.tone">
            <div class="trend-title">{{ item.title }}</div>
            <div class="trend-signal">{{ item.signal }}</div>
            <div class="trend-desc">{{ item.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">{{ selectedTitle }} 价格结构</div>
          <div ref="priceRef" style="height:360px" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="card-title">趋势评分</div>
          <div ref="scoreGaugeRef" style="height:360px" />
          <div class="score-note" v-if="scoreDescription">
            <div class="score-head">
              <span>{{ scoreDescription.title }}</span>
              <b :style="{ color: scoreDescription.color }">{{ scoreDescription.bias }}</b>
            </div>
            <div class="score-text">{{ scoreDescription.text }}</div>
            <div class="score-factors">
              <span v-for="item in scoreDescription.factors" :key="item.label" :class="item.tone">{{ item.label }} {{ item.value }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">RSI / MACD</div>
          <div ref="momentumRef" style="height:320px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">成交量与均量</div>
          <div ref="volumeRef" style="height:320px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">资金费率</div>
          <div ref="fundingRef" style="height:260px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">恐惧贪婪指数</div>
          <div ref="greedRef" style="height:260px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">波动率 / ATR</div>
          <div ref="atrRef" style="height:260px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">BOLL 带宽挤压</div>
          <div ref="bollWidthRef" style="height:260px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">趋势斜率</div>
          <div ref="slopeRef" style="height:260px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">成交量冲击</div>
          <div ref="volumeShockRef" style="height:260px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">BTC / ETH 相对强弱</div>
          <div ref="relativeRef" style="height:300px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">BTC vs ETH 趋势对比</div>
          <div ref="compareRef" style="height:300px" />
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">关键价位与结论</div>
      <el-table :data="levelRows" style="width:100%" size="small">
        <el-table-column prop="label" label="指标" min-width="140" />
        <el-table-column prop="value" label="数值" min-width="120" />
        <el-table-column prop="judge" label="判断" min-width="130">
          <template #default="{ row }">
            <span :style="{ color: row.color }">{{ row.judge }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="320" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { getCandles, getMarketFundingRateHistory } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const props = defineProps({
  active: { type: Boolean, default: true }
})

const period = ref('1H')
const activeSymbol = ref('BTC')
const loading = ref(false)
const lastUpdate = ref('')
const datasets = ref({ BTC: null, ETH: null })

const priceRef = ref(null)
const momentumRef = ref(null)
const volumeRef = ref(null)
const relativeRef = ref(null)
const compareRef = ref(null)
const scoreGaugeRef = ref(null)
const fundingRef = ref(null)
const greedRef = ref(null)
const atrRef = ref(null)
const bollWidthRef = ref(null)
const slopeRef = ref(null)
const volumeShockRef = ref(null)
let charts = {}

const symbolConfig = {
  BTC: { instId: 'BTC-USDT-SWAP', title: 'BTC' },
  ETH: { instId: 'ETH-USDT-SWAP', title: 'ETH' },
}

const selected = computed(() => datasets.value[activeSymbol.value])
const selectedTitle = computed(() => `${activeSymbol.value} 当前结构`)

const overviewCards = computed(() => {
  const s = selected.value
  if (!s) {
    return [
      emptyCard('最新价'),
      emptyCard('24H 变动'),
      emptyCard('RSI'),
      emptyCard('趋势评分'),
    ]
  }
  const close = s.lastClose
  const ema20Now = Array.isArray(s.ema20) ? s.ema20.at(-1) : s.ema20
  const ema50Now = Array.isArray(s.ema50) ? s.ema50.at(-1) : s.ema50
  return [
    { label: '最新价', value: fmtPrice(s.lastClose), sub: `区间 ${s.highest20.toFixed(2)} / ${s.lowest20.toFixed(2)}`, color: '#58a6ff' },
    { label: '24H 变动', value: `${s.change24h >= 0 ? '+' : ''}${s.change24h.toFixed(2)}%`, sub: close > ema20Now ? '站上 20EMA' : '低于 20EMA', color: s.change24h >= 0 ? '#3fb950' : '#f85149' },
    { label: 'RSI', value: s.rsi.toFixed(1), sub: s.rsi > 70 ? '偏热' : s.rsi < 30 ? '偏冷' : '中性', color: s.rsi > 70 ? '#d29922' : s.rsi < 30 ? '#58a6ff' : '#8b949e' },
    { label: '趋势评分', value: `${s.score > 0 ? '+' : ''}${s.score.toFixed(0)}`, sub: s.trendText, color: scoreColor(s.score) },
  ]
})

const trendCards = computed(() => {
  const s = selected.value
  if (!s) return []
  const hist = Array.isArray(s.macdHist) ? s.macdHist.at(-1) : s.macdHist
  const close = s.lastClose
  const ema20Now = Array.isArray(s.ema20) ? s.ema20.at(-1) : s.ema20
  const ema50Now = Array.isArray(s.ema50) ? s.ema50.at(-1) : s.ema50
  return [
    { title: '趋势', signal: s.trendText, tone: s.score > 2 ? 'bullish' : s.score < -2 ? 'bearish' : 'neutral', desc: `EMA20 ${close > ema20Now ? '上方' : '下方'}，EMA20/50 ${ema20Now > ema50Now ? '多头' : '空头'}。` },
    { title: '动量', signal: s.rsi > 70 ? '过热' : s.rsi < 30 ? '过冷' : '平衡', tone: s.rsi > 70 ? 'warning' : s.rsi < 30 ? 'bullish' : 'neutral', desc: `RSI ${s.rsi.toFixed(1)}，MACD 柱 ${hist > 0 ? '为正' : '为负'}。` },
    { title: '结构', signal: close > s.highest20 ? '突破高点' : close < s.lowest20 ? '跌破低点' : '区间内', tone: close > s.highest20 ? 'bullish' : close < s.lowest20 ? 'bearish' : 'neutral', desc: `20 根高低点区间 ${s.lowest20.toFixed(2)} - ${s.highest20.toFixed(2)}。` },
    { title: '可能走势', signal: s.forecast, tone: s.score > 2 ? 'bullish' : s.score < -2 ? 'bearish' : 'warning', desc: s.forecastDetail },
  ]
})

const scoreDescription = computed(() => {
  const s = selected.value
  if (!s) return null
  const hist = Array.isArray(s.macdHist) ? s.macdHist.at(-1) : s.macdHist
  const shock = Array.isArray(s.volumeShock) ? (s.volumeShock.at(-1) || 1) : (s.volumeShock || 1)
  const close = s.lastClose
  const ema20Now = Array.isArray(s.ema20) ? s.ema20.at(-1) : s.ema20
  const ema50Now = Array.isArray(s.ema50) ? s.ema50.at(-1) : s.ema50
  const emaBias = close > ema20Now && ema20Now > ema50Now ? '多头' : close < ema20Now && ema20Now < ema50Now ? '空头' : '震荡'
  const momentum = s.rsi > 70 ? '过热' : s.rsi < 30 ? '超卖修复' : hist > 0 ? '动能扩张' : '动能收缩'
  const structure = close > s.highest20 ? '突破20根高点' : close < s.lowest20 ? '跌破20根低点' : '仍在结构区间内'
  const volume = shock >= 1.5 ? '放量确认' : shock <= .7 ? '缩量观望' : '量能正常'
  const bias = s.score > 3 ? '偏多执行' : s.score < -3 ? '偏空防守' : '中性等待'
  return {
    title: `评分 ${s.score > 0 ? '+' : ''}${s.score.toFixed(0)} / 10`,
    bias,
    color: scoreColor(s.score),
    text: `当前评分来自 EMA 趋势、RSI/MACD 动量、20根K线突破结构与成交量确认。现在是${emaBias}结构，${momentum}，${structure}，${volume}。`,
    factors: [
      { label: 'EMA', value: emaBias, tone: emaBias === '多头' ? 'good' : emaBias === '空头' ? 'bad' : 'neutral' },
      { label: 'RSI', value: s.rsi.toFixed(1), tone: s.rsi > 70 ? 'warn' : s.rsi < 30 ? 'good' : 'neutral' },
      { label: 'MACD', value: hist > 0 ? '正柱' : '负柱', tone: hist > 0 ? 'good' : 'bad' },
      { label: '量能', value: `${shock.toFixed(2)}x`, tone: shock >= 1.5 ? 'warn' : 'neutral' },
    ],
  }
})

const levelRows = computed(() => {
  const s = selected.value
  if (!s) return []
  return [
    { label: '支撑位', value: s.support.toFixed(2), judge: '下方防守', color: '#58a6ff', note: '来自最近 20 根 K 线低点与 ATR 缓冲。' },
    { label: '压力位', value: s.resistance.toFixed(2), judge: '上方观察', color: '#d29922', note: '来自最近 20 根 K 线高点与 ATR 缓冲。' },
    { label: '趋势评分', value: `${s.score > 0 ? '+' : ''}${s.score.toFixed(0)}`, judge: s.score > 2 ? '偏多' : s.score < -2 ? '偏空' : '中性', color: scoreColor(s.score), note: '综合 EMA、RSI、MACD、突破与成交量加权。' },
    { label: '偏离均值', value: `${s.deviationPct.toFixed(2)}%`, judge: Math.abs(s.deviationPct) > 3 ? '偏离较大' : '正常', color: Math.abs(s.deviationPct) > 3 ? '#d29922' : '#3fb950', note: '当前价格相对 20EMA 的偏离程度。' },
  ]
})

function emptyCard(label) {
  return { label, value: '-', sub: '等待数据', color: '#8b949e' }
}

function chart(key, refEl) {
  if (!charts[key] && refEl.value) charts[key] = initChart(refEl.value)
  return charts[key]
}

async function loadAll() {
  loading.value = true
  try {
    const [btcRaw, ethRaw, btcFunding, ethFunding] = await Promise.all([
      getCandles(symbolConfig.BTC.instId, period.value, 240),
      getCandles(symbolConfig.ETH.instId, period.value, 240),
      getMarketFundingRateHistory(symbolConfig.BTC.instId, 100).catch(() => []),
      getMarketFundingRateHistory(symbolConfig.ETH.instId, 100).catch(() => []),
    ])
    datasets.value = {
      BTC: buildDataset('BTC', reverseCandles(btcRaw), reverseFunding(btcFunding)),
      ETH: buildDataset('ETH', reverseCandles(ethRaw), reverseFunding(ethFunding)),
    }
    lastUpdate.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

function renderAll() {
  renderPrice()
  renderMomentum()
  renderVolume()
  renderRelative()
  renderCompare()
  renderGauge()
  renderFunding()
  renderGreed()
  renderAtr()
  renderBollWidth()
  renderSlope()
  renderVolumeShock()
}

function renderPrice() {
  const s = selected.value
  if (!s) return
  const c = chart('price', priceRef)
  if (!c) return
  c.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: p => {
        const i = p[0]?.dataIndex ?? 0
        const bar = s.bars[i]
        if (!bar) return ''
        return `${fmtTime(bar.ts)}<br/>开: ${bar.open}<br/>高: ${bar.high}<br/>低: ${bar.low}<br/>收: ${bar.close}`
      }
    },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 68, right: 24, top: 35, bottom: 70 },
    xAxis: { type: 'category', data: s.labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    dataZoom: [
      { type: 'inside', start: 55, end: 100 },
      { type: 'slider', start: 55, end: 100, height: 22, bottom: 10 }
    ],
    series: [
      { name: 'K线', type: 'candlestick', data: s.ohlc,
        itemStyle: { color: '#3fb950', color0: '#f85149', borderColor: '#3fb950', borderColor0: '#f85149' } },
      { name: 'EMA20', type: 'line', data: s.ema20, smooth: false, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
      { name: 'EMA50', type: 'line', data: s.ema50, smooth: false, symbol: 'none', lineStyle: { color: '#d29922', width: 1.8 } },
      { name: 'EMA100', type: 'line', data: s.ema100, smooth: false, symbol: 'none', lineStyle: { color: '#8b949e', width: 1.5, type: 'dashed' } },
      { name: '上轨', type: 'line', data: s.bbUpper, smooth: false, symbol: 'none', lineStyle: { color: 'rgba(88,166,255,0.6)', width: 1 } },
      { name: '下轨', type: 'line', data: s.bbLower, smooth: false, symbol: 'none', lineStyle: { color: 'rgba(248,81,73,0.6)', width: 1 } },
    ]
  })
}

function renderMomentum() {
  const s = selected.value
  if (!s) return
  const c = chart('momentum', momentumRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 58, right: 24, top: 35, bottom: 45 },
    xAxis: { type: 'category', data: s.labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: [
      { type: 'value', min: 0, max: 100, axisLabel: { formatter: v => `${v}` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}` }, splitLine: { show: false } }
    ],
    series: [
      { name: 'RSI', type: 'line', data: s.rsiSeries, yAxisIndex: 0, smooth: false, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
      { name: 'MACD', type: 'line', data: s.macdLine, yAxisIndex: 1, smooth: false, symbol: 'none', lineStyle: { color: '#d29922', width: 1.8 } },
      { name: 'Signal', type: 'line', data: s.signalLine, yAxisIndex: 1, smooth: false, symbol: 'none', lineStyle: { color: '#8b949e', width: 1.4, type: 'dashed' } },
      { name: 'Hist', type: 'bar', data: s.macdHist, yAxisIndex: 1, barMaxWidth: 8,
        itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149' } },
    ]
  })
}

function renderVolume() {
  const s = selected.value
  if (!s) return
  const c = chart('volume', volumeRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 58, right: 24, top: 35, bottom: 45 },
    xAxis: { type: 'category', data: s.labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '成交量', type: 'bar', data: s.volumes, barMaxWidth: 10,
        itemStyle: { color: p => p.value >= s.volMa20[p.dataIndex] ? 'rgba(63,185,80,0.7)' : 'rgba(88,166,255,0.5)' } },
      { name: '均量20', type: 'line', data: s.volMa20, smooth: false, symbol: 'none', lineStyle: { color: '#d29922', width: 2 } }
    ]
  })
}

function renderRelative() {
  const btc = datasets.value.BTC
  const eth = datasets.value.ETH
  if (!btc || !eth) return
  const c = chart('relative', relativeRef)
  if (!c) return
  const len = Math.min(btc.normalized.length, eth.normalized.length)
  const labels = btc.labels.slice(-len)
  const btcNorm = btc.normalized.slice(-len)
  const ethNorm = eth.normalized.slice(-len)
  const ratio = btc.closes.slice(-len).map((v, i) => round(eth.closes.slice(-len)[i] / v, 6))
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 60, right: 24, top: 35, bottom: 45 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => `${v}` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => v.toFixed(4) }, splitLine: { show: false } }
    ],
    series: [
      { name: 'BTC 归一化', type: 'line', data: btcNorm, smooth: false, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
      { name: 'ETH 归一化', type: 'line', data: ethNorm, smooth: false, symbol: 'none', lineStyle: { color: '#3fb950', width: 2 } },
      { name: 'ETH/BTC', type: 'line', yAxisIndex: 1, data: ratio, smooth: false, symbol: 'none', lineStyle: { color: '#d29922', width: 1.5 } },
    ]
  })
}

function renderCompare() {
  const btc = datasets.value.BTC
  const eth = datasets.value.ETH
  if (!btc || !eth) return
  const c = chart('compare', compareRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis' },
    radar: {
      indicator: [
        { name: '趋势', max: 10 },
        { name: '动量', max: 10 },
        { name: '成交量', max: 10 },
        { name: '波动', max: 10 },
        { name: '突破', max: 10 },
      ],
      axisName: { color: cs.value.labelColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent'] } }
    },
    series: [{
      type: 'radar',
      data: [
        { name: 'BTC', value: [btc.scoreNorm, btc.momentumNorm, btc.volumeNorm, btc.volatilityNorm, btc.breakoutNorm], areaStyle: { color: 'rgba(88,166,255,0.18)' }, lineStyle: { color: '#58a6ff' } },
        { name: 'ETH', value: [eth.scoreNorm, eth.momentumNorm, eth.volumeNorm, eth.volatilityNorm, eth.breakoutNorm], areaStyle: { color: 'rgba(63,185,80,0.18)' }, lineStyle: { color: '#3fb950' } }
      ]
    }]
  })
}

function renderGauge() {
  const s = selected.value
  if (!s) return
  const c = chart('gauge', scoreGaugeRef)
  if (!c) return
  c.setOption({
    tooltip: { formatter: () => `${activeSymbol.value} 趋势评分：${s.score.toFixed(0)}` },
    series: [{
      type: 'gauge',
      min: -10,
      max: 10,
      startAngle: 210,
      endAngle: -30,
      progress: { show: true, width: 14 },
      axisLine: { lineStyle: { width: 14, color: [[0.3, '#f85149'], [0.5, '#8b949e'], [1, '#3fb950']] } },
      pointer: { width: 4 },
      axisTick: { distance: -18 },
      splitLine: { distance: -18, length: 12 },
      axisLabel: { color: cs.value.labelColor },
      detail: { valueAnimation: true, formatter: '{value}', color: cs.value.labelColor, fontSize: 28 },
      data: [{ value: s.score }]
    }]
  })
}

function renderFunding() {
  const s = selected.value
  if (!s) return
  const c = chart('funding', fundingRef)
  if (!c) return
  c.setOption(smallLineOption(s.fundingLabels, s.fundingRates, '#d29922', v => `${v.toFixed(4)}%`, '资金费率'))
}

function renderGreed() {
  const s = selected.value
  if (!s) return
  const c = chart('greed', greedRef)
  if (!c) return
  const value = s.greedIndex.at(-1) || 50
  c.setOption({
    tooltip: { formatter: () => `指数: ${value.toFixed(0)}<br/>${greedLabel(value)}` },
    series: [{
      type: 'gauge',
      min: 0,
      max: 100,
      startAngle: 210,
      endAngle: -30,
      radius: '88%',
      axisLine: { lineStyle: { width: 12, color: [[0.25, '#3fb950'], [0.55, '#58a6ff'], [0.75, '#d29922'], [1, '#f85149']] } },
      progress: { show: true, width: 12, itemStyle: { color: value > 75 ? '#f85149' : value > 55 ? '#d29922' : value < 25 ? '#3fb950' : '#58a6ff' } },
      pointer: { width: 4 },
      detail: { formatter: v => `${Math.round(v)}`, fontSize: 28, color: cs.value.labelColor },
      title: { color: cs.value.legendColor, fontSize: 12 },
      data: [{ value, name: greedLabel(value) }],
    }]
  })
}

function renderAtr() {
  const s = selected.value
  if (!s) return
  const c = chart('atr', atrRef)
  if (!c) return
  c.setOption(smallLineOption(s.labels, s.atrPctSeries, '#a371f7', v => `${v.toFixed(2)}%`, 'ATR%'))
}

function renderBollWidth() {
  const s = selected.value
  if (!s) return
  const c = chart('bollWidth', bollWidthRef)
  if (!c) return
  c.setOption(smallLineOption(s.labels, s.bollWidth, '#58a6ff', v => `${v.toFixed(2)}%`, 'BOLL宽度'))
}

function renderSlope() {
  const s = selected.value
  if (!s) return
  const c = chart('slope', slopeRef)
  if (!c) return
  c.setOption(smallLineOption(s.labels, s.emaSlope, '#3fb950', v => `${v.toFixed(3)}%`, 'EMA斜率'))
}

function renderVolumeShock() {
  const s = selected.value
  if (!s) return
  const c = chart('volumeShock', volumeShockRef)
  if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>量能倍数: ${p[0].value.toFixed(2)}x` },
    grid: { left: 50, right: 16, top: 18, bottom: 42 },
    xAxis: { type: 'category', data: s.labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}x` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: s.volumeShock, barMaxWidth: 10, itemStyle: { color: p => p.value >= 1.5 ? '#d29922' : '#58a6ff', borderRadius: [3, 3, 0, 0] } }]
  })
}

function smallLineOption(labels, values, color, formatter, name) {
  return {
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${name}: ${formatter(p[0].value)}` },
    grid: { left: 52, right: 16, top: 18, bottom: 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { color, width: 2 }, areaStyle: { color: `${color}22` } }]
  }
}

function buildDataset(symbol, candles, fundingRows = []) {
  const bars = candles.map(c => ({
    ts: +c[0],
    open: num(c[1]),
    high: num(c[2]),
    low: num(c[3]),
    close: num(c[4]),
    volume: num(c[5]),
  }))
  const closes = bars.map(b => b.close)
  const highs = bars.map(b => b.high)
  const lows = bars.map(b => b.low)
  const volumes = bars.map(b => b.volume)
  const labels = bars.map(b => fmtTime(b.ts))
  const ema20 = emaSeries(closes, 20)
  const ema50 = emaSeries(closes, 50)
  const ema100 = emaSeries(closes, 100)
  const rsiSeries = rsiSeriesCalc(closes, 14)
  const { macdLine, signalLine, hist } = macdSeries(closes)
  const bb = bollinger(closes, 20, 2)
  const volMa20 = smaSeries(volumes, 20)
  const highest20 = highest(highs, 20)
  const lowest20 = lowest(lows, 20)
  const atr14 = atrSeries(bars, 14)
  const atrPctSeries = atr14.map((v, i) => closes[i] ? round(v / closes[i] * 100, 3) : 0)
  const lastClose = closes[closes.length - 1] || 0
  const close24 = closes[closes.length - 24] || closes[0] || lastClose
  const change24h = close24 ? (lastClose - close24) / close24 * 100 : 0
  const trendScore = scoreTrend({ closes, ema20, ema50, rsiSeries, macdLine, signalLine, hist, volumes, volMa20, highest20, lowest20 })
  const trendText = trendLabel(trendScore)
  const support = lowest20 - (atr14[atr14.length - 1] || 0) * 0.5
  const resistance = highest20 + (atr14[atr14.length - 1] || 0) * 0.5
  const deviationPct = ema20[ema20.length - 1] ? (lastClose - ema20[ema20.length - 1]) / ema20[ema20.length - 1] * 100 : 0
  const bollWidth = closes.map((close, i) => close ? round((bb.upper[i] - bb.lower[i]) / close * 100, 3) : 0)
  const emaSlope = ema20.map((v, i) => i === 0 || !ema20[i - 1] ? 0 : round((v - ema20[i - 1]) / ema20[i - 1] * 100, 4))
  const volumeShock = volumes.map((v, i) => round(v / ((volMa20[i] || v || 1)), 3))
  const fundingProxy = closes.map((close, i) => round(((close - (ema20[i] || close)) / close * 0.03) + ((rsiSeries[i] - 50) * 0.0008), 5))
  const realFundingRates = fundingRows.map(row => round(num(row.fundingRate) * 100, 5))
  const realFundingLabels = fundingRows.map(row => fmtTime(num(row.fundingTime)))
  const greedIndex = closes.map((close, i) => clampValue(50 + (rsiSeries[i] - 50) * 0.55 + emaSlope[i] * 18 + (volumeShock[i] - 1) * 7 - atrPctSeries[i] * 1.2, 0, 100))
  const forecast = trendScore > 3 ? '偏多延续' : trendScore < -3 ? '偏空延续' : Math.abs(rsiSeries[rsiSeries.length - 1] || 0 - 50) < 6 ? '震荡整理' : '等待方向'
  const forecastDetail = trendScore > 3
    ? '价格与均线同向，MACD 和成交量配合，优先关注回踩承接。'
    : trendScore < -3
      ? '价格弱于均线，动量偏空，优先关注反弹承压。'
      : '趋势信号未共振，当前位置更像区间或等待突破。'

  const scoreNorm = Math.max(0, Math.min(10, trendScore + 5))
  const momentumNorm = normalize(Math.abs((rsiSeries[rsiSeries.length - 1] || 50) - 50), 50)
  const volumeNorm = normalize((volumes[volumes.length - 1] || 0) / ((volMa20[volMa20.length - 1] || 1) || 1), 3)
  const volatilityNorm = normalize((atr14[atr14.length - 1] || 0) / (lastClose || 1), 0.05)
  const breakoutNorm = lastClose >= highest20 ? 10 : lastClose <= lowest20 ? 2 : 5

  return {
    symbol,
    bars,
    labels,
    closes,
    volumes,
    ema20,
    ema50,
    ema100,
    rsiSeries,
    rsi: rsiSeries[rsiSeries.length - 1] || 50,
    macdLine,
    signalLine,
    macdHist: hist,
    bbUpper: bb.upper,
    bbLower: bb.lower,
    volMa20,
    highest20,
    lowest20,
    atr14,
    atrPctSeries,
    bollWidth,
    emaSlope,
    volumeShock,
    fundingProxy,
    fundingRates: realFundingRates.length ? realFundingRates : fundingProxy,
    fundingLabels: realFundingLabels.length ? realFundingLabels : labels,
    greedIndex,
    lastClose,
    change24h,
    score: trendScore,
    trendText,
    support,
    resistance,
    deviationPct,
    forecast,
    forecastDetail,
    ohlc: bars.map(b => [b.open, b.close, b.low, b.high]),
    normalized: normalizeSeries(closes),
    scoreNorm,
    momentumNorm,
    volumeNorm,
    volatilityNorm,
    breakoutNorm,
  }
}

function reverseCandles(raw) {
  return Array.isArray(raw) ? raw.slice().reverse() : []
}

function reverseFunding(raw) {
  return Array.isArray(raw) ? raw.slice().reverse() : []
}

function emaSeries(values, period) {
  if (!values.length) return []
  const k = 2 / (period + 1)
  const out = [values[0]]
  for (let i = 1; i < values.length; i++) {
    out.push(values[i] * k + out[i - 1] * (1 - k))
  }
  return out
}

function smaSeries(values, period) {
  return values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - period + 1), i + 1)
    return slice.reduce((sum, v) => sum + v, 0) / slice.length
  })
}

function rsiSeriesCalc(values, period) {
  if (!values.length) return []
  const out = Array(values.length).fill(50)
  let gains = 0
  let losses = 0
  for (let i = 1; i <= period && i < values.length; i++) {
    const diff = values[i] - values[i - 1]
    if (diff >= 0) gains += diff
    else losses -= diff
  }
  let avgGain = gains / period
  let avgLoss = losses / period
  out[period] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss)
  for (let i = period + 1; i < values.length; i++) {
    const diff = values[i] - values[i - 1]
    const gain = Math.max(diff, 0)
    const loss = Math.max(-diff, 0)
    avgGain = (avgGain * (period - 1) + gain) / period
    avgLoss = (avgLoss * (period - 1) + loss) / period
    out[i] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss)
  }
  return out
}

function macdSeries(values) {
  const ema12 = emaSeries(values, 12)
  const ema26 = emaSeries(values, 26)
  const macdLine = values.map((_, i) => (ema12[i] || 0) - (ema26[i] || 0))
  const signalLine = emaSeries(macdLine, 9)
  const hist = macdLine.map((v, i) => v - (signalLine[i] || 0))
  return { macdLine, signalLine, hist }
}

function bollinger(values, period, mult) {
  return {
    upper: values.map((_, i) => {
      const slice = values.slice(Math.max(0, i - period + 1), i + 1)
      const mean = slice.reduce((sum, v) => sum + v, 0) / slice.length
      const std = Math.sqrt(slice.reduce((sum, v) => sum + (v - mean) ** 2, 0) / slice.length)
      return mean + std * mult
    }),
    lower: values.map((_, i) => {
      const slice = values.slice(Math.max(0, i - period + 1), i + 1)
      const mean = slice.reduce((sum, v) => sum + v, 0) / slice.length
      const std = Math.sqrt(slice.reduce((sum, v) => sum + (v - mean) ** 2, 0) / slice.length)
      return mean - std * mult
    })
  }
}

function atrSeries(bars, period) {
  const trs = bars.map((bar, i) => {
    if (i === 0) return bar.high - bar.low
    const prevClose = bars[i - 1].close
    return Math.max(
      bar.high - bar.low,
      Math.abs(bar.high - prevClose),
      Math.abs(bar.low - prevClose),
    )
  })
  return smaSeries(trs, period)
}

function highest(values, period) {
  return values.slice(-period).reduce((max, v) => Math.max(max, v), Number.NEGATIVE_INFINITY)
}

function lowest(values, period) {
  return values.slice(-period).reduce((min, v) => Math.min(min, v), Number.POSITIVE_INFINITY)
}

function normalizeSeries(values) {
  if (!values.length) return []
  const base = values[0] || 1
  return values.map(v => +(v / base * 100).toFixed(2))
}

function scoreTrend({ closes, ema20, ema50, rsiSeries, macdLine, signalLine, hist, volumes, volMa20, highest20, lowest20 }) {
  const i = closes.length - 1
  const close = closes[i] || 0
  let score = 0
  if (close > (ema20[i] || 0)) score += 2
  else score -= 2
  if ((ema20[i] || 0) > (ema50[i] || 0)) score += 1.5
  else score -= 1.5
  const rsi = rsiSeries[i] || 50
  if (rsi >= 55 && rsi <= 68) score += 1.5
  else if (rsi > 72) score -= 1
  else if (rsi < 30) score += 0.8
  if ((macdLine[i] || 0) > (signalLine[i] || 0)) score += 1.5
  else score -= 1.5
  if ((hist[i] || 0) > 0) score += 0.5
  else score -= 0.5
  if (close >= highest20) score += 1
  if (close <= lowest20) score -= 1
  if ((volumes[i] || 0) > (volMa20[i] || 0)) score += 0.5
  return Math.max(-10, Math.min(10, score))
}

function trendLabel(score) {
  if (score >= 5) return '强势偏多'
  if (score >= 2) return '偏多'
  if (score <= -5) return '强势偏空'
  if (score <= -2) return '偏空'
  return '震荡'
}

function scoreColor(score) {
  if (score >= 5) return '#3fb950'
  if (score >= 2) return '#58a6ff'
  if (score <= -5) return '#f85149'
  if (score <= -2) return '#d29922'
  return '#8b949e'
}

function normalize(value, max) {
  return Math.max(0, Math.min(10, value / max * 10))
}

function clampValue(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function greedLabel(value) {
  if (value >= 75) return '极度贪婪'
  if (value >= 55) return '偏贪婪'
  if (value <= 25) return '极度恐惧'
  if (value <= 45) return '偏恐惧'
  return '中性'
}

function num(v) { return Number.parseFloat(v) || 0 }
function round(v, digits = 2) { return +Number(v || 0).toFixed(digits) }
function fmtTime(ts) { return dayjs(ts).format('MM-DD HH:mm') }
function fmtPrice(v) { return Number(v || 0).toFixed(2) }

watch(activeSymbol, async () => {
  await nextTick()
  renderAll()
})

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
})

watch(() => props.active, async active => {
  if (!active) return
  await nextTick()
  renderAll()
  onResize()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(() => { loadAll(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.filter-card { margin-bottom: 16px; padding: 12px 20px; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.info-card { text-align: center; }
.trend-item {
  min-height: 118px;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-main);
}
.trend-title { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.trend-signal { font-size: 18px; font-weight: 700; margin-bottom: 6px; }
.trend-desc { font-size: 12px; color: var(--text-dim); line-height: 1.5; }
.trend-item.bullish .trend-signal { color: #3fb950; }
.trend-item.bearish .trend-signal { color: #f85149; }
.trend-item.warning .trend-signal { color: #d29922; }
.trend-item.neutral .trend-signal { color: #8b949e; }
.score-note {
  margin: -14px 8px 8px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-main);
}
.score-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 6px; }
.score-head span { color: var(--text-secondary); font-size: 12px; font-weight: 700; }
.score-head b { font-size: 15px; }
.score-text { color: var(--text-secondary); font-size: 12px; line-height: 1.55; margin-bottom: 9px; }
.score-factors { display: flex; flex-wrap: wrap; gap: 6px; }
.score-factors span {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 11px;
  color: var(--text-dim);
  background: var(--bg-card);
}
.score-factors .good { color: #3fb950; border-color: rgba(63,185,80,.28); }
.score-factors .bad { color: #f85149; border-color: rgba(248,81,73,.28); }
.score-factors .warn { color: #d29922; border-color: rgba(210,153,34,.28); }
.last-update { font-size: 12px; }
.hint { font-size: 11px; color: var(--text-dim); font-weight: 400; margin-left: 6px; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
</style>
