<template>
  <div>
    <div class="page-title">当前持仓</div>

    <div class="chart-card filter-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <div class="filter-item">
            <span class="text-muted">数据刷新</span>
            <el-button type="primary" :loading="loading" @click="load">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </el-col>
        <el-col :span="16" style="text-align:right">
          <span class="text-dim last-update">最后更新：{{ lastUpdate || '未更新' }}</span>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="card in summaryCards" :key="card.label">
        <div class="stat-card info-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card ai-card">
      <div class="card-title ai-title">
        <div>
          <span>DeepSeek V4 Pro 当前仓位解析</span>
          <span class="hint">仓位 / 行情 / 动作</span>
        </div>
        <el-button type="primary" :loading="aiLoading" @click="generateAiAnalysis">
          <el-icon><DataAnalysis /></el-icon>
          生成分析
        </el-button>
      </div>
      <el-row :gutter="16">
        <el-col :span="7">
          <div ref="aiRef" class="ai-chart" />
        </el-col>
        <el-col :span="17">
          <div v-if="aiLoading || aiStreamText" class="ai-stream">
            <div class="ai-stream-head">
              <span>{{ aiStage || 'DeepSeek 流式分析中' }}</span>
              <el-tag type="info" size="small">SSE</el-tag>
            </div>
            <pre class="ai-stream-text">{{ aiStreamText || '等待模型开始输出...' }}</pre>
          </div>
          <div v-else-if="aiResult" class="ai-result">
            <div class="ai-headline">
              <div class="ai-headline-text">{{ aiResult.headline || '暂无结论' }}</div>
              <div class="ai-tags">
                <el-tag :type="riskTone(aiResult.riskLevel)" size="small">{{ aiResult.riskLevel || '未知风险' }}</el-tag>
                <el-tag :type="biasTone(aiResult.biasScore)" size="small">{{ aiResult.actionBias || '观望' }}</el-tag>
                <span class="text-dim ai-meta">置信 {{ aiResult.confidence || 0 }}%</span>
                <span class="text-dim ai-meta">{{ fmtGeneratedAt(aiResult.generatedAt) }}</span>
              </div>
            </div>
            <div class="ai-sections">
              <div class="ai-section">
                <div class="ai-section-title">当前仓位解析</div>
                <div class="ai-section-text">{{ aiResult.positionAnalysis || '-' }}</div>
              </div>
              <div class="ai-section">
                <div class="ai-section-title">行情解析</div>
                <div class="ai-section-text">{{ aiResult.marketAnalysis || '-' }}</div>
              </div>
              <div class="ai-section">
                <div class="ai-section-title">动作推荐</div>
                <div class="ai-section-text">{{ aiResult.actionRecommendation || '-' }}</div>
              </div>
            </div>
            <div class="ai-lists">
              <div v-if="asList(aiResult.keySignals).length" class="ai-list-block">
                <div class="ai-section-title">关键信号</div>
                <div v-for="item in asList(aiResult.keySignals)" :key="item" class="ai-list-item">{{ item }}</div>
              </div>
              <div v-if="asList(aiResult.riskWarnings).length" class="ai-list-block">
                <div class="ai-section-title">风险提示</div>
                <div v-for="item in asList(aiResult.riskWarnings)" :key="item" class="ai-list-item warning">{{ item }}</div>
              </div>
              <div v-if="asList(aiResult.actionChecklist).length" class="ai-list-block">
                <div class="ai-section-title">检查项</div>
                <div v-for="item in asList(aiResult.actionChecklist)" :key="item" class="ai-list-item">{{ item }}</div>
              </div>
            </div>
          </div>
          <div v-else class="ai-placeholder">
            <div class="ai-placeholder-title">{{ aiError || '暂无 AI 解析' }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="chart-card">
      <div class="card-title">当前持仓明细</div>
      <el-table :data="tableRows" style="width:100%" size="small">
        <el-table-column prop="instId" label="品种" min-width="130">
          <template #default="{ row }">
            <div>
              <div style="font-weight:600">{{ symbolName(row.instId) }}</div>
              <div class="text-dim" style="font-size:11px">{{ row.instId }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" width="84">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'long' ? 'success' : 'danger'" size="small">
              {{ row.direction === 'long' ? '做多' : '做空' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="仓位" width="90">
          <template #default="{ row }">{{ fmtNum(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="avgPx" label="均价" width="110">
          <template #default="{ row }">{{ fmtPrice(row.avgPx) }}</template>
        </el-table-column>
        <el-table-column prop="markPx" label="现价" width="110">
          <template #default="{ row }">{{ fmtPrice(row.markPx) }}</template>
        </el-table-column>
        <el-table-column prop="upl" label="浮动盈亏" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.upl >= 0 ? '#3fb950' : '#f85149' }">{{ fmtPnl(row.upl) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="uplRatioPct" label="盈亏比" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.uplRatioPct >= 0 ? '#3fb950' : '#f85149' }">{{ fmtPct(row.uplRatioPct) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="holdingMinutes" label="持有多久" width="110">
          <template #default="{ row }">{{ fmtHolding(row.holdingMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="addCount" label="补仓" width="100">
          <template #default="{ row }">
            <el-tag :type="row.addCount > 1 ? 'warning' : 'info'" size="small">
              {{ row.addCount > 1 ? `补仓 ${row.addCount} 次` : '单次建仓' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alignment" label="方向判断" width="120">
          <template #default="{ row }">
            <el-tag :type="row.alignment === '顺势' ? 'success' : 'danger'" size="small">
              {{ row.alignment }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="divergence" label="背离" min-width="150">
          <template #default="{ row }">
            <el-tag :type="divergenceTone(row.divergence)" size="small">
              {{ row.divergence }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="liquidationDistancePct" label="爆仓距" width="110">
          <template #default="{ row }">{{ fmtPct(row.liquidationDistancePct) }}</template>
        </el-table-column>
        <el-table-column prop="lever" label="杠杆" width="80">
          <template #default="{ row }">{{ row.lever }}x</template>
        </el-table-column>
        <el-table-column prop="marginMode" label="保证金" width="100" />
        <el-table-column prop="marketTrend" label="市场趋势" width="100" />
      </el-table>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持有多久</div>
          <div ref="durationRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">浮动盈亏</div>
          <div ref="pnlRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">补仓与仓位规模</div>
          <div ref="addRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">方向是否正确</div>
          <div ref="alignmentRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">是否与市场背离</div>
          <div ref="divergenceRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">爆仓距离与杠杆</div>
          <div ref="riskRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持仓位置散点</div>
          <div ref="scatterRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持仓质量雷达</div>
          <div ref="radarRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-title">持仓诊断</div>
      <el-table :data="diagnosticRows" style="width:100%" size="small">
        <el-table-column prop="metric" label="指标" min-width="150" />
        <el-table-column prop="value" label="当前值" min-width="140" />
        <el-table-column prop="judge" label="判断" min-width="120">
          <template #default="{ row }">
            <span :style="{ color: row.color }">{{ row.judge }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="300" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getCurrentPositions, openPositionAiAnalysisStream } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const positions = ref([])
const loading = ref(false)
const lastUpdate = ref('')
const aiLoading = ref(false)
const aiResult = ref(null)
const aiError = ref('')
const aiStage = ref('')
const aiStreamText = ref('')
let aiSource = null

const aiRef = ref(null)
const durationRef = ref(null)
const pnlRef = ref(null)
const addRef = ref(null)
const alignmentRef = ref(null)
const divergenceRef = ref(null)
const riskRef = ref(null)
const scatterRef = ref(null)
const radarRef = ref(null)
let charts = {}

const tableRows = computed(() => [...positions.value].sort((a, b) => Math.abs(num(b.notional)) - Math.abs(num(a.notional))))

const summaryCards = computed(() => {
  const rows = tableRows.value
  const count = rows.length
  const longCount = rows.filter(row => row.direction === 'long').length
  const shortCount = rows.filter(row => row.direction === 'short').length
  const totalUpl = sum(rows.map(row => num(row.upl)))
  const totalNotional = sum(rows.map(row => Math.abs(num(row.notional))))
  const avgHolding = count ? sum(rows.map(row => num(row.holdingMinutes))) / count : 0
  const alignedCount = rows.filter(row => row.alignment === '顺势').length
  const addCount = rows.filter(row => num(row.addCount) > 1).length
  return [
    { label: '持仓数量', value: count, sub: `多 ${longCount} / 空 ${shortCount}`, color: '#58a6ff' },
    { label: '净浮盈亏', value: fmtPnl(totalUpl), sub: `名义价值 ${fmtMoney(totalNotional)}`, color: totalUpl >= 0 ? '#3fb950' : '#f85149' },
    { label: '顺势仓位', value: count ? `${alignedCount}/${count}` : '-', sub: `方向正确率 ${count ? fmtPct(alignedCount / count * 100) : '-'}`, color: alignedCount >= Math.ceil(count / 2) ? '#3fb950' : '#f0883e' },
    { label: '补仓仓位', value: count ? `${addCount}/${count}` : '-', sub: `平均持仓 ${fmtHolding(avgHolding)}`, color: addCount > 0 ? '#f0883e' : '#8b949e' },
  ]
})

const diagnosticRows = computed(() => {
  const rows = tableRows.value
  const count = rows.length || 1
  const alignedCount = rows.filter(row => row.alignment === '顺势').length
  const divergedCount = rows.filter(row => isDivergence(row.divergence)).length
  const addCount = rows.filter(row => num(row.addCount) > 1).length
  const avgHolding = rows.length ? sum(rows.map(row => num(row.holdingMinutes))) / rows.length : 0
  const avgLeverage = rows.length ? sum(rows.map(row => num(row.lever))) / rows.length : 0
  const minLiq = rows.length ? Math.min(...rows.map(row => num(row.liquidationDistancePct) || 0)) : 0
  return [
    {
      metric: '方向正确率',
      value: fmtPct(alignedCount / count * 100),
      judge: alignedCount / count >= 0.5 ? '偏顺势' : '偏逆势',
      color: alignedCount / count >= 0.5 ? '#3fb950' : '#f85149',
      note: '顺势仓位占比越高，当前持仓和市场节奏越一致。',
    },
    {
      metric: '补仓占比',
      value: fmtPct(addCount / count * 100),
      judge: addCount > 0 ? '存在补仓' : '无补仓',
      color: addCount > 0 ? '#f0883e' : '#3fb950',
      note: '补仓不是问题本身，问题在于补仓后方向是否仍然正确。',
    },
    {
      metric: '背离占比',
      value: fmtPct(divergedCount / count * 100),
      judge: divergedCount > 0 ? '存在背离' : '未见明显背离',
      color: divergedCount > 0 ? '#f85149' : '#3fb950',
      note: '若背离仓位同时亏损并补仓，通常是扛单信号更强。',
    },
    {
      metric: '平均持仓',
      value: fmtHolding(avgHolding),
      judge: avgHolding >= 240 ? '持有偏久' : '尚可',
      color: avgHolding >= 240 ? '#f0883e' : '#3fb950',
      note: '时间长并不必然错误，但若亏损仓位明显更久，要重点看止损纪律。',
    },
    {
      metric: '平均杠杆',
      value: avgLeverage ? `${avgLeverage.toFixed(1)}x` : '-',
      judge: avgLeverage >= 10 ? '杠杆偏高' : '正常',
      color: avgLeverage >= 10 ? '#f0883e' : '#3fb950',
      note: '杠杆越高，对持仓方向和爆仓距离的容错越低。',
    },
    {
      metric: '最短爆仓距',
      value: fmtPct(minLiq),
      judge: minLiq > 0 && minLiq < 8 ? '风险偏近' : '尚可',
      color: minLiq > 0 && minLiq < 8 ? '#f85149' : '#3fb950',
      note: '距离爆仓越近，越要关注减仓、止损和补仓行为。',
    },
  ]
})

async function load() {
  loading.value = true
  try {
    const res = await getCurrentPositions()
    positions.value = Array.isArray(res) ? res : []
    lastUpdate.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

function renderAll() {
  renderAi()
  renderDuration()
  renderPnl()
  renderAdd()
  renderAlignment()
  renderDivergence()
  renderRisk()
  renderScatter()
  renderRadar()
}

async function generateAiAnalysis() {
  closeAiStream()
  aiLoading.value = true
  aiError.value = ''
  aiResult.value = null
  aiStage.value = '正在连接流式分析'
  aiStreamText.value = ''
  await nextTick()
  renderAi()

  const source = openPositionAiAnalysisStream()
  aiSource = source

  source.addEventListener('stage', async e => {
    const data = parseSseData(e)
    aiStage.value = data.message || 'DeepSeek 流式分析中'
    await nextTick()
    renderAi()
  })

  source.addEventListener('delta', e => {
    const data = parseSseData(e)
    if (data.delta) {
      aiStreamText.value += data.delta
    }
  })

  source.addEventListener('done', async e => {
    const data = parseSseData(e)
    aiResult.value = data.result || null
    aiStreamText.value = ''
    aiStage.value = '分析完成'
    aiLoading.value = false
    closeAiStream()
    await nextTick()
    renderAi()
    ElMessage.success('DeepSeek 分析完成')
  })

  source.addEventListener('fail', async e => {
    const data = parseSseData(e)
    aiError.value = data.message || 'DeepSeek 流式分析失败'
    aiStreamText.value = ''
    aiStage.value = ''
    aiLoading.value = false
    closeAiStream()
    await nextTick()
    renderAi()
  })

  source.onerror = async () => {
    if (!aiLoading.value) return
    aiError.value = 'DeepSeek 流式连接中断'
    aiStreamText.value = ''
    aiStage.value = ''
    aiLoading.value = false
    closeAiStream()
    await nextTick()
    renderAi()
  }
}

function renderAi() {
  const c = chart('ai', aiRef)
  if (aiLoading.value) {
    c.clear()
    return c.setOption(emptyOption(aiStage.value || 'DeepSeek 分析中'))
  }
  if (!aiResult.value) {
    c.clear()
    return c.setOption(emptyOption(aiError.value ? 'AI 分析失败' : '暂无 AI 解析'))
  }
  const score = clamp(num(aiResult.value.biasScore), -100, 100)
  const color = score > 25 ? '#3fb950' : score < -25 ? '#f85149' : '#f0883e'
  c.setOption({
    tooltip: { formatter: () => `动作偏向分: ${score}<br/>${aiResult.value.actionBias || '观望'}<br/>置信度: ${aiResult.value.confidence || 0}%` },
    series: [{
      type: 'gauge',
      min: -100,
      max: 100,
      startAngle: 210,
      endAngle: -30,
      radius: '88%',
      splitNumber: 4,
      axisLine: { lineStyle: { width: 12, color: [[0.35, '#f85149'], [0.65, '#f0883e'], [1, '#3fb950']] } },
      progress: { show: true, width: 12, itemStyle: { color } },
      pointer: { width: 4, itemStyle: { color } },
      axisTick: { distance: -18, length: 5, lineStyle: { color: cs.value.gridLine } },
      splitLine: { distance: -20, length: 12, lineStyle: { color: cs.value.gridLine } },
      axisLabel: { color: cs.value.labelColor, distance: 18, fontSize: 10 },
      detail: { formatter: v => `${Math.round(v)}`, color, fontSize: 30, offsetCenter: [0, '38%'] },
      title: { color: cs.value.legendColor, fontSize: 12, offsetCenter: [0, '68%'] },
      data: [{ value: score, name: aiResult.value.actionBias || '观望' }],
    }],
  })
}

function closeAiStream() {
  if (aiSource) {
    aiSource.close()
    aiSource = null
  }
}

function parseSseData(event) {
  try {
    return JSON.parse(event.data || '{}')
  } catch (e) {
    return {}
  }
}

function renderDuration() {
  const c = chart('duration', durationRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>持有: ${fmtHolding(p[0].value, true)}` },
    grid: { left: 70, right: 24, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: rows.map(positionLabel), axisLabel: { rotate: 25, fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v >= 60 ? `${(v / 60).toFixed(1)}h` : `${v}m` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: rows.map(row => num(row.holdingMinutes)),
      barMaxWidth: 26,
      itemStyle: { color: '#58a6ff', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', formatter: p => fmtHolding(p.value), color: cs.value.labelColor, fontSize: 11 },
    }],
  })
}

function renderPnl() {
  const c = chart('pnl', pnlRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const upl = rows.map(row => round(num(row.upl), 2))
  const ratios = rows.map(row => round(num(row.uplRatioPct), 2))
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: p => `${p[0].name}<br/>浮动盈亏: ${fmtPnl(p[0].value)}<br/>盈亏比: ${fmtPct(p[1].value)}` },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 68, right: 42, top: 36, bottom: 48 },
    xAxis: { type: 'category', data: rows.map(positionLabel), axisLabel: { rotate: 25, fontSize: 10 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { show: false } },
    ],
    series: [
      { name: '浮动盈亏', type: 'bar', data: upl, barMaxWidth: 24, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [4, 4, 0, 0] } },
      { name: '盈亏比', type: 'line', yAxisIndex: 1, data: ratios, smooth: false, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#f0883e', width: 2 }, itemStyle: { color: '#f0883e' } },
    ],
  })
}

function renderAdd() {
  const c = chart('add', addRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => `${p[0].name}<br/>补仓次数: ${p[0].value}<br/>名义价值: ${fmtMoney(p[1].value)}` },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 68, right: 42, top: 36, bottom: 48 },
    xAxis: { type: 'category', data: rows.map(positionLabel), axisLabel: { rotate: 25, fontSize: 10 } },
    yAxis: [
      { type: 'value', min: 0, axisLabel: { formatter: v => `${v}次` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${(v / 1000).toFixed(0)}K` }, splitLine: { show: false } },
    ],
    series: [
      { name: '补仓次数', type: 'bar', data: rows.map(row => num(row.addCount)), barMaxWidth: 24, itemStyle: { color: '#f0883e', borderRadius: [4, 4, 0, 0] } },
      { name: '名义价值', type: 'line', yAxisIndex: 1, data: rows.map(row => round(Math.abs(num(row.notional)), 2)), smooth: false, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
    ],
  })
}

function renderAlignment() {
  const c = chart('alignment', alignmentRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const aligned = rows.filter(row => row.alignment === '顺势').length
  const diverged = rows.length - aligned
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${p.value} 个 (${p.percent}%)` },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: [
        { name: '顺势', value: aligned, itemStyle: { color: '#3fb950' } },
        { name: '逆势/背离', value: diverged, itemStyle: { color: '#f85149' } },
      ],
      label: { color: cs.value.labelColor },
    }],
  })
}

function renderDivergence() {
  const c = chart('divergence', divergenceRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const groups = groupDivergence(rows)
  const labels = Object.keys(groups)
  const values = Object.values(groups)
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 120, right: 24, top: 24, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { formatter: v => `${v}` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'category', data: labels, axisLabel: { color: cs.value.labelColor, fontSize: 12 } },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 24,
      itemStyle: { color: '#a371f7', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: cs.value.labelColor },
    }],
  })
}

function renderRisk() {
  const c = chart('risk', riskRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: p => `${p[0].name}<br/>爆仓距: ${fmtPct(p[0].value)}<br/>杠杆: ${p[1].value}x` },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 68, right: 42, top: 36, bottom: 48 },
    xAxis: { type: 'category', data: rows.map(positionLabel), axisLabel: { rotate: 25, fontSize: 10 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}x` }, splitLine: { show: false } },
    ],
    series: [
      { name: '爆仓距离', type: 'bar', data: rows.map(row => round(num(row.liquidationDistancePct), 2)), barMaxWidth: 24, itemStyle: { color: '#58a6ff', borderRadius: [4, 4, 0, 0] } },
      { name: '杠杆', type: 'line', yAxisIndex: 1, data: rows.map(row => num(row.lever)), smooth: false, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#f0883e', width: 2 }, itemStyle: { color: '#f0883e' } },
    ],
  })
}

function renderScatter() {
  const c = chart('scatter', scatterRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const maxNotional = Math.max(...rows.map(row => Math.abs(num(row.notional))), 1)
  c.setOption({
    tooltip: { formatter: p => `${p.name}<br/>持有: ${fmtHolding(p.value[0])}<br/>盈亏比: ${fmtPct(p.value[1])}<br/>名义价值: ${fmtMoney(p.value[2])}` },
    grid: { left: 70, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'value', name: '持有分钟', axisLabel: { formatter: v => v >= 60 ? `${(v / 60).toFixed(1)}h` : `${v}m` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '盈亏比', axisLabel: { formatter: v => `${v}%` } },
    series: [{
      type: 'scatter',
      data: rows.map(row => ({
        name: positionLabel(row),
        value: [num(row.holdingMinutes), num(row.uplRatioPct), Math.abs(num(row.notional))],
        symbolSize: bubbleSize(num(row.notional), maxNotional),
        itemStyle: { color: num(row.upl) >= 0 ? '#3fb950' : '#f85149' },
      })),
      label: { show: true, formatter: p => p.name, position: 'right', color: cs.value.labelColor, fontSize: 10 },
    }],
  })
}

function renderRadar() {
  const c = chart('radar', radarRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const count = rows.length || 1
  const alignedRatio = rows.filter(row => row.alignment === '顺势').length / count
  const divergenceRatio = rows.filter(row => isDivergence(row.divergence)).length / count
  const addRatio = rows.filter(row => num(row.addCount) > 1).length / count
  const avgHold = count ? sum(rows.map(row => num(row.holdingMinutes))) / count : 0
  const avgPnlRatio = count ? sum(rows.map(row => num(row.uplRatioPct))) / count : 0
  const avgLiq = count ? sum(rows.map(row => num(row.liquidationDistancePct))) / count : 0
  const values = [
    clamp(100 - avgHold / 720 * 100, 0, 100),
    clamp(50 + avgPnlRatio * 5, 0, 100),
    clamp(100 - addRatio * 100, 0, 100),
    Math.round(alignedRatio * 100),
    clamp(100 - divergenceRatio * 100, 0, 100),
    clamp(avgLiq * 5, 0, 100),
  ]
  c.setOption({
    tooltip: {},
    radar: {
      indicator: [
        { name: '时长控制', max: 100 },
        { name: '盈亏健康', max: 100 },
        { name: '补仓克制', max: 100 },
        { name: '方向正确', max: 100 },
        { name: '背离克制', max: 100 },
        { name: '风控距离', max: 100 },
      ],
      axisName: { color: cs.value.labelColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent'] } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: values,
        name: '当前持仓质量',
        areaStyle: { color: 'rgba(88,166,255,0.22)' },
        lineStyle: { color: '#58a6ff' },
      }],
    }],
  })
}

function chart(key, elRef) {
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

function emptyOption(text = '暂无数据') {
  return {
    backgroundColor: 'transparent',
    graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text, fill: cs.value.legendColor, fontSize: 13 } }],
  }
}

function positionLabel(row) {
  return `${symbolName(row.instId)} ${row.direction === 'long' ? '多' : '空'}`
}

function symbolName(instId) {
  return String(instId || '').replace('-USDT-SWAP', '')
}

function divergenceTone(value) {
  if (value && value.includes('顺势')) return 'success'
  if (value && value.includes('背离')) return 'danger'
  if (value && value.includes('补仓')) return 'warning'
  return 'info'
}

function groupDivergence(rows) {
  const counts = {}
  rows.forEach(row => {
    const key = divergenceBucket(row.divergence)
    counts[key] = (counts[key] || 0) + 1
  })
  return counts
}

function divergenceBucket(value) {
  const text = String(value || '')
  if (!text) return '未判定'
  if (text.includes('顺势') && text.includes('盈利')) return '盈利顺势'
  if (text.includes('顺势')) return '顺势'
  if (text.includes('补仓') && text.includes('背离')) return '亏损补仓背离'
  if (text.includes('补仓')) return '亏损补仓'
  if (text.includes('背离')) return '方向背离'
  if (text.includes('盈利')) return '盈利'
  return '观望'
}

function isDivergence(value) {
  const bucket = divergenceBucket(value)
  return bucket === '方向背离' || bucket === '亏损补仓背离' || bucket === '亏损补仓'
}

function riskTone(value) {
  const text = String(value || '')
  if (text.includes('极高') || text.includes('高')) return 'danger'
  if (text.includes('中')) return 'warning'
  if (text.includes('低')) return 'success'
  return 'info'
}

function biasTone(value) {
  const score = num(value)
  if (score > 25) return 'success'
  if (score < -25) return 'danger'
  return 'warning'
}

function fmtGeneratedAt(value) {
  return value ? dayjs(value).format('MM-DD HH:mm') : ''
}

function asList(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function bubbleSize(notional, maxNotional) {
  if (!maxNotional) return 14
  return Math.max(12, Math.sqrt(Math.abs(notional) / maxNotional) * 36)
}

function fmtNum(v, digits = 2) {
  return Number(num(v)).toFixed(digits)
}

function fmtMoney(v) {
  const n = num(v)
  return `${n >= 0 ? '+' : ''}${fmtNum(n)} U`
}

function fmtPnl(v) {
  const n = num(v)
  return `${n >= 0 ? '+' : ''}${fmtNum(n)} U`
}

function fmtPrice(v) {
  return fmtNum(v, 2)
}

function fmtPct(v) {
  return `${fmtNum(v, 2)}%`
}

function fmtHolding(min, compact = false) {
  const n = num(min)
  if (!Number.isFinite(n) || n <= 0) return '未推算'
  if (compact) return n < 60 ? `${n.toFixed(0)}m` : `${(n / 60).toFixed(1)}h`
  return n < 60 ? `${n.toFixed(0)} 分钟` : `${(n / 60).toFixed(1)} 小时`
}

function sum(values) {
  return values.reduce((total, value) => total + num(value), 0)
}

function round(v, digits = 2) {
  return +Number(num(v)).toFixed(digits)
}

function num(v) {
  return Number.parseFloat(v) || 0
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

watch(isDark, async () => {
  Object.values(charts).forEach(chart => chart?.dispose())
  charts = {}
  await nextTick()
  renderAll()
})

function onResize() {
  Object.values(charts).forEach(chart => chart?.resize())
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  closeAiStream()
  Object.values(charts).forEach(chart => chart?.dispose())
})
</script>

<style scoped>
.filter-card { margin-bottom: 16px; padding: 12px 20px; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.last-update { font-size: 12px; }
.info-card { text-align: center; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
.hint { font-size: 11px; color: var(--text-dim); font-weight: 400; margin-left: 8px; }
.ai-title { justify-content: space-between; align-items: center; }
.ai-title > div { display: flex; align-items: center; min-width: 0; }
.ai-chart { height: 320px; }
.ai-result { min-height: 320px; }
.ai-stream {
  min-height: 320px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-main);
  padding: 14px;
}
.ai-stream-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 12px;
  margin-bottom: 10px;
}
.ai-stream-text {
  max-height: 270px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.ai-headline { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.ai-headline-text { font-size: 18px; font-weight: 700; color: var(--text-heading); line-height: 1.35; }
.ai-tags { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 8px; min-width: 260px; }
.ai-meta { font-size: 12px; white-space: nowrap; }
.ai-sections { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 14px; }
.ai-section-title { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.ai-section-text { font-size: 13px; color: var(--text-primary); line-height: 1.65; white-space: pre-wrap; }
.ai-lists { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; border-top: 1px solid var(--border-color); padding-top: 14px; }
.ai-list-item { font-size: 12px; color: var(--text-primary); line-height: 1.55; margin-bottom: 6px; padding-left: 10px; position: relative; }
.ai-list-item::before { content: ''; width: 4px; height: 4px; border-radius: 50%; background: #58a6ff; position: absolute; left: 0; top: 8px; }
.ai-list-item.warning::before { background: #f0883e; }
.ai-placeholder { height: 320px; display: flex; align-items: center; justify-content: center; }
.ai-placeholder-title { color: var(--text-dim); font-size: 13px; }
</style>
