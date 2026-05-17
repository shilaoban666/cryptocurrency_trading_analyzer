<template>
  <div class="insights-page">
    <div class="page-head">
      <div>
        <div class="page-title">交易心得</div>
        <div class="page-sub">每日采集公开市场交易心得，沉淀交易大忌、应做动作和成功因子</div>
      </div>
      <div class="head-actions">
        <div class="refresh-meta">
          <span>最近采集</span>
          <strong>{{ formatTime(meta.lastCollectedAt) }}</strong>
        </div>
        <el-button type="primary" :loading="refreshing" @click="refreshNow">
          <el-icon><Refresh /></el-icon>
          立即刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <div class="stat-card insight-stat">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="insight-tabs" @tab-change="onTabChange">
      <el-tab-pane label="热门心得" name="list">
        <div class="chart-card filter-card">
          <div class="filter-grid">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索标题、摘要、标签"
              class="filter-item"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="categoryFilter" clearable placeholder="分类" class="filter-item">
              <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="typeFilter" clearable placeholder="类型" class="filter-item">
              <el-option v-for="item in types" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="sourceFilter" clearable placeholder="来源" class="filter-item">
              <el-option v-for="item in sources" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
        </div>

        <el-empty v-if="!loading && !filteredInsights.length" description="暂无交易心得，点击立即刷新采集公开来源" />

        <div v-loading="loading" class="insight-list">
          <div v-for="item in filteredInsights" :key="item.id || item.contentHash" class="insight-card">
            <div class="insight-card-head">
              <div>
                <div class="insight-title">{{ item.title }}</div>
                <div class="insight-meta">
                  <el-tag size="small" effect="dark" :type="tagType(item.category)">{{ item.category || '未分类' }}</el-tag>
                  <el-tag size="small" effect="plain">{{ item.insightType || '策略原则' }}</el-tag>
                  <span>{{ item.sourceName || '-' }}</span>
                  <span>{{ formatTime(item.publishedAt) }}</span>
                </div>
              </div>
              <div class="heat-badge">
                <span>{{ score(item.heatScore) }}</span>
                <small>热度</small>
              </div>
            </div>

            <div class="summary">{{ item.summary }}</div>

            <el-row :gutter="14" class="do-grid">
              <el-col :span="12">
                <div class="rule-box danger">
                  <div class="rule-label">交易大忌</div>
                  <div>{{ item.taboo || '不要在计划不完整时进场。' }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="rule-box success">
                  <div class="rule-label">应该怎么做</div>
                  <div>{{ item.shouldDo || '先定义风险，再等待结构确认。' }}</div>
                </div>
              </el-col>
            </el-row>

            <div class="card-foot">
              <div class="tag-line">
                <span v-for="tag in splitTags(item.tags)" :key="tag" class="mini-tag">{{ tag }}</span>
              </div>
              <a
                v-if="item.originalUrl && !String(item.originalUrl).startsWith('system://')"
                :href="item.originalUrl"
                target="_blank"
                rel="noreferrer"
                class="source-link"
              >
                查看来源
                <el-icon><TopRight /></el-icon>
              </a>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="成功因子分析" name="analysis">
        <div class="analysis-hint">
          <div>
            <strong>交易成功看什么</strong>
            <span>先看亏损是否可控，再看仓位、趋势、市场结构、情绪纪律和复盘闭环是否一致。</span>
          </div>
          <div class="hint-pill">九图联动 · {{ filteredInsights.length }} 条心得样本</div>
        </div>

        <div class="analysis-grid">
          <div class="chart-card compact-chart-card wide">
            <div class="card-title strong">成功因子权重：哪些能力最影响胜率</div>
            <div ref="factorRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">交易成功雷达：六项基本盘</div>
            <div ref="radarRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">失败来源：最该避开的交易大忌</div>
            <div ref="tabooRef" class="chart-box compact" />
          </div>

          <div class="chart-card compact-chart-card">
            <div class="card-title strong">行动优先级：分数越高越先执行</div>
            <div ref="priorityRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">类别 × 类型矩阵：忌讳与做法分布</div>
            <div ref="matrixRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">市场阶段矩阵：不同环境看什么</div>
            <div ref="phaseRef" class="chart-box compact" />
          </div>

          <div class="chart-card compact-chart-card">
            <div class="card-title strong">有效来源：哪些信息源贡献最大</div>
            <div ref="sourceRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">热度趋势：近期市场关注点变化</div>
            <div ref="trendRef" class="chart-box compact" />
          </div>
          <div class="chart-card compact-chart-card">
            <div class="card-title strong">执行清单漏斗：从机会到可交易</div>
            <div ref="playbookRef" class="chart-box compact" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTradingInsights, refreshTradingInsights } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const activeTab = ref('list')
const loading = ref(false)
const refreshing = ref(false)
const insights = ref([])
const meta = ref({ total: 0, added: 0, todayAdded: 0, lastCollectedAt: null, sources: [] })
const keyword = ref('')
const categoryFilter = ref('')
const typeFilter = ref('')
const sourceFilter = ref('')

const factorRef = ref(null)
const tabooRef = ref(null)
const sourceRef = ref(null)
const matrixRef = ref(null)
const trendRef = ref(null)
const radarRef = ref(null)
const priorityRef = ref(null)
const phaseRef = ref(null)
const playbookRef = ref(null)
let charts = {}

const categories = computed(() => uniq(insights.value.map(i => i.category).filter(Boolean)))
const types = computed(() => uniq(insights.value.map(i => i.insightType).filter(Boolean)))
const sources = computed(() => uniq(insights.value.map(i => i.sourceName).filter(Boolean)))

const filteredInsights = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return insights.value.filter(item => {
    if (categoryFilter.value && item.category !== categoryFilter.value) return false
    if (typeFilter.value && item.insightType !== typeFilter.value) return false
    if (sourceFilter.value && item.sourceName !== sourceFilter.value) return false
    if (!kw) return true
    return [item.title, item.summary, item.taboo, item.shouldDo, item.tags, item.sourceName]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(kw)
  })
})

const statCards = computed(() => {
  const rows = insights.value
  const avgHeat = avg(rows.map(i => Number(i.heatScore || 0)))
  const top = topEntry(groupBySum(rows, i => i.successFactor || '未归类', i => Number(i.heatScore || 0)))
  const tabooCount = rows.filter(i => i.insightType === '忌讳' || i.insightType === '风险提示').length
  return [
    { label: '心得总数', value: meta.value.total || rows.length, sub: `今日采集 ${meta.value.todayAdded || 0} 条`, color: '#58a6ff' },
    { label: '平均热度', value: avgHeat.toFixed(1), sub: '来源权重 + 时效 + 关键词', color: '#d29922' },
    { label: '核心成功因子', value: top?.name || '-', sub: top ? `${top.value.toFixed(1)} 热度贡献` : '等待采集', color: '#3fb950' },
    { label: '风险类心得', value: tabooCount, sub: '忌讳 + 风险提示', color: '#f85149' },
  ]
})

function chart(key, elRef) {
  if (!elRef.value) return null
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

async function load() {
  loading.value = true
  try {
    const res = await getTradingInsights({ limit: 160 })
    applyResponse(res)
  } finally {
    loading.value = false
  }
}

async function refreshNow() {
  refreshing.value = true
  try {
    const res = await refreshTradingInsights()
    applyResponse(res)
    ElMessage.success(`刷新完成，新增 ${res.added || 0} 条心得`)
  } finally {
    refreshing.value = false
  }
}

function applyResponse(res) {
  insights.value = Array.isArray(res?.list) ? res.list : []
  meta.value = {
    total: res?.total || insights.value.length,
    added: res?.added || 0,
    todayAdded: res?.todayAdded || 0,
    lastCollectedAt: res?.lastCollectedAt || null,
    sources: res?.sources || [],
  }
  nextTick(renderAll)
}

function onTabChange() {
  nextTick(renderAll)
}

function renderAll() {
  if (activeTab.value !== 'analysis') return
  renderFactor()
  renderTaboo()
  renderSource()
  renderMatrix()
  renderTrend()
  renderRadar()
  renderPriority()
  renderPhase()
  renderPlaybook()
}

function renderFactor() {
  const c = chart('factor', factorRef)
  if (!c) return
  const entries = topEntries(groupBySum(filteredInsights.value, i => i.successFactor || '未归类', i => Number(i.heatScore || 0)), 8)
  c.setOption({
    ...baseOption(),
    tooltip: { trigger: 'axis', formatter: params => `${params[0].name}<br/>权重 ${params[0].value}` },
    grid: grid(78, 20, 22, 18),
    xAxis: axis('value'),
    yAxis: axis('category', entries.map(i => i.name).reverse()),
    series: [{
      name: '成功权重',
      type: 'bar',
      data: entries.map(i => round(i.value)).reverse(),
      barWidth: 14,
      label: { show: true, position: 'right', color: cs.value.labelColor, fontSize: 10 },
      itemStyle: { borderRadius: [0, 6, 6, 0], color: gradient('#58a6ff', '#3fb950') },
      emphasis: { focus: 'series' },
    }]
  }, true)
}

function renderTaboo() {
  const c = chart('taboo', tabooRef)
  if (!c) return
  const riskRows = filteredInsights.value.filter(i => ['忌讳', '风险提示'].includes(i.insightType))
  const entries = topEntries(groupBySum(riskRows, i => i.category || '未分类', i => Number(i.heatScore || 0)), 8)
  c.setOption({
    ...baseOption(),
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>热度 ${round(p.value)}` },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { color: cs.value.legendColor, fontSize: 10 } },
    series: [{
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '43%'],
      label: { color: cs.value.labelColor, formatter: '{b}\n{d}%', fontSize: 10 },
      data: entries.map((i, idx) => ({ name: i.name, value: round(i.value), itemStyle: { color: palette[idx % palette.length] } }))
    }]
  }, true)
}

function renderSource() {
  const c = chart('source', sourceRef)
  if (!c) return
  const entries = topEntries(groupBySum(filteredInsights.value, i => i.sourceName || '未知来源', i => Number(i.heatScore || 0)), 8).reverse()
  c.setOption({
    ...baseOption(),
    tooltip: { trigger: 'axis' },
    grid: grid(84, 18, 18, 14),
    xAxis: axis('value'),
    yAxis: axis('category', entries.map(i => i.name)),
    series: [{
      name: '热度贡献',
      type: 'bar',
      data: entries.map(i => round(i.value)),
      barWidth: 14,
      itemStyle: { borderRadius: [0, 6, 6, 0], color: gradient('#d29922', '#58a6ff') },
    }]
  }, true)
}

function renderMatrix() {
  const c = chart('matrix', matrixRef)
  if (!c) return
  const cats = categories.value.length ? categories.value : ['风险控制', '仓位管理', '情绪纪律']
  const typs = types.value.length ? types.value : ['忌讳', '做法', '风险提示', '策略原则']
  const data = []
  cats.forEach((cat, x) => {
    typs.forEach((typ, y) => {
      const value = filteredInsights.value
        .filter(i => i.category === cat && i.insightType === typ)
        .reduce((sum, i) => sum + Number(i.heatScore || 0), 0)
      data.push([x, y, round(value)])
    })
  })
  c.setOption({
    ...baseOption(),
    tooltip: { position: 'top', formatter: p => `${cats[p.value[0]]} / ${typs[p.value[1]]}<br/>热度 ${p.value[2]}` },
    grid: grid(54, 18, 36, 18),
    xAxis: axis('category', cats),
    yAxis: axis('category', typs),
    visualMap: {
      min: 0,
      max: Math.max(...data.map(i => i[2]), 10),
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: cs.value.heatmap },
      itemHeight: 70,
      itemWidth: 10,
      textStyle: { color: cs.value.legendColor, fontSize: 10 }
    },
    series: [{
      type: 'heatmap',
      data,
      label: { show: true, color: isDark.value ? '#f0f6fc' : '#111827', fontSize: 10 },
      emphasis: { itemStyle: { borderColor: '#58a6ff', borderWidth: 1 } },
    }]
  }, true)
}

function renderTrend() {
  const c = chart('trend', trendRef)
  if (!c) return
  const byDay = groupBySum(filteredInsights.value, i => String(i.publishedAt || i.collectedAt || '').slice(0, 10) || '未知', i => Number(i.heatScore || 0))
  const entries = Object.entries(byDay).sort(([a], [b]) => a.localeCompare(b)).slice(-21)
  c.setOption({
    ...baseOption(),
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor, fontSize: 10 } },
    grid: grid(42, 16, 24, 28),
    xAxis: axis('category', entries.map(i => i[0].slice(5))),
    yAxis: axis('value'),
    series: [{
      name: '采集热度',
      type: 'line',
      data: entries.map(i => round(i[1])),
      smooth: true,
      symbolSize: 7,
      lineStyle: { width: 3, color: '#58a6ff' },
      itemStyle: { color: '#58a6ff' },
      areaStyle: { color: areaGradient('#58a6ff') },
    }]
  }, true)
}

function renderRadar() {
  const c = chart('radar', radarRef)
  if (!c) return
  const dimensions = ['风险控制', '仓位管理', '趋势跟随', '情绪纪律', '计划复盘', '市场结构']
  const values = dimensions.map(name => filteredInsights.value
    .filter(i => i.category === name)
    .reduce((sum, i) => sum + Number(i.confidenceScore || 0), 0))
  const max = Math.max(...values, 80)
  c.setOption({
    ...baseOption(),
    tooltip: {},
    radar: {
      radius: '58%',
      center: ['50%', '54%'],
      indicator: dimensions.map(name => ({ name, max })),
      axisName: { color: cs.value.labelColor, fontSize: 10 },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: isDark.value ? ['rgba(88,166,255,.04)', 'rgba(63,185,80,.03)'] : ['rgba(88,166,255,.06)', 'rgba(63,185,80,.04)'] } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
    },
    series: [{
      type: 'radar',
      data: [{
        name: '成功路径',
        value: values.map(v => round(v)),
        areaStyle: { color: 'rgba(88, 166, 255, .22)' },
        lineStyle: { color: '#58a6ff', width: 3 },
        itemStyle: { color: '#58a6ff' },
      }]
    }]
  }, true)
}

function renderPriority() {
  const c = chart('priority', priorityRef)
  if (!c) return
  const grouped = Object.entries(groupByRows(filteredInsights.value, i => i.successFactor || '未归类'))
    .map(([name, rows]) => ({
      name,
      heat: avg(rows.map(i => Number(i.heatScore || 0))),
      confidence: avg(rows.map(i => Number(i.confidenceScore || 0))),
      count: rows.length,
      category: rows[0]?.category || '交易框架',
    }))
    .map(item => ({
      ...item,
      priority: item.heat * 0.52 + item.confidence * 0.40 + Math.min(item.count, 8) * 1.8
    }))
    .sort((a, b) => b.priority - a.priority)
    .slice(0, 7)
    .reverse()
  c.setOption({
    ...baseOption(),
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const d = params[0].data
        const action = d.priority >= 78 ? '立即执行' : d.priority >= 68 ? '纳入计划' : '继续观察'
        return `${d.name}<br/>优先级 ${round(d.priority, 1)} ｜ ${action}<br/>热度 ${round(d.heat, 1)} / 置信 ${round(d.confidence, 1)} / 样本 ${d.count}`
      }
    },
    grid: grid(70, 22, 22, 12),
    xAxis: {
      ...axis('value'),
      min: value => Math.max(0, Math.floor(value.min - 6)),
      max: value => Math.ceil(value.max + 4),
      axisLabel: { color: cs.value.legendColor, fontSize: 10 }
    },
    yAxis: axis('category', grouped.map(i => i.name)),
    series: [{
      name: '优先级',
      type: 'bar',
      data: grouped.map((i) => ({
        name: i.name,
        count: i.count,
        heat: i.heat,
        confidence: i.confidence,
        priority: round(i.priority, 1),
        value: round(i.priority, 1),
        itemStyle: {
          color: i.priority >= 78
            ? gradient('#3fb950', '#58a6ff')
            : i.priority >= 68
              ? gradient('#d29922', '#58a6ff')
              : gradient('#8b949e', '#58a6ff')
        }
      })),
      barWidth: 14,
      label: {
        show: true,
        position: 'right',
        color: cs.value.labelColor,
        fontSize: 10,
        formatter: p => `${p.data.value}  ${p.data.priority >= 78 ? '立即' : p.data.priority >= 68 ? '计划' : '观察'}`
      },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { color: cs.value.legendColor, fontSize: 10, formatter: '执行线' },
        lineStyle: { color: '#3fb950', type: 'dashed', opacity: .75 },
        data: [{ xAxis: 78 }],
      }
    }]
  }, true)
}

function renderPhase() {
  const c = chart('phase', phaseRef)
  if (!c) return
  const phases = ['多头趋势', '空头压力', '高波动', '震荡整理', '杠杆清算', '结构观察']
  const factors = ['亏损截断', '风险预算', '顺势等待', '纪律执行', '可验证流程', '流动性识别']
  const data = []
  factors.forEach((factor, x) => {
    phases.forEach((phase, y) => {
      const value = filteredInsights.value
        .filter(i => (i.successFactor === factor || i.category === factor) && i.marketPhase === phase)
        .reduce((sum, i) => sum + Number(i.heatScore || 0), 0)
      data.push([x, y, round(value)])
    })
  })
  c.setOption({
    ...baseOption(),
    tooltip: { position: 'top', formatter: p => `${factors[p.value[0]]} / ${phases[p.value[1]]}<br/>热度 ${p.value[2]}` },
    grid: grid(58, 18, 42, 18),
    xAxis: axis('category', factors),
    yAxis: axis('category', phases),
    visualMap: {
      min: 0,
      max: Math.max(...data.map(i => i[2]), 10),
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['rgba(88,166,255,.08)', '#58a6ff', '#3fb950'] },
      itemHeight: 70,
      itemWidth: 10,
      textStyle: { color: cs.value.legendColor, fontSize: 10 }
    },
    series: [{
      type: 'heatmap',
      data,
      label: { show: false },
      emphasis: { itemStyle: { borderColor: '#3fb950', borderWidth: 1 } },
    }]
  }, true)
}

function renderPlaybook() {
  const c = chart('playbook', playbookRef)
  if (!c) return
  const rows = filteredInsights.value
  const scoreBy = names => names.reduce((sum, name) => sum + rows
    .filter(i => i.category === name || i.successFactor === name)
    .reduce((s, i) => s + Number(i.heatScore || 0), 0), 0)
  const raw = [
    { name: '机会识别', value: scoreBy(['趋势跟随', '资金流向', '宏观周期', '顺势等待', '跟踪主导资金']) },
    { name: '结构确认', value: scoreBy(['市场结构', '流动性识别', '信号一致性']) },
    { name: '风险预算', value: scoreBy(['风险控制', '仓位管理', '亏损截断', '风险预算']) },
    { name: '纪律执行', value: scoreBy(['情绪纪律', '纪律执行']) },
    { name: '复盘迭代', value: scoreBy(['计划复盘', '可验证流程']) },
  ]
  const max = Math.max(...raw.map(i => i.value), 1)
  const data = raw.map((item, idx) => ({
    name: item.name,
    value: round(Math.max(item.value, max * (0.48 - idx * 0.05))),
    itemStyle: { color: palette[idx % palette.length] }
  }))
  c.setOption({
    ...baseOption(),
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>有效信号 ${round(p.value)}` },
    series: [{
      type: 'funnel',
      left: '8%',
      top: 14,
      bottom: 8,
      width: '84%',
      minSize: '35%',
      maxSize: '96%',
      sort: 'none',
      gap: 3,
      label: { show: true, position: 'inside', color: '#fff', fontSize: 12, fontWeight: 700 },
      labelLine: { show: false },
      itemStyle: { borderWidth: 0, borderRadius: 4 },
      data,
    }]
  }, true)
}

function baseOption() {
  return {
    backgroundColor: 'transparent',
    animationDuration: 650,
    animationEasing: 'cubicOut',
    textStyle: { color: cs.value.labelColor },
  }
}

function grid(left = 50, right = 22, bottom = 40, top = 36) {
  return { left, right, bottom, top, containLabel: true }
}

function axis(type, data) {
  return {
    type,
    data,
    axisLabel: { color: cs.value.legendColor, fontSize: 11, interval: 0 },
    axisLine: { lineStyle: { color: cs.value.gridLine } },
    axisTick: { show: false },
    splitLine: { show: type === 'value', lineStyle: { color: cs.value.gridLine, type: 'dashed' } },
  }
}

function gradient(from, to) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 1,
    y2: 1,
    colorStops: [{ offset: 0, color: from }, { offset: 1, color: to }],
  }
}

function areaGradient(color) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: `${color}55` },
      { offset: 1, color: `${color}04` },
    ],
  }
}

function groupBySum(rows, keyFn, valueFn) {
  return rows.reduce((map, row) => {
    const key = keyFn(row) || '未归类'
    map[key] = (map[key] || 0) + Number(valueFn(row) || 0)
    return map
  }, {})
}

function groupByRows(rows, keyFn) {
  return rows.reduce((map, row) => {
    const key = keyFn(row) || '未归类'
    if (!map[key]) map[key] = []
    map[key].push(row)
    return map
  }, {})
}

function topEntries(map, limit = 10) {
  return Object.entries(map)
    .map(([name, value]) => ({ name, value: Number(value || 0) }))
    .sort((a, b) => b.value - a.value)
    .slice(0, limit)
}

function topEntry(map) {
  return topEntries(map, 1)[0]
}

function uniq(rows) {
  return [...new Set(rows)]
}

function avg(rows) {
  const values = rows.filter(v => Number.isFinite(v) && v > 0)
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : 0
}

function round(v, n = 2) {
  return Number(Number(v || 0).toFixed(n))
}

function score(v) {
  return Number(v || 0).toFixed(0)
}

function splitTags(tags) {
  if (!tags) return []
  return String(tags).split(',').map(i => i.trim()).filter(Boolean).slice(0, 7)
}

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value).slice(0, 16).replace('T', ' ')
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function tagType(category) {
  if (['风险控制', '仓位管理'].includes(category)) return 'danger'
  if (['趋势跟随', '计划复盘'].includes(category)) return 'success'
  if (['市场结构', '资金流向'].includes(category)) return 'warning'
  return 'info'
}

function onResize() {
  Object.values(charts).forEach(c => c?.resize())
}

const palette = ['#58a6ff', '#3fb950', '#d29922', '#f85149', '#a371f7', '#39c5cf', '#f0883e', '#7ee787']

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
})

watch(isDark, () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  nextTick(renderAll)
})

watch([filteredInsights, activeTab], () => nextTick(renderAll), { deep: true })
</script>

<style scoped>
.insights-page { min-height: 100%; }
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title { margin-bottom: 4px; }
.page-sub { color: var(--text-secondary); font-size: 13px; }
.head-actions { display: flex; align-items: center; gap: 12px; }
.refresh-meta {
  min-width: 150px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
}
.refresh-meta strong {
  display: block;
  color: var(--text-heading);
  margin-top: 2px;
  font-size: 13px;
}
.stat-row { margin-bottom: 16px; }
.insight-stat { padding: 16px; min-height: 116px; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 24px; font-weight: 800; margin: 8px 0 4px; letter-spacing: 0; }
.stat-sub { color: var(--text-dim); font-size: 12px; }
.insight-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 600; }
.insight-tabs :deep(.el-tabs__item.is-active) { color: var(--accent-blue); }
.insight-tabs :deep(.el-tabs__nav-wrap::after) { background: var(--border-color); opacity: .65; }
.insight-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #58a6ff, #3fb950);
}
.filter-card { padding: 14px; margin-bottom: 14px; }
.filter-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1.4fr) repeat(3, minmax(150px, .8fr));
  gap: 12px;
}
.filter-item { width: 100%; }
.insight-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.insight-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--shadow-card);
  transition: transform .18s ease, border-color .18s ease, background .18s ease;
}
.insight-card:hover { transform: translateY(-2px); border-color: rgba(88, 166, 255, .32); }
.insight-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.insight-title {
  color: var(--text-heading);
  font-size: 15px;
  font-weight: 800;
  line-height: 1.45;
  letter-spacing: 0;
}
.insight-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: var(--text-dim);
  font-size: 12px;
}
.heat-badge {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(88, 166, 255, .24);
  background: linear-gradient(180deg, rgba(88, 166, 255, .16), rgba(63, 185, 80, .08));
}
.heat-badge span { color: var(--text-heading); font-size: 20px; font-weight: 900; }
.heat-badge small { color: var(--text-secondary); font-size: 11px; }
.summary {
  margin: 14px 0;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}
.do-grid { margin-bottom: 12px; }
.rule-box {
  min-height: 112px;
  border-radius: 8px;
  padding: 12px;
  line-height: 1.65;
  font-size: 13px;
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}
.rule-box.danger {
  background: linear-gradient(180deg, rgba(248, 81, 73, .10), rgba(248, 81, 73, .035));
  border-color: rgba(248, 81, 73, .20);
}
.rule-box.success {
  background: linear-gradient(180deg, rgba(63, 185, 80, .10), rgba(63, 185, 80, .035));
  border-color: rgba(63, 185, 80, .20);
}
.rule-label {
  color: var(--text-heading);
  font-size: 12px;
  font-weight: 800;
  margin-bottom: 6px;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
}
.tag-line { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.mini-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  background: rgba(88, 166, 255, .06);
  font-size: 12px;
}
.source-link {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent-blue);
  font-size: 12px;
  text-decoration: none;
}
.analysis-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background:
    linear-gradient(90deg, rgba(88, 166, 255, .10), rgba(63, 185, 80, .06)),
    var(--bg-card);
  box-shadow: var(--shadow-card);
}
.analysis-hint strong {
  display: block;
  color: var(--text-heading);
  font-size: 14px;
  margin-bottom: 4px;
}
.analysis-hint span { color: var(--text-secondary); font-size: 12px; }
.hint-pill {
  flex: 0 0 auto;
  height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(88, 166, 255, .24);
  color: var(--accent-blue);
  background: rgba(88, 166, 255, .08);
  font-size: 12px;
  font-weight: 700;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.compact-chart-card {
  margin-bottom: 0;
  padding: 13px;
  min-width: 0;
}
.compact-chart-card.wide { grid-column: span 2; }
.card-title.strong {
  margin-bottom: 8px;
  color: var(--text-heading);
  font-size: 13px;
  font-weight: 800;
}
.chart-box { height: 320px; }
.chart-box.tall { height: 340px; }
.chart-box.compact { height: 250px; }

@media (max-width: 1200px) {
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .insight-list { grid-template-columns: 1fr; }
  .analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .compact-chart-card.wide { grid-column: span 2; }
}

@media (max-width: 820px) {
  .analysis-grid { grid-template-columns: 1fr; }
  .compact-chart-card.wide { grid-column: span 1; }
  .analysis-hint { align-items: flex-start; flex-direction: column; }
}
</style>
