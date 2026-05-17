import { useStorage } from '@vueuse/core'

const isDark = useStorage('okx-theme-dark', true)

export function applyTheme(dark) {
  if (typeof document === 'undefined') return
  document.documentElement.classList.toggle('dark', dark)
}

export function toggleTheme() {
  isDark.value = !isDark.value
  applyTheme(isDark.value)
}

applyTheme(isDark.value)

export function useThemeState() {
  return { isDark, applyTheme, toggleTheme }
}
