import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** 화면 보기 설정 (설정 서랍 → 앱 설정). 브라우저에 기억해 둠 */
export type FontSize = 'normal' | 'large'
/** system: 휴대폰 설정(다크모드)을 따라감 */
export type Theme = 'light' | 'dark' | 'system'

interface DisplayState {
  fontSize: FontSize
  theme: Theme
  setFontSize: (fontSize: FontSize) => void
  setTheme: (theme: Theme) => void
}

export const useDisplayStore = create<DisplayState>()(
  persist(
    (set) => ({
      fontSize: 'normal',
      theme: 'light',
      setFontSize: (fontSize) => set({ fontSize }),
      setTheme: (theme) => set({ theme }),
    }),
    { name: 'itda-display' },
  ),
)

const systemDark = window.matchMedia('(prefers-color-scheme: dark)')

/**
 * <html data-font-size="large"> → styles/index.css 의 --font-scale 이 커짐
 * <html data-theme="dark"> → styles/index.css 의 다크 색으로 바뀜
 */
function applyToDocument() {
  const { fontSize, theme } = useDisplayStore.getState()
  const root = document.documentElement
  if (fontSize === 'large') root.dataset.fontSize = 'large'
  else delete root.dataset.fontSize

  const dark = theme === 'dark' || (theme === 'system' && systemDark.matches)
  root.dataset.theme = dark ? 'dark' : 'light'
}

// 첫 화면이 그려지기 전에 적용 (localStorage 는 바로 읽혀서 깜빡임 없음)
applyToDocument()
useDisplayStore.subscribe(applyToDocument)
// '시스템' 일 때 휴대폰 다크모드를 바꾸면 바로 따라감
systemDark.addEventListener('change', applyToDocument)
