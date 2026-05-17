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

    <el-tabs v-model="activePositionTab" class="position-tabs" @tab-change="onPositionTabChange">
      <el-tab-pane label="仓位总览" name="overview">
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
      <el-row :gutter="16" class="ai-layout">
        <el-col :span="7" class="ai-left">
          <div ref="aiRef" class="ai-chart" />
        </el-col>
        <el-col :span="17" class="ai-right">
          <div v-if="aiLoading && !aiResult" class="ai-stream">
            <div class="ai-stream-head">
              <span>{{ aiStage || 'DeepSeek 流式分析中' }}</span>
              <el-tag type="info" size="small">SSE</el-tag>
            </div>
            <div class="ai-progress">
              <div class="ai-progress-dot" />
              <div>
                <div class="ai-progress-title">{{ aiCleanPreview || '正在分析仓位、行情和风险结构' }}</div>
                <div class="ai-progress-sub">模型返回内容会先在后台整理，完成后自动切换成可读报告。</div>
              </div>
            </div>
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
        <el-table-column prop="notional" label="名义价值" width="130">
          <template #default="{ row }">{{ fmtMoney(Math.abs(num(row.notional))) }}</template>
        </el-table-column>
        <el-table-column prop="avgPx" label="均价" width="110">
          <template #default="{ row }">{{ fmtPrice(row.avgPx) }}</template>
        </el-table-column>
        <el-table-column prop="markPx" label="现价" width="110">
          <template #default="{ row }">{{ fmtPrice(row.markPx) }}</template>
        </el-table-column>
        <el-table-column prop="liqPx" label="强平价" width="110">
          <template #default="{ row }">{{ fmtPrice(row.liqPx) }}</template>
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
        <el-table-column prop="addStatus" label="仓位状态" width="120" />
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
        <el-table-column label="风险等级" width="120">
          <template #default="{ row }">
            <el-tag :type="positionRiskType(row)" size="small">{{ positionRiskLabel(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

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
      </el-tab-pane>

      <el-tab-pane label="持仓分析" name="analysis">
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持有多久</div>
          <div ref="durationRef" style="height:240px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">浮动盈亏</div>
          <div ref="pnlRef" style="height:240px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="chart-card compact-chart">
          <div class="card-title">风险矩阵</div>
          <div ref="riskMatrixRef" style="height:220px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card compact-chart">
          <div class="card-title">方向敞口</div>
          <div ref="exposureRef" style="height:220px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card compact-chart">
          <div class="card-title">杠杆分布</div>
          <div ref="leverageDistRef" style="height:220px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card compact-chart">
          <div class="card-title">盈亏 / 爆仓距离气泡</div>
          <div ref="pnlLiqBubbleRef" style="height:230px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card compact-chart">
          <div class="card-title">持仓风险热力</div>
          <div ref="positionHeatRef" style="height:230px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">补仓与仓位规模</div>
          <div ref="addRef" style="height:240px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">方向是否正确</div>
          <div ref="alignmentRef" style="height:240px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">是否与市场背离</div>
          <div ref="divergenceRef" style="height:240px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">爆仓距离与杠杆</div>
          <div ref="riskRef" style="height:240px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持仓位置散点</div>
          <div ref="scatterRef" style="height:240px" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="card-title">持仓质量雷达</div>
          <div ref="radarRef" style="height:240px" />
        </div>
      </el-col>
    </el-row>
      </el-tab-pane>

      <el-tab-pane label="仓位管理" name="management">
        <el-row :gutter="12" class="management-card-row">
          <el-col :span="6" v-for="card in managementCards" :key="card.label">
            <div class="stat-card management-stat">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value management-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
          </el-col>
        </el-row>

        <div class="chart-card management-guidance-card">
          <div class="card-title management-title">
            <div>
              <span>仓位管理指导</span>
              <span class="hint">基于当前仓位、杠杆、浮盈亏、补仓、爆仓距离和方向一致性</span>
            </div>
            <el-tag :type="managementRiskType" size="small">{{ managementSummary.riskLevel }}</el-tag>
          </div>
          <div class="management-headline" :style="{ color: managementSummary.color }">
            {{ managementSummary.headline }}
          </div>
          <div class="management-rules">
            <div v-for="rule in managementRules" :key="rule.title" class="management-rule">
              <span>{{ rule.title }}</span>
              <strong>{{ rule.value }}</strong>
              <em>{{ rule.note }}</em>
            </div>
          </div>
        </div>

        <el-row :gutter="16">
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">管理评分</div>
              <div ref="managementScoreRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">仓位占比分布</div>
              <div ref="managementAllocationRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">动作建议分布</div>
              <div ref="managementActionRef" style="height:250px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <div class="chart-card compact-management-card">
              <div class="card-title">杠杆 × 爆仓缓冲</div>
              <div ref="managementBufferRef" style="height:270px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card compact-management-card">
              <div class="card-title">当前占比 vs 目标占比</div>
              <div ref="managementTargetRef" style="height:270px" />
            </div>
          </el-col>
        </el-row>

        <div class="chart-card management-guidance-card">
          <div class="card-title management-title">
            <div>
              <span>成熟交易员仓位管理框架</span>
              <span class="hint">从风险预算、失效位、分批、复利克制和爆仓缓冲反推仓位</span>
            </div>
            <el-tag type="info" size="small">经验规则</el-tag>
          </div>
          <div class="management-playbook">
            <div v-for="item in managementPlaybook" :key="item.title" class="management-playbook-item">
              <span>{{ item.title }}</span>
              <strong>{{ item.rule }}</strong>
              <em>{{ item.why }}</em>
            </div>
          </div>
        </div>

        <el-row :gutter="16">
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">风险预算缺口</div>
              <div ref="managementRiskBudgetRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">纪律扣分来源</div>
              <div ref="managementDisciplineRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-management-card">
              <div class="card-title">成熟规则匹配度</div>
              <div ref="managementExperienceRef" style="height:250px" />
            </div>
          </el-col>
        </el-row>

        <div class="chart-card">
          <div class="card-title">单仓管理计划</div>
          <el-table :data="managementPlanRows" style="width:100%" size="small">
            <el-table-column prop="symbol" label="品种" min-width="100" />
            <el-table-column prop="role" label="仓位角色" min-width="110">
              <template #default="{ row }">
                <el-tag :type="row.type" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="current" label="当前状态" min-width="190" />
            <el-table-column prop="action" label="建议动作" min-width="120">
              <template #default="{ row }">
                <span :style="{ color: row.color, fontWeight: 700 }">{{ row.action }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="riskBudget" label="风险预算" min-width="170" />
            <el-table-column prop="invalidLine" label="失效位" min-width="160" />
            <el-table-column prop="sizeRule" label="仓位调整" min-width="180" />
            <el-table-column prop="addRule" label="补仓纪律" min-width="220" />
            <el-table-column prop="defense" label="防守线" min-width="230" />
            <el-table-column prop="note" label="说明" min-width="260" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="仓位策略" name="strategy">
        <div class="chart-card strategy-filter-card">
          <el-row :gutter="12" align="middle">
            <el-col :span="7">
              <el-select v-model="strategySymbol" class="strategy-select" @change="loadStrategy">
                <el-option v-for="item in strategySymbolOptions" :key="item" :label="symbolName(item)" :value="item" />
              </el-select>
            </el-col>
            <el-col :span="7">
              <el-segmented v-model="strategyBar" :options="strategyBarOptions" @change="loadStrategy" />
            </el-col>
            <el-col :span="10" style="text-align:right">
              <el-button type="primary" :loading="strategyLoading" @click="loadStrategy">
                <el-icon><Refresh /></el-icon>
                刷新行情策略
              </el-button>
              <span class="text-dim strategy-update">策略更新：{{ strategyLastUpdate || '未更新' }}</span>
            </el-col>
          </el-row>
        </div>

        <el-row :gutter="12" class="strategy-card-row">
          <el-col :span="6" v-for="card in strategyCards" :key="card.label">
            <div class="stat-card strategy-stat">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value strategy-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
          </el-col>
        </el-row>

        <div class="chart-card strategy-ai-card">
          <div class="card-title ai-title">
            <div>
              <span>DeepSeek 行情 + 仓位联动分析</span>
              <span class="hint">预留：后续可接入模型生成仓位动作剧本</span>
            </div>
            <el-button disabled>
              <el-icon><DataAnalysis /></el-icon>
              预留分析
            </el-button>
          </div>
          <div class="strategy-report">
            <div class="report-main" :style="{ color: strategyActionColor }">{{ strategyReport.headline }}</div>
            <div class="report-grid">
              <div v-for="item in strategyReport.items" :key="item.label" class="report-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>
        </div>

        <el-row :gutter="16">
          <el-col :span="16">
            <div class="chart-card">
              <div class="card-title">行情位置与仓位关键线</div>
              <div ref="strategyKlineRef" style="height:420px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card">
              <div class="card-title">操作计划：支撑 / 压力 / 目标</div>
              <el-table :data="strategyPlanRows" size="small" style="width:100%">
                <el-table-column prop="name" label="项目" min-width="90" />
                <el-table-column prop="value" label="价格/状态" min-width="110">
                  <template #default="{ row }">
                    <span :style="{ color: row.color }">{{ row.value }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="note" label="说明" min-width="180" />
              </el-table>
            </div>
            <div class="chart-card compact-strategy-card">
              <div class="card-title">策略执行评分</div>
              <div ref="strategyDecisionRef" style="height:210px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <div class="chart-card compact-strategy-card">
              <div class="card-title">量能与BOLL中线距离</div>
              <div ref="strategyVolumeRef" style="height:240px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-strategy-card">
              <div class="card-title">主力 / 散户 / OI 结构</div>
              <div ref="strategyMajorRef" style="height:240px" />
            </div>
          </el-col>
          <el-col :span="8">
            <div class="chart-card compact-strategy-card">
              <div class="card-title">主动买卖与资金费率</div>
              <div ref="strategyFlowRef" style="height:240px" />
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <div class="chart-card compact-strategy-card">
              <div class="card-title">支撑压力场景矩阵</div>
              <div ref="strategyScenarioRef" style="height:250px" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card compact-strategy-card">
              <div class="card-title">风险收益比与执行窗口</div>
              <div ref="strategyRiskRewardRef" style="height:250px" />
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import {
  getCandles,
  getCurrentPositions,
  getEliteLongShortPosition,
  getFundingRateHistory,
  getLongShortAccountRatio,
  getOpenInterestHistory,
  getTakerVolume,
  openPositionAiAnalysisStream,
} from '@/api'
import { useTheme } from '@/composables/useTheme'

const { isDark, cs, initChart } = useTheme()

const positions = ref([])
const loading = ref(false)
const lastUpdate = ref('')
const activePositionTab = ref('overview')
const aiLoading = ref(false)
const aiResult = ref(null)
const aiError = ref('')
const aiStage = ref('')
const aiStreamText = ref('')
let aiSource = null
const strategySymbol = ref('BTC-USDT-SWAP')
const strategyBar = ref('1H')
const strategyLoading = ref(false)
const strategyLastUpdate = ref('')
const strategyDataset = ref(null)
const strategySentiment = ref({ elite: [], account: [], taker: [], funding: [], oi: [] })

const aiRef = ref(null)
const durationRef = ref(null)
const pnlRef = ref(null)
const addRef = ref(null)
const alignmentRef = ref(null)
const divergenceRef = ref(null)
const riskRef = ref(null)
const scatterRef = ref(null)
const radarRef = ref(null)
const riskMatrixRef = ref(null)
const exposureRef = ref(null)
const leverageDistRef = ref(null)
const pnlLiqBubbleRef = ref(null)
const positionHeatRef = ref(null)
const strategyKlineRef = ref(null)
const strategyVolumeRef = ref(null)
const strategyMajorRef = ref(null)
const strategyFlowRef = ref(null)
const strategyDecisionRef = ref(null)
const strategyScenarioRef = ref(null)
const strategyRiskRewardRef = ref(null)
const managementScoreRef = ref(null)
const managementAllocationRef = ref(null)
const managementActionRef = ref(null)
const managementBufferRef = ref(null)
const managementTargetRef = ref(null)
const managementRiskBudgetRef = ref(null)
const managementDisciplineRef = ref(null)
const managementExperienceRef = ref(null)
let charts = {}

const POSITION_RISK_BUDGET_PCT = 1
const PORTFOLIO_RISK_BUDGET_PCT = 3
const MAX_SINGLE_POSITION_WEIGHT = 35
const MAX_LEVERAGE_NORMAL = 10
const MIN_LIQ_BUFFER_PCT = 12

const strategyBarOptions = [
  { label: '15分', value: '15m' },
  { label: '1小时', value: '1H' },
  { label: '4小时', value: '4H' },
  { label: '1日', value: '1D' },
]

const tableRows = computed(() => [...positions.value].sort((a, b) => Math.abs(num(b.notional)) - Math.abs(num(a.notional))))
const strategySymbolOptions = computed(() => {
  const active = [...new Set(tableRows.value.map(row => row.instId).filter(Boolean))]
  const base = ['BTC-USDT-SWAP', 'ETH-USDT-SWAP']
  return [...new Set([...active, ...base])]
})
const selectedStrategyPosition = computed(() =>
  tableRows.value.find(row => row.instId === strategySymbol.value) || tableRows.value[0] || null
)
const aiCleanPreview = computed(() => {
  const text = aiStreamText.value || ''
  const headline = text.match(/"headline"\s*:\s*"([^"]+)/)?.[1]
  const action = text.match(/"actionBias"\s*:\s*"([^"]+)/)?.[1]
  if (headline && action) return `${headline}，建议：${action}`
  if (headline) return headline
  return ''
})

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
    { label: '顺势仓位', value: count ? `${alignedCount}/${count}` : '-', sub: `方向正确率 ${count ? fmtPct(alignedCount / count * 100) : '-'}`, color: alignedCount >= Math.ceil(count / 2) ? '#3fb950' : '#d29922' },
    { label: '补仓仓位', value: count ? `${addCount}/${count}` : '-', sub: `平均持仓 ${fmtHolding(avgHolding)}`, color: addCount > 0 ? '#d29922' : '#8b949e' },
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
      color: addCount > 0 ? '#d29922' : '#3fb950',
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
      color: avgHolding >= 240 ? '#d29922' : '#3fb950',
      note: '时间长并不必然错误，但若亏损仓位明显更久，要重点看止损纪律。',
    },
    {
      metric: '平均杠杆',
      value: avgLeverage ? `${avgLeverage.toFixed(1)}x` : '-',
      judge: avgLeverage >= 10 ? '杠杆偏高' : '正常',
      color: avgLeverage >= 10 ? '#d29922' : '#3fb950',
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

const managementMetrics = computed(() => {
  const rows = tableRows.value
  const totalNotional = sum(rows.map(row => Math.abs(num(row.notional))))
  const totalUpl = sum(rows.map(row => num(row.upl)))
  const riskScores = rows.map(row => positionRiskScore(row))
  const avgRisk = riskScores.length ? sum(riskScores) / riskScores.length : 0
  const maxRisk = riskScores.length ? Math.max(...riskScores) : 0
  const maxWeight = rows.length && totalNotional ? Math.max(...rows.map(row => Math.abs(num(row.notional)) / totalNotional * 100)) : 0
  const positiveLiq = rows.map(row => num(row.liquidationDistancePct)).filter(v => v > 0)
  const minLiq = positiveLiq.length ? Math.min(...positiveLiq) : 0
  const avgLeverage = rows.length ? sum(rows.map(row => num(row.lever))) / rows.length : 0
  const alignedRatio = rows.length ? rows.filter(row => row.alignment === '顺势').length / rows.length * 100 : 0
  const addRatio = rows.length ? rows.filter(row => num(row.addCount) > 1).length / rows.length * 100 : 0
  const lossRatio = rows.length ? rows.filter(row => num(row.upl) < 0).length / rows.length * 100 : 0
  const isolatedRatio = rows.length ? rows.filter(row => String(row.marginMode || '').toLowerCase() === 'isolated').length / rows.length * 100 : 0
  const planned = rows.map(row => managementPlanFor(row, totalNotional))
  const totalCurrentRisk = sum(planned.map(row => row.currentRiskPct))
  const totalSuggestedWeight = sum(planned.map(row => row.targetWeight))
  const avgRuleFit = planned.length ? sum(planned.map(row => row.ruleFit)) / planned.length : 0
  const dangerCount = rows.filter(row => managementActionFor(row, totalNotional).level >= 4).length
  return {
    rows,
    totalNotional,
    totalUpl,
    avgRisk,
    maxRisk,
    maxWeight,
    minLiq,
    avgLeverage,
    alignedRatio,
    addRatio,
    lossRatio,
    isolatedRatio,
    totalCurrentRisk,
    totalSuggestedWeight,
    avgRuleFit,
    dangerCount,
  }
})

const managementSummary = computed(() => {
  const m = managementMetrics.value
  if (!m.rows.length) {
    return { riskLevel: '无仓位', headline: '当前没有持仓，仓位管理重点是等待高胜率结构，不要为了交易而开仓。', color: '#8b949e' }
  }
  if (m.dangerCount > 0 || m.maxRisk >= 75 || (m.minLiq > 0 && m.minLiq < 8)) {
    return { riskLevel: '高风险', headline: `优先降风险：当前组合估算风险 ${round(m.totalCurrentRisk, 2)}%，已超过成熟交易员常用的 ${PORTFOLIO_RISK_BUDGET_PCT}% 组合刹车线；先处理近强平、亏损补仓、方向背离仓位，暂停新增仓位。`, color: '#f85149' }
  }
  if (m.avgRisk >= 45 || m.maxWeight >= 70 || m.addRatio >= 50) {
    return { riskLevel: '中风险', headline: `仓位需要收敛：成熟规则匹配度约 ${round(m.avgRuleFit, 1)} 分，控制单仓集中度，盈利仓只做保护性移动止损，亏损仓不补仓。`, color: '#d29922' }
  }
  return { riskLevel: '可管理', headline: `当前仓位风险可管理：组合估算风险 ${round(m.totalCurrentRisk, 2)}%，允许顺势盈利仓继续持有，但新增仓位仍要满足止损距离和风险收益比。`, color: '#3fb950' }
})

const managementRiskType = computed(() => {
  const level = managementSummary.value.riskLevel
  if (level === '高风险') return 'danger'
  if (level === '中风险') return 'warning'
  if (level === '可管理') return 'success'
  return 'info'
})

const managementCards = computed(() => {
  const m = managementMetrics.value
  return [
    { label: '总名义敞口', value: fmtMoney(m.totalNotional), sub: `${m.rows.length} 个持仓`, color: '#58a6ff' },
    { label: '最大单仓占比', value: m.rows.length ? fmtPct(m.maxWeight) : '-', sub: m.maxWeight >= 70 ? '集中度过高' : '集中度可控', color: m.maxWeight >= 70 ? '#f85149' : m.maxWeight >= 45 ? '#d29922' : '#3fb950' },
    { label: '组合风险分', value: m.rows.length ? Math.round(m.avgRisk) : '-', sub: `最高单仓 ${Math.round(m.maxRisk)}`, color: m.avgRisk >= 60 ? '#f85149' : m.avgRisk >= 35 ? '#d29922' : '#3fb950' },
    { label: '估算组合风险', value: m.rows.length ? `${round(m.totalCurrentRisk, 2)}%` : '-', sub: `规则匹配 ${round(m.avgRuleFit, 0)} 分`, color: m.totalCurrentRisk > PORTFOLIO_RISK_BUDGET_PCT ? '#f85149' : m.totalCurrentRisk > 2 ? '#d29922' : '#3fb950' },
  ]
})

const managementRules = computed(() => {
  const m = managementMetrics.value
  const addRule = m.lossRatio > 0 || m.addRatio > 0 ? '亏损仓禁止补仓' : '只允许顺势确认后小幅加仓'
  return [
    { title: '风险预算', value: `单仓 ${POSITION_RISK_BUDGET_PCT}% / 组合 ${PORTFOLIO_RISK_BUDGET_PCT}%`, note: '先定义账户可亏金额，再用止损距离反推仓位；不是先开仓再想止损。' },
    { title: '补仓原则', value: addRule, note: '只加盈利顺势仓，亏损仓越补越容易把交易变成赌方向。' },
    { title: '集中度控制', value: m.maxWeight >= MAX_SINGLE_POSITION_WEIGHT ? '需要降集中度' : '集中度合格', note: `单仓目标上限 ${MAX_SINGLE_POSITION_WEIGHT}% 左右，BTC/ETH 也不要让单笔波动决定账户。` },
    { title: '执行优先级', value: '强平距 > 失效位 > 浮亏 > 盈利', note: '先处理会让账户出局的风险，再讨论目标位、加仓和收益优化。' },
  ]
})

const managementPlaybook = computed(() => [
  {
    title: '1% 风险规则',
    rule: '单笔计划亏损控制在账户约 1%',
    why: '成熟交易者先限制单次错误的破坏力；连续错几次仍有修正空间。',
  },
  {
    title: '2% 组合刹车',
    rule: '同向/相关仓位总风险接近 2%-3% 就停止加仓',
    why: 'BTC 和 ETH 高相关，多个同向仓位本质上常是一个大仓。',
  },
  {
    title: '失效位反推仓位',
    rule: '仓位 = 可亏金额 / 入场到止损距离',
    why: '止损远就小仓，止损近才允许大一点，避免固定手数硬扛波动。',
  },
  {
    title: '亏损不摊平',
    rule: '亏损、逆势、背离、近强平时禁止补仓',
    why: '成功经验普遍把补仓限定在盈利顺势后的回踩确认，而不是摊低成本。',
  },
  {
    title: '波动率定仓',
    rule: '高杠杆或高波动阶段自动降仓',
    why: '币圈波动会放大滑点和插针风险，66x 这类仓位几乎没有容错。',
  },
  {
    title: '移动保护利润',
    rule: '盈利仓用结构/均线/BOLL 中线保护',
    why: '盈利仓可以让利润奔跑，但不能从盈利变成扛单。',
  },
])

const managementPlanRows = computed(() => {
  const totalNotional = managementMetrics.value.totalNotional
  return tableRows.value.map(row => managementPlanFor(row, totalNotional))
})

const strategyCards = computed(() => {
  const s = strategyDataset.value
  const p = selectedStrategyPosition.value
  if (!s) {
    return [
      { label: '策略动作', value: '-', sub: '等待行情', color: '#8b949e' },
      { label: '关键支撑', value: '-', sub: '等待行情', color: '#58a6ff' },
      { label: '关键压力', value: '-', sub: '等待行情', color: '#d29922' },
      { label: 'BOLL中线', value: '-', sub: '等待行情', color: '#8b949e' },
    ]
  }
  const action = strategyAction(s, p)
  return [
    { label: '策略动作', value: action.label, sub: action.reason, color: action.color },
    { label: '关键支撑', value: fmtPrice(s.support), sub: `跌破看 ${fmtPrice(s.stopLine)}`, color: '#58a6ff' },
    { label: '关键压力', value: fmtPrice(s.resistance), sub: `突破看 ${fmtPrice(s.targetLine)}`, color: '#d29922' },
    { label: 'BOLL中线', value: fmtPrice(s.bollMidNow), sub: bollCrossText(s), color: s.close >= s.bollMidNow ? '#3fb950' : '#f85149' },
  ]
})

const strategyPlanRows = computed(() => {
  const s = strategyDataset.value
  const p = selectedStrategyPosition.value
  if (!s) return []
  const action = strategyAction(s, p)
  return [
    { name: '现价', value: fmtPrice(s.close), color: '#58a6ff', note: `${strategyBar.value} 最新收盘价` },
    { name: '持仓均价', value: p ? fmtPrice(p.avgPx) : '无当前仓位', color: '#8b949e', note: p ? `${p.direction === 'long' ? '多' : '空'}，浮盈亏 ${fmtPnl(p.upl)}` : '可作为观察模式' },
    { name: '第一支撑', value: fmtPrice(s.support), color: '#58a6ff', note: '最近低点 + ATR缓冲，回踩不破才算承接。' },
    { name: '防守止损', value: fmtPrice(s.stopLine), color: '#f85149', note: '跌破/站回失败需要减仓或退出。' },
    { name: 'BOLL中线', value: fmtPrice(s.bollMidNow), color: s.close >= s.bollMidNow ? '#3fb950' : '#f85149', note: bollCrossText(s) },
    { name: 'EMA结构', value: s.emaText, color: s.emaBull ? '#3fb950' : '#f85149', note: `EMA20 ${fmtPrice(s.ema20Now)} / EMA50 ${fmtPrice(s.ema50Now)} / EMA100 ${fmtPrice(s.ema100Now)}` },
    { name: '第一压力', value: fmtPrice(s.resistance), color: '#d29922', note: '最近高点 + ATR缓冲，突破后看延续。' },
    { name: '目标观察', value: fmtPrice(s.targetLine), color: '#3fb950', note: action.targetNote },
  ]
})

const strategyReport = computed(() => {
  const s = strategyDataset.value
  const p = selectedStrategyPosition.value
  if (!s) return { headline: '等待行情数据加载后生成仓位策略。', items: [] }
  const action = strategyAction(s, p)
  const major = majorReading.value
  return {
    headline: action.fullText,
    items: [
      { label: 'BOLL中线', value: bollCrossText(s) },
      { label: '均线状态', value: s.emaText },
      { label: '量能', value: s.volumeText },
      { label: '主力', value: major.summary },
      { label: '资金费率', value: fundingText.value },
      { label: '看到哪里', value: action.lookTo },
    ],
  }
})

const strategyActionColor = computed(() => {
  const action = strategyAction(strategyDataset.value, selectedStrategyPosition.value)
  return action.color
})

const majorReading = computed(() => {
  const elite = strategySentiment.value.elite
  const account = strategySentiment.value.account
  const taker = strategySentiment.value.taker
  const oi = strategySentiment.value.oi
  const eliteNow = num(last(elite)?.ratio)
  const accountNow = num(last(account)?.ratio)
  const eliteSlope = slopePct(elite.map(i => i.ratio), 18)
  const accountSlope = slopePct(account.map(i => i.ratio), 18)
  const oiSlope = slopePct(oi.map(i => i.oi), 18)
  const flow = takerImbalance(taker.slice(-18))
  const biasScore = clamp((eliteNow - 1) * 28 + eliteSlope * 4 + flow * 0.25 + oiSlope * 2, -100, 100)
  const summary = biasScore > 12
    ? '主力偏多'
    : biasScore < -12
      ? '主力偏空'
      : '主力中性'
  return { eliteNow, accountNow, eliteSlope, accountSlope, oiSlope, flow, biasScore, summary }
})

const fundingText = computed(() => {
  const rows = strategySentiment.value.funding
  const latest = num(last(rows)?.fundingRate) * 100
  if (!rows.length) return '暂无资金费率'
  if (latest > 0.03) return `偏多拥挤 ${latest.toFixed(4)}%`
  if (latest < -0.01) return `空头付费 ${latest.toFixed(4)}%`
  return `中性 ${latest.toFixed(4)}%`
})

async function load() {
  loading.value = true
  try {
    const res = await getCurrentPositions()
    positions.value = Array.isArray(res) ? res : []
    lastUpdate.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderAll()
    if (!aiResult.value && !aiLoading.value) {
      generateAiAnalysis()
    }
  } finally {
    loading.value = false
  }
}

function renderAll() {
  if (activePositionTab.value === 'overview') {
    renderAi()
    return
  }
  if (activePositionTab.value === 'strategy') {
    renderStrategyAll()
    return
  }
  if (activePositionTab.value === 'management') {
    renderManagementAll()
    return
  }
  renderDuration()
  renderPnl()
  renderAdd()
  renderAlignment()
  renderDivergence()
  renderRisk()
  renderScatter()
  renderRadar()
  renderRiskMatrix()
  renderExposure()
  renderLeverageDist()
  renderPnlLiqBubble()
  renderPositionHeat()
}

async function onPositionTabChange(name) {
  await nextTick()
  if (name === 'overview') {
    renderAi()
    return
  }
  if (name === 'strategy') {
    if (!strategyDataset.value) await loadStrategy()
    renderStrategyAll()
    return
  }
  if (name === 'management') {
    renderManagementAll()
    return
  }
  renderAll()
}

async function loadStrategy() {
  strategyLoading.value = true
  try {
    const symbol = strategySymbol.value || selectedStrategyPosition.value?.instId || 'BTC-USDT-SWAP'
    strategySymbol.value = symbol
    const [candlesRaw, eliteRaw, accountRaw, takerRaw, fundingRaw, oiRaw] = await Promise.all([
      getCandles(symbol, strategyBar.value, 260),
      getEliteLongShortPosition(symbol, strategyBar.value, 120).catch(() => ({ data: [] })),
      getLongShortAccountRatio(symbol, strategyBar.value, 120).catch(() => ({ data: [] })),
      getTakerVolume(symbol, strategyBar.value, 120).catch(() => ({ data: [] })),
      getFundingRateHistory(symbol, 100).catch(() => ({ data: [] })),
      getOpenInterestHistory(symbol, strategyBar.value, 120).catch(() => ({ data: [] })),
    ])
    strategyDataset.value = buildStrategyDataset(symbol, normalizeOkxData(candlesRaw).slice().reverse())
    strategySentiment.value = {
      elite: normalizeOkxData(eliteRaw).slice().reverse().map(d => ({ ts: num(d[0]), ratio: num(d[1]) })),
      account: normalizeOkxData(accountRaw).slice().reverse().map(d => ({ ts: num(d[0]), ratio: num(d[1]) })),
      taker: normalizeOkxData(takerRaw).slice().reverse().map(d => ({ ts: num(d[0]), sellVol: num(d[1]), buyVol: num(d[2]) })),
      funding: normalizeOkxData(fundingRaw).slice().reverse().map(d => ({ fundingTime: num(d.fundingTime), fundingRate: num(d.fundingRate) })),
      oi: normalizeOkxData(oiRaw).slice().reverse().map(d => ({ ts: num(d[0]), oi: num(d[1]) })),
    }
    strategyLastUpdate.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    await nextTick()
    renderStrategyAll()
  } finally {
    strategyLoading.value = false
  }
}

function renderStrategyAll() {
  renderStrategyKline()
  renderStrategyDecision()
  renderStrategyVolume()
  renderStrategyMajor()
  renderStrategyFlow()
  renderStrategyScenario()
  renderStrategyRiskReward()
}

function renderManagementAll() {
  renderManagementScore()
  renderManagementAllocation()
  renderManagementAction()
  renderManagementBuffer()
  renderManagementTarget()
  renderManagementRiskBudget()
  renderManagementDiscipline()
  renderManagementExperience()
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
    const currentScore = aiResult.value ? clamp(num(aiResult.value.biasScore), -100, 100) : 0
    return c.setOption(aiGaugeOption(currentScore, aiStage.value || 'DeepSeek 分析中', '#58a6ff', true))
  }
  if (!aiResult.value) {
    return c.setOption(aiGaugeOption(0, aiError.value ? 'AI 分析失败' : '等待分析', '#8b949e', false))
  }
  const score = clamp(num(aiResult.value.biasScore), -100, 100)
  const color = score > 25 ? '#3fb950' : score < -25 ? '#f85149' : '#d29922'
  c.setOption(aiGaugeOption(score, aiResult.value.actionBias || '观望', color, false))
}

function aiGaugeOption(score, title, color, loading = false) {
  return {
    tooltip: { formatter: () => `动作偏向分: ${score}<br/>${title}<br/>置信度: ${aiResult.value?.confidence || 0}%` },
    series: [{
      type: 'gauge',
      min: -100,
      max: 100,
      startAngle: 210,
      endAngle: -30,
      radius: '88%',
      splitNumber: 4,
      axisLine: { lineStyle: { width: 12, color: [[0.35, '#f85149'], [0.65, '#d29922'], [1, '#3fb950']] } },
      progress: { show: true, width: 12, itemStyle: { color } },
      pointer: { width: 4, itemStyle: { color } },
      axisTick: { distance: -18, length: 5, lineStyle: { color: cs.value.gridLine } },
      splitLine: { distance: -20, length: 12, lineStyle: { color: cs.value.gridLine } },
      axisLabel: { color: cs.value.labelColor, distance: 18, fontSize: 10 },
      detail: { formatter: v => loading ? '...' : `${Math.round(v)}`, color, fontSize: 30, offsetCenter: [0, '38%'] },
      title: { color: cs.value.legendColor, fontSize: 12, offsetCenter: [0, '68%'] },
      data: [{ value: score, name: title }],
    }],
  }
}

function renderStrategyKline() {
  const c = chart('strategyKline', strategyKlineRef)
  if (!c) return
  const s = strategyDataset.value
  if (!s) return c.setOption(emptyOption('等待行情数据'))
  const p = selectedStrategyPosition.value
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: [
      { left: 70, right: 28, top: 38, height: 260 },
      { left: 70, right: 28, top: 330, height: 62 },
    ],
    xAxis: [
      { type: 'category', data: s.labels, axisLabel: { color: cs.value.legendColor, fontSize: 10 }, axisLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'category', gridIndex: 1, data: s.labels, axisLabel: { show: false }, axisLine: { lineStyle: { color: cs.value.gridLine } } },
    ],
    yAxis: [
      { type: 'value', scale: true, axisLabel: { color: cs.value.legendColor }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', gridIndex: 1, axisLabel: { formatter: v => compactMoney(v), color: cs.value.legendColor }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    ],
    dataZoom: [{ type: 'inside', xAxisIndex: [0, 1], start: 45, end: 100 }],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: s.ohlc,
        itemStyle: { color: '#3fb950', color0: '#f85149', borderColor: '#3fb950', borderColor0: '#f85149' },
        markLine: {
          symbol: 'none',
          label: { color: cs.value.labelColor, fontSize: 10 },
          data: [
            { name: '支撑', yAxis: s.support, lineStyle: { color: '#58a6ff', type: 'dashed' }, label: { formatter: `支撑 ${fmtPrice(s.support)}` } },
            { name: '压力', yAxis: s.resistance, lineStyle: { color: '#d29922', type: 'dashed' }, label: { formatter: `压力 ${fmtPrice(s.resistance)}` } },
            ...(p ? [{ name: '持仓均价', yAxis: num(p.avgPx), lineStyle: { color: '#a371f7', type: 'solid' }, label: { formatter: `均价 ${fmtPrice(p.avgPx)}` } }] : []),
          ],
        },
      },
      lineSeries('EMA20', s.ema20, '#58a6ff'),
      lineSeries('EMA50', s.ema50, '#d29922'),
      lineSeries('EMA100', s.ema100, '#8b949e', { type: 'dashed' }),
      lineSeries('BOLL中线', s.bollMid, '#c9d1d9', { type: 'dotted' }),
      lineSeries('BOLL上轨', s.bollUpper, '#7ee787', { opacity: .65 }),
      lineSeries('BOLL下轨', s.bollLower, '#ff7b72', { opacity: .65 }),
      { name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: s.volumes, barMaxWidth: 10, itemStyle: { color: p => s.bars[p.dataIndex]?.close >= s.bars[p.dataIndex]?.open ? 'rgba(63,185,80,.62)' : 'rgba(248,81,73,.62)' } },
    ],
  }, true)
}

function renderStrategyDecision() {
  const c = chart('strategyDecision', strategyDecisionRef)
  if (!c) return
  const s = strategyDataset.value
  if (!s) return c.setOption(emptyOption('等待策略评分'))
  const action = strategyAction(s, selectedStrategyPosition.value)
  c.setOption({
    tooltip: { formatter: () => `${action.label}<br/>评分 ${action.score}<br/>${action.reason}` },
    series: [{
      type: 'gauge',
      min: -100,
      max: 100,
      startAngle: 210,
      endAngle: -30,
      radius: '90%',
      axisLine: { lineStyle: { width: 10, color: [[0.35, '#f85149'], [0.65, '#d29922'], [1, '#3fb950']] } },
      progress: { show: true, width: 10, itemStyle: { color: action.color } },
      pointer: { width: 4, itemStyle: { color: action.color } },
      axisTick: { show: false },
      splitLine: { distance: -16, length: 10, lineStyle: { color: cs.value.gridLine } },
      axisLabel: { color: cs.value.legendColor, fontSize: 10, distance: 14 },
      detail: { formatter: v => `${Math.round(v)}`, color: action.color, fontSize: 26, offsetCenter: [0, '38%'] },
      title: { color: cs.value.labelColor, fontSize: 12, offsetCenter: [0, '68%'] },
      data: [{ value: action.score, name: action.label }],
    }],
  }, true)
}

function renderStrategyVolume() {
  const c = chart('strategyVolume', strategyVolumeRef)
  if (!c) return
  const s = strategyDataset.value
  if (!s) return c.setOption(emptyOption())
  const distance = s.closes.map((close, i) => s.bollMid[i] ? round((close - s.bollMid[i]) / s.bollMid[i] * 100, 3) : 0)
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 56, right: 40, top: 36, bottom: 42 },
    xAxis: { type: 'category', data: s.labels, axisLabel: { rotate: 25, fontSize: 9 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => compactMoney(v) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { show: false } },
    ],
    series: [
      { name: '成交量', type: 'bar', data: s.volumes, barMaxWidth: 8, itemStyle: { color: p => s.volumeShock[p.dataIndex] >= 1.5 ? '#d29922' : '#58a6ff' } },
      { name: 'BOLL中线距离', type: 'line', yAxisIndex: 1, data: distance, smooth: true, symbol: 'none', lineStyle: { color: '#3fb950', width: 2 } },
    ],
  }, true)
}

function renderStrategyMajor() {
  const c = chart('strategyMajor', strategyMajorRef)
  if (!c) return
  const elite = strategySentiment.value.elite
  const account = strategySentiment.value.account
  const oi = strategySentiment.value.oi
  if (!elite.length && !account.length && !oi.length) return c.setOption(emptyOption('暂无主力数据'))
  const labels = (elite.length ? elite : account.length ? account : oi).map(i => fmtTime(i.ts))
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 52, right: 44, top: 36, bottom: 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 25, fontSize: 9 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => v.toFixed(2) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => compactMoney(v) }, splitLine: { show: false } },
    ],
    series: [
      { name: '主力多空比', type: 'line', data: alignSeries(elite.map(i => i.ratio), labels.length), smooth: true, symbol: 'none', lineStyle: { color: '#58a6ff', width: 2 } },
      { name: '散户账户比', type: 'line', data: alignSeries(account.map(i => i.ratio), labels.length), smooth: true, symbol: 'none', lineStyle: { color: '#d29922', width: 2 } },
      { name: 'OI', type: 'bar', yAxisIndex: 1, data: alignSeries(oi.map(i => i.oi), labels.length), barMaxWidth: 8, itemStyle: { color: 'rgba(163,113,247,.35)' } },
    ],
  }, true)
}

function renderStrategyFlow() {
  const c = chart('strategyFlow', strategyFlowRef)
  if (!c) return
  const taker = strategySentiment.value.taker
  const funding = strategySentiment.value.funding
  if (!taker.length && !funding.length) return c.setOption(emptyOption('暂无主动买卖数据'))
  const labels = taker.map(i => fmtTime(i.ts))
  const imbalance = taker.map(i => {
    const total = i.buyVol + i.sellVol
    return total ? round((i.buyVol - i.sellVol) / total * 100, 2) : 0
  })
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 52, right: 42, top: 36, bottom: 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 25, fontSize: 9 } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
      { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { show: false } },
    ],
    series: [
      { name: '主动买卖差', type: 'bar', data: imbalance, barMaxWidth: 9, itemStyle: { color: p => p.value >= 0 ? '#3fb950' : '#f85149' } },
      { name: '资金费率', type: 'line', yAxisIndex: 1, data: alignSeries(funding.map(i => round(i.fundingRate * 100, 4)), labels.length), smooth: true, symbol: 'none', lineStyle: { color: '#d29922', width: 2 } },
    ],
  }, true)
}

function renderStrategyScenario() {
  const c = chart('strategyScenario', strategyScenarioRef)
  if (!c) return
  const s = strategyDataset.value
  if (!s) return c.setOption(emptyOption('等待行情策略'))
  const p = selectedStrategyPosition.value
  const action = strategyAction(s, p)
  const major = majorReading.value
  const rows = [
    { name: '跌破防守', price: s.stopLine, risk: 90, reward: 18, color: '#f85149', note: '减仓/退出' },
    { name: '回踩支撑', price: s.support, risk: s.close > s.support ? 42 : 72, reward: 58, color: '#58a6ff', note: '观察承接' },
    { name: '站回BOLL中线', price: s.bollMidNow, risk: s.close >= s.bollMidNow ? 30 : 54, reward: s.close >= s.bollMidNow ? 68 : 46, color: '#d29922', note: bollCrossText(s) },
    { name: '突破压力', price: s.resistance, risk: 36, reward: 78, color: '#3fb950', note: '放量延续' },
    { name: '目标区', price: s.targetLine, risk: 52, reward: 92, color: '#7ee787', note: action.lookTo },
  ]
  c.setOption({
    tooltip: { formatter: p => `${p.name}<br/>价格: ${fmtPrice(p.value[0])}<br/>风险: ${p.value[1]}<br/>收益潜力: ${p.value[2]}<br/>${rows[p.dataIndex]?.note || ''}` },
    grid: { left: 72, right: 24, top: 26, bottom: 42 },
    xAxis: { type: 'value', name: '关键价位', scale: true, axisLabel: { formatter: v => fmtPrice(v) }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '风险分', min: 0, max: 100, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      name: '执行场景',
      type: 'scatter',
      data: rows.map(row => ({
        name: row.name,
        value: [row.price, row.risk, row.reward],
        symbolSize: Math.max(12, Math.min(36, row.reward / 2.8)),
        itemStyle: { color: row.color, opacity: .78 },
        label: { show: true, formatter: row.name, position: 'top', color: cs.value.labelColor, fontSize: 10 },
      })),
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: cs.value.gridLine, type: 'dashed' },
        data: [{ yAxis: 60 }, { xAxis: s.close, label: { formatter: `现价 ${fmtPrice(s.close)}` } }],
      },
    }, {
      name: '主力偏向',
      type: 'effectScatter',
      data: [{ value: [s.close, clamp(50 - major.biasScore * .25, 8, 92), Math.abs(major.biasScore)], name: major.summary }],
      symbolSize: Math.max(14, Math.min(32, Math.abs(major.biasScore) / 3 + 14)),
      rippleEffect: { scale: 2.4, brushType: 'stroke' },
      itemStyle: { color: major.biasScore >= 0 ? '#3fb950' : '#f85149' },
      label: { show: true, formatter: major.summary, position: 'bottom', color: cs.value.labelColor },
    }],
  }, true)
}

function renderStrategyRiskReward() {
  const c = chart('strategyRiskReward', strategyRiskRewardRef)
  if (!c) return
  const s = strategyDataset.value
  if (!s) return c.setOption(emptyOption('等待风险收益测算'))
  const p = selectedStrategyPosition.value
  const isShort = p?.direction === 'short'
  const entry = p ? num(p.avgPx) : s.close
  const riskDistance = Math.abs(entry - (isShort ? s.resistance : s.stopLine))
  const rewardDistance = Math.abs((isShort ? s.support : s.targetLine) - entry)
  const rr = riskDistance ? rewardDistance / riskDistance : 0
  const bollDistance = s.bollMidNow ? round((s.close - s.bollMidNow) / s.bollMidNow * 100, 2) : 0
  const volumeScore = clamp((last(s.volumeShock) || 1) / 2 * 100, 0, 100)
  const emaScore = s.emaBull ? 72 : 34
  const majorScore = clamp(50 + majorReading.value.biasScore / 2, 0, 100)
  const labels = ['风险收益比', 'BOLL位置', '量能确认', '均线顺势', '主力配合']
  const values = [
    clamp(rr / 2.5 * 100, 0, 100),
    clamp(50 + bollDistance * 5, 0, 100),
    volumeScore,
    emaScore,
    majorScore,
  ]
  c.setOption({
    tooltip: { trigger: 'item', formatter: () => `RR: ${rr.toFixed(2)}<br/>入场: ${fmtPrice(entry)}<br/>风险距离: ${fmtPrice(riskDistance)}<br/>收益距离: ${fmtPrice(rewardDistance)}` },
    radar: {
      radius: '62%',
      indicator: labels.map(name => ({ name, max: 100 })),
      axisName: { color: cs.value.labelColor, fontSize: 11 },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(88,166,255,.05)'] } },
    },
    series: [{
      type: 'radar',
      data: [{ name: '执行窗口', value: values.map(v => round(v, 1)), areaStyle: { color: 'rgba(88,166,255,.18)' }, lineStyle: { color: '#58a6ff', width: 2 } }],
    }],
    graphic: [{
      type: 'text',
      right: 18,
      bottom: 10,
      style: { text: `RR ${rr.toFixed(2)} | ${isShort ? '空头' : '多头'}窗口`, fill: cs.value.legendColor, font: '12px sans-serif' },
    }],
  }, true)
}

function renderManagementScore() {
  const c = chart('managementScore', managementScoreRef)
  if (!c) return
  const m = managementMetrics.value
  if (!m.rows.length) return c.setOption(emptyOption('暂无持仓'))
  const values = [
    clamp(100 - m.avgRisk, 0, 100),
    clamp(100 - Math.max(0, m.maxWeight - 35) * 1.5, 0, 100),
    clamp(m.minLiq ? m.minLiq * 5 : 70, 0, 100),
    clamp(100 - m.avgLeverage * 4, 0, 100),
    clamp(m.alignedRatio, 0, 100),
    clamp(100 - m.addRatio, 0, 100),
  ].map(v => round(v, 1))
  c.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '62%',
      indicator: [
        { name: '风险余量', max: 100 },
        { name: '集中度', max: 100 },
        { name: '强平缓冲', max: 100 },
        { name: '杠杆克制', max: 100 },
        { name: '方向一致', max: 100 },
        { name: '补仓纪律', max: 100 },
      ],
      axisName: { color: cs.value.labelColor, fontSize: 11 },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(88,166,255,.05)'] } },
    },
    series: [{ type: 'radar', data: [{ name: '仓位管理质量', value: values, areaStyle: { color: 'rgba(88,166,255,.18)' }, lineStyle: { color: '#58a6ff', width: 2 } }] }],
  }, true)
}

function renderManagementAllocation() {
  const c = chart('managementAllocation', managementAllocationRef)
  if (!c) return
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption('暂无持仓'))
  const total = managementMetrics.value.totalNotional || 1
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>名义: ${fmtMoney(p.value)}<br/>占比: ${p.percent}%` },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: cs.value.legendColor } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '43%'],
      data: rows.map(row => {
        const weight = Math.abs(num(row.notional)) / total * 100
        return { name: positionLabel(row), value: Math.abs(num(row.notional)), itemStyle: { color: allocationColor(weight) } }
      }),
      label: { color: cs.value.labelColor, formatter: p => `${p.name}\n${p.percent}%` },
    }],
  }, true)
}

function renderManagementAction() {
  const c = chart('managementAction', managementActionRef)
  if (!c) return
  const rows = managementPlanRows.value
  if (!rows.length) return c.setOption(emptyOption('暂无管理动作'))
  const labels = ['止损/减仓', '降低风险', '禁止补仓', '观察持有', '顺势持有', '可小幅加仓']
  const counts = labels.map(label => rows.filter(row => row.action === label).length)
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 18, top: 24, bottom: 58 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 28, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: counts,
      barMaxWidth: 24,
      itemStyle: { color: p => actionColor(labels[p.dataIndex]), borderRadius: [4,4,0,0] },
      label: { show: true, position: 'top', color: cs.value.labelColor },
    }],
  }, true)
}

function renderManagementBuffer() {
  const c = chart('managementBuffer', managementBufferRef)
  if (!c) return
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption('暂无持仓'))
  const maxNotional = Math.max(...rows.map(row => Math.abs(num(row.notional))), 1)
  c.setOption({
    tooltip: { formatter: p => `${p.name}<br/>杠杆: ${p.value[0]}x<br/>爆仓缓冲: ${fmtPct(p.value[1])}<br/>浮盈亏: ${fmtPnl(p.value[2])}<br/>管理动作: ${p.data.action}` },
    grid: { left: 60, right: 24, top: 26, bottom: 46 },
    xAxis: { type: 'value', name: '杠杆', min: 0, axisLabel: { formatter: v => `${v}x` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '爆仓缓冲', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'scatter',
      data: rows.map(row => {
        const plan = managementPlanFor(row, managementMetrics.value.totalNotional)
        return {
          name: positionLabel(row),
          value: [num(row.lever), num(row.liquidationDistancePct), num(row.upl), Math.abs(num(row.notional))],
          symbolSize: bubbleSize(Math.abs(num(row.notional)), maxNotional),
          action: plan.action,
          itemStyle: { color: plan.color, opacity: .78 },
          label: { show: true, formatter: plan.symbol, position: 'right', color: cs.value.labelColor, fontSize: 10 },
        }
      }),
      markArea: {
        silent: true,
        itemStyle: { color: 'rgba(248,81,73,.08)' },
        data: [[{ yAxis: 0 }, { yAxis: 8 }]],
      },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { color: cs.value.legendColor, fontSize: 10 },
        lineStyle: { color: '#d29922', type: 'dashed' },
        data: [{ yAxis: 15, name: '安全缓冲线' }],
      },
    }],
  }, true)
}

function renderManagementTarget() {
  const c = chart('managementTarget', managementTargetRef)
  if (!c) return
  const rows = managementPlanRows.value
  if (!rows.length) return c.setOption(emptyOption('暂无目标仓位'))
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>当前占比: ${p[0].value}%<br/>目标占比: ${p[1].value}%<br/>${rows[p[0].dataIndex]?.sizeRule || ''}` },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 58, right: 24, top: 38, bottom: 54 },
    xAxis: { type: 'category', data: rows.map(row => row.symbol), axisLabel: { rotate: 20, fontSize: 10 } },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '当前占比', type: 'bar', data: rows.map(row => row.weight), barMaxWidth: 20, itemStyle: { color: p => rows[p.dataIndex].color, borderRadius: [4,4,0,0] } },
      { name: '建议目标', type: 'line', data: rows.map(row => row.targetWeight), symbolSize: 8, lineStyle: { color: '#58a6ff', width: 2 }, itemStyle: { color: '#58a6ff' } },
    ],
  }, true)
}

function renderManagementRiskBudget() {
  const c = chart('managementRiskBudget', managementRiskBudgetRef)
  if (!c) return
  const rows = managementPlanRows.value
  if (!rows.length) return c.setOption(emptyOption('暂无风险预算'))
  c.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: p => {
        const row = rows[p[0].dataIndex]
        return `${p[0].name}<br/>当前风险: ${row.currentRiskPct}%<br/>建议风险: ${row.targetRiskPct}%<br/>${row.riskBudget}`
      },
    },
    legend: { top: 0, textStyle: { color: cs.value.legendColor } },
    grid: { left: 58, right: 24, top: 38, bottom: 54 },
    xAxis: { type: 'category', data: rows.map(row => row.symbol), axisLabel: { rotate: 20, fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [
      { name: '当前风险', type: 'bar', data: rows.map(row => row.currentRiskPct), barMaxWidth: 20, itemStyle: { color: p => rows[p.dataIndex].color, borderRadius: [4,4,0,0] } },
      { name: '建议风险', type: 'line', data: rows.map(row => row.targetRiskPct), symbolSize: 8, lineStyle: { color: '#58a6ff', width: 2 }, itemStyle: { color: '#58a6ff' } },
      {
        name: '单笔预算',
        type: 'line',
        data: rows.map(() => POSITION_RISK_BUDGET_PCT),
        symbol: 'none',
        lineStyle: { color: '#d29922', type: 'dashed' },
      },
    ],
  }, true)
}

function renderManagementDiscipline() {
  const c = chart('managementDiscipline', managementDisciplineRef)
  if (!c) return
  const m = managementMetrics.value
  if (!m.rows.length) return c.setOption(emptyOption('暂无纪律评分'))
  const items = [
    { name: '高杠杆', value: clamp((m.avgLeverage - MAX_LEVERAGE_NORMAL) * 6, 0, 100), color: '#f85149' },
    { name: '近强平', value: m.minLiq ? clamp((MIN_LIQ_BUFFER_PCT - m.minLiq) * 8, 0, 100) : 0, color: '#f0883e' },
    { name: '亏损仓', value: clamp(m.lossRatio, 0, 100), color: '#d29922' },
    { name: '补仓', value: clamp(m.addRatio, 0, 100), color: '#ff7b72' },
    { name: '逆势', value: clamp(100 - m.alignedRatio, 0, 100), color: '#a371f7' },
    { name: '集中', value: clamp((m.maxWeight - MAX_SINGLE_POSITION_WEIGHT) * 2, 0, 100), color: '#58a6ff' },
  ]
  c.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>扣分压力: ${p[0].value}` },
    grid: { left: 54, right: 18, top: 22, bottom: 52 },
    xAxis: { type: 'category', data: items.map(i => i.name), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{
      type: 'bar',
      data: items.map(i => i.value),
      barMaxWidth: 24,
      itemStyle: { color: p => items[p.dataIndex].color, borderRadius: [4,4,0,0] },
      label: { show: true, position: 'top', color: cs.value.labelColor, fontSize: 10 },
    }],
  }, true)
}

function renderManagementExperience() {
  const c = chart('managementExperience', managementExperienceRef)
  if (!c) return
  const m = managementMetrics.value
  if (!m.rows.length) return c.setOption(emptyOption('暂无规则匹配'))
  const values = [
    clamp(100 - Math.max(0, m.totalCurrentRisk - PORTFOLIO_RISK_BUDGET_PCT) * 18, 0, 100),
    clamp(100 - Math.max(0, m.maxWeight - MAX_SINGLE_POSITION_WEIGHT) * 2, 0, 100),
    clamp(m.minLiq ? m.minLiq / MIN_LIQ_BUFFER_PCT * 100 : 55, 0, 100),
    clamp(100 - Math.max(0, m.avgLeverage - MAX_LEVERAGE_NORMAL) * 7, 0, 100),
    clamp(100 - m.lossRatio, 0, 100),
    clamp(100 - m.addRatio, 0, 100),
  ].map(v => round(v, 1))
  c.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '60%',
      indicator: [
        { name: '风险预算', max: 100 },
        { name: '集中度', max: 100 },
        { name: '爆仓缓冲', max: 100 },
        { name: '杠杆克制', max: 100 },
        { name: '亏损控制', max: 100 },
        { name: '补仓纪律', max: 100 },
      ],
      axisName: { color: cs.value.labelColor, fontSize: 11 },
      splitLine: { lineStyle: { color: cs.value.gridLine } },
      axisLine: { lineStyle: { color: cs.value.gridLine } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(63,185,80,.05)'] } },
    },
    series: [{
      type: 'radar',
      data: [{
        name: '成熟规则匹配度',
        value: values,
        areaStyle: { color: 'rgba(63,185,80,.16)' },
        lineStyle: { color: '#3fb950', width: 2 },
      }],
    }],
  }, true)
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
      { name: '盈亏比', type: 'line', yAxisIndex: 1, data: ratios, smooth: false, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#d29922', width: 2 }, itemStyle: { color: '#d29922' } },
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
      { name: '补仓次数', type: 'bar', data: rows.map(row => num(row.addCount)), barMaxWidth: 24, itemStyle: { color: '#d29922', borderRadius: [4, 4, 0, 0] } },
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
      { name: '杠杆', type: 'line', yAxisIndex: 1, data: rows.map(row => num(row.lever)), smooth: false, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#d29922', width: 2 }, itemStyle: { color: '#d29922' } },
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

function renderRiskMatrix() {
  const c = chart('riskMatrix', riskMatrixRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  c.setOption({
    tooltip: { formatter: p => `${p.name}<br/>杠杆: ${p.value[0]}x<br/>爆仓距: ${fmtPct(p.value[1])}<br/>浮盈亏: ${fmtPnl(p.value[2])}` },
    grid: { left: 54, right: 18, top: 18, bottom: 42 },
    xAxis: { type: 'value', name: '杠杆', axisLabel: { formatter: v => `${v}x` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '爆仓距', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'scatter', data: rows.map(r => ({ name: positionLabel(r), value: [num(r.lever), num(r.liquidationDistancePct), num(r.upl), Math.abs(num(r.notional))] })), symbolSize: p => bubbleSize(p[3], Math.max(...rows.map(r => Math.abs(num(r.notional))), 1)), itemStyle: { color: p => p.value[2] >= 0 ? '#3fb950' : '#f85149', opacity: .78 } }]
  })
}

function renderExposure() {
  const c = chart('exposure', exposureRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const long = rows.filter(r => r.direction === 'long').reduce((s, r) => s + Math.abs(num(r.notional)), 0)
  const short = rows.filter(r => r.direction === 'short').reduce((s, r) => s + Math.abs(num(r.notional)), 0)
  c.setOption({
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${fmtMoney(p.value)} (${p.percent}%)` },
    legend: { bottom: 0, textStyle: { color: cs.value.legendColor } },
    series: [{ type: 'pie', radius: ['45%', '72%'], center: ['50%', '43%'], data: [{ name: '多头敞口', value: long, itemStyle: { color: '#3fb950' } }, { name: '空头敞口', value: short, itemStyle: { color: '#f85149' } }], label: { color: cs.value.labelColor } }]
  })
}

function renderLeverageDist() {
  const c = chart('leverageDist', leverageDistRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const buckets = ['1-3x', '4-10x', '11-30x', '30x+']
  const values = [
    rows.filter(r => num(r.lever) <= 3).length,
    rows.filter(r => num(r.lever) > 3 && num(r.lever) <= 10).length,
    rows.filter(r => num(r.lever) > 10 && num(r.lever) <= 30).length,
    rows.filter(r => num(r.lever) > 30).length,
  ]
  c.setOption(simpleBarOption(buckets, values, '#d29922'))
}

function renderPnlLiqBubble() {
  const c = chart('pnlLiqBubble', pnlLiqBubbleRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const maxNotional = Math.max(...rows.map(r => Math.abs(num(r.notional))), 1)
  c.setOption({
    tooltip: { formatter: p => `${p.name}<br/>盈亏比: ${fmtPct(p.value[0])}<br/>爆仓距: ${fmtPct(p.value[1])}<br/>名义: ${fmtMoney(p.value[2])}` },
    grid: { left: 62, right: 18, top: 18, bottom: 44 },
    xAxis: { type: 'value', name: '盈亏比', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    yAxis: { type: 'value', name: '爆仓距', axisLabel: { formatter: v => `${v}%` }, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'scatter', data: rows.map(r => ({ name: positionLabel(r), value: [num(r.uplRatioPct), num(r.liquidationDistancePct), Math.abs(num(r.notional))] })), symbolSize: p => bubbleSize(p[2], maxNotional), itemStyle: { color: p => p.value[0] >= 0 ? '#3fb950' : '#f85149', opacity: .72 } }]
  })
}

function renderPositionHeat() {
  const c = chart('positionHeat', positionHeatRef)
  const rows = tableRows.value
  if (!rows.length) return c.setOption(emptyOption())
  const cols = ['亏损', '补仓', '高杠杆', '近爆仓']
  const data = []
  rows.forEach((row, y) => {
    data.push([0, y, num(row.upl) < 0 ? 1 : 0])
    data.push([1, y, num(row.addCount) > 1 ? 1 : 0])
    data.push([2, y, num(row.lever) >= 20 ? 1 : 0])
    data.push([3, y, num(row.liquidationDistancePct) > 0 && num(row.liquidationDistancePct) < 8 ? 1 : 0])
  })
  c.setOption({
    tooltip: { formatter: p => `${rows[p.value[1]] ? positionLabel(rows[p.value[1]]) : ''}<br/>${cols[p.value[0]]}: ${p.value[2] ? '是' : '否'}` },
    grid: { left: 90, right: 20, top: 18, bottom: 34 },
    xAxis: { type: 'category', data: cols, axisLabel: { color: cs.value.labelColor } },
    yAxis: { type: 'category', data: rows.map(positionLabel), axisLabel: { color: cs.value.labelColor, fontSize: 10 } },
    visualMap: { min: 0, max: 1, show: false, inRange: { color: [isDark.value ? '#161b22' : '#f8fafc', '#f85149'] } },
    series: [{ type: 'heatmap', data, label: { show: true, formatter: p => p.value[2] ? '!' : '', color: '#fff' } }]
  })
}

function buildStrategyDataset(symbol, candles) {
  const bars = candles.map(c => ({
    ts: num(c[0]),
    open: num(c[1]),
    high: num(c[2]),
    low: num(c[3]),
    close: num(c[4]),
    volume: num(c[5]),
  })).filter(i => i.close)
  const closes = bars.map(i => i.close)
  const highs = bars.map(i => i.high)
  const lows = bars.map(i => i.low)
  const volumes = bars.map(i => i.volume)
  const labels = bars.map(i => fmtTime(i.ts))
  const ema20 = emaSeries(closes, 20)
  const ema50 = emaSeries(closes, 50)
  const ema100 = emaSeries(closes, 100)
  const boll = bollinger(closes, 20, 2)
  const atr = atrSeries(bars, 14)
  const volMa20 = smaSeries(volumes, 20)
  const volumeShock = volumes.map((v, i) => round(v / ((volMa20[i] || v || 1)), 2))
  const close = lastValue(closes)
  const atrNow = lastValue(atr)
  const low20 = lowest(lows, 20)
  const high20 = highest(highs, 20)
  const support = low20 - atrNow * 0.35
  const resistance = high20 + atrNow * 0.35
  const ema20Now = lastValue(ema20)
  const ema50Now = lastValue(ema50)
  const ema100Now = lastValue(ema100)
  const bollMidNow = lastValue(boll.mid)
  const emaBull = close >= ema20Now && ema20Now >= ema50Now
  const emaBear = close <= ema20Now && ema20Now <= ema50Now
  return {
    symbol,
    bars,
    labels,
    closes,
    highs,
    lows,
    volumes,
    ema20,
    ema50,
    ema100,
    bollUpper: boll.upper,
    bollMid: boll.mid,
    bollLower: boll.lower,
    atr,
    volMa20,
    volumeShock,
    ohlc: bars.map(i => [i.open, i.close, i.low, i.high]),
    close,
    atrNow,
    support,
    resistance,
    stopLine: support - atrNow * 0.55,
    targetLine: resistance + atrNow * 0.65,
    ema20Now,
    ema50Now,
    ema100Now,
    bollMidNow,
    emaBull,
    emaBear,
    emaText: emaBull ? 'EMA多头排列' : emaBear ? 'EMA空头排列' : 'EMA纠缠',
    volumeText: lastValue(volumeShock) >= 1.5 ? '放量' : lastValue(volumeShock) <= 0.7 ? '缩量' : '量能正常',
  }
}

function strategyAction(s, p) {
  if (!s) return { label: '-', score: 0, color: '#8b949e', reason: '等待行情', fullText: '等待行情数据。', targetNote: '-', lookTo: '-' }
  const major = majorReading.value
  const direction = p?.direction || (s.emaBull ? 'long' : s.emaBear ? 'short' : 'neutral')
  const bollScore = s.close >= s.bollMidNow ? 16 : -16
  const emaScore = s.emaBull ? 22 : s.emaBear ? -22 : 0
  const majorScore = major.biasScore * 0.35
  const volumeScore = lastValue(s.volumeShock) >= 1.25 ? (s.close >= s.bollMidNow ? 10 : -6) : 0
  const positionPenalty = p && num(p.upl) < 0 && num(p.addCount) > 1 ? -16 : 0
  const raw = clamp(bollScore + emaScore + majorScore + volumeScore + positionPenalty, -100, 100)
  const aligned = direction === 'long' ? raw : direction === 'short' ? -raw : raw
  const score = Math.round(aligned)
  const longMode = direction !== 'short'
  const label = score >= 35 ? '顺势持有' : score >= 12 ? '轻仓观察' : score <= -35 ? '减仓/止损' : score <= -12 ? '降低风险' : '等待确认'
  const color = score >= 20 ? '#3fb950' : score <= -20 ? '#f85149' : '#d29922'
  const lookTo = longMode
    ? (score >= 12 ? `先看压力 ${fmtPrice(s.resistance)}，突破看 ${fmtPrice(s.targetLine)}` : `先守支撑 ${fmtPrice(s.support)}`)
    : (score >= 12 ? `先看支撑 ${fmtPrice(s.support)}，跌破看 ${fmtPrice(s.stopLine)}` : `先看压力 ${fmtPrice(s.resistance)}`)
  const reason = `${s.emaText}，${bollCrossText(s)}，${s.volumeText}，${major.summary}`
  return {
    label,
    score,
    color,
    reason,
    lookTo,
    targetNote: lookTo,
    fullText: `${symbolName(s.symbol)} 当前建议：${label}。${reason}。${lookTo}。`,
  }
}

function bollCrossText(s) {
  if (!s) return '-'
  const dist = s.bollMidNow ? (s.close - s.bollMidNow) / s.bollMidNow * 100 : 0
  const slope = slopePct(s.bollMid, 8)
  if (Math.abs(dist) <= 0.18) return `正在测试中线，距离 ${dist.toFixed(2)}%`
  if (s.close > s.bollMidNow) return `已站上中线 ${dist.toFixed(2)}%，中线斜率 ${slope.toFixed(2)}%`
  return `尚未过中线 ${dist.toFixed(2)}%，中线斜率 ${slope.toFixed(2)}%`
}

function lineSeries(name, data, color, extra = {}) {
  return {
    name,
    type: 'line',
    data,
    smooth: false,
    symbol: 'none',
    lineStyle: { color, width: 1.7, ...extra },
  }
}

function normalizeOkxData(raw) {
  if (Array.isArray(raw)) return raw
  if (Array.isArray(raw?.data)) return raw.data
  return []
}

function emaSeries(values, period) {
  if (!values.length) return []
  const k = 2 / (period + 1)
  const out = [values[0]]
  for (let i = 1; i < values.length; i++) out.push(values[i] * k + out[i - 1] * (1 - k))
  return out
}

function smaSeries(values, period) {
  return values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - period + 1), i + 1)
    return slice.reduce((total, value) => total + value, 0) / (slice.length || 1)
  })
}

function bollinger(values, period, mult) {
  const mid = smaSeries(values, period)
  const upper = values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - period + 1), i + 1)
    const mean = mid[i] || 0
    const std = Math.sqrt(slice.reduce((total, value) => total + (value - mean) ** 2, 0) / (slice.length || 1))
    return mean + std * mult
  })
  const lower = values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - period + 1), i + 1)
    const mean = mid[i] || 0
    const std = Math.sqrt(slice.reduce((total, value) => total + (value - mean) ** 2, 0) / (slice.length || 1))
    return mean - std * mult
  })
  return { mid, upper, lower }
}

function atrSeries(bars, period) {
  const tr = bars.map((bar, i) => {
    if (i === 0) return bar.high - bar.low
    const prev = bars[i - 1].close
    return Math.max(bar.high - bar.low, Math.abs(bar.high - prev), Math.abs(bar.low - prev))
  })
  return smaSeries(tr, period)
}

function highest(values, period) {
  return values.slice(-period).reduce((max, value) => Math.max(max, value), Number.NEGATIVE_INFINITY)
}

function lowest(values, period) {
  return values.slice(-period).reduce((min, value) => Math.min(min, value), Number.POSITIVE_INFINITY)
}

function slopePct(values, lookback = 12) {
  if (!values.length) return 0
  const latest = lastValue(values)
  const prev = values[Math.max(0, values.length - lookback - 1)]
  return prev ? (latest - prev) / Math.abs(prev) * 100 : 0
}

function takerImbalance(rows) {
  const buy = rows.reduce((total, row) => total + num(row.buyVol), 0)
  const sell = rows.reduce((total, row) => total + num(row.sellVol), 0)
  return buy + sell ? (buy - sell) / (buy + sell) * 100 : 0
}

function alignSeries(values, len) {
  if (values.length >= len) return values.slice(values.length - len)
  return [...Array(len - values.length).fill(null), ...values]
}

function fmtTime(ts) {
  const n = num(ts)
  return n ? dayjs(n).format('MM-DD HH:mm') : '-'
}

function compactMoney(v) {
  const n = Math.abs(num(v))
  if (n >= 1e9) return `${(num(v) / 1e9).toFixed(1)}B`
  if (n >= 1e6) return `${(num(v) / 1e6).toFixed(1)}M`
  if (n >= 1e3) return `${(num(v) / 1e3).toFixed(1)}K`
  return fmtNum(v, 0)
}

function simpleBarOption(labels, values, color) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 16, top: 18, bottom: 42 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: cs.value.gridLine } } },
    series: [{ type: 'bar', data: values, barMaxWidth: 28, itemStyle: { color, borderRadius: [4,4,0,0] }, label: { show: true, position: 'top', color: cs.value.labelColor } }]
  }
}

function chart(key, elRef) {
  if (!elRef.value) return null
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

function positionRiskLabel(row) {
  const score = positionRiskScore(row)
  if (score >= 75) return '极高风险'
  if (score >= 50) return '高风险'
  if (score >= 25) return '中风险'
  return '低风险'
}

function positionRiskType(row) {
  const score = positionRiskScore(row)
  if (score >= 75) return 'danger'
  if (score >= 50) return 'warning'
  if (score >= 25) return 'info'
  return 'success'
}

function managementPlanFor(row, totalNotional) {
  const action = managementActionFor(row, totalNotional)
  const weight = totalNotional ? Math.abs(num(row.notional)) / totalNotional * 100 : 0
  const currentRiskPct = estimatedPositionRiskPct(row, totalNotional)
  const targetRiskPct = managementTargetRiskPct(row, action)
  const formulaWeight = formulaTargetWeight(row, weight, targetRiskPct)
  const targetWeight = Math.min(managementTargetWeight(row, weight), formulaWeight)
  const liq = num(row.liquidationDistancePct)
  const leverage = num(row.lever)
  const pnlPct = num(row.uplRatioPct)
  const isLoss = num(row.upl) < 0
  const isAlignedRow = row.alignment === '顺势'
  const addCount = num(row.addCount)
  const invalidLine = managementInvalidLine(row)
  const reducePct = Math.max(0, weight - targetWeight)
  const ruleFit = managementRuleFit(row, currentRiskPct, weight)
  return {
    symbol: symbolName(row.instId),
    action: action.label,
    role: action.role,
    type: action.type,
    color: action.color,
    weight: round(weight, 1),
    targetWeight: round(targetWeight, 1),
    currentRiskPct: round(currentRiskPct, 2),
    targetRiskPct: round(targetRiskPct, 2),
    ruleFit: round(ruleFit, 1),
    current: `${row.direction === 'long' ? '多' : '空'} | ${fmtPct(pnlPct)} | ${fmtMoney(Math.abs(num(row.notional)))}`,
    riskBudget: `当前约 ${round(currentRiskPct, 2)}%，目标 ${round(targetRiskPct, 2)}%`,
    invalidLine: invalidLine ? `${fmtPrice(invalidLine)}（结构失效）` : '等待关键位',
    sizeRule: targetWeight < weight
      ? `降至 ${targetWeight.toFixed(1)}% 左右，先减 ${reducePct.toFixed(1)}%`
      : targetWeight > weight
        ? `最多加至 ${targetWeight.toFixed(1)}%，必须分批`
        : `维持 ${weight.toFixed(1)}%`,
    addRule: isLoss
      ? '亏损仓不补仓；只有重新顺势并站回关键均线后再评估。'
      : isAlignedRow && liq >= 18 && addCount <= 1
        ? '盈利顺势且缓冲充足，可用 1/3 原仓以内试探加仓。'
        : '不主动加仓，等待方向、量能、关键位共振。',
    defense: liq > 0 && liq < 10
      ? `爆仓距仅 ${fmtPct(liq)}，先降杠杆/减仓，防守优先于利润。`
      : leverage >= 15
        ? `${leverage.toFixed(1)}x 杠杆偏高，止损必须前置，不允许扛单。`
        : `爆仓距 ${liq ? fmtPct(liq) : '未推算'}，按计划止损执行。`,
    note: action.note,
  }
}

function managementActionFor(row, totalNotional) {
  const risk = positionRiskScore(row)
  const weight = totalNotional ? Math.abs(num(row.notional)) / totalNotional * 100 : 0
  const liq = num(row.liquidationDistancePct)
  const isLoss = num(row.upl) < 0
  const pnlPct = num(row.uplRatioPct)
  const addCount = num(row.addCount)
  const aligned = row.alignment === '顺势'
  const diverged = isDivergence(row.divergence)
  if ((liq > 0 && liq < 8) || risk >= 75) {
    return { label: '止损/减仓', level: 5, role: '危险仓', type: 'danger', color: '#f85149', note: '强平缓冲或综合风险过高，先把账户存活放在第一位。' }
  }
  if ((isLoss && diverged) || (isLoss && addCount > 1)) {
    return { label: '降低风险', level: 4, role: '问题仓', type: 'danger', color: '#f85149', note: '亏损叠加背离/补仓，容易从交易变成扛单，先减仓再等确认。' }
  }
  if (isLoss) {
    return { label: '禁止补仓', level: 3, role: '观察仓', type: 'warning', color: '#d29922', note: '亏损仓不扩大风险，只能等失效位、均线或结构重新确认。' }
  }
  if (weight >= 70) {
    return { label: '降低风险', level: 4, role: '集中仓', type: 'warning', color: '#d29922', note: '单仓占比过高，即使盈利也要防止一次波动吞掉账户。' }
  }
  if (aligned && pnlPct > 0 && liq >= 18 && risk < 35) {
    return { label: '可小幅加仓', level: 1, role: '顺势仓', type: 'success', color: '#3fb950', note: '只允许盈利顺势仓加仓，加仓后总风险不能超过原计划。' }
  }
  if (aligned && pnlPct >= 0) {
    return { label: '顺势持有', level: 1, role: '核心仓', type: 'success', color: '#3fb950', note: '顺势仓以移动止损保护利润，不急于落袋也不盲目加码。' }
  }
  return { label: '观察持有', level: 2, role: '中性仓', type: 'info', color: '#58a6ff', note: '方向和盈亏没有形成强共振，先保持轻仓观察。' }
}

function managementTargetWeight(row, currentWeight) {
  const action = managementActionFor(row, managementMetrics.value.totalNotional)
  if (action.label === '止损/减仓') return Math.max(0, Math.min(currentWeight * 0.35, 25))
  if (action.label === '降低风险') return Math.max(0, Math.min(currentWeight * 0.55, 35))
  if (action.label === '禁止补仓') return Math.min(currentWeight, 25)
  if (action.label === '可小幅加仓') return Math.min(45, currentWeight + 12)
  if (action.label === '顺势持有') return Math.min(45, currentWeight)
  return Math.min(30, currentWeight)
}

function managementTargetRiskPct(row, action) {
  if (action.label === '止损/减仓') return 0.25
  if (action.label === '降低风险') return 0.5
  if (action.label === '禁止补仓') return 0.75
  if (action.label === '可小幅加仓') return POSITION_RISK_BUDGET_PCT
  if (action.label === '顺势持有') return POSITION_RISK_BUDGET_PCT
  return 0.65
}

function estimatedPositionRiskPct(row, totalNotional) {
  const total = totalNotional || managementMetrics.value.totalNotional || 1
  const weight = Math.abs(num(row.notional)) / total * 100
  const liq = num(row.liquidationDistancePct)
  const pnlDrawdown = Math.max(0, -num(row.uplRatioPct))
  const structuralRisk = liq > 0 ? Math.max(2, Math.min(liq, 18)) : Math.max(3, Math.min(pnlDrawdown, 12))
  return weight * structuralRisk / 100
}

function formulaTargetWeight(row, currentWeight, targetRiskPct) {
  const liq = num(row.liquidationDistancePct)
  const stopDistance = liq > 0 ? Math.max(2, Math.min(liq * 0.72, 14)) : 6
  const raw = targetRiskPct / stopDistance * 100
  const leveragePenalty = num(row.lever) > MAX_LEVERAGE_NORMAL ? MAX_LEVERAGE_NORMAL / num(row.lever) : 1
  const directionPenalty = row.alignment === '顺势' ? 1 : 0.55
  const lossPenalty = num(row.upl) < 0 ? 0.65 : 1
  return Math.max(0, Math.min(currentWeight, raw * leveragePenalty * directionPenalty * lossPenalty, MAX_SINGLE_POSITION_WEIGHT))
}

function managementInvalidLine(row) {
  const mark = num(row.markPx)
  const liq = num(row.liqPx)
  if (!mark) return 0
  if (liq > 0) {
    const buffer = Math.abs(mark - liq) * 0.42
    return row.direction === 'short' ? mark + buffer : mark - buffer
  }
  const pct = Math.max(1.2, Math.min(4, 100 / Math.max(num(row.lever), 1)))
  return row.direction === 'short' ? mark * (1 + pct / 100) : mark * (1 - pct / 100)
}

function managementRuleFit(row, currentRiskPct, weight) {
  let score = 100
  if (currentRiskPct > POSITION_RISK_BUDGET_PCT) score -= Math.min(35, (currentRiskPct - POSITION_RISK_BUDGET_PCT) * 14)
  if (weight > MAX_SINGLE_POSITION_WEIGHT) score -= Math.min(25, (weight - MAX_SINGLE_POSITION_WEIGHT) * 0.8)
  if (num(row.lever) > MAX_LEVERAGE_NORMAL) score -= Math.min(25, (num(row.lever) - MAX_LEVERAGE_NORMAL) * 0.6)
  if (num(row.liquidationDistancePct) > 0 && num(row.liquidationDistancePct) < MIN_LIQ_BUFFER_PCT) score -= Math.min(25, (MIN_LIQ_BUFFER_PCT - num(row.liquidationDistancePct)) * 2)
  if (num(row.upl) < 0) score -= 12
  if (num(row.addCount) > 1) score -= Math.min(18, num(row.addCount) * 3)
  if (row.alignment !== '顺势') score -= 15
  return clamp(score, 0, 100)
}

function actionColor(label) {
  if (label === '止损/减仓' || label === '降低风险') return '#f85149'
  if (label === '禁止补仓') return '#d29922'
  if (label === '可小幅加仓' || label === '顺势持有') return '#3fb950'
  return '#58a6ff'
}

function allocationColor(weight) {
  if (weight >= 70) return '#f85149'
  if (weight >= 45) return '#d29922'
  if (weight >= 25) return '#58a6ff'
  return '#3fb950'
}

function positionRiskScore(row) {
  let score = 0
  if (num(row.upl) < 0) score += 22
  if (num(row.uplRatioPct) < -20) score += 18
  if (num(row.addCount) > 1) score += 18
  if (isDivergence(row.divergence)) score += 18
  if (num(row.lever) >= 20) score += 14
  if (num(row.liquidationDistancePct) > 0 && num(row.liquidationDistancePct) < 8) score += 20
  if (num(row.holdingMinutes) > 24 * 60) score += 10
  return clamp(score, 0, 100)
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

function lastValue(values) {
  return Array.isArray(values) && values.length ? num(values[values.length - 1]) : 0
}

function last(values) {
  return Array.isArray(values) && values.length ? values[values.length - 1] : null
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
.position-tabs { animation: none; }
.position-tabs :deep(.el-tabs__item) { color: var(--text-secondary); font-weight: 700; }
.position-tabs :deep(.el-tabs__item.is-active) { color: var(--accent-blue); }
.position-tabs :deep(.el-tabs__active-bar) { background: var(--accent-blue); }
.filter-item { display: flex; align-items: center; gap: 8px; }
.last-update { font-size: 12px; }
.strategy-filter-card { padding: 12px 16px; margin-bottom: 14px; }
.strategy-select { width: 100%; }
.strategy-update { margin-left: 10px; font-size: 12px; }
.strategy-card-row { margin-bottom: 14px; }
.strategy-stat { text-align: center; min-height: 112px; padding: 15px 12px; }
.strategy-stat-value { font-size: 21px; white-space: nowrap; }
.management-card-row { margin-bottom: 14px; }
.management-stat { text-align: center; min-height: 108px; padding: 14px 12px; }
.management-stat-value { font-size: 21px; white-space: nowrap; }
.management-guidance-card { padding: 16px; }
.management-title { justify-content: space-between; align-items: center; }
.management-title > div { display: flex; align-items: center; min-width: 0; }
.management-headline {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(88,166,255,.08), rgba(63,185,80,.04));
  padding: 12px 14px;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.55;
  margin-bottom: 12px;
}
.management-rules {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.management-rule {
  min-height: 92px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-main);
  padding: 10px 12px;
}
.management-rule span {
  display: block;
  color: var(--text-secondary);
  font-size: 11px;
  margin-bottom: 6px;
}
.management-rule strong {
  display: block;
  color: var(--text-heading);
  font-size: 14px;
  margin-bottom: 6px;
}
.management-rule em {
  color: var(--text-dim);
  font-size: 12px;
  line-height: 1.45;
  font-style: normal;
}
.compact-management-card { padding: 14px; }
.strategy-ai-card { padding: 16px; }
.strategy-report {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(88,166,255,.08), rgba(63,185,80,.045));
  padding: 14px;
}
.report-main {
  font-size: 16px;
  font-weight: 800;
  line-height: 1.55;
  margin-bottom: 12px;
}
.report-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.report-item {
  min-height: 58px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(255,255,255,.025);
  padding: 9px 10px;
}
.report-item span {
  display: block;
  color: var(--text-secondary);
  font-size: 11px;
  margin-bottom: 5px;
}
.report-item strong {
  color: var(--text-heading);
  font-size: 12px;
  line-height: 1.45;
}
.compact-strategy-card { padding: 14px; }
.info-card { text-align: center; }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 4px; }
.stat-sub { font-size: 11px; color: var(--text-dim); }
.hint { font-size: 11px; color: var(--text-dim); font-weight: 400; margin-left: 8px; }
.ai-title { justify-content: space-between; align-items: center; }
.ai-title > div { display: flex; align-items: center; min-width: 0; }
.ai-layout { align-items: stretch; }
.ai-left,
.ai-right { display: flex; flex-direction: column; }
.ai-chart { height: 360px; min-height: 360px; }
.ai-result { min-height: 360px; width: 100%; }
.ai-stream {
  min-height: 360px;
  width: 100%;
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
  max-height: 302px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  line-height: 1.65;
  margin: 0;
}
.ai-progress {
  min-height: 300px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-primary);
}
.ai-progress-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #58a6ff;
  box-shadow: 0 0 0 0 rgba(88,166,255,.55);
  animation: pulse 1.2s infinite;
}
.ai-progress-title { font-size: 16px; font-weight: 700; margin-bottom: 8px; }
.ai-progress-sub { color: var(--text-secondary); font-size: 13px; }
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(88,166,255,.55); }
  70% { box-shadow: 0 0 0 14px rgba(88,166,255,0); }
  100% { box-shadow: 0 0 0 0 rgba(88,166,255,0); }
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
.ai-list-item.warning::before { background: #d29922; }
.ai-placeholder { height: 360px; display: flex; align-items: center; justify-content: center; }
.ai-placeholder-title { color: var(--text-dim); font-size: 13px; }
</style>
