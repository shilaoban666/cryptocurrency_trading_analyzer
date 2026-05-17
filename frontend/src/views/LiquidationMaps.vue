<template>
  <div class="liq-map-page">
    <div class="page-head">
      <div>
        <div class="page-title">清算热图</div>
        <div class="page-sub">OKX 合约清算热力层与价格清算地图</div>
      </div>
      <div class="toolbar">
        <el-select v-model="instId" style="width:180px" @change="reload">
          <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
        </el-select>
        <el-radio-group v-model="range" @change="reload">
          <el-radio-button value="12h">12H</el-radio-button>
          <el-radio-button value="24h">24H</el-radio-button>
          <el-radio-button value="7d">7D</el-radio-button>
          <el-radio-button value="30d">30D</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="reload">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="12" class="summary-row">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card metric-card">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="metric-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="map-tabs" @tab-change="renderActive">
      <el-tab-pane label="清算热图" name="heatmap">
        <div class="chart-card">
          <div class="card-title">
            价格区间清算热力
            <span class="hint">等待 Coinank API 后替换为实时数据</span>
          </div>
          <div ref="heatmapRef" class="heatmap-chart" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="清算地图" name="map">
        <div class="map-layout">
          <div class="chart-card">
            <div class="card-title">多空清算分布</div>
            <div ref="mapRef" class="map-chart" />
          </div>
          <div class="chart-card level-card">
            <div class="card-title">关键价格带</div>
            <div class="level-list">
              <div v-for="level in keyLevels" :key="level.price" class="level-row">
                <span>{{ level.price }}</span>
                <b :class="level.side === 'long' ? 'loss' : 'profit'">{{ level.amount }}</b>
                <em>{{ level.side === 'long' ? '多头清算' : '空头清算' }}</em>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const symbols = ['BTC-USDT-SWAP', 'ETH-USDT-SWAP', 'SOL-USDT-SWAP', 'XRP-USDT-SWAP', 'BNB-USDT-SWAP']
const basePrices = {
  'BTC-USDT-SWAP': 65000,
  'ETH-USDT-SWAP': 3200,
  'SOL-USDT-SWAP': 150,
  'XRP-USDT-SWAP': 0.55,
  'BNB-USDT-SWAP': 580,
}

const instId = ref('BTC-USDT-SWAP')
const range = ref('24h')
const activeTab = ref('heatmap')
const heatmapRef = ref(null)
const mapRef = ref(null)
let charts = {}

const preview = computed(() => buildPreview(instId.value, range.value))

const cards = computed(() => {
  const p = preview.value
  return [
    { label: '估算清算总额', value: compact(p.total), sub: range.value, color: '#d29922' },
    { label: '多头清算', value: compact(p.longTotal), sub: '下方价格带', color: '#f85149' },
    { label: '空头清算', value: compact(p.shortTotal), sub: '上方价格带', color: '#3fb950' },
    { label: '最密集价格', value: price(p.hotPrice), sub: instId.value, color: '#58a6ff' },
  ]
})

const keyLevels = computed(() => preview.value.levels
  .slice()
  .sort((a, b) => b.amount - a.amount)
  .slice(0, 10)
  .map(row => ({ price: price(row.price), amount: compact(row.amount), side: row.side })))

function reload() {
  nextTick(renderActive)
}

function renderActive() {
  if (activeTab.value === 'heatmap') renderHeatmap()
  else renderMap()
}

function renderHeatmap() {
  if (!charts.heatmap && heatmapRef.value) charts.heatmap = initChart(heatmapRef.value)
  const chart = charts.heatmap
  if (!chart) return
  const p = preview.value
  chart.setOption({
    tooltip: {
      formatter: item => {
        const [timeIndex, priceIndex, value] = item.value
        return `${p.times[timeIndex]}<br/>价格: ${price(p.prices[priceIndex])}<br/>强度: ${compact(value)}`
      }
    },
    grid: { left: 76, right: 26, top: 20, bottom: 48 },
    xAxis: { type: 'category', data: p.times, axisLabel: { color: cs.value.legendColor, fontSize: 10 } },
    yAxis: { type: 'category', data: p.prices.map(price), axisLabel: { color: cs.value.legendColor, fontSize: 10 } },
    visualMap: {
      min: 0,
      max: p.maxHeat,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: isDark.value ? ['#121820', '#1f6feb', '#d29922', '#f85149'] : ['#eef6ff', '#60a5fa', '#f59e0b', '#dc2626'] },
      textStyle: { color: cs.value.legendColor },
    },
    series: [{
      type: 'heatmap',
      data: p.heatmap,
      emphasis: { itemStyle: { borderColor: '#ffffff', borderWidth: 1 } },
      progressive: 0,
    }]
  }, true)
}

function renderMap() {
  if (!charts.map && mapRef.value) charts.map = initChart(mapRef.value)
  const chart = charts.map
  if (!chart) return
  const levels = preview.value.levels
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: p => `${p[0].name}<br/>多头清算: ${compact(Math.abs(p[0].value))}<br/>空头清算: ${compact(p[1].value)}`,
    },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 82, right: 26, top: 34, bottom: 42 },
    xAxis: {
      type: 'value',
      axisLabel: { formatter: v => compact(Math.abs(v)), color: cs.value.legendColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
    },
    yAxis: {
      type: 'category',
      data: levels.map(row => price(row.price)),
      axisLabel: { color: cs.value.legendColor, fontSize: 10 },
    },
    series: [
      { name: '多头清算', type: 'bar', stack: 'liq', data: levels.map(row => row.side === 'long' ? -row.amount : 0), itemStyle: { color: '#f85149' } },
      { name: '空头清算', type: 'bar', stack: 'liq', data: levels.map(row => row.side === 'short' ? row.amount : 0), itemStyle: { color: '#3fb950' } },
    ]
  }, true)
}

function buildPreview(symbol, selectedRange) {
  const base = basePrices[symbol] || 100
  const timeCount = selectedRange === '12h' ? 12 : selectedRange === '24h' ? 24 : selectedRange === '7d' ? 28 : 30
  const levelCount = 32
  const rangePct = selectedRange === '30d' ? 0.18 : selectedRange === '7d' ? 0.12 : 0.07
  const prices = Array.from({ length: levelCount }, (_, i) => {
    const pct = -rangePct + (rangePct * 2 * i) / (levelCount - 1)
    return base * (1 + pct)
  })
  const times = Array.from({ length: timeCount }, (_, i) => selectedRange.endsWith('d') ? `T-${timeCount - i}` : `${String(i).padStart(2, '0')}:00`)
  const seed = symbol.length + selectedRange.length * 11
  const heatmap = []
  const levels = prices.map((priceValue, priceIndex) => {
    const distance = (priceValue - base) / base
    const side = distance < 0 ? 'long' : 'short'
    const centerBoost = Math.exp(-Math.abs(distance) * 18)
    const clusterBoost = 0.55 + Math.abs(Math.sin(priceIndex * 0.7 + seed)) * 0.7
    const amount = Math.round(base * centerBoost * clusterBoost * (side === 'long' ? 12 : 10))
    return { price: priceValue, amount, side }
  })

  let maxHeat = 0
  for (let t = 0; t < timeCount; t++) {
    for (let p = 0; p < levelCount; p++) {
      const wave = 0.45 + Math.abs(Math.sin((t + 1) * 0.35 + p * 0.28 + seed)) * 0.9
      const value = Math.round(levels[p].amount * wave)
      maxHeat = Math.max(maxHeat, value)
      heatmap.push([t, p, value])
    }
  }
  const longTotal = levels.filter(l => l.side === 'long').reduce((sum, l) => sum + l.amount, 0)
  const shortTotal = levels.filter(l => l.side === 'short').reduce((sum, l) => sum + l.amount, 0)
  const hot = levels.reduce((best, row) => row.amount > best.amount ? row : best, levels[0])
  return { prices, times, heatmap, levels, maxHeat, total: longTotal + shortTotal, longTotal, shortTotal, hotPrice: hot.price }
}

function price(v) {
  const digits = v >= 100 ? 0 : v >= 10 ? 2 : 4
  return Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: digits })
}

function compact(v) {
  const n = Math.abs(Number(v || 0))
  if (n >= 1e9) return `${(n / 1e9).toFixed(2)}B`
  if (n >= 1e6) return `${(n / 1e6).toFixed(2)}M`
  if (n >= 1e3) return `${(n / 1e3).toFixed(1)}K`
  return n.toFixed(0)
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderActive()
})

function onResize() {
  Object.values(charts).forEach(c => c?.resize())
}

onMounted(() => {
  nextTick(renderActive)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.liq-map-page { color: var(--text-primary); }
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title { margin-bottom: 4px; }
.page-sub { color: var(--text-secondary); font-size: 13px; }
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.summary-row { margin-bottom: 14px; }
.metric-card { min-height: 104px; }
.metric-label { color: var(--text-secondary); font-size: 12px; margin-bottom: 8px; }
.metric-value { font-size: 24px; font-weight: 700; margin-bottom: 6px; }
.metric-sub { color: var(--text-dim); font-size: 12px; }
.hint { color: var(--text-dim); font-size: 12px; font-weight: 400; margin-left: 8px; }
.map-tabs :deep(.el-tabs__item) { color: var(--text-secondary); }
.map-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.heatmap-chart { height: calc(100vh - 330px); min-height: 620px; }
.map-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.map-chart { height: calc(100vh - 330px); min-height: 620px; }
.level-list { display: grid; gap: 8px; }
.level-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 10px;
  align-items: center;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
  background: var(--bg-main);
}
.level-row span { font-weight: 700; }
.level-row b { text-align: right; }
.level-row em { grid-column: 1 / -1; color: var(--text-dim); font-size: 12px; font-style: normal; }
.profit { color: #3fb950; }
.loss { color: #f85149; }
</style>


