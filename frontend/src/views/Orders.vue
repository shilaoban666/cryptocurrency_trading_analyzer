<template>
  <div>
    <div class="page-title">📋 交易记录</div>

    <!-- 筛选栏 -->
    <div class="chart-card" style="margin-bottom:16px">
      <el-row :gutter="12" align="middle">
        <el-col :span="5">
          <el-select v-model="filter.instId" placeholder="全部品种" clearable @change="load">
            <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="filter.side" placeholder="全部方向" clearable @change="load">
            <el-option label="做多" value="long" />
            <el-option label="做空" value="short" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="filter.state" placeholder="全部状态" clearable @change="load">
            <el-option label="已成交" value="filled" />
            <el-option label="已撤销" value="canceled" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-col>
        <el-col :span="6" style="text-align:right;font-size:13px" class="text-muted">
          共 {{ total }} 条记录
        </el-col>
      </el-row>
    </div>

    <!-- 表格 -->
    <div class="chart-card">
      <el-table :data="orders" stripe style="width:100%;background:transparent"
                :header-cell-style="tableHeader"
                :cell-style="tableCell">
        <el-table-column prop="createTime" label="开单时间" width="160"
                         :formatter="r => fmtDt(r.createTime)" />
        <el-table-column prop="instId"  label="品种"   width="160" />
        <el-table-column prop="posSide" label="方向"   width="80">
          <template #default="{ row }">
            <el-tag :type="row.posSide === 'long' ? 'success' : 'danger'" size="small">
              {{ row.posSide === 'long' ? '做多' : '做空' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lever"    label="杠杆"  width="70" :formatter="r => r.lever + 'x'" />
        <el-table-column prop="sz"       label="数量"  width="100" />
        <el-table-column prop="avgPx"    label="均价"  width="120" />
        <el-table-column prop="pnl"      label="盈亏"  width="120">
          <template #default="{ row }">
            <span :style="{color: row.pnl > 0 ? '#3fb950' : row.pnl < 0 ? '#f85149' : 'var(--text-dim)'}">
              {{ row.pnl > 0 ? '+' : '' }}{{ row.pnl?.toFixed(2) }} U
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="fee" label="手续费" width="100">
          <template #default="{ row }">
            <span style="color:#f0883e">{{ row.fee?.toFixed(4) }} U</span>
          </template>
        </el-table-column>
        <el-table-column prop="holdingMinutes" label="持仓时长" width="100"
                         :formatter="r => fmtMin(r.holdingMinutes)" />
        <el-table-column prop="isWin" label="结果" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isWin === 1" type="success" size="small">盈利</el-tag>
            <el-tag v-else-if="row.isWin === 0" type="danger"  size="small">亏损</el-tag>
            <span v-else class="text-dim">开仓</span>
          </template>
        </el-table-column>
        <el-table-column prop="state" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.state === 'filled' ? 'info' : 'warning'" size="small">
              {{ row.state === 'filled' ? '已成交' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="page" v-model:page-size="size"
                     :total="total" :page-sizes="[20, 50, 100]"
                     layout="sizes, prev, pager, next, jumper"
                     style="margin-top:16px;justify-content:flex-end;display:flex"
                     @change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getOrders, getSymbols } from '@/api'
import { useTheme } from '@/composables/useTheme'
import dayjs from 'dayjs'

const { isDark } = useTheme()

const orders  = ref([])
const symbols = ref([])
const total   = ref(0)
const page    = ref(1)
const size    = ref(20)
const filter  = ref({ instId: null, side: null, state: null })

const tableHeader = computed(() => ({
  background:  isDark.value ? '#161b22' : '#f6f8fa',
  color:       isDark.value ? '#8b949e' : '#57606a',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))
const tableCell = computed(() => ({
  background:  isDark.value ? '#161b22' : '#ffffff',
  color:       isDark.value ? '#c9d1d9' : '#24292f',
  borderColor: isDark.value ? '#21262d' : '#d0d7de',
}))

async function load() {
  const res = await getOrders({ page: page.value - 1, size: size.value, ...filter.value })
  orders.value = res.list
  total.value  = res.total
}

function reset() {
  filter.value = { instId: null, side: null, state: null }
  page.value = 1
  load()
}

const fmtDt  = v => dayjs(v).format('MM-DD HH:mm:ss')
const fmtMin = v => !v ? '-' : v < 60 ? v + 'min' : (v/60).toFixed(1) + 'h'

onMounted(async () => {
  symbols.value = await getSymbols()
  load()
})
</script>
