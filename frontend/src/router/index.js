import { createRouter, createWebHistory } from 'vue-router'
import Dashboard      from '@/views/Dashboard.vue'
import Analysis       from '@/views/Analysis.vue'
import Orders         from '@/views/Orders.vue'
import Liquidation    from '@/views/Liquidation.vue'
import MarketSentiment from '@/views/MarketSentiment.vue'
import Review         from '@/views/Review.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',            component: Dashboard,       meta: { title: '总览' } },
    { path: '/analysis',   component: Analysis,        meta: { title: '深度分析' } },
    { path: '/orders',     component: Orders,          meta: { title: '交易记录' } },
    { path: '/liquidation', component: Liquidation,    meta: { title: '爆仓分析' } },
    { path: '/market',      component: MarketSentiment, meta: { title: '主力大户' } },
    { path: '/review',      component: Review,          meta: { title: '交易复盘' } },
  ]
})
