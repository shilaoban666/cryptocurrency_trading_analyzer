import { computed } from 'vue'
import { useStorage } from '@vueuse/core'
import * as echarts from 'echarts'

export function useTheme() {
  const isDark = useStorage('okx-theme-dark', true)

  function applyTheme(dark) {
    if (typeof document === 'undefined') return
    document.documentElement.classList.toggle('dark', dark)
  }

  applyTheme(isDark.value)

  const cs = computed(() => isDark.value ? {
    echarts:     'dark',
    labelColor:  '#c9d1d9',
    legendColor: '#8b949e',
    gridLine:    '#21262d',
    dzBorder:    '#21262d',
    dzText:      { color: '#8b949e', fontSize: 10 },
    heatmap:     ['#0d1117', '#1f3a5f', '#58a6ff'],
  } : {
    echarts:     '',
    labelColor:  '#374151',
    legendColor: '#6b7280',
    gridLine:    '#e5e7eb',
    dzBorder:    '#d0d7de',
    dzText:      { color: '#57606a', fontSize: 10 },
    heatmap:     ['#f0f9ff', '#bfdbfe', '#2563eb'],
  })

  function initChart(el) {
    return echarts.init(el, cs.value.echarts, { backgroundColor: 'transparent' })
  }

  function toggle() {
    isDark.value = !isDark.value
    applyTheme(isDark.value)
  }

  return { isDark, cs, initChart, toggle }
}
