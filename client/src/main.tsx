import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from '@/app/App'
import '@/styles/index.css'
// 큰글씨 모드 등 보기 설정을 첫 화면 전에 <html> 에 적용
import '@/stores/displayStore'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
