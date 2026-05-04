<template>
  <div>
    <div class="page-title">🐳 主力大户分析</div>

    <div class="chart-card" style="margin-bottom:16px; padding:12px 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <div style="display:flex;align-items:center;gap:8px">
            <span class="text-muted" style="font-size:13px;white-space:nowrap">交易对</span>
            <el-select v-model="instId" style="flex:1" @change="loadAll">
              <el-option v-for="s in SYMBOLS" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
        </el-col>
        <el-col :span="5">
          <div style="display:flex;align-items:center;gap:8px">
            <span class="text-muted" style="font-size:13px;white-space:nowrap">K线周期</span>
            <el-select v-model="period" style="flex:1" @change="loadAll">
              <el-option label="5分钟"  value="5m"  />
              <el-option label="15分钟" value="15m" />
              <el-option label="1小时"  value="1H"  />
              <el-option label="4小时"  value="4H"  />
              <el-option label="1天"    value="1D"  />
            </el-select>
          </div>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" :loading="loading" @click="loadAll">
            <el-icon><Refresh /></el-icon>&nbsp;刷新数据
          </el-button>
        </el-col>
        <el-col :span="9" style="text-align:right">
          <span v-if="lastUpdate" class="text-dim" style="font-size:12px">
            最后更新：{{ lastUpdate }}
          </span>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="s in signals" :key="s.label">
        <div class="stat-card" style="text-align:center">
          <div class="sig-label">{{ s.label }}</div>
          <div class="sig-value" :style="{color:s.color}">{{ s.value }}</div>
          <div class="sig-desc">{{ s.desc }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">📊 大户持仓多空比（精英交易员）
            <span class="hint">多空比 &gt; 1 = 大户偏多；&lt; 1 = 大户偏空</span>
          </div>
          <div ref="posRatioRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">👥 多空账户人数比
            <span class="hint">散户多空情绪参考</span>
          </div>
          <div ref="acctRatioRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">💹 主动买卖量对比
            <span class="hint">Taker 主动买入 vs 主动卖出</span>
          </div>
          <div ref="takerRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="card-title">📈 主动买卖净差值（累计）
            <span class="hint">持续正值 = 买方主导</span>
          </div>
          <div ref="takerCumRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">💰 资金费率历史
            <span class="hint">极高正值警惕多头爆仓；极低负值警惕空头爆仓</span>
          </div>
          <div ref="fundingRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">📦 合约持仓量 (OI) 变化
            <span class="hint">OI↑价格↑ = 多头趋势；OI↑价格↓ = 空头趋势</span>
          </div>
          <div ref="oiRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'
import dayjs from 'dayjs'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const SYMBOLS = [
  'BTC-USDT-SWAP','ETH-USDT-SWAP','SOL-USDT-SWAP',
  'XRP-USDT-SWAP','BNB-USDT-SWAP','DOGE-USDT-SWAP',
  'ADA-USDT-SWAP','AVAX-USDT-SWAP','LINK-USDT-SWAP','OP-USDT-SWAP',
]

const instId     = ref('BTC-USDT-SWAP')
const period     = ref('1H')
const loading    = ref(false)
const lastUpdate = ref('')

const posRatioData  = ref([]); const acctRatioData = ref([])
const takerData     = ref([]); const fundingData   = ref([])
const oiData        = ref([])

const posRatioRef  = ref(null); const acctRatioRef = ref(null)
const takerRef     = ref(null); const takerCumRef  = ref(null)
const fundingRef   = ref(null); const oiRef        = ref(null)

let charts = {}

const okxHttp = axios.create({ baseURL: '/okx', timeout: 30000 })
async function fetchOkx(path, params) {
  try {
    const res = await okxHttp.get(path, { params })
    if (res.data && res.data.code === '0') return [...(res.data.data || [])].reverse()
    return []
  } catch { return [] }
}

const ccy = computed(() => instId.value.split('-')[0])

const signals = computed(() => {
  const pos   = posRatioData.value; const taker = takerData.value
  const fund  = fundingData.value;  const oi    = oiData.value

  const lastPos  = pos.length   ? +pos[pos.length - 1].lsRatio       : null
  const last10   = taker.slice(-10)
  const buySum   = last10.reduce((s, d) => s + +d.buyVol,  0)
  const sellSum  = last10.reduce((s, d) => s + +d.sellVol, 0)
  const buyRatio = (buySum + sellSum) > 0 ? buySum / (buySum + sellSum) : null
  const lastFund = fund.length  ? +fund[fund.length - 1].fundingRate  : null
  const oiSlice  = oi.slice(-20)
  const oiChange = oiSlice.length >= 2
    ? ((+oiSlice[oiSlice.length-1].oi - +oiSlice[0].oi) / Math.abs(+oiSlice[0].oi) * 100) : null

  const posSignal = lastPos == null
    ? { label:'大户持仓信号', value:'暂无数据',      color:'#8b949e', desc:'等待数据加载' }
    : lastPos > 1.5  ? { label:'大户持仓信号', value:'强势偏多 ↑↑', color:'#3fb950', desc:`多空比 ${lastPos.toFixed(3)}` }
    : lastPos > 1.1  ? { label:'大户持仓信号', value:'小幅偏多 ↑',  color:'#58a6ff', desc:`多空比 ${lastPos.toFixed(3)}` }
    : lastPos < 0.67 ? { label:'大户持仓信号', value:'强势偏空 ↓↓', color:'#f85149', desc:`多空比 ${lastPos.toFixed(3)}` }
    : lastPos < 0.9  ? { label:'大户持仓信号', value:'小幅偏空 ↓',  color:'#f0883e', desc:`多空比 ${lastPos.toFixed(3)}` }
    :                  { label:'大户持仓信号', value:'多空中性',    color:'#8b949e', desc:`多空比 ${lastPos.toFixed(3)}` }

  const takerSignal = buyRatio == null
    ? { label:'主动资金流向', value:'暂无数据',        color:'#8b949e', desc:'等待数据加载' }
    : buyRatio > 0.60 ? { label:'主动资金流向', value:'强势净流入 ↑↑', color:'#3fb950', desc:`买入占比 ${(buyRatio*100).toFixed(1)}%` }
    : buyRatio > 0.53 ? { label:'主动资金流向', value:'小幅净流入 ↑',  color:'#58a6ff', desc:`买入占比 ${(buyRatio*100).toFixed(1)}%` }
    : buyRatio < 0.40 ? { label:'主动资金流向', value:'强势净流出 ↓↓', color:'#f85149', desc:`买入占比 ${(buyRatio*100).toFixed(1)}%` }
    : buyRatio < 0.47 ? { label:'主动资金流向', value:'小幅净流出 ↓',  color:'#f0883e', desc:`买入占比 ${(buyRatio*100).toFixed(1)}%` }
    :                   { label:'主动资金流向', value:'多空均衡',      color:'#8b949e', desc:`买入占比 ${(buyRatio*100).toFixed(1)}%` }

  const fundSignal = lastFund == null
    ? { label:'资金费率信号', value:'暂无数据',         color:'#8b949e', desc:'等待数据加载' }
    : lastFund >  0.001  ? { label:'资金费率信号', value:'费率极高 ⚠️', color:'#f85149', desc:`${(lastFund*100).toFixed(4)}% 警惕多头爆仓` }
    : lastFund >  0.0003 ? { label:'资金费率信号', value:'多头偏高',    color:'#f0883e', desc:`${(lastFund*100).toFixed(4)}% 多头持续付费` }
    : lastFund < -0.001  ? { label:'资金费率信号', value:'费率极低 ⚠️', color:'#3fb950', desc:`${(lastFund*100).toFixed(4)}% 警惕空头爆仓` }
    : lastFund < -0.0003 ? { label:'资金费率信号', value:'空头偏高',    color:'#58a6ff', desc:`${(lastFund*100).toFixed(4)}% 空头持续付费` }
    :                      { label:'资金费率信号', value:'费率正常',    color:'#8b949e', desc:`${(lastFund*100).toFixed(4)}%` }

  const oiSignal = oiChange == null
    ? { label:'持仓量趋势', value:'暂无数据',       color:'#8b949e', desc:'等待数据加载' }
    : oiChange >  8 ? { label:'持仓量趋势', value:'持仓快速扩张', color:'#3fb950', desc:`近期OI +${oiChange.toFixed(1)}%` }
    : oiChange >  3 ? { label:'持仓量趋势', value:'持仓小幅扩张', color:'#58a6ff', desc:`近期OI +${oiChange.toFixed(1)}%` }
    : oiChange < -8 ? { label:'持仓量趋势', value:'持仓快速收缩', color:'#f85149', desc:`近期OI ${oiChange.toFixed(1)}%` }
    : oiChange < -3 ? { label:'持仓量趋势', value:'持仓小幅收缩', color:'#f0883e', desc:`近期OI ${oiChange.toFixed(1)}%` }
    :                 { label:'持仓量趋势', value:'持仓量稳定',   color:'#8b949e', desc:`近期OI ${oiChange.toFixed(1)}%` }

  return [posSignal, takerSignal, fundSignal, oiSignal]
})

function fmtTs(ts) { return dayjs(+ts).format('MM-DD HH:mm') }

function ic(key, elRef) {
  if (!charts[key]) charts[key] = initChart(elRef.value)
  return charts[key]
}

function renderAll() {
  renderPosRatio(); renderAcctRatio(); renderTaker()
  renderTakerCum(); renderFunding(); renderOI()
}

function renderPosRatio() {
  const c = ic('posRatio', posRatioRef)
  const arr    = posRatioData.value
  const times  = arr.map(d => fmtTs(d.ts))
  const ratios = arr.map(d => +parseFloat(d.lsRatio).toFixed(4))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>大户多空比: <b>${p[0].value}</b>` },
    grid:    { left:65, right:20, top:35, bottom:48 },
    xAxis:   { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:   { type:'value', axisLabel:{formatter:v=>v.toFixed(2)} },
    series:  [{ type:'line', data:ratios, smooth:true, symbol:'none',
      lineStyle:{ color:'#58a6ff', width:2 },
      areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
        colorStops:[{offset:0,color:'rgba(88,166,255,0.3)'},{offset:1,color:'transparent'}] } },
      markLine:{ silent:true,
        data:[{yAxis:1, lineStyle:{color:'#f0883e',type:'dashed'}}],
        label:{formatter:'多空平衡线 1.0', color:'#f0883e', fontSize:10} }
    }]
  })
}

function renderAcctRatio() {
  const c = ic('acctRatio', acctRatioRef)
  const arr    = acctRatioData.value
  const times  = arr.map(d => fmtTs(d.ts))
  const ratios = arr.map(d => +parseFloat(d.lsRatio).toFixed(4))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>多空账户比: <b>${p[0].value}</b>` },
    grid:    { left:65, right:20, top:35, bottom:48 },
    xAxis:   { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:   { type:'value', axisLabel:{formatter:v=>v.toFixed(2)} },
    series:  [{ type:'line', data:ratios, smooth:true, symbol:'none',
      lineStyle:{ color:'#3fb950', width:2 },
      areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
        colorStops:[{offset:0,color:'rgba(63,185,80,0.25)'},{offset:1,color:'transparent'}] } },
      markLine:{ silent:true,
        data:[{yAxis:1, lineStyle:{color:'#f0883e',type:'dashed'}}],
        label:{formatter:'多空平衡线 1.0', color:'#f0883e', fontSize:10} }
    }]
  })
}

function renderTaker() {
  const c = ic('taker', takerRef)
  const arr      = takerData.value
  const times    = arr.map(d => fmtTs(d.ts))
  const buyVols  = arr.map(d => +parseFloat(d.buyVol).toFixed(2))
  const sellVols = arr.map(d => -Math.abs(+parseFloat(d.sellVol).toFixed(2)))
  c.setOption({
    tooltip: { trigger:'axis',
      formatter: p => `${p[0].name}<br/>主动买入: ${p[0].value}<br/>主动卖出: ${-p[1].value}` },
    legend: { top:0, textStyle:{ color: cs.value.legendColor } },
    grid:   { left:60, right:20, top:35, bottom:48 },
    xAxis:  { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:  { type:'value' },
    series: [
      { name:'主动买入', type:'bar', data:buyVols,  stack:'vol', barMaxWidth:20, itemStyle:{color:'#3fb950'} },
      { name:'主动卖出', type:'bar', data:sellVols, stack:'vol', barMaxWidth:20, itemStyle:{color:'#f85149'} },
    ]
  })
}

function renderTakerCum() {
  const c = ic('takerCum', takerCumRef)
  const arr     = takerData.value
  const times   = arr.map(d => fmtTs(d.ts))
  let cum = 0
  const cumData = arr.map(d => { cum += (+d.buyVol - +d.sellVol); return +cum.toFixed(2) })
  const isPos   = cumData.length ? cumData[cumData.length - 1] >= 0 : true
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>累计净差: <b>${p[0].value}</b>` },
    grid:    { left:75, right:20, top:35, bottom:48 },
    xAxis:   { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:   { type:'value' },
    series:  [{ type:'line', data:cumData, smooth:true, symbol:'none',
      lineStyle:{ color: isPos ? '#3fb950' : '#f85149', width:2 },
      areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
        colorStops:[
          {offset:0, color: isPos ? 'rgba(63,185,80,0.3)' : 'rgba(248,81,73,0.3)'},
          {offset:1, color:'transparent'}
        ] } }
    }]
  })
}

function renderFunding() {
  const c = ic('funding', fundingRef)
  const arr   = fundingData.value
  const times = arr.map(d => fmtTs(d.fundingTime))
  const rates = arr.map(d => +(+d.fundingRate * 100).toFixed(5))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>资金费率: <b>${p[0].value}%</b>` },
    grid:    { left:65, right:20, top:35, bottom:48 },
    xAxis:   { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:   { type:'value', axisLabel:{formatter:v=>v+'%'} },
    series:  [{ type:'bar', data:rates, barMaxWidth:16,
      itemStyle:{ color:p=>rates[p.dataIndex] >= 0 ? '#3fb950' : '#f85149', borderRadius:[3,3,0,0] }
    }]
  })
}

function renderOI() {
  const c = ic('oi', oiRef)
  const arr   = oiData.value
  const times = arr.map(d => fmtTs(d.ts))
  const vals  = arr.map(d => +parseFloat(d.oi).toFixed(0))
  c.setOption({
    tooltip: { trigger:'axis', formatter: p => `${p[0].name}<br/>持仓量: <b>${(p[0].value/1000).toFixed(1)}K</b>` },
    grid:    { left:70, right:20, top:35, bottom:48 },
    xAxis:   { type:'category', data:times, axisLabel:{rotate:30, fontSize:9} },
    yAxis:   { type:'value', axisLabel:{formatter:v=>(v/1000).toFixed(0)+'K'} },
    series:  [{ type:'line', data:vals, smooth:true, symbol:'none',
      lineStyle:{ color:'#f0883e', width:2 },
      areaStyle:{ color:{ type:'linear', x:0,y:0,x2:0,y2:1,
        colorStops:[{offset:0,color:'rgba(240,136,62,0.3)'},{offset:1,color:'transparent'}] } }
    }]
  })
}

async function loadAll() {
  loading.value = true
  try {
    const [pos, acct, taker, fund, oi] = await Promise.all([
      fetchOkx('/api/v5/rubik/stat/contracts/long-short-position-ratio-contract-top-trader', { instId:instId.value, period:period.value, limit:200 }),
      fetchOkx('/api/v5/rubik/stat/contracts/long-short-account-ratio-contract',             { instId:instId.value, period:period.value, limit:200 }),
      fetchOkx('/api/v5/rubik/stat/taker-volume-contract',                                   { instId:instId.value, period:period.value, limit:200 }),
      fetchOkx('/api/v5/public/funding-rate-history',                                        { instId:instId.value, limit:100 }),
      fetchOkx('/api/v5/rubik/stat/contracts/open-interest-history',                         { instId:instId.value, period:period.value, limit:200 }),
    ])
    posRatioData.value  = pos.map(d => ({ ts: d[0], lsRatio: d[1] }))
    acctRatioData.value = acct.map(d => ({ ts: d[0], lsRatio: d[1] }))
    takerData.value     = taker.map(d => ({ ts: d[0], sellVol: d[1], buyVol: d[2] }))
    fundingData.value   = fund
    oiData.value        = oi.map(d => ({ ts: d[0], oi: d[1] }))
    lastUpdate.value    = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  renderAll()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(() => { loadAll(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.sig-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.sig-value { font-size: 18px; font-weight: 700; margin-bottom: 4px; }
.sig-desc  { font-size: 11px; color: var(--text-dim); }
.hint      { font-size: 11px; color: var(--text-dim); font-weight: 400; margin-left: 6px; }
</style>
