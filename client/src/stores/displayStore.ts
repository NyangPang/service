import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** 화면 보기 설정 (설정 서랍 → 앱 설정). 브라우저에 기억해 둠 */
export type FontSize = 'normal' | 'large'

interface DisplayState {
  fontSize: FontSize
  setFontSize: (fontSize: FontSize) => void
}

export const useDisplayStore = create<DisplayState>()(
  persist(
    (set) => ({
      fontSize: 'normal',
      setFontSize: (fontSize) => set({ fontSize }),
    }),
    { name: 'itda-display' },
  ),
)

/** <html data-font-size="large"> → styles/index.css 의 --font-scale 이 커짐 */
function applyToDocument({ fontSize }: DisplayState) {
  const root = document.documentElement
  if (fontSize === 'large') root.dataset.fontSize = 'large'
  else delete root.dataset.fontSize
}

// 첫 화면이 그려지기 전에 적용 (localStorage 는 바로 읽혀서 깜빡임 없음)
applyToDocument(useDisplayStore.getState())
useDisplayStore.subscribe(applyToDocument)
