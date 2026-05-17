<template>
  <div class="orders-page">
    <div class="orders-head">
      <div>
        <div class="section-title">加密交易详情</div>
        <div class="section-sub">展示已同步的交易核心字段，展开行可查看原始订单字段</div>
      </div>
      <div class="orders-total">共 {{ total }} 条记录</div>
    </div>

    <div class="chart-card filter-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="5">
          <el-select v-model="filter.instId" placeholder="全部品种" clearable @change="load">
            <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="filter.side" placeholder="全部仓位方向" clearable @change="load">
            <el-option label="做多" value="long" />
            <el-option label="做空" value="short" />
            <el-option label="净持仓" value="net" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="filter.state" placeholder="全部状态" clearable @change="load">
            <el-option label="已成交" value="filled" />
            <el-option label="已撤销" value="canceled" />
            <el-option label="未成交" value="live" />
            <el-option label="部分成交" value="partially_filled" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <div class="filter-actions">
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </div>
        </el-col>
        <el-col :span="5" style="text-align:right;font-size:12px" class="text-muted">
          核心字段 + 派生风险指标
        </el-col>
      </el-row>
    </div>

    <div class="chart-card table-card">
      <el-table
        :data="orders"
        stripe
        style="width:100%;background:transparent"
        :header-cell-style="tableHeader"
        :cell-style="tableCell"
        row-key="ordId"
        show-overflow-tooltip
      >
        <el-table-column type="expand" width="46" fixed="left">
          <template #default="{ row }">
            <div class="detail-expand">
              <div v-for="group in detailGroups(row)" :key="group.title" class="detail-group">
                <div class="detail-title">{{ group.title }}</div>
                <div class="detail-grid">
                  <div v-for="item in group.items" :key="item.label" class="detail-item">
                    <span>{{ item.label }}</span>
                    <strong :class="item.class">{{ item.value }}</strong>
                  </div>
                </div>
              </div>

              <div v-if="rawPairs(row).length" class="detail-group raw-group">
                <div class="detail-title">原始订单字段</div>
                <div class="raw-grid">
                  <div v-for="item in rawPairs(row)" :key="item.key" class="raw-item">
                    <span>{{ item.key }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </div>
              <div v-else class="raw-empty">
                历史数据未保存原始 JSON；重新同步后的新订单会展示原始订单字段。
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="160" fixed="left" :formatter="r => fmtDt(r.createTime)" />
        <el-table-column prop="instId" label="品种" width="150" fixed="left" />
        <el-table-column prop="posSide" label="仓位方向" width="92" fixed="left">
          <template #default="{ row }">
            <el-tag :type="sideTag(row.posSide)" size="small">{{ posSideText(row.posSide) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="side" label="买卖方向" width="92">
          <template #default="{ row }">
            <el-tag :type="row.side === 'buy' ? 'success' : 'danger'" size="small" effect="plain">
              {{ sideText(row.side) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ordType" label="订单类型" width="112" />
        <el-table-column prop="state" label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="stateTag(row.state)" size="small">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pnl" label="已实现盈亏" width="126" align="right">
          <template #default="{ row }">
            <span :class="pnlClass(row.pnl)">{{ money(row.pnl, 2, true) }} U</span>
          </template>
        </el-table-column>
        <el-table-column prop="fee" label="手续费" width="112" align="right">
          <template #default="{ row }">
            <span class="fee-text">{{ money(row.fee, 4, true) }} U</span>
          </template>
        </el-table-column>
        <el-table-column label="净盈亏" width="120" align="right">
          <template #default="{ row }">
            <span :class="pnlClass(netPnl(row))">{{ money(netPnl(row), 2, true) }} U</span>
          </template>
        </el-table-column>
        <el-table-column prop="sz" label="委托数量" width="118" align="right" :formatter="r => num(r.sz)" />
        <el-table-column prop="fillSz" label="成交数量" width="118" align="right" :formatter="r => num(r.fillSz)" />
        <el-table-column prop="px" label="委托价" width="118" align="right" :formatter="r => num(r.px)" />
        <el-table-column prop="avgPx" label="成交均价" width="126" align="right" :formatter="r => num(r.avgPx)" />
        <el-table-column label="名义成交额" width="130" align="right">
          <template #default="{ row }">{{ money(turnover(row), 2) }} U</template>
        </el-table-column>
        <el-table-column prop="lever" label="杠杆" width="82" align="right" :formatter="r => fmtLever(r.lever)" />
        <el-table-column prop="instType" label="产品类型" width="100" />
        <el-table-column prop="holdingMinutes" label="持仓时长" width="110" :formatter="r => fmtMin(r.holdingMinutes)" />
        <el-table-column prop="isWin" label="交易结果" width="92">
          <template #default="{ row }">
            <el-tag v-if="row.isWin === 1" type="success" size="small">盈利</el-tag>
            <el-tag v-else-if="row.isWin === 0" type="danger" size="small">亏损</el-tag>
            <span v-else class="text-dim">开仓</span>
          </template>
        </el-table-column>
        <el-table-column prop="isLiquidation" label="强平标记" width="96">
          <template #default="{ row }">
            <el-tag v-if="row.isLiquidation === 1" type="danger" size="small">强平</el-tag>
            <span v-else class="text-dim">普通</span>
          </template>
        </el-table-column>
        <el-table-column prop="ordId" label="订单ID" width="220" />
        <el-table-column prop="id" label="本地ID" width="86" align="right" />
        <el-table-column prop="updateTime" label="更新时间" width="160" :formatter="r => fmtDt(r.updateTime)" />
        <el-table-column prop="syncedAt" label="同步时间" width="160" :formatter="r => fmtDt(r.syncedAt)" />
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100, 200]"
        layout="sizes, prev, pager, next, jumper"
        style="margin-top:16px;justify-content:flex-end;display:flex"
        @change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getOrders, getSymbols } from '@/api'
import { useTheme } from '@/composables/useTheme'
import dayjs from 'dayjs'

const { isDark } = useTheme()

const orders = ref([])
const symbols = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filter = ref({ instId: null, side: null, state: null })

const tableHeader = computed(() => ({
  background: isDark.value ? '#161b22' : '#f6f8fa',
  color: isDark.value ? '#8b949e' : '#57606a',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))

const tableCell = computed(() => ({
  background: isDark.value ? '#161b22' : '#ffffff',
  color: isDark.value ? '#c9d1d9' : '#24292f',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))

async function load() {
  const res = await getOrders({ page: page.value - 1, size: size.value, ...filter.value })
  orders.value = res.list
  total.value = res.total
}

function reset() {
  filter.value = { instId: null, side: null, state: null }
  page.value = 1
  load()
}

const fmtDt = v => v ? dayjs(v).format('MM-DD HH:mm:ss') : '-'
const fmtFullDt = v => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'
const fmtMin = v => !v ? '-' : v < 60 ? `${v}min` : `${(v / 60).toFixed(1)}h`
const fmtLever = v => v ? `${v}x` : '-'
const netPnl = row => Number(row?.pnl || 0) + Number(row?.fee || 0)
const turnover = row => Number(row?.avgPx || row?.px || 0) * Number(row?.fillSz || row?.sz || 0)

function num(v, digits = 8) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return n.toLocaleString('en-US', { maximumFractionDigits: digits })
}

function money(v, digits = 2, sign = false) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  const s = n.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  return sign && n > 0 ? `+${s}` : s
}

const pnlClass = v => Number(v) > 0 ? 'pnl-up' : Number(v) < 0 ? 'pnl-down' : 'text-dim'
const sideTag = v => v === 'long' ? 'success' : v === 'short' ? 'danger' : 'info'
const posSideText = v => ({ long: '做多', short: '做空', net: '净持仓' }[v] || v || '-')
const sideText = v => ({ buy: '买入', sell: '卖出' }[v] || v || '-')
const stateText = v => ({ filled: '已成交', canceled: '已撤销', live: '未成交', partially_filled: '部分成交' }[v] || v || '-')
const stateTag = v => v === 'filled' ? 'success' : v === 'canceled' ? 'warning' : 'info'

const rawLabel = {
  accFillSz: '累计成交数量',
  avgPx: '成交均价',
  cTime: '创建时间戳',
  category: '订单种类',
  fee: '手续费',
  feeCcy: '手续费币种',
  fillPx: '最新成交价',
  fillSz: '最新成交数量',
  fillTime: '最新成交时间',
  instId: '产品',
  instType: '产品类型',
  lever: '杠杆',
  ordId: '订单ID',
  ordType: '订单类型',
  pnl: '收益',
  posSide: '持仓方向',
  px: '委托价格',
  rebate: '返佣',
  rebateCcy: '返佣币种',
  reduceOnly: '只减仓',
  side: '买卖方向',
  slOrdPx: '止损委托价',
  slTriggerPx: '止损触发价',
  state: '订单状态',
  sz: '委托数量',
  tag: '订单标签',
  tdMode: '交易模式',
  tgtCcy: '市价单单位',
  tpOrdPx: '止盈委托价',
  tpTriggerPx: '止盈触发价',
  tradeId: '成交ID',
  uTime: '更新时间戳',
}

function safeRaw(row) {
  if (!row?.rawData) return null
  try {
    return JSON.parse(row.rawData)
  } catch {
    return null
  }
}

function rawPairs(row) {
  const raw = safeRaw(row)
  if (!raw || typeof raw !== 'object') return []
  return Object.entries(raw)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      key: rawLabel[key] ? `${rawLabel[key]} (${key})` : key,
      value: typeof value === 'object' ? JSON.stringify(value) : String(value),
    }))
}

function feeRatio(row) {
  const pnl = Math.abs(Number(row?.pnl || 0))
  const fee = Math.abs(Number(row?.fee || 0))
  return pnl > 0 ? `${((fee / pnl) * 100).toFixed(2)}%` : '-'
}

function detailGroups(row) {
  return [
    {
      title: '基础字段',
      items: [
        { label: '本地ID', value: row.id ?? '-' },
        { label: '订单ID', value: row.ordId || '-' },
        { label: '产品', value: row.instId || '-' },
        { label: '产品类型', value: row.instType || '-' },
        { label: '订单类型', value: row.ordType || '-' },
        { label: '状态', value: stateText(row.state) },
      ],
    },
    {
      title: '交易规模',
      items: [
        { label: '买卖方向', value: sideText(row.side) },
        { label: '仓位方向', value: posSideText(row.posSide) },
        { label: '杠杆', value: fmtLever(row.lever) },
        { label: '委托数量', value: num(row.sz) },
        { label: '成交数量', value: num(row.fillSz) },
        { label: '名义成交额', value: `${money(turnover(row), 2)} U` },
      ],
    },
    {
      title: '价格与盈亏',
      items: [
        { label: '委托价', value: num(row.px) },
        { label: '成交均价', value: num(row.avgPx) },
        { label: '已实现盈亏', value: `${money(row.pnl, 2, true)} U`, class: pnlClass(row.pnl) },
        { label: '手续费', value: `${money(row.fee, 4, true)} U`, class: 'fee-text' },
        { label: '净盈亏', value: `${money(netPnl(row), 2, true)} U`, class: pnlClass(netPnl(row)) },
        { label: '结果', value: row.isWin === 1 ? '盈利' : row.isWin === 0 ? '亏损' : '开仓/未结算' },
      ],
    },
    {
      title: '时间与风控',
      items: [
        { label: '创建时间', value: fmtFullDt(row.createTime) },
        { label: '更新时间', value: fmtFullDt(row.updateTime) },
        { label: '同步时间', value: fmtFullDt(row.syncedAt) },
        { label: '持仓时长', value: fmtMin(row.holdingMinutes) },
        { label: '强平标记', value: row.isLiquidation === 1 ? '强平单' : '普通单' },
        { label: '手续费占盈亏', value: feeRatio(row) },
      ],
    },
  ]
}

onMounted(async () => {
  symbols.value = await getSymbols()
  load()
})
</script>

<style scoped>
.orders-page { animation: pageFadeIn .28s ease both; }

.orders-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--text-heading);
}

.section-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.orders-total {
  padding: 6px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  background: var(--bg-input);
}

.filter-card { margin-bottom: 16px; }
.filter-actions { display: flex; gap: 8px; }
.table-card { padding: 14px; }
.pnl-up { color: #3fb950; font-weight: 700; }
.pnl-down { color: #f85149; font-weight: 700; }
.fee-text { color: #d29922; font-weight: 650; }

.detail-expand {
  padding: 14px 18px 18px;
  background:
    linear-gradient(180deg, rgba(88, 166, 255, .07), transparent 160px),
    var(--bg-input);
  border-radius: 8px;
}

.detail-group { margin-bottom: 16px; }

.detail-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 800;
  color: var(--text-heading);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 8px;
}

.raw-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 8px;
}

.detail-item,
.raw-item {
  min-width: 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
}

.detail-item span,
.raw-item span {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.detail-item strong,
.raw-item strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

.raw-empty {
  padding: 10px 12px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  color: var(--text-dim);
  font-size: 12px;
}

:deep(.el-table__expanded-cell) {
  background: var(--bg-card) !important;
  padding: 0 !important;
}

:deep(.el-table__cell) { font-size: 12px; }
:deep(.el-table th.el-table__cell) { font-weight: 800; }

@media (max-width: 1100px) {
  .detail-grid,
  .raw-grid { grid-template-columns: 1fr; }
}
</style>
