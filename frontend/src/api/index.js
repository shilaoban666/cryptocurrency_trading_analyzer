import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.response.use(
  res => res.data,
  err => {
    ElMessage.error(err.response?.data?.message || '请求失败')
    return Promise.reject(err)
  }
)

export const syncOrders = (instType = 'all') =>
  http.post(`/sync/${instType}`)

export const getAnalysis = (params = {}) =>
  http.get('/analysis', { params })

export const getOrders = (params = {}) =>
  http.get('/orders', { params })

export const getSymbols = () =>
  http.get('/orders/symbols')

export const getOrderCount = () =>
  http.get('/orders/count')

export const getLiquidation = () =>
  http.get('/liquidation')

export const syncLiquidation = () =>
  http.post('/liquidation/sync')

export const getCandles = (instId, bar = '1H', limit = 300) =>
  http.get('/market/candles', { params: { instId, bar, limit } })

export const getReviewOrders = (params = {}) =>
  http.get('/review/orders', { params })

export const saveReview = (ordId, body) =>
  http.post(`/review/${ordId}`, body)

export const getReviewStats = () =>
  http.get('/review/stats')

// ── OKX 公开市场数据（主力大户分析）──────────────────────────
const okx = axios.create({ baseURL: '/okx', timeout: 30000 })
okx.interceptors.response.use(res => res.data, err => {
  ElMessage.error('OKX数据请求失败')
  return Promise.reject(err)
})

export const getEliteLongShortPosition = (instId, period = '1H', limit = 200) =>
  okx.get('/api/v5/rubik/stat/contracts/long-short-position-ratio-contract-top-trader', { params: { instId, period, limit } })

export const getLongShortAccountRatio = (instId, period = '1H', limit = 200) =>
  okx.get('/api/v5/rubik/stat/contracts/long-short-account-ratio-contract', { params: { instId, period, limit } })

export const getTakerVolume = (instId, period = '1H', limit = 200) =>
  okx.get('/api/v5/rubik/stat/taker-volume-contract', { params: { instId, period, limit } })

export const getFundingRateHistory = (instId, limit = 100) =>
  okx.get('/api/v5/public/funding-rate-history', { params: { instId, limit } })

export const getOpenInterestHistory = (instId, period = '1H', limit = 200) =>
  okx.get('/api/v5/rubik/stat/contracts/open-interest-history', { params: { instId, period, limit } })
