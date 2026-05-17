import { createRouter, createWebHistory } from 'vue-router'
const Dashboard = () => import('@/views/Dashboard.vue')
const Balance = () => import('@/views/Balance.vue')
const CurrentPositions = () => import('@/views/CurrentPositions.vue')
const Liquidation = () => import('@/views/Liquidation.vue')
const MarketSentiment = () => import('@/views/MarketSentiment.vue')
const TradingCalendar = () => import('@/views/TradingCalendar.vue')
const LiquidationMaps = () => import('@/views/LiquidationMaps.vue')
const MarketAnalysis = () => import('@/views/MarketAnalysis.vue')
const TradeRecords = () => import('@/views/TradeRecords.vue')
const TradingInsights = () => import('@/views/TradingInsights.vue')

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Dashboard, meta: { title: '总览' } },
    { path: '/balance', component: Balance, meta: { title: '资金变化' } },
    { path: '/calendar', component: TradingCalendar, meta: { title: '交易日历' } },
    { path: '/market-analysis', component: MarketAnalysis, meta: { title: '行情分析' } },
    { path: '/kline-screen', redirect: '/market-analysis' },
    { path: '/liquidation-maps', component: LiquidationMaps, meta: { title: '清算热图' } },
    { path: '/current-market', redirect: '/market-analysis' },
    { path: '/positions', component: CurrentPositions, meta: { title: '当前持仓' } },
    { path: '/analysis', redirect: '/orders' },
    { path: '/orders', component: TradeRecords, meta: { title: '交易分析' } },
    { path: '/insights', component: TradingInsights, meta: { title: '交易心得' } },
    { path: '/liquidation', component: Liquidation, meta: { title: '爆仓分析' } },
    { path: '/market', component: MarketSentiment, meta: { title: '主力大户' } },
    { path: '/review', redirect: '/calendar' },
  ]
})
