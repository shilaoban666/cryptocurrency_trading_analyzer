<template>
  <div>
    <div class="page-title">主力大户分析</div>

    <el-tabs v-model="activeTab" class="market-tabs" @tab-change="onMarketTabChange">
      <el-tab-pane label="大户指标" name="sentiment">
    <div class="chart-card filter-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <div class="filter-item">
            <span class="text-muted">交易对</span>
            <el-select v-model="instId" style="flex:1" @change="loadAll">
              <el-option v-for="s in SYMBOLS" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="filter-item">
            <span class="text-muted">周期</span>
            <el-select v-model="period" style="flex:1" @change="loadAll">
              <el-option label="5分钟" value="5m" />
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
            刷新数据
          </el-button>
        </el-col>
        <el-col :span="9" style="text-align:right">
          <span v-if="lastUpdate" class="text-dim last-update">
            最后更新：{{ lastUpdate }}
          </span>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6" v-for="s in signals" :key="s.label">
        <div class="stat-card signal-card">
          <div class="sig-label">{{ s.label }}</div>
          <div class="sig-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="sig-desc">{{ s.desc }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card summary-card">
      <div class="card-title">图表解读<span class="hint">基于大户多空、账户情绪、主动成交、资金费率和 OI 的自动判断</span></div>
      <el-row :gutter="12">
        <el-col :span="6" v-for="item in marketReadings" :key="item.title">
          <div class="reading-item" :class="item.tone">
            <div class="reading-title">{{ item.title }}</div>
            <div class="reading-signal">{{ item.signal }}</div>
            <div class="reading-desc">{{ item.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">大户持仓多空比（精英交易员）<span class="hint">大于 1 偏多，小于 1 偏空</span></div>
          <div ref="posRatioRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">多空账户人数比<span class="hint">市场账户情绪参考</span></div>
          <div ref="acctRatioRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="chart-card">
          <div class="card-title">主动买卖量对比<span class="hint">Taker 主动买入 vs 主动卖出</span></div>
          <div ref="takerRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="card-title">主动买卖净差值（累计）<span class="hint">持续正值代表买方主导</span></div>
          <div ref="takerCumRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">资金费率历史<span class="hint">极端值提示拥挤方向风险</span></div>
          <div ref="fundingRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">合约持仓量（OI）变化<span class="hint">持仓扩张或收缩节奏</span></div>
          <div ref="oiRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">大户-账户多空背离<span class="hint">正值代表大户比普通账户更偏多</span></div>
          <div ref="ratioGapRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">主动买卖失衡率<span class="hint">(买入 - 卖出) / 总成交</span></div>
          <div ref="takerImbalanceRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">资金费率趋势线<span class="hint">柱状为原始费率，折线为滚动均值</span></div>
          <div ref="fundingTrendRef" style="height:280px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">OI 增减速度<span class="hint">相邻周期持仓量变化百分比</span></div>
          <div ref="oiDeltaRef" style="height:280px" />
        </div>
      </el-col>
    </el-row>
      </el-tab-pane>

      <el-tab-pane label="资金流" name="flow">
        <div class="chart-card filter-card">
          <el-row :gutter="16" align="middle">
            <el-col :span="14">
              <div class="flow-note">BTC/ETH 主力与散户流入流出，基于 OKX 主动成交量和精英交易员多空变化估算</div>
            </el-col>
            <el-col :span="4">
              <el-select v-model="flowFocusPeriod" @change="renderFlowCharts">
                <el-option v-for="p in FLOW_PERIODS" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-button type="primary" :loading="flowLoading" @click="loadMoneyFlow">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </el-col>
            <el-col :span="3" style="text-align:right">
              <span v-if="flowLastUpdate" class="text-dim last-update">{{ flowLastUpdate }}</span>
            </el-col>
          </el-row>
        </div>

        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="12" v-for="symbol in FLOW_SYMBOLS" :key="symbol">
            <div class="chart-card flow-summary">
              <div class="card-title">{{ symbolLabel(symbol) }} 资金流快照<span class="hint">{{ periodLabel(flowFocusPeriod) }}</span></div>
              <div class="flow-metric-grid">
                <div v-for="item in flowSnapshot(symbol)" :key="item.label" class="flow-metric">
                  <span>{{ item.label }}</span>
                  <b :style="{ color: item.color }">{{ item.value }}</b>
                  <em>{{ item.sub }}</em>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">BTC 主力/散户资金流</div>
              <div ref="flowBtcRef" style="height:340px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="card-title">ETH 主力/散户资金流</div>
              <div ref="flowEthRef" style="height:340px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12" v-for="symbol in FLOW_SYMBOLS" :key="`${symbol}-table`">
            <div class="chart-card">
              <div class="card-title">{{ symbolLabel(symbol) }} 分周期资金流明细</div>
              <el-table :data="moneyFlowRows[symbol] || []" size="small" style="width:100%">
                <el-table-column prop="label" label="周期" width="72" />
                <el-table-column label="主力流入">
                  <template #default="{ row }"><span class="profit">{{ fmtMoney(row.whaleIn) }}</span></template>
                </el-table-column>
                <el-table-column label="主力流出">
                  <template #default="{ row }"><span class="loss">{{ fmtMoney(row.whaleOut) }}</span></template>
                </el-table-column>
                <el-table-column label="散户流入">
                  <template #default="{ row }"><span class="profit">{{ fmtMoney(row.retailIn) }}</span></template>
                </el-table-column>
                <el-table-column label="散户流出">
                  <template #default="{ row }"><span class="loss">{{ fmtMoney(row.retailOut) }}</span></template>
                </el-table-column>
                <el-table-column label="总流入">
                  <template #default="{ row }">{{ fmtMoney(row.totalIn) }}</template>
                </el-table-column>
                <el-table-column label="总流出">
                  <template #default="{ row }">{{ fmtMoney(row.totalOut) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import dayjs from 'dayjs'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const SYMBOLS = [
  'BTC-USDT-SWAP', 'ETH-USDT-SWAP', 'SOL-USDT-SWAP',
  'XRP-USDT-SWAP', 'BNB-USDT-SWAP', 'DOGE-USDT-SWAP',
  'ADA-USDT-SWAP', 'AVAX-USDT-SWAP', 'LINK-USDT-SWAP', 'OP-USDT-SWAP',
]
const FLOW_SYMBOLS = ['BTC-USDT-SWAP', 'ETH-USDT-SWAP']
const FLOW_PERIODS = [
  { label: '1分', value: '1m' },
  { label: '5分', value: '5m' },
  { label: '15分', value: '15m' },
  { label: '1小时', value: '1H' },
  { label: '4小时', value: '4H' },
  { label: '1日', value: '1D' },
  { label: '三日', value: '3D', source: '1D', aggregate: 3 },
  { label: '1周', value: '1W', source: '1D', aggregate: 7 },
]

const activeTab = ref('sentiment')
const instId = ref('BTC-USDT-SWAP')
const period = ref('1H')
const loading = ref(false)
const lastUpdate = ref('')
const flowLoading = ref(false)
const flowFocusPeriod = ref('15m')
const flowLastUpdate = ref('')

const posRatioData = ref([])
const acctRatioData = ref([])
const takerData = ref([])
const fundingData = ref([])
const oiData = ref([])
const moneyFlowRows = ref({ 'BTC-USDT-SWAP': [], 'ETH-USDT-SWAP': [] })

const posRatioRef = ref(null)
const acctRatioRef = ref(null)
const takerRef = ref(null)
const takerCumRef = ref(null)
const fundingRef = ref(null)
const oiRef = ref(null)
const ratioGapRef = ref(null)
const takerImbalanceRef = ref(null)
const fundingTrendRef = ref(null)
const oiDeltaRef = ref(null)
const flowBtcRef = ref(null)
const flowEthRef = ref(null)
let charts = {}

const okxHttp = axios.create({ baseURL: '/okx', timeout: 30000 })

async function fetchOkx(path, params) {
  try {
    const res = await okxHttp.get(path, { params })
    if (res.data?.code === '0') return [...(res.data.data || [])].reverse()
    return []
  } catch {
    return []
  }
}

const signals = computed(() => {
  const lastPos = last(posRatioData.value)?.lsRatio
  const lastFund = last(fundingData.value)?.fundingRate
  const takerSlice = takerData.value.slice(-10)
  const buySum = takerSlice.reduce((sum, row) => sum + num(row.buyVol), 0)
  const sellSum = takerSlice.reduce((sum, row) => sum + num(row.sellVol), 0)
  const buyRatio = buySum + sellSum > 0 ? buySum / (buySum + sellSum) : null
  const oiSlice = oiData.value.slice(-20)
  const oiChange = oiSlice.length >= 2
    ? (num(last(oiSlice).oi) - num(oiSlice[0].oi)) / Math.abs(num(oiSlice[0].oi)) * 100
    : null

  return [
    posSignal(lastPos),
    takerSignal(buyRatio),
    fundingSignal(lastFund),
    oiSignal(oiChange),
  ]
})

const marketReadings = computed(() => {
  const pos = posRatioData.value.map(d => num(d.lsRatio))
  const acct = acctRatioData.value.map(d => num(d.lsRatio))
  const imbalance = takerData.value.map(d => {
    const buy = num(d.buyVol)
    const sell = num(d.sellVol)
    return buy + sell > 0 ? (buy - sell) / (buy + sell) * 100 : 0
  })
  const funding = fundingData.value.map(d => num(d.fundingRate) * 100)
  const oi = oiData.value.map(d => num(d.oi))

  const posNow = lastValue(pos)
  const posSlope = slopePct(pos, 24)
  const acctSlope = slopePct(acct, 24)
  const takerNow = avg(imbalance.slice(-12))
  const fundingNow = lastValue(funding)
  const oiSlope = slopePct(oi, 24)
  const gapNow = pos.length && acct.length ? posNow - lastValue(acct) : 0

  const divergence = buildDivergenceReading(posSlope, oiSlope, takerNow)
  const smc = buildSmcReading(posNow, posSlope, takerNow, oiSlope, fundingNow)
  const crowd = buildCrowdReading(gapNow, fundingNow, acctSlope)
  const action = buildActionReading(divergence, smc, takerNow, oiSlope)

  return [divergence, smc, crowd, action]
})

function buildDivergenceReading(posSlope, oiSlope, takerNow) {
  if (oiSlope > 1.2 && posSlope < -0.8 && takerNow < -2) {
    return {
      title: '背离结构',
      signal: '顶背离',
      tone: 'bearish',
      desc: `OI 扩张 ${oiSlope.toFixed(1)}%，但大户多空比转弱，主动卖出占优。`
    }
  }
  if (oiSlope < -1.2 && posSlope > 0.8 && takerNow > 2) {
    return {
      title: '背离结构',
      signal: '底背离',
      tone: 'bullish',
      desc: `OI 收缩 ${oiSlope.toFixed(1)}%，但大户多空比回升，主动买入恢复。`
    }
  }
  if (posSlope > 1 && takerNow < -2) {
    return {
      title: '背离结构',
      signal: '拉升分歧',
      tone: 'warning',
      desc: '大户持仓偏多，但主动成交没有跟随，追多质量一般。'
    }
  }
  return {
    title: '背离结构',
    signal: '未见明显背离',
    tone: 'neutral',
    desc: '大户多空、主动成交与 OI 当前没有形成强烈反向信号。'
  }
}

function buildSmcReading(posNow, posSlope, takerNow, oiSlope, fundingNow) {
  if (posNow < 0.95 && posSlope < -0.5 && takerNow < -3 && oiSlope >= 0) {
    return {
      title: 'SMC 判断',
      signal: 'SMC 看空',
      tone: 'bearish',
      desc: '大户偏空，主动卖出增强，OI 没有同步萎缩，空头控盘更明显。'
    }
  }
  if (posNow > 1.05 && posSlope > 0.5 && takerNow > 3 && oiSlope >= 0) {
    return {
      title: 'SMC 判断',
      signal: 'SMC 看多',
      tone: 'bullish',
      desc: '大户偏多，主动买入增强，OI 扩张，趋势延续质量较好。'
    }
  }
  if (Math.abs(fundingNow) > 0.08) {
    return {
      title: 'SMC 判断',
      signal: '拥挤风险',
      tone: 'warning',
      desc: `资金费率 ${fundingNow.toFixed(4)}%，方向过热时容易出现清算回撤。`
    }
  }
  return {
    title: 'SMC 判断',
    signal: '结构中性',
    tone: 'neutral',
    desc: '资金、持仓和账户情绪没有同时指向单边控盘。'
  }
}

function buildCrowdReading(gapNow, fundingNow, acctSlope) {
  if (gapNow > 0.25 && fundingNow < 0.04) {
    return {
      title: '大户/散户',
      signal: '大户吸筹',
      tone: 'bullish',
      desc: `大户相对账户多空比高 ${gapNow.toFixed(2)}，费率尚未明显拥挤。`
    }
  }
  if (gapNow < -0.25 && fundingNow > -0.04) {
    return {
      title: '大户/散户',
      signal: '大户派发',
      tone: 'bearish',
      desc: `大户相对账户多空比低 ${Math.abs(gapNow).toFixed(2)}，需要警惕上方抛压。`
    }
  }
  if (acctSlope > 1 && gapNow < 0) {
    return {
      title: '大户/散户',
      signal: '散户追多',
      tone: 'warning',
      desc: '账户多空比上升快于大户，可能是散户情绪推升。'
    }
  }
  return {
    title: '大户/散户',
    signal: '情绪同步',
    tone: 'neutral',
    desc: '大户与账户情绪差距不大，暂未出现明显筹码分化。'
  }
}

function buildActionReading(divergence, smc, takerNow, oiSlope) {
  if (divergence.signal === '顶背离' || smc.signal === 'SMC 看空') {
    return {
      title: '操作倾向',
      signal: '偏空防守',
      tone: 'bearish',
      desc: '优先观察反弹承压和主动卖出延续，避免在高位追多。'
    }
  }
  if (divergence.signal === '底背离' || smc.signal === 'SMC 看多') {
    return {
      title: '操作倾向',
      signal: '偏多跟随',
      tone: 'bullish',
      desc: '优先观察回踩承接和主动买入延续，趋势未破前偏顺势。'
    }
  }
  if (Math.abs(takerNow) < 2 && Math.abs(oiSlope) < 1) {
    return {
      title: '操作倾向',
      signal: '震荡观望',
      tone: 'neutral',
      desc: '主动成交和 OI 都不强，等待新的放量方向。'
    }
  }
  return {
    title: '操作倾向',
    signal: '轻仓试探',
    tone: 'warning',
    desc: '信号未完全共振，仓位和止损要更保守。'
  }
}

function posSignal(v) {
  if (v == null) return { label: '大户持仓信号', value: '暂无数据', color: '#8b949e', desc: '等待数据加载' }
  if (v > 1.5) return { label: '大户持仓信号', value: '强势偏多 ↑↑', color: '#3fb950', desc: `多空比 ${v.toFixed(3)}` }
  if (v > 1.1) return { label: '大户持仓信号', value: '小幅偏多 ↑', color: '#58a6ff', desc: `多空比 ${v.toFixed(3)}` }
  if (v < 0.67) return { label: '大户持仓信号', value: '强势偏空 ↓↓', color: '#f85149', desc: `多空比 ${v.toFixed(3)}` }
  if (v < 0.9) return { label: '大户持仓信号', value: '小幅偏空 ↓', color: '#f0883e', desc: `多空比 ${v.toFixed(3)}` }
  return { label: '大户持仓信号', value: '多空中性', color: '#8b949e', desc: `多空比 ${v.toFixed(3)}` }
}

function takerSignal(v) {
  if (v == null) return { label: '主动资金流向', value: '暂无数据', color: '#8b949e', desc: '等待数据加载' }
  if (v > 0.6) return { label: '主动资金流向', value: '强势净流入 ↑↑', color: '#3fb950', desc: `买入占比 ${(v * 100).toFixed(1)}%` }
  if (v > 0.53) return { label: '主动资金流向', value: '小幅净流入 ↑', color: '#58a6ff', desc: `买入占比 ${(v * 100).toFixed(1)}%` }
  if (v < 0.4) return { label: '主动资金流向', value: '强势净流出 ↓↓', color: '#f85149', desc: `买入占比 ${(v * 100).toFixed(1)}%` }
  if (v < 0.47) return { label: '主动资金流向', value: '小幅净流出 ↓', color: '#f0883e', desc: `买入占比 ${(v * 100).toFixed(1)}%` }
  return { label: '主动资金流向', value: '多空均衡', color: '#8b949e', desc: `买入占比 ${(v * 100).toFixed(1)}%` }
}

function fundingSignal(v) {
  if (v == null) return { label: '资金费率信号', value: '暂无数据', color: '#8b949e', desc: '等待数据加载' }
  if (v > 0.001) return { label: '资金费率信号', value: '费率极高', color: '#f85149', desc: `${(v * 100).toFixed(4)}% 多头拥挤` }
  if (v > 0.0003) return { label: '资金费率信号', value: '多头偏高', color: '#f0883e', desc: `${(v * 100).toFixed(4)}%` }
  if (v < -0.001) return { label: '资金费率信号', value: '费率极低', color: '#3fb950', desc: `${(v * 100).toFixed(4)}% 空头拥挤` }
  if (v < -0.0003) return { label: '资金费率信号', value: '空头偏高', color: '#58a6ff', desc: `${(v * 100).toFixed(4)}%` }
  return { label: '资金费率信号', value: '费率正常', color: '#8b949e', desc: `${(v * 100).toFixed(4)}%` }
}

function oiSignal(v) {
  if (v == null) return { label: '持仓量趋势', value: '暂无数据', color: '#8b949e', desc: '等待数据加载' }
  if (v > 8) return { label: '持仓量趋势', value: '快速扩张', color: '#3fb950', desc: `近期 OI +${v.toFixed(1)}%` }
  if (v > 3) return { label: '持仓量趋势', value: '小幅扩张', color: '#58a6ff', desc: `近期 OI +${v.toFixed(1)}%` }
  if (v < -8) return { label: '持仓量趋势', value: '快速收缩', color: '#f85149', desc: `近期 OI ${v.toFixed(1)}%` }
  if (v < -3) return { label: '持仓量趋势', value: '小幅收缩', color: '#f0883e', desc: `近期 OI ${v.toFixed(1)}%` }
  return { label: '持仓量趋势', value: '持仓稳定', color: '#8b949e', desc: `近期 OI ${v.toFixed(1)}%` }
}

function renderAll() {
  renderPosRatio()
  renderAcctRatio()
  renderTaker()
  renderTakerCum()
  renderFunding()
  renderOI()
  renderRatioGap()
  renderTakerImbalance()
  renderFundingTrend()
  renderOIDelta()
}

function renderPosRatio() {
  const c = ic('posRatio', posRatioRef)
  const arr = posRatioData.value
  const times = arr.map(d => fmtTs(d.ts))
  const ratios = arr.map(d => round(num(d.lsRatio), 4))
  c.setOption(lineRatioOption(times, ratios, '#58a6ff', '大户多空比'))
}

function renderAcctRatio() {
  const c = ic('acctRatio', acctRatioRef)
  const arr = acctRatioData.value
  const times = arr.map(d => fmtTs(d.ts))
  const ratios = arr.map(d => round(num(d.lsRatio), 4))
  c.setOption(lineRatioOption(times, ratios, '#3fb950', '账户多空比'))
}

function renderTaker() {
  const c = ic('taker', takerRef)
  const arr = takerData.value
  const times = arr.map(d => fmtTs(d.ts))
  const buyVols = arr.map(d => round(num(d.buyVol), 2))
  const sellVols = arr.map(d => -Math.abs(round(num(d.sellVol), 2)))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>主动买入: ${p[0].value}<br/>主动卖出: ${Math.abs(p[1].value)}` },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 60, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '主动买入', type: 'bar', data: buyVols, stack: 'vol', barMaxWidth: 20, itemStyle: { color: '#3fb950' } },
      { name: '主动卖出', type: 'bar', data: sellVols, stack: 'vol', barMaxWidth: 20, itemStyle: { color: '#f85149' } },
    ]
  })
}

function renderTakerCum() {
  const c = ic('takerCum', takerCumRef)
  const arr = takerData.value
  const times = arr.map(d => fmtTs(d.ts))
  let cum = 0
  const cumData = arr.map(d => {
    cum += num(d.buyVol) - num(d.sellVol)
    return round(cum, 2)
  })
  const isPos = cumData.length ? cumData[cumData.length - 1] >= 0 : true
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>累计净差: <b>${p[0].value}</b>` },
    grid: { left: 75, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: cumData,
      smooth: false,
      symbol: 'none',
      lineStyle: { color: isPos ? '#3fb950' : '#f85149', width: 2 },
      areaStyle: { color: isPos ? 'rgba(63,185,80,0.18)' : 'rgba(248,81,73,0.18)' }
    }]
  })
}

function renderFunding() {
  const c = ic('funding', fundingRef)
  const arr = fundingData.value
  const times = arr.map(d => fmtTs(d.fundingTime))
  const rates = arr.map(d => round(num(d.fundingRate) * 100, 5))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>资金费率: <b>${p[0].value}%</b>` },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: rates,
      barMaxWidth: 16,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [3, 3, 0, 0] }
    }]
  })
}

function renderOI() {
  const c = ic('oi', oiRef)
  const arr = oiData.value
  const times = arr.map(d => fmtTs(d.ts))
  const vals = arr.map(d => round(num(d.oi), 0))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>持仓量: <b>${(p[0].value / 1000).toFixed(1)}K</b>` },
    grid: { left: 70, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${(v / 1000).toFixed(0)}K` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: vals,
      smooth: false,
      symbol: 'none',
      lineStyle: { color: '#f0883e', width: 2 },
      areaStyle: { color: 'rgba(240,136,62,0.18)' }
    }]
  })
}

function renderRatioGap() {
  const c = ic('ratioGap', ratioGapRef)
  const n = Math.min(posRatioData.value.length, acctRatioData.value.length)
  const times = Array.from({ length: n }, (_, i) => fmtTs(posRatioData.value[i].ts))
  const gaps = Array.from({ length: n }, (_, i) => round(num(posRatioData.value[i].lsRatio) - num(acctRatioData.value[i].lsRatio), 4))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>背离值: <b>${p[0].value}</b>` },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v.toFixed(2) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: gaps,
      smooth: false,
      symbol: 'none',
      lineStyle: { color: '#a371f7', width: 2 },
      markLine: { silent: true, data: [{ yAxis: 0, lineStyle: { color: '#8b949e', type: 'dashed' } }], label: { formatter: '无背离', color: '#8b949e' } },
      areaStyle: { color: 'rgba(163,113,247,0.14)' }
    }]
  })
}

function renderTakerImbalance() {
  const c = ic('takerImbalance', takerImbalanceRef)
  const arr = takerData.value
  const times = arr.map(d => fmtTs(d.ts))
  const values = arr.map(d => {
    const buy = num(d.buyVol)
    const sell = num(d.sellVol)
    return buy + sell > 0 ? round((buy - sell) / (buy + sell) * 100, 2) : 0
  })
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>失衡率: <b>${p[0].value}%</b>` },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 16,
      itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149', borderRadius: [3, 3, 0, 0] },
      markLine: { silent: true, data: [{ yAxis: 0, lineStyle: { color: '#8b949e', type: 'dashed' } }], label: { show: false } }
    }]
  })
}

function renderFundingTrend() {
  const c = ic('fundingTrend', fundingTrendRef)
  const arr = fundingData.value
  const times = arr.map(d => fmtTs(d.fundingTime))
  const rates = arr.map(d => round(num(d.fundingRate) * 100, 5))
  const ma = rollingAverage(rates, 8)
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '原始费率', type: 'bar', data: rates, barMaxWidth: 14, itemStyle: { color: p => p.value >= 0 ? 'rgba(63,185,80,0.55)' : 'rgba(248,81,73,0.55)' } },
      { name: '滚动均值', type: 'line', data: ma, smooth: false, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
    ]
  })
}

function renderOIDelta() {
  const c = ic('oiDelta', oiDeltaRef)
  const arr = oiData.value
  const times = arr.map(d => fmtTs(d.ts))
  const values = arr.map((d, i) => {
    if (i === 0) return 0
    const prev = num(arr[i - 1].oi)
    return prev ? round((num(d.oi) - prev) / Math.abs(prev) * 100, 3) : 0
  })
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>OI 变化: <b>${p[0].value}%</b>` },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 16,
      itemStyle: { color: p => p.value >= 0 ? '#58a6ff' : '#f0883e', borderRadius: [3, 3, 0, 0] },
      markLine: { silent: true, data: [{ yAxis: 0, lineStyle: { color: '#8b949e', type: 'dashed' } }], label: { show: false } }
    }]
  })
}

function lineRatioOption(times, values, color, name) {
  return {
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${name}: <b>${p[0].value}</b>` },
    grid: { left: 65, right: 20, top: 35, bottom: 48 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => v.toFixed(2) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'line',
      data: values,
      smooth: false,
      symbol: 'circle',
      showSymbol: false,
      lineStyle: { color, width: 2 },
      areaStyle: { color: colorAlpha(color, 0.16) },
      markLine: {
        silent: true,
        data: [{ yAxis: 1, lineStyle: { color: '#f0883e', type: 'dashed' } }],
        label: { formatter: '多空平衡线 1.0', color: '#f0883e', fontSize: 10 }
      }
    }]
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [pos, acct, taker, fund, oi] = await Promise.all([
      fetchOkx('/api/v5/rubik/stat/contracts/long-short-position-ratio-contract-top-trader', { instId: instId.value, period: period.value, limit: 200 }),
      fetchOkx('/api/v5/rubik/stat/contracts/long-short-account-ratio-contract', { instId: instId.value, period: period.value, limit: 200 }),
      fetchOkx('/api/v5/rubik/stat/taker-volume-contract', { instId: instId.value, period: period.value, limit: 200 }),
      fetchOkx('/api/v5/public/funding-rate-history', { instId: instId.value, limit: 100 }),
      fetchOkx('/api/v5/rubik/stat/contracts/open-interest-history', { instId: instId.value, period: period.value, limit: 200 }),
    ])
    posRatioData.value = pos.map(d => ({ ts: d[0], lsRatio: num(d[1]) }))
    acctRatioData.value = acct.map(d => ({ ts: d[0], lsRatio: num(d[1]) }))
    takerData.value = taker.map(d => ({ ts: d[0], sellVol: num(d[1]), buyVol: num(d[2]) }))
    fundingData.value = fund.map(d => ({ ...d, fundingRate: num(d.fundingRate), fundingTime: d.fundingTime }))
    oiData.value = oi.map(d => ({ ts: d[0], oi: num(d[1]) }))
    lastUpdate.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

async function loadMoneyFlow() {
  flowLoading.value = true
  try {
    const entries = await Promise.all(FLOW_SYMBOLS.map(async symbol => {
      const rows = await Promise.all(FLOW_PERIODS.map(async p => {
        const [taker, pos, acct] = await Promise.all([
          fetchOkx('/api/v5/rubik/stat/taker-volume-contract', { instId: symbol, period: p.source || p.value, limit: p.aggregate ? p.aggregate + 10 : 30 }),
          fetchOkx('/api/v5/rubik/stat/contracts/long-short-position-ratio-contract-top-trader', { instId: symbol, period: p.source || p.value, limit: p.aggregate ? p.aggregate + 10 : 30 }),
          fetchOkx('/api/v5/rubik/stat/contracts/long-short-account-ratio-contract', { instId: symbol, period: p.source || p.value, limit: p.aggregate ? p.aggregate + 10 : 30 }),
        ])
        return buildFlowRow(symbol, p, taker, pos, acct)
      }))
      return [symbol, rows]
    }))
    moneyFlowRows.value = Object.fromEntries(entries)
    flowLastUpdate.value = dayjs().format('HH:mm:ss')
    await nextTick()
    renderFlowCharts()
  } finally {
    flowLoading.value = false
  }
}

function buildFlowRow(symbol, periodItem, taker, pos, acct) {
  const sampleSize = periodItem.aggregate || Math.min(12, taker.length)
  const slice = taker.slice(-Math.min(sampleSize, taker.length))
  const buyVol = slice.reduce((sum, d) => sum + num(d[2]), 0)
  const sellVol = slice.reduce((sum, d) => sum + num(d[1]), 0)
  const totalIn = buyVol
  const totalOut = sellVol
  const posMomentum = ratioMomentum(pos, periodItem.aggregate)
  const acctMomentum = ratioMomentum(acct, periodItem.aggregate)
  const whaleWeight = clamp(0.5 + (posMomentum - acctMomentum) * 0.18, 0.28, 0.72)
  const retailWeight = 1 - whaleWeight
  return {
    symbol,
    period: periodItem.value,
    label: periodItem.label,
    whaleIn: round(totalIn * whaleWeight, 2),
    whaleOut: round(totalOut * whaleWeight, 2),
    retailIn: round(totalIn * retailWeight, 2),
    retailOut: round(totalOut * retailWeight, 2),
    totalIn: round(totalIn, 2),
    totalOut: round(totalOut, 2),
    net: round(totalIn - totalOut, 2),
    whaleWeight,
  }
}

function ratioMomentum(rows, sampleSize) {
  if (!rows.length) return 0
  const latest = num(last(rows)?.[1])
  const lookback = sampleSize || 6
  const prev = num(rows[Math.max(0, rows.length - lookback)]?.[1])
  if (!prev) return 0
  return (latest - prev) / Math.abs(prev)
}

function renderFlowCharts() {
  renderFlowChart('flowBtc', flowBtcRef, 'BTC-USDT-SWAP')
  renderFlowChart('flowEth', flowEthRef, 'ETH-USDT-SWAP')
}

function renderFlowChart(key, elRef, symbol) {
  const c = ic(key, elRef)
  const rows = moneyFlowRows.value[symbol] || []
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 70, right: 20, top: 35, bottom: 42 },
    xAxis: { type: 'category', data: rows.map(r => r.label), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => compactMoney(v) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '主力流入', type: 'bar', stack: 'in', data: rows.map(r => r.whaleIn), itemStyle: { color: '#238636' } },
      { name: '散户流入', type: 'bar', stack: 'in', data: rows.map(r => r.retailIn), itemStyle: { color: '#3fb950' } },
      { name: '主力流出', type: 'bar', stack: 'out', data: rows.map(r => -r.whaleOut), itemStyle: { color: '#da3633' } },
      { name: '散户流出', type: 'bar', stack: 'out', data: rows.map(r => -r.retailOut), itemStyle: { color: '#f85149' } },
      { name: '净流入', type: 'line', data: rows.map(r => r.net), symbol: 'circle', lineStyle: { color: '#58a6ff', width: 2 } },
    ]
  })
}

function flowSnapshot(symbol) {
  const row = (moneyFlowRows.value[symbol] || []).find(r => r.period === flowFocusPeriod.value)
  if (!row) {
    return [
      { label: '主力流入', value: '--', sub: '等待数据', color: '#3fb950' },
      { label: '主力流出', value: '--', sub: '等待数据', color: '#f85149' },
      { label: '散户流入', value: '--', sub: '等待数据', color: '#3fb950' },
      { label: '散户流出', value: '--', sub: '等待数据', color: '#f85149' },
      { label: '总流入', value: '--', sub: '等待数据', color: '#58a6ff' },
      { label: '总流出', value: '--', sub: '等待数据', color: '#f0883e' },
    ]
  }
  return [
    { label: '主力流入', value: fmtMoney(row.whaleIn), sub: `权重 ${(row.whaleWeight * 100).toFixed(0)}%`, color: '#3fb950' },
    { label: '主力流出', value: fmtMoney(row.whaleOut), sub: '主动卖出估算', color: '#f85149' },
    { label: '散户流入', value: fmtMoney(row.retailIn), sub: '账户差值估算', color: '#3fb950' },
    { label: '散户流出', value: fmtMoney(row.retailOut), sub: '账户差值估算', color: '#f85149' },
    { label: '总流入', value: fmtMoney(row.totalIn), sub: `净额 ${fmtMoney(row.net)}`, color: '#58a6ff' },
    { label: '总流出', value: fmtMoney(row.totalOut), sub: periodLabel(row.period), color: '#f0883e' },
  ]
}

function onMarketTabChange(name) {
  nextTick(() => {
    if (name === 'flow') {
      if (!moneyFlowRows.value['BTC-USDT-SWAP']?.length) loadMoneyFlow()
      else renderFlowCharts()
    } else {
      renderAll()
    }
  })
}

function fmtTs(ts) { return dayjs(+ts).format('MM-DD HH:mm') }
function num(v) { return Number.parseFloat(v) || 0 }
function round(v, digits = 2) { return +Number(v || 0).toFixed(digits) }
function last(arr) { return arr.length ? arr[arr.length - 1] : null }
function lastValue(values) { return values.length ? values[values.length - 1] : 0 }
function avg(values) { return values.length ? values.reduce((sum, v) => sum + v, 0) / values.length : 0 }
function slopePct(values, windowSize) {
  if (values.length < 2) return 0
  const slice = values.slice(-Math.min(windowSize, values.length))
  const first = slice[0]
  const latest = slice[slice.length - 1]
  if (!first) return latest - first
  return (latest - first) / Math.abs(first) * 100
}
function colorAlpha(color, alpha) {
  const hex = color.replace('#', '')
  const r = Number.parseInt(hex.slice(0, 2), 16)
  const g = Number.parseInt(hex.slice(2, 4), 16)
  const b = Number.parseInt(hex.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${alpha})`
}
function rollingAverage(values, windowSize) {
  return values.map((_, i) => {
    const start = Math.max(0, i - windowSize + 1)
    const slice = values.slice(start, i + 1)
    return round(slice.reduce((sum, v) => sum + v, 0) / slice.length, 5)
  })
}
function ic(key, elRef) {
  if (!charts[key] && elRef.value) charts[key] = initChart(elRef.value)
  return charts[key]
}
function clamp(value, min, max) { return Math.min(max, Math.max(min, value)) }
function symbolLabel(symbol) { return symbol.startsWith('BTC') ? 'BTC' : symbol.startsWith('ETH') ? 'ETH' : symbol }
function periodLabel(value) { return FLOW_PERIODS.find(p => p.value === value)?.label || value }
function compactMoney(v) {
  const abs = Math.abs(Number(v || 0))
  const sign = Number(v || 0) < 0 ? '-' : ''
  if (abs >= 1e9) return `${sign}${(abs / 1e9).toFixed(2)}B`
  if (abs >= 1e6) return `${sign}${(abs / 1e6).toFixed(2)}M`
  if (abs >= 1e3) return `${sign}${(abs / 1e3).toFixed(1)}K`
  return `${sign}${abs.toFixed(0)}`
}
function fmtMoney(v) { return compactMoney(v) }

watch(isDark, async () => {
  Object.values(charts).forEach(c => c?.dispose())
  charts = {}
  await nextTick()
  activeTab.value === 'flow' ? renderFlowCharts() : renderAll()
})

function onResize() { Object.values(charts).forEach(c => c?.resize()) }

onMounted(() => { loadAll(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c?.dispose()) })
</script>

<style scoped>
.filter-card { margin-bottom: 16px; padding: 12px 20px; }
.market-tabs :deep(.el-tabs__item) { color: var(--text-secondary); }
.market-tabs :deep(.el-tabs__item.is-active) { color: #58a6ff; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.filter-item .text-muted { font-size: 13px; white-space: nowrap; }
.last-update { font-size: 12px; }
.signal-card { text-align: center; min-height: 104px; }
.summary-card { padding-bottom: 16px; }
.reading-item {
  min-height: 116px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 14px;
  background: var(--bg-main);
}
.reading-title { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.reading-signal { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
.reading-desc { font-size: 12px; color: var(--text-dim); line-height: 1.55; }
.reading-item.bullish .reading-signal { color: #3fb950; }
.reading-item.bearish .reading-signal { color: #f85149; }
.reading-item.warning .reading-signal { color: #f0883e; }
.reading-item.neutral .reading-signal { color: var(--text-secondary); }
.sig-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.sig-value { font-size: 18px; font-weight: 700; margin-bottom: 4px; }
.sig-desc { font-size: 11px; color: var(--text-dim); }
.hint { font-size: 11px; color: var(--text-dim); font-weight: 400; margin-left: 6px; }
.flow-note { color: var(--text-secondary); font-size: 13px; }
.flow-summary { min-height: 210px; }
.flow-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.flow-metric {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--bg-main);
}
.flow-metric span { display: block; color: var(--text-secondary); font-size: 12px; margin-bottom: 6px; }
.flow-metric b { display: block; font-size: 18px; margin-bottom: 4px; }
.flow-metric em { color: var(--text-dim); font-size: 11px; font-style: normal; }
.profit { color: #3fb950; }
.loss { color: #f85149; }
</style>

