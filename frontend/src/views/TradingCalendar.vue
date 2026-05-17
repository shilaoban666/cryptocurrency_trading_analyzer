<template>
  <div class="calendar-page">
    <div class="page-head">
      <div>
        <div class="page-title">交易日历</div>
        <div class="page-sub">交易日历、订单记录和交易复盘</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="calendar-tabs" @tab-change="onTabChange">
      <el-tab-pane label="日历复盘" name="calendar">
        <div class="calendar-workspace">
    <div class="page-head">
      <div>
        <div class="page-sub">按日查看盈利亏损、月内资金变化和交易反思</div>
      </div>
      <div class="head-actions">
        <el-date-picker
          v-model="monthValue"
          type="month"
          format="YYYY-MM"
          value-format="YYYY-MM"
          :clearable="false"
          @change="loadMonth"
        />
        <el-button type="primary" :loading="loading" @click="loadMonth">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="6" v-for="card in summaryCards" :key="card.label">
        <div class="stat-card metric-card">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="metric-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="16">
        <div class="chart-card calendar-card">
          <div class="card-title">
            月度日历
            <span class="hint">点击任意日期写当日反思</span>
          </div>
          <div class="week-row">
            <span v-for="d in weekDays" :key="d">{{ d }}</span>
          </div>
          <div class="month-grid">
            <button
              v-for="cell in calendarCells"
              :key="cell.key"
              class="day-cell"
              :class="cellClass(cell)"
              :disabled="!cell.inMonth"
              @click="openJournal(cell)"
            >
              <div class="day-top">
                <span class="day-num">{{ cell.day }}</span>
                <span v-if="cell.hasJournal" class="journal-dot" />
              </div>
              <div class="day-pnl">{{ signed(cell.pnl) }}</div>
              <div class="day-meta">{{ cell.orderCount }} 单</div>
            </button>
          </div>
        </div>

        <div class="chart-card">
          <div class="card-title">本月资金变化</div>
          <div ref="curveRef" class="chart-lg" />
        </div>
      </el-col>

      <el-col :span="8">
        <div class="chart-card">
          <div class="card-title">当日详情</div>
          <template v-if="selectedDay">
            <div class="selected-date">{{ selectedDay.date }}</div>
            <div class="day-detail-grid">
              <div>
                <span>净盈亏</span>
                <b :class="selectedDay.pnl >= 0 ? 'profit' : 'loss'">{{ signed(selectedDay.pnl) }}</b>
              </div>
              <div>
                <span>盈利</span>
                <b class="profit">{{ money(selectedDay.profit) }}</b>
              </div>
              <div>
                <span>亏损</span>
                <b class="loss">{{ money(selectedDay.loss) }}</b>
              </div>
              <div>
                <span>订单数</span>
                <b>{{ selectedDay.orderCount }}</b>
              </div>
            </div>
            <el-button type="primary" plain class="write-btn" @click="openJournal(selectedDay)">
              <el-icon><EditPen /></el-icon>
              写反思
            </el-button>
          </template>
          <div v-else class="empty-block">选择一个日期查看详情</div>
        </div>

        <div class="chart-card">
          <div class="card-title">品种盈利/亏损</div>
          <div ref="instRef" class="chart-md" />
        </div>

        <div class="chart-card">
          <div class="card-title">方向盈利/亏损</div>
          <div ref="sideRef" class="chart-sm" />
        </div>
      </el-col>
    </el-row>

    <el-drawer v-model="journalVisible" title="每日交易反思" size="440px" @closed="resetJournal">
      <div v-if="journalForm.date" class="journal-form">
        <div class="journal-date">{{ journalForm.date }}</div>
        <el-form label-position="top">
          <el-form-item label="当日状态">
            <el-radio-group v-model="journalForm.mood">
              <el-radio-button value="冷静">冷静</el-radio-button>
              <el-radio-button value="焦虑">焦虑</el-radio-button>
              <el-radio-button value="贪婪">贪婪</el-radio-button>
              <el-radio-button value="执行好">执行好</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="标签">
            <el-select
              v-model="journalForm.tags"
              multiple
              allow-create
              filterable
              default-first-option
              placeholder="选择或输入标签"
              style="width:100%"
            >
              <el-option v-for="tag in presetTags" :key="tag" :label="tag" :value="tag" />
            </el-select>
          </el-form-item>
          <el-form-item label="复盘内容">
            <el-input
              v-model="journalForm.content"
              type="textarea"
              :rows="12"
              maxlength="3000"
              show-word-limit
              placeholder="记录当天的入场理由、执行问题、情绪变化和下一次改进动作"
            />
          </el-form-item>
        </el-form>
        <div class="drawer-actions">
          <el-button @click="journalVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveJournal">保存</el-button>
        </div>
      </div>
    </el-drawer>
        </div>
      </el-tab-pane>
      <el-tab-pane label="交易记录" name="orders">
        <Orders />
      </el-tab-pane>
      <el-tab-pane label="交易复盘" name="review">
        <Review />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getCalendarMonth, getDailyJournal, saveDailyJournal } from '@/api'
import { useTheme } from '@/composables/useTheme'
import Orders from '@/views/Orders.vue'
import Review from '@/views/Review.vue'

const { isDark, cs, initChart } = useTheme()

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const presetTags = ['冲动开仓', '止损犹豫', '计划内交易', '追涨杀跌', '仓位过重', '错过机会', '执行到位']

const activeTab = ref('calendar')
const monthValue = ref(dayjs().format('YYYY-MM'))
const loading = ref(false)
const saving = ref(false)
const data = ref({ summary: {}, days: [], instStats: [], sideStats: [] })
const selectedDay = ref(null)
const journalVisible = ref(false)
const journalForm = ref({ date: '', mood: '', tags: [], content: '' })

const curveRef = ref(null)
const instRef = ref(null)
const sideRef = ref(null)
let charts = {}

const dayMap = computed(() => new Map((data.value.days || []).map(d => [d.date, d])))

const summaryCards = computed(() => {
  const s = data.value.summary || {}
  const pnl = Number(s.pnl || 0)
  const winDays = Number(s.winDays || 0)
  const lossDays = Number(s.lossDays || 0)
  const tradeDays = Number(s.tradeDays || 0)
  const winRate = tradeDays ? `${((winDays / tradeDays) * 100).toFixed(1)}%` : '0.0%'
  return [
    { label: '本月净盈亏', value: signed(pnl), sub: `${s.orderCount || 0} 笔已关仓订单`, color: pnl >= 0 ? '#3fb950' : '#f85149' },
    { label: '盈利合计', value: money(s.profit), sub: `${winDays} 个盈利日`, color: '#3fb950' },
    { label: '亏损合计', value: money(s.loss), sub: `${lossDays} 个亏损日`, color: '#f85149' },
    { label: '交易日胜率', value: winRate, sub: `${tradeDays} 个交易日`, color: '#58a6ff' },
  ]
})

const calendarCells = computed(() => {
  const first = dayjs(`${monthValue.value}-01`)
  const daysInMonth = first.daysInMonth()
  const leading = (first.day() + 6) % 7
  const cells = []

  for (let i = 0; i < leading; i++) {
    cells.push({ key: `blank-${i}`, inMonth: false, day: '', pnl: 0, orderCount: 0 })
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const date = first.date(day).format('YYYY-MM-DD')
    const stat = dayMap.value.get(date) || { date, pnl: 0, orderCount: 0, hasJournal: false }
    cells.push({ key: date, inMonth: true, day, ...stat })
  }
  while (cells.length % 7 !== 0) {
    cells.push({ key: `tail-${cells.length}`, inMonth: false, day: '', pnl: 0, orderCount: 0 })
  }
  return cells
})

async function loadMonth() {
  loading.value = true
  try {
    data.value = await getCalendarMonth(monthValue.value)
    selectedDay.value = (data.value.days || []).find(d => d.date === dayjs().format('YYYY-MM-DD'))
      || (data.value.days || [])[0]
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

async function openJournal(day) {
  if (!day?.inMonth && !day?.date) return
  selectedDay.value = day
  const journal = await getDailyJournal(day.date)
  journalForm.value = {
    date: journal.date,
    mood: journal.mood || '',
    tags: journal.tags || [],
    content: journal.content || '',
  }
  journalVisible.value = true
}

async function saveJournal() {
  saving.value = true
  try {
    const form = journalForm.value
    await saveDailyJournal(form.date, {
      mood: form.mood,
      tags: form.tags,
      content: form.content,
    })
    ElMessage.success('反思已保存')
    journalVisible.value = false
    await loadMonth()
  } finally {
    saving.value = false
  }
}

function resetJournal() {
  journalForm.value = { date: '', mood: '', tags: [], content: '' }
}

function onTabChange(name) {
  if (name === 'calendar') {
    nextTick(renderAll)
  }
}

function renderAll() {
  renderCurve()
  renderInst()
  renderSide()
}

function chart(key, elRef) {
  if (!charts[key] && elRef.value) charts[key] = initChart(elRef.value)
  return charts[key]
}

function renderCurve() {
  const c = chart('curve', curveRef)
  if (!c) return
  let cumulative = 0
  const dates = (data.value.days || []).map(d => d.date.slice(5))
  const daily = (data.value.days || []).map(d => Number(d.pnl || 0))
  const curve = daily.map(v => {
    cumulative += v
    return round(cumulative)
  })
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>累计变化: ${signed(p[0].value)}<br/>当日: ${signed(daily[p[0].dataIndex])}` },
    grid: { left: 70, right: 22, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: curve,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: curve.at(-1) >= 0 ? '#3fb950' : '#f85149' },
      areaStyle: { color: curve.at(-1) >= 0 ? 'rgba(63,185,80,0.16)' : 'rgba(248,81,73,0.16)' },
    }]
  })
}

function renderInst() {
  const c = chart('inst', instRef)
  if (!c) return
  const rows = (data.value.instStats || []).slice(0, 8)
  c.setOption(categoryOption(rows, true))
}

function renderSide() {
  const c = chart('side', sideRef)
  if (!c) return
  c.setOption(categoryOption(data.value.sideStats || [], false))
}

function categoryOption(rows, rotate) {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 62, right: 12, top: 34, bottom: rotate ? 62 : 38 },
    xAxis: { type: 'category', data: rows.map(r => sideLabel(r.name)), axisLabel: { rotate: rotate ? 35 : 0, fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}U` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '盈利', type: 'bar', stack: 'pnl', data: rows.map(r => round(r.profit)), itemStyle: { color: '#3fb950', borderRadius: [3, 3, 0, 0] } },
      { name: '亏损', type: 'bar', stack: 'pnl', data: rows.map(r => round(r.loss)), itemStyle: { color: '#f85149', borderRadius: [0, 0, 3, 3] } },
    ]
  }
}

function cellClass(cell) {
  if (!cell.inMonth) return 'blank'
  return {
    profit: cell.pnl > 0,
    loss: cell.pnl < 0,
    flat: cell.pnl === 0,
    selected: selectedDay.value?.date === cell.date,
  }
}

function sideLabel(v) {
  if (v === 'long') return '做多'
  if (v === 'short') return '做空'
  return v || '未知'
}

function money(v) {
  return `${Number(v || 0).toFixed(2)} U`
}

function signed(v) {
  const n = Number(v || 0)
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)} U`
}

function round(v) {
  return +Number(v || 0).toFixed(2)
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
})

function onResize() {
  Object.values(charts).forEach(c => c?.resize())
}

onMounted(() => {
  loadMonth()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.calendar-page { color: var(--text-primary); }
.calendar-workspace { animation: pageIn .35s ease both; }
.calendar-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 600; }
.calendar-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.calendar-tabs :deep(.el-tabs__nav-wrap::after) { background: var(--border-color); opacity: .65; }
.calendar-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #58a6ff, #3fb950);
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.page-title { margin-bottom: 4px; }
.page-sub { color: var(--text-secondary); font-size: 13px; }
.head-actions { display: flex; gap: 10px; align-items: center; }
.summary-row { margin-bottom: 16px; }
.metric-card { min-height: 112px; }
.metric-label { color: var(--text-secondary); font-size: 12px; margin-bottom: 8px; }
.metric-value { font-size: 26px; font-weight: 700; margin-bottom: 6px; }
.metric-sub { color: var(--text-dim); font-size: 12px; }
.calendar-card { padding-bottom: 18px; }
.hint { color: var(--text-dim); font-size: 12px; font-weight: 400; margin-left: 8px; }
.week-row,
.month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}
.week-row { margin-bottom: 8px; color: var(--text-secondary); font-size: 12px; text-align: center; }
.day-cell {
  min-height: 88px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-main);
  color: var(--text-primary);
  padding: 10px;
  text-align: left;
  cursor: pointer;
  transition: border-color .15s, transform .15s, background .15s;
}
.day-cell:hover { transform: translateY(-1px); border-color: #58a6ff; }
.day-cell.blank { visibility: hidden; cursor: default; }
.day-cell.selected { outline: 2px solid rgba(88, 166, 255, .45); }
.day-cell.profit { background: rgba(63, 185, 80, .10); }
.day-cell.loss { background: rgba(248, 81, 73, .10); }
.day-cell.flat { opacity: .82; }
.day-top { display: flex; justify-content: space-between; align-items: center; }
.day-num { font-weight: 700; font-size: 14px; }
.journal-dot { width: 7px; height: 7px; border-radius: 999px; background: #58a6ff; }
.day-pnl { font-size: 15px; font-weight: 700; margin-top: 14px; }
.day-cell.profit .day-pnl { color: #3fb950; }
.day-cell.loss .day-pnl { color: #f85149; }
.day-meta { color: var(--text-dim); font-size: 11px; margin-top: 4px; }
.chart-lg { height: 320px; }
.chart-md { height: 290px; }
.chart-sm { height: 220px; }
.selected-date { font-size: 18px; font-weight: 700; margin-bottom: 14px; }
.day-detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.day-detail-grid div {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--bg-main);
}
.day-detail-grid span { display: block; color: var(--text-secondary); font-size: 12px; margin-bottom: 8px; }
.day-detail-grid b { font-size: 16px; }
.profit { color: #3fb950; }
.loss { color: #f85149; }
.write-btn { width: 100%; margin-top: 16px; }
.empty-block { height: 180px; display: flex; align-items: center; justify-content: center; color: var(--text-dim); }
.journal-date { font-size: 20px; font-weight: 700; margin-bottom: 18px; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 10px; }
@keyframes pageIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>


