<template>
  <div>
    <div class="page-title">🔬 深度分析</div>

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

    <el-row :gutter="16">
      <el-col :span="12"><div class="chart-card"><div class="card-title">📈 多空胜率对比</div><div ref="directionRef" style="height:220px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">⏱ 赢单 vs 输单持仓时长</div><div ref="holdingCompRef" style="height:220px" /></div></el-col>
    </el-row>
    <div class="chart-card"><div class="card-title">📊 滚动20单胜率趋势</div><div ref="rollingRef" style="height:220px" /></div>
    <div class="chart-card"><div class="card-title">🗓 月度交易统计（盈亏 + 胜率）</div><div ref="monthlyRef" style="height:280px" /></div>
    <div class="chart-card"><div class="card-title">🔥 开单时段热力图（星期 × 小时）</div><div ref="heatmapRef" style="height:240px" /></div>
    <el-row :gutter="16">
      <el-col :span="14"><div class="chart-card"><div class="card-title">🕐 各小时胜率 & 交易量</div><div ref="hourlyRef" style="height:260px" /></div></el-col>
      <el-col :span="10"><div class="chart-card"><div class="card-title">💹 各小时总盈亏</div><div ref="hourlyPnlRef" style="height:260px" /></div></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12"><div class="chart-card"><div class="card-title">📆 星期胜率分布</div><div ref="weekdayRef" style="height:260px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">💵 星期总盈亏</div><div ref="weekdayPnlRef" style="height:260px" /></div></el-col>
    </el-row>
    <div class="chart-card"><div class="card-title">🪙 品种盈亏 & 胜率对比</div><div ref="symbolRef" style="height:300px" /></div>
    <el-row :gutter="16">
      <el-col :span="12"><div class="chart-card"><div class="card-title">⏱ 持仓时间分布</div><div ref="holdingRef" style="height:260px" /></div></el-col>
      <el-col :span="12"><div class="chart-card"><div class="card-title">💰 盈亏金额分布</div><div ref="pnlDistRef" style="height:260px" /></div></el-col>
    </el-row>
    <div class="chart-card"><div class="card-title">📉 资金曲线 & 最大回撤分析（双面板）</div><div ref="drawdownRef" style="height:380px" /></div>
    <el-row :gutter="16">
      <el-col :span="10"><div class="chart-card"><div class="card-title">⚙️ 杠杆胜率精细分析</div><div ref="leverageRef" style="height:280px" /></div></el-col>
      <el-col :span="14"><div class="chart-card"><div class="card-title">🎯 品种综合散点（胜率 vs 总盈亏）</div><div ref="symbolScatterRef" style="height:280px" /></div></el-col>
    </el-row>
    <div class="chart-card"><div class="card-title">📊 月度每单均盈亏 & 交易笔数趋势</div><div ref="monthlyDetailRef" style="height:260px" /></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getAnalysis } from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const data = ref({})
const WEEKDAYS = ['周一','周二','周三','周四','周五','周六','周日']

const heatmapRef    = ref(null); const hourlyRef    = ref(null); const hourlyPnlRef  = ref(null)
const weekdayRef    = ref(null); const weekdayPnlRef= ref(null); const symbolRef     = ref(null)
const holdingRef    = ref(null); const holdingCompRef= ref(null); const pnlDistRef    = ref(null)
const rollingRef    = ref(null); const monthlyRef   = ref(null); const directionRef  = ref(null)
const drawdownRef   = ref(null); const leverageRef  = ref(null); const symbolScatterRef = ref(null)
const monthlyDetailRef = ref(null)

let charts = {}

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

function fmt(v) { return v != null ? (+v).toFixed(2) : '-' }
function fmtHolding(min) {
  if (!min) return '-'
  return min < 60 ? min.toFixed(0) + ' 分钟' : (min / 60).toFixed(1) + ' 小时'
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
  renderWeekdayPnl(); renderSymbol(); renderHolding(); renderPnlDist()
  renderDrawdown(); renderLeverage(); renderSymbolScatter(); renderMonthlyDetail()
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
  const rates = (d.rollingWinRate || []).map(v => (v * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `第 ${p[0].name} 单<br/>近20单胜率: ${p[0].value}%` },
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
  const winRates = ms.map(m => (m.winRate * 100).toFixed(1))
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 60, right: 60, top: 40, bottom: 60 },
    xAxis: { type: 'category', data: months, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '盈亏(U)', axisLabel: { formatter: v => v + 'U' } },
      { type: 'value', name: '胜率',   max: 100, axisLabel: { formatter: v => v + '%' } }
    ],
    series: [
      { name: '月度盈亏', type: 'bar', data: pnls, barMaxWidth: 40,
        itemStyle: { color: p => pnls[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius: [4,4,0,0] } },
      { name: '月度胜率', type: 'line', yAxisIndex: 1, data: winRates, smooth: true,
        symbol: 'circle', symbolSize: 6, lineStyle: { color: '#f0883e' }, itemStyle: { color: '#f0883e' } },
    ]
  })
}

function renderHeatmap() {
  const c = ic('heatmap', heatmapRef)
  const d = data.value
  const maxVal = Math.max(...(d.heatmapData || []).map(i => i[2]))
  c.setOption({
    tooltip: { formatter: p => `${WEEKDAYS[p.data[1]]} ${p.data[0]}:00<br/>交易 ${p.data[2]} 次` },
    grid: { left: 60, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'category', data: Array.from({length:24}, (_,i)=>i+'h'), axisLabel:{fontSize:10} },
    yAxis: { type: 'category', data: WEEKDAYS },
    visualMap: { min:0, max: maxVal||1, calculable: true, orient:'horizontal', left:'right', bottom:'-5px',
                 inRange:{ color: cs.value.heatmap } },
    series: [{ type: 'heatmap', data: d.heatmapData || [],
      label: { show: false },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } }
    }]
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

function renderSymbol() {
  const c = ic('symbol', symbolRef)
  const d = data.value
  const syms = d.symbolNames || []
  c.setOption({
    tooltip: { trigger:'axis' },
    legend: { top:0, textStyle:{ color: cs.value.legendColor } },
    grid: { left:80, right:60, top:30, bottom:40 },
    xAxis: { type:'category', data: syms, axisLabel:{ rotate:30, fontSize:10 } },
    yAxis: [
      { type:'value', name:'盈亏(U)', axisLabel:{ formatter:v=>v+'U' } },
      { type:'value', name:'胜率',   max:100, axisLabel:{ formatter:v=>v+'%' } }
    ],
    series: [
      { name:'总盈亏', type:'bar', data: d.symbolTotalPnl,
        itemStyle:{ color: p => (d.symbolTotalPnl||[])[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius:[4,4,0,0] },
        barMaxWidth:30 },
      { name:'胜率', type:'line', yAxisIndex:1, smooth:true, symbol:'circle', symbolSize:8,
        data: (d.symbolWinRate||[]).map(v=>(v*100).toFixed(1)),
        lineStyle:{ color:'#f0883e' }, itemStyle:{ color:'#f0883e' } }
    ]
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

function renderSymbolScatter() {
  const c = ic('symbolScatter', symbolScatterRef)
  const d     = data.value
  const names = d.symbolNames    || []
  const pnls  = d.symbolTotalPnl || []
  const rates = d.symbolWinRate  || []
  const scData = names.map((name, i) => ({
    name,
    value: [+(rates[i] * 100).toFixed(1), +pnls[i].toFixed(2)],
    itemStyle:{ color: pnls[i] >= 0 ? '#3fb950' : '#f85149' }
  }))
  c.setOption({
    tooltip: { formatter: p => `<b>${p.name}</b><br/>胜率: ${p.value[0]}%<br/>总盈亏: ${p.value[1]} U` },
    grid:    { left:72, right:40, top:20, bottom:40 },
    xAxis:   { type:'value', name:'胜率(%)', min:0, max:100, axisLabel:{ formatter:v => v + '%' } },
    yAxis:   { type:'value', name:'盈亏(U)',  axisLabel:{ formatter:v => v + 'U' } },
    series:  [{ type:'scatter', data:scData, symbolSize:18,
      label:{ show:true, formatter:p => p.name, position:'right', color: cs.value.labelColor, fontSize:10 },
      markLine:{ silent:true,
        data:[
          { xAxis:50, lineStyle:{ color:'rgba(248,81,73,0.35)', type:'dashed' } },
          { yAxis:0,  lineStyle:{ color:'rgba(248,81,73,0.35)', type:'dashed' } }
        ], label:{ show:false } }
    }]
  })
}

function renderMonthlyDetail() {
  const c = ic('monthlyDetail', monthlyDetailRef)
  const ms     = data.value.monthlyStats || []
  const months = ms.map(m => m.month)
  const avgPnl = ms.map(m => m.trades > 0 ? +(m.totalPnl / m.trades).toFixed(2) : 0)
  const trades = ms.map(m => m.trades)
  c.setOption({
    tooltip: { trigger:'axis', axisPointer:{ type:'cross' } },
    legend:  { top:0, textStyle:{ color: cs.value.legendColor } },
    grid:    { left:72, right:60, top:35, bottom:60 },
    xAxis:   { type:'category', data:months, axisLabel:{ rotate:30, fontSize:10 } },
    yAxis:   [
      { type:'value', name:'每单均盈亏(U)', axisLabel:{ formatter:v => v + 'U' } },
      { type:'value', name:'交易笔数',      axisLabel:{ formatter:v => v + '笔' } }
    ],
    series: [
      { name:'每单均盈亏', type:'bar', data:avgPnl, barMaxWidth:30,
        itemStyle:{ color:p => avgPnl[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius:[4,4,0,0] } },
      { name:'交易笔数', type:'line', yAxisIndex:1, data:trades, smooth:true,
        symbol:'circle', symbolSize:6, lineStyle:{ color:'#58a6ff' }, itemStyle:{ color:'#58a6ff' } }
    ]
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
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub   { font-size: 11px; color: var(--text-dim); }
</style>
