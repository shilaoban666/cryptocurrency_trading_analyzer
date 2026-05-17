<template>
  <div>
    <div class="page-title">🔬 深度分析</div>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="c in winRateCards" :key="c.label">
        <div class="stat-card focus-stat" style="text-align:center">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{color:c.color}">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="c in advancedCards" :key="c.label">
        <div class="stat-card" style="text-align:center">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{color:c.color}">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="c in streakCards" :key="c.label">
        <div class="stat-card" style="text-align:center">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{color:c.color}">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="c in riskCards" :key="c.label">
        <div class="stat-card" style="text-align:center">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{color:c.color}">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="12"><div class="chart-card"><div class="card-title">📈 多空胜率对比</div><div ref="directionRef" style="height:200px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">⏱ 赢单 vs 输单持仓时长</div><div ref="holdingCompRef" style="height:200px" /></div></el-col>
    </el-row>
    <div class="chart-card">
      <div class="card-title">📊 滚动20单胜率趋势</div>
      <div ref="rollingRef" style="height:210px" />
    </div>
    <div class="chart-card">
      <div class="card-title">🗓 月度交易统计（盈亏 / 胜率 / 均盈亏 / 笔数）</div>
      <div ref="monthlyRef" style="height:260px" />
    </div>
    <div class="chart-card compact-analysis-chart">
      <div class="card-title">⏱ 开单时段分布（星期 × 小时）</div>
      <div ref="heatmapRef" style="height:240px" />
    </div>
    <el-row :gutter="12">
      <el-col :span="14"><div class="chart-card"><div class="card-title">🕐 各小时胜率 & 交易量</div><div ref="hourlyRef" style="height:230px" /></div></el-col>
      <el-col :span="10"><div class="chart-card"><div class="card-title">💹 各小时总盈亏</div><div ref="hourlyPnlRef" style="height:230px" /></div></el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="12"><div class="chart-card"><div class="card-title">📆 星期胜率分布</div><div ref="weekdayRef" style="height:230px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">💵 星期总盈亏</div><div ref="weekdayPnlRef" style="height:230px" /></div></el-col>
    </el-row>
    <div class="chart-card compact-analysis-chart">
      <div class="card-title">📋 交易质量诊断（扛单 / 止盈 / 风险）</div>
      <el-table :data="combinedDiagnostics" style="width:100%" size="small">
        <el-table-column prop="metric" label="指标" min-width="150" />
        <el-table-column prop="value" label="当前值" min-width="140" />
        <el-table-column prop="judge" label="判断" min-width="140">
          <template #default="{ row }">
            <span :style="{ color: row.color }">{{ row.judge }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="280" />
      </el-table>
    </div>
    <el-row :gutter="12">
      <el-col :span="12"><div class="chart-card"><div class="card-title">⏱ 持仓时间分布</div><div ref="holdingRef" style="height:230px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">💰 盈亏金额分布</div><div ref="pnlDistRef" style="height:230px" /></div></el-col>
    </el-row>
    <div class="chart-card">
      <div class="card-title">📉 资金曲线 & 最大回撤分析（双面板）</div>
      <div ref="drawdownRef" style="height:380px" />
    </div>
    <el-row :gutter="12">
      <el-col :span="10"><div class="chart-card"><div class="card-title">⚙️ 杠杆胜率精细分析</div><div ref="leverageRef" style="height:250px" /></div></el-col>
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">🎯 盈亏效率象限</div>
          <div ref="efficiencyRef" style="height:250px" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { getAnalysis } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const data = ref({})
const WEEKDAYS = ['周一','周二','周三','周四','周五','周六','周日']

const heatmapRef    = ref(null); const hourlyRef    = ref(null); const hourlyPnlRef  = ref(null)
const weekdayRef    = ref(null); const weekdayPnlRef= ref(null)
const holdingRef    = ref(null); const holdingCompRef= ref(null); const pnlDistRef    = ref(null)
const rollingRef    = ref(null); const monthlyRef   = ref(null); const directionRef  = ref(null)
const drawdownRef   = ref(null); const leverageRef  = ref(null)
const efficiencyRef = ref(null)

let charts = {}

const winRateCards = computed(() => {
  const d = data.value
  const total = Number(d.totalTrades || 0)
  const winRate = Number(d.winRate || 0) * 100
  const longRate = Number(d.longWinRate || 0) * 100
  const shortRate = Number(d.shortWinRate || 0) * 100
  const pf = Number(d.profitFactor || 0)
  return [
    { label: '综合胜率', value: `${winRate.toFixed(1)}%`, color: winRate >= 50 ? '#3fb950' : '#f85149', sub: `赢 ${d.winCount || 0} / 亏 ${d.lossCount || 0} / 总 ${total}` },
    { label: '做多胜率', value: `${longRate.toFixed(1)}%`, color: longRate >= 50 ? '#3fb950' : '#d29922', sub: '多头平仓单胜率' },
    { label: '做空胜率', value: `${shortRate.toFixed(1)}%`, color: shortRate >= 50 ? '#3fb950' : '#d29922', sub: '空头平仓单胜率' },
    { label: '盈亏因子', value: pf ? pf.toFixed(2) : '-', color: pf >= 1.5 ? '#3fb950' : pf >= 1 ? '#d29922' : '#f85149', sub: '总盈利 / 总亏损' },
  ]
})

const advancedCards = computed(() => {
  const d = data.value
  return [
    { label: '期望值',   value: fmt(d.expectedValue)  + ' U', color: '#58a6ff', sub: '每单期望收益' },
    { label: '凯利仓位', value: fmt(d.kellyCriterion) + '%',  color: '#f0883e', sub: '建议1/4凯利' },
    { label: '夏普比率', value: fmt(d.sharpeRatio),            color: d.sharpeRatio >= 1 ? '#3fb950' : '#f85149', sub: '风险收益比' },
    { label: '卡玛比率', value: fmt(d.calmarRatio),            color: d.calmarRatio >= 1 ? '#3fb950' : '#f85149', sub: '净盈亏/最大回撤' },
  ]
})
const streakCards = computed(() => {
  const d = data.value
  return [
    { label: '最大连胜',   value: d.maxConsecWins   ?? '-', color: '#3fb950', sub: `累计 ${fmt(d.maxConsecWinAmount)} U` },
    { label: '最大连败',   value: d.maxConsecLosses ?? '-', color: '#f85149', sub: `累计 ${fmt(d.maxConsecLossAmount)} U` },
    { label: '赢单均持仓', value: fmtHolding(d.avgWinHoldingMinutes),  color: '#3fb950', sub: '赢单平均时长' },
    { label: '输单均持仓', value: fmtHolding(d.avgLossHoldingMinutes), color: '#f85149', sub: '输单平均时长' },
  ]
})
const riskCards = computed(() => {
  const d = data.value
  const rr = (d.avgWin != null && d.avgLoss != null && d.avgLoss !== 0) ? Math.abs(d.avgWin / d.avgLoss).toFixed(2) : '-'
  const feeCost = (d.totalPnl != null && d.netPnl != null) ? fmt(d.totalPnl - d.netPnl) : '-'
  return [
    { label:'最大单笔盈利', value: fmt(d.maxWin)  + ' U', color:'#3fb950', sub:'历史最佳单笔收益' },
    { label:'最大单笔亏损', value: fmt(d.maxLoss)  + ' U', color:'#f85149', sub:'历史最差单笔亏损' },
    { label:'期望盈亏比 RR',value: rr,                     color:'#f0883e', sub:'均赢 ÷ 均亏 绝对值' },
    { label:'手续费总损耗', value: feeCost + ' U',          color:'#8b949e', sub:'毛盈亏 − 净盈亏' },
  ]
})

const holdingDiagnostics = computed(() => {
  const d = data.value
  const winHold = +d.avgWinHoldingMinutes || 0
  const lossHold = +d.avgLossHoldingMinutes || 0
  const ratio = winHold > 0 ? lossHold / winHold : 0
  const avgWin = Math.abs(+d.avgWin || 0)
  const avgLoss = Math.abs(+d.avgLoss || 0)
  const rr = avgLoss > 0 ? avgWin / avgLoss : 0
  return [
    {
      metric: '输赢持仓时长比',
      value: ratio ? ratio.toFixed(2) + 'x' : '-',
      judge: ratio >= 3 ? '明显扛单' : ratio >= 1.5 ? '有扛单倾向' : '相对健康',
      color: ratio >= 3 ? '#f85149' : ratio >= 1.5 ? '#f0883e' : '#3fb950',
      note: `赢单 ${fmtHolding(winHold)}，输单 ${fmtHolding(lossHold)}。输单时间越长，越像亏损后迟迟不止损。`
    },
    {
      metric: '盈亏金额比',
      value: rr ? rr.toFixed(2) : '-',
      judge: rr < 1 ? '赚小亏大' : rr < 1.5 ? '一般' : '较好',
      color: rr < 1 ? '#f85149' : rr < 1.5 ? '#f0883e' : '#3fb950',
      note: '低于 1 代表平均盈利覆盖不了平均亏损，容易被少数大亏吃掉。'
    },
    {
      metric: '最大单笔亏损',
      value: fmt(d.maxLoss) + ' U',
      judge: Math.abs(+d.maxLoss || 0) > avgWin * 2 ? '尾部亏损偏大' : '可控',
      color: Math.abs(+d.maxLoss || 0) > avgWin * 2 ? '#f85149' : '#3fb950',
      note: '如果最大亏损显著大于平均盈利，说明止损纪律需要更硬。'
    },
    {
      metric: '持仓结论',
      value: ratio >= 3 ? '亏损拖延' : '继续观察',
      judge: ratio >= 3 && rr < 1 ? '高风险' : ratio >= 1.5 ? '中风险' : '低风险',
      color: ratio >= 3 && rr < 1 ? '#f85149' : ratio >= 1.5 ? '#f0883e' : '#3fb950',
      note: ratio >= 3 ? '输单持仓显著长于赢单，基本可以判断存在扛单或止损过慢。' : '单看时长还不能确认扛单，需要结合最大亏损和回撤。'
    },
  ]
})

const qualityDiagnostics = computed(() => {
  const d = data.value
  const winRate = +d.winRate || 0
  const profitFactor = +d.profitFactor || 0
  const drawdown = +d.maxDrawdownPct || 0
  const expectancy = +d.expectedValue || 0
  return [
    {
      metric: '胜率',
      value: (winRate * 100).toFixed(1) + '%',
      judge: winRate >= 0.5 ? '达标' : '偏低',
      color: winRate >= 0.5 ? '#3fb950' : '#f85149',
      note: '胜率不是单独目标，要和盈亏比一起看。'
    },
    {
      metric: '盈亏因子',
      value: profitFactor ? profitFactor.toFixed(2) : '-',
      judge: profitFactor >= 1.5 ? '较好' : profitFactor >= 1 ? '勉强' : '危险',
      color: profitFactor >= 1.5 ? '#3fb950' : profitFactor >= 1 ? '#f0883e' : '#f85149',
      note: '低于 1 表示总盈利小于总亏损，策略整体不可持续。'
    },
    {
      metric: '单笔期望',
      value: fmt(expectancy) + ' U',
      judge: expectancy > 0 ? '正期望' : '负期望',
      color: expectancy > 0 ? '#3fb950' : '#f85149',
      note: '正期望说明长期还有边际，但仍要控制尾部亏损。'
    },
    {
      metric: '最大回撤',
      value: drawdown.toFixed(1) + '%',
      judge: drawdown <= 20 ? '可控' : drawdown <= 40 ? '偏高' : '过高',
      color: drawdown <= 20 ? '#3fb950' : drawdown <= 40 ? '#f0883e' : '#f85149',
      note: '回撤过高通常来自扛单、加仓摊平或止损不一致。'
    },
  ]
})

const combinedDiagnostics = computed(() => [
  ...holdingDiagnostics.value.map(i => ({ ...i, metric: `持仓｜${i.metric}` })),
  ...qualityDiagnostics.value.map(i => ({ ...i, metric: `质量｜${i.metric}` })),
])

function fmt(v) { return v != null ? (+v).toFixed(2) : '-' }
function fmtHolding(min) {
  if (min == null) return '暂无'
  const n = +min
  if (!Number.isFinite(n)) return '暂无'
  return n < 60 ? n.toFixed(0) + ' 分钟' : (n / 60).toFixed(1) + ' 小时'
}

function ic(key, elRef) {
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

async function load() {
  data.value = await getAnalysis()
  await nextTick()
  renderAll()
}

function renderAll() {
  renderDirection(); renderHoldingComp(); renderRolling(); renderMonthly()
  renderHeatmap(); renderHourly(); renderHourlyPnl(); renderWeekday()
  renderWeekdayPnl(); renderHolding(); renderPnlDist()
  renderDrawdown(); renderLeverage(); renderEfficiency()
}

function renderDirection() {
  const c = ic('direction', directionRef)
  const d = data.value
  const long  = ((d.longWinRate  || 0) * 100).toFixed(1)
  const short = ((d.shortWinRate || 0) * 100).toFixed(1)
  const total = ((d.winRate      || 0) * 100).toFixed(1)
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['做多', '做空', '综合'] },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: v => v + '%' } },
    series: [{ type: 'bar', barMaxWidth: 60,
      data: [
        { value: long,  itemStyle: { color: '#3fb950' } },
        { value: short, itemStyle: { color: '#f85149' } },
        { value: total, itemStyle: { color: '#58a6ff' } },
      ],
      label: { show: true, position: 'top', formatter: p => p.value + '%', color: cs.value.labelColor, fontWeight: 700 },
      itemStyle: { borderRadius: [6, 6, 0, 0] }
    }]
  })
}

function renderHoldingComp() {
  const c = ic('holdingComp', holdingCompRef)
  const d = data.value
  const toH = v => +(v / 60).toFixed(2)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>持仓: ${p[0].value} 小时` },
    grid: { left: 60, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['赢单均持仓', '输单均持仓', '总体均持仓'] },
    yAxis: { type: 'value', axisLabel: { formatter: v => v + 'h' } },
    series: [{ type: 'bar', barMaxWidth: 60,
      data: [
        { value: toH(d.avgWinHoldingMinutes  || 0), itemStyle: { color: '#3fb950' } },
        { value: toH(d.avgLossHoldingMinutes || 0), itemStyle: { color: '#f85149' } },
        { value: toH(d.avgHoldingMinutes     || 0), itemStyle: { color: '#58a6ff' } },
      ],
      label: { show: true, position: 'top', formatter: p => p.value + 'h', color: cs.value.labelColor },
      itemStyle: { borderRadius: [6, 6, 0, 0] }
    }]
  })
}

function renderRolling() {
  const c = ic('rolling', rollingRef)
  const d = data.value
  const rates = (d.rollingWinRate || []).map(v => +(v * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `第 ${p[0].name} 单<br/>近 20 单胜率: ${p[0].value}%` },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: d.rollingIndex || [], axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: v => v + '%' } },
    series: [{ type: 'line', data: rates, smooth: true, symbol: 'none',
      lineStyle: { color: '#58a6ff', width: 2 },
      areaStyle: { color: { type: 'linear', x:0,y:0,x2:0,y2:1,
        colorStops: [{ offset: 0, color: 'rgba(88,166,255,0.3)' }, { offset: 1, color: 'transparent' }] } },
      markLine: { silent: true,
        data: [{ yAxis: 50, lineStyle: { color: '#f85149', type: 'dashed' } }],
        label: { formatter: '盈亏平衡线 50%', color: '#f85149' } }
    }]
  })
}

function renderMonthly() {
  const c = ic('monthly', monthlyRef)
  const ms = data.value.monthlyStats || []
  const months   = ms.map(m => m.month)
  const pnls     = ms.map(m => m.totalPnl)
  const winRates = ms.map(m => +(m.winRate * 100).toFixed(1))
  const avgPnl = ms.map(m => m.trades > 0 ? +(m.totalPnl / m.trades).toFixed(2) : 0)
  const trades = ms.map(m => m.trades)
  const quality = ms.map((m, i) => {
    const pnlScore = pnls[i] >= 0 ? 30 : -20
    const winScore = (winRates[i] - 50) * .9
    const sampleScore = Math.min(20, trades[i] * 1.8)
    return +(50 + pnlScore + winScore + sampleScore).toFixed(1)
  }).map(v => clamp(v, 0, 100))
  c.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: items => {
        const idx = items[0]?.dataIndex ?? 0
        return `${months[idx]}<br/>盈亏: ${pnls[idx]} U<br/>胜率: ${winRates[idx]}%<br/>均盈亏: ${avgPnl[idx]} U<br/>笔数: ${trades[idx]}<br/>月度质量: ${quality[idx]}`
      },
    },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 60, right: 64, top: 40, bottom: 52 },
    xAxis: { type: 'category', data: months, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '盈亏(U)', axisLabel: { formatter: v => v + 'U' } },
      { type: 'value', name: '胜率/笔数/质量', max: value => Math.max(100, value.max), axisLabel: { formatter: v => v } }
    ],
    series: [
      { name: '月度盈亏', type: 'bar', data: pnls, barMaxWidth: 40,
        itemStyle: { color: p => pnls[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius: [4,4,0,0] } },
      { name: '月度胜率', type: 'line', yAxisIndex: 1, data: winRates, smooth: true,
        symbol: 'circle', symbolSize: 6, lineStyle: { color: '#f0883e' }, itemStyle: { color: '#f0883e' } },
      { name: '每单均盈亏', type: 'line', data: avgPnl, smooth: true, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2, type: 'dashed' } },
      { name: '交易笔数', type: 'line', yAxisIndex: 1, data: trades, smooth: true, symbol: 'none', lineStyle: { color: '#a371f7', width: 1.8 } },
      { name: '月度质量分', type: 'line', yAxisIndex: 1, data: quality, smooth: true, symbol: 'diamond', symbolSize: 7, lineStyle: { color: '#7ee787', width: 2.2 } },
    ]
  })
}

function renderHeatmap() {
  const c = ic('heatmap', heatmapRef)
  const d = data.value
  const dataRows = d.heatmapData || []
  const hourTotals = Array.from({ length: 24 }, (_, hour) =>
    dataRows.filter(i => i[0] === hour).reduce((sum, i) => sum + (i[2] || 0), 0)
  )
  const dayTotals = WEEKDAYS.map((_, day) =>
    dataRows.filter(i => i[1] === day).reduce((sum, i) => sum + (i[2] || 0), 0)
  )
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: [
      { left: 56, right: 24, top: 34, height: 78 },
      { left: 56, right: 24, top: 150, height: 54 },
    ],
    xAxis: [
      { type: 'category', data: Array.from({ length: 24 }, (_, i) => `${i}h`), axisLabel: { fontSize: 10 } },
      { type: 'category', gridIndex: 1, data: WEEKDAYS, axisLabel: { fontSize: 10 } },
    ],
    yAxis: [
      { type: 'value', name: '小时', splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', gridIndex: 1, name: '星期', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    ],
    series: [
      { name: '小时开单数', type: 'bar', data: hourTotals, barMaxWidth: 10, itemStyle: { color: '#58a6ff', borderRadius: [3,3,0,0] } },
      { name: '星期开单数', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: dayTotals, barMaxWidth: 18, itemStyle: { color: '#d29922', borderRadius: [3,3,0,0] } },
    ]
  })
}

function renderHourly() {
  const c = ic('hourly', hourlyRef)
  const d = data.value
  const hours = Array.from({length:24}, (_,i) => i + ':00')
  c.setOption({
    tooltip: { trigger:'axis' },
    legend: { top: 0, textStyle:{ color: cs.value.legendColor } },
    grid: { left:50, right:20, top:30, bottom:30 },
    xAxis: { type:'category', data: hours, axisLabel:{ fontSize:9, rotate:45 } },
    yAxis: [
      { type:'value', name:'胜率', max:1, axisLabel:{ formatter:v=>(v*100).toFixed(0)+'%' } },
      { type:'value', name:'交易量', axisLabel:{ formatter:v=>v+'单' } }
    ],
    series: [
      { name:'胜率',   type:'line', data: d.hourlyWinRate,    yAxisIndex:0, smooth:true, lineStyle:{color:'#58a6ff'}, symbol:'none' },
      { name:'交易量', type:'bar',  data: d.hourlyTradeCount, yAxisIndex:1, itemStyle:{color:'rgba(63,185,80,0.4)'}, barMaxWidth:14 }
    ]
  })
}

function renderHourlyPnl() {
  const c = ic('hourlyPnl', hourlyPnlRef)
  const hours = Array.from({length:24}, (_,i) => i + ':00')
  const pnls  = data.value.hourlyTotalPnl || Array(24).fill(0)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>总盈亏: ${p[0].value} U` },
    grid: { left: 60, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'category', data: hours, axisLabel: { fontSize: 9, rotate: 45 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v + 'U' } },
    series: [{ type: 'bar', data: pnls, barMaxWidth: 14,
      itemStyle: { color: p => pnls[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius: [3,3,0,0] }
    }]
  })
}

function renderWeekday() {
  const c = ic('weekday', weekdayRef)
  const d = data.value
  const rates = (d.weekdayWinRate || []).map(v => (v * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>胜率: ${p[0].value}%<br/>交易: ${(d.weekdayTradeCount||[])[p[0].dataIndex]} 次` },
    grid: { left:50, right:20, top:20, bottom:30 },
    xAxis: { type:'category', data: WEEKDAYS },
    yAxis: { type:'value', max:100, axisLabel:{ formatter:v=>v+'%' } },
    series: [{ type:'bar', data: rates, barMaxWidth:40,
      itemStyle: { color: p => parseFloat(p.value) >= 50 ? '#3fb950' : '#f85149', borderRadius:[4,4,0,0] },
      label: { show:true, position:'top', formatter: p => p.value+'%', color: cs.value.labelColor }
    }]
  })
}

function renderWeekdayPnl() {
  const c = ic('weekdayPnl', weekdayPnlRef)
  const pnls = data.value.weekdayTotalPnl || Array(7).fill(0)
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>总盈亏: ${p[0].value} U` },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: WEEKDAYS },
    yAxis: { type: 'value', axisLabel: { formatter: v => v + 'U' } },
    series: [{ type: 'bar', data: pnls, barMaxWidth: 40,
      itemStyle: { color: p => pnls[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius: [4,4,0,0] },
      label: { show: true, position: 'top', formatter: p => (+p.value).toFixed(0) + 'U', color: cs.value.labelColor, fontSize: 11 }
    }]
  })
}

function renderHolding() {
  const c = ic('holding', holdingRef)
  const d = data.value
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>次数: ${p[0].value}<br/>胜率: ${((d.holdingWinRates||[])[p[0].dataIndex]*100).toFixed(1)}%` },
    grid: { left:50, right:20, top:10, bottom:60 },
    xAxis: { type:'category', data: d.holdingLabels, axisLabel:{ rotate:30, fontSize:10 } },
    yAxis: { type:'value' },
    series: [{ type:'bar', data: d.holdingCounts, barMaxWidth:40,
      itemStyle:{ color:'#58a6ff', borderRadius:[4,4,0,0] },
      label:{ show:true, position:'top', color: cs.value.labelColor }
    }]
  })
}

function renderPnlDist() {
  const c = ic('pnlDist', pnlDistRef)
  const d = data.value
  c.setOption({
    tooltip: { trigger:'axis' },
    grid: { left:50, right:20, top:10, bottom:60 },
    xAxis: { type:'category', data: d.pnlRangeLabels, axisLabel:{ rotate:30, fontSize:9 } },
    yAxis: { type:'value' },
    series: [{ type:'bar', data: d.pnlRangeCounts, barMaxWidth:40,
      itemStyle:{ color: p => {
        const label = (d.pnlRangeLabels||[])[p.dataIndex] || ''
        return label.startsWith('-') ? '#f85149' : '#3fb950'
      }, borderRadius:[4,4,0,0] },
      label:{ show:true, position:'top', color: cs.value.labelColor, fontSize:10 }
    }]
  })
}

function renderDrawdown() {
  const c = ic('drawdown', drawdownRef)
  const d      = data.value
  const dates  = d.pnlDates      || []
  const cumPnl = d.pnlCumulative || []
  const n = Math.min(dates.length, cumPnl.length)
  const xs = dates.slice(0, n), ys = cumPnl.slice(0, n)
  let peak = 0
  const ddPct = ys.map(v => {
    if (v > peak) peak = v
    return peak > 0 ? +((v - peak) / Math.abs(peak) * 100).toFixed(2) : 0
  })
  c.setOption({
    tooltip:     { trigger:'axis', axisPointer:{ type:'cross' } },
    legend:      { top:4, textStyle:{ color: cs.value.legendColor } },
    axisPointer: { link:[{ xAxisIndex:'all' }] },
    grid: [
      { left:72, right:20, top:35, height:'48%' },
      { left:72, right:20, top:'58%', height:'30%' }
    ],
    xAxis: [
      { type:'category', data:xs, gridIndex:0, axisLabel:{ show:false }, boundaryGap:false },
      { type:'category', data:xs, gridIndex:1, axisLabel:{ rotate:30, fontSize:9 }, boundaryGap:false }
    ],
    yAxis: [
      { type:'value', name:'盈亏(U)',  gridIndex:0, axisLabel:{ formatter:v=>v+'U' } },
      { type:'value', name:'回撤%',    gridIndex:1, axisLabel:{ formatter:v=>v+'%' } }
    ],
    series: [
      { name:'累计盈亏', type:'line', data:ys, xAxisIndex:0, yAxisIndex:0,
        smooth:true, symbol:'none', lineStyle:{ color:'#58a6ff', width:2 },
        areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
          colorStops:[{offset:0,color:'rgba(88,166,255,0.3)'},{offset:1,color:'transparent'}] } } },
      { name:'回撤幅度', type:'line', data:ddPct, xAxisIndex:1, yAxisIndex:1,
        smooth:true, symbol:'none', lineStyle:{ color:'#f85149', width:1.5 },
        areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
          colorStops:[{offset:0,color:'rgba(248,81,73,0.4)'},{offset:1,color:'transparent'}] } } }
    ]
  })
}

function renderLeverage() {
  const c = ic('leverage', leverageRef)
  const levMap = data.value.leverWinRate || {}
  const levers = Object.keys(levMap).sort((a, b) => +a - +b)
  const rates  = levers.map(l => +(levMap[l] * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name} 杠杆<br/>胜率: <b>${p[0].value}%</b>` },
    grid:    { left:55, right:20, top:40, bottom:30 },
    xAxis:   { type:'category', data:levers.map(l => l + 'x'), name:'杠杆倍数' },
    yAxis:   { type:'value', min:0, max:100, axisLabel:{ formatter:v => v + '%' } },
    series:  [{ type:'bar', data:rates, barMaxWidth:60,
      itemStyle:{ color:p => parseFloat(p.value) >= 50 ? '#3fb950' : '#f85149', borderRadius:[6,6,0,0] },
      label:{ show:true, position:'top', formatter:p => p.value + '%', color: cs.value.labelColor, fontWeight:700 },
      markLine:{ silent:true,
        data:[{ yAxis:50, lineStyle:{ color:'#f0883e', type:'dashed', width:2 } }],
        label:{ formatter:'盈亏平衡线 50%', color:'#f0883e', fontSize:10 } }
    }]
  })
}

function renderEfficiency() {
  const c = ic('efficiency', efficiencyRef)
  const d = data.value
  const labels = ['胜率', '盈亏因子', '期望值', '回撤控制', '止盈效率', '抗扛单']
  const holdRatio = (d.avgLossHoldingMinutes || 0) / Math.max(d.avgWinHoldingMinutes || 1, 1)
  const values = [
    clamp((d.winRate || 0) * 100, 0, 100),
    clamp((d.profitFactor || 0) / 2 * 100, 0, 100),
    clamp(((d.expectedValue || 0) + 50) / 100 * 100, 0, 100),
    clamp(100 - Math.abs(d.maxDrawdownPct || 0) * 2, 0, 100),
    clamp((d.avgWin || 0) / Math.max(Math.abs(d.avgLoss || 1), 1) * 50, 0, 100),
    clamp(100 - holdRatio * 22, 0, 100),
  ]
  c.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '64%',
      indicator: labels.map(name => ({ name, max: 100 })),
      axisName: { color: cs.value.labelColor },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(88,166,255,.05)'] } },
    },
    series: [{
      type: 'radar',
      data: [{ name: '交易效率', value: values.map(v => +v.toFixed(1)), areaStyle: { color: 'rgba(88,166,255,.18)' }, lineStyle: { color: '#58a6ff', width: 2 } }]
    }]
  })
}

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
.chart-card { padding: 12px; margin-bottom: 10px; }
.card-title { margin-bottom: 8px; }
.compact-analysis-chart { padding: 12px; }
.stat-card { padding: 12px; }
.focus-stat {
  background:
    linear-gradient(180deg, rgba(88, 166, 255, .09), transparent 70%),
    var(--bg-card);
}
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.stat-value { font-size: 22px; font-weight: 700; margin-bottom: 3px; }
.stat-sub   { font-size: 11px; color: var(--text-dim); }
</style>
