import { computed } from 'vue'
import { useThemeState } from './useThemeState'
import * as echarts from 'echarts/core'
import {
  BarChart,
  CandlestickChart,
  GaugeChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  ScatterChart,
} from 'echarts/charts'
import {
  CalendarComponent,
  DataZoomComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  MarkPointComponent,
  PolarComponent,
  RadarComponent,
  SingleAxisComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  CandlestickChart,
  GaugeChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  ScatterChart,
  CalendarComponent,
  DataZoomComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  MarkPointComponent,
  PolarComponent,
  RadarComponent,
  SingleAxisComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent,
  VisualMapComponent,
  CanvasRenderer,
])

export function useTheme() {
  const { isDark, applyTheme, toggleTheme } = useThemeState()

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
    heatmap:     ['#ddf4ff', '#79c0ff', '#0969da'],
  })

  function initChart(el) {
    if (!el) return null
    const renderer = 'canvas'
    const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 1.5)
    return echarts.init(el, cs.value.echarts, { backgroundColor: 'transparent', renderer, devicePixelRatio })
  }

  function toggle() {
    toggleTheme()
  }

  return { isDark, cs, initChart, toggle }
}
