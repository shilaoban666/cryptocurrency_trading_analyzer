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
            <span :style="{color: row.pnl >= 0 ? '#3fb950' : '#f85149'}">{{ row.pnl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fee"          label="手续费(U)"   width="100" />
        <el-table-column prop="px"           label="强平价"      width="120" />
        <el-table-column prop="sz"           label="仓位(张)"    width="100" />
        <el-table-column prop="balanceAfter" label="余额(U)"     />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getLiquidation, syncLiquidation, getCandles } from '@/api'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const stats          = ref({})
const syncing        = ref(false)
const selectedSymbol = ref('')
const selectedBar    = ref('1H')

const klineRef     = ref(null)
const symbolPieRef = ref(null)
const hourRef      = ref(null)
const dailyRef     = ref(null)
let charts = {}

const symbolList = computed(() => stats.value.symbolNames || [])

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
    { icon: '💥', label: '总爆仓次数',   value: s.totalCount ?? 0,          color: '#f85149', sub: '历史强平单数量' },
    { icon: '📉', label: '总亏损',       value: fmt(s.totalLoss) + ' U',     color: '#f85149', sub: '强平累计损失' },
    { icon: '📊', label: '平均单次亏损', value: fmt(s.avgLoss)   + ' U',     color: '#f0883e', sub: '每次爆仓平均' },
    { icon: '🔥', label: '最惨单次亏损', value: fmt(s.maxSingleLoss) + ' U', color: '#f85149',
      sub: s.mostLiquidatedSymbol ? `最多：${s.mostLiquidatedSymbol}` : '无数据' },
  ]
})

function fmt(v) { return v != null ? (+v).toFixed(2) : '0.00' }

async function loadStats() {
  stats.value = await getLiquidation()
  await nextTick()
  renderAll()
  if (symbolList.value.length > 0 && !selectedSymbol.value) {
    selectedSymbol.value = symbolList.value[0]
    await loadCandles()
  }
}

function renderAll() {
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
      itemStyle: { color: '#f85149' }, symbol: 'pin', symbolSize: 36,
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
        handleStyle: { color: '#58a6ff' }, fillerColor: 'rgba(88,166,255,0.1)',
        borderColor: cs.value.dzBorder, textStyle: cs.value.dzText }
    ],
    series: [{
      type: 'candlestick', data: ohlc,
      itemStyle: { color: '#3fb950', color0: '#f85149', borderColor: '#3fb950', borderColor0: '#f85149' },
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
          const r = Math.round(248 * ratio + 30 * (1-ratio))
          const g = Math.round(81 * ratio + 185 * (1-ratio))
          const b = Math.round(73 * ratio + 80 * (1-ratio))
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
        itemStyle: { color: 'rgba(248,81,73,0.6)', borderRadius: [3,3,0,0] } },
      { name: '亏损(U)', type: 'line', yAxisIndex: 1, data: s.dailyLoss || [],
        smooth: true, symbol: 'none', lineStyle: { color: '#f0883e' } }
    ]
  })
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
  if (selectedSymbol.value) loadCandles()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(() => { loadStats(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.liq-page { color: var(--text-primary); }

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
