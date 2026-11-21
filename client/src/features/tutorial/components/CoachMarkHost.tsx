import { useEffect, useState } from 'react'
import { router } from '@/app/router'
import { COACH_OPEN_EVENT, COACH_SCREENS, coachSeen, markCoachSeen, type CoachScreen } from '../coach'
import { CoachMarks } from './CoachMarks'

/** 처음 들어온 화면이면 코치마크를 띄움 (사장님 홈·영상 만들기 첫 화면). [사용법]으로 다시 열 수도 있음 */
export function CoachMarkHost() {
  const [path, setPath] = useState(router.state.location.pathname)
  const [screen, setScreen] = useState<CoachScreen | null>(null)

  useEffect(() => router.subscribe((s) => setPath(s.location.pathname)), [])

  // 처음 온 화면: 버튼들이 다 그려질 때까지 잠깐 기다렸다가 띄움 (시작 화면 가림막이 걷힌 뒤)
  useEffect(() => {
    const target = COACH_SCREENS.find((s) => s.path === path && !coachSeen(s.id))
    if (!target) return
    let tries = 0
    const timer = setInterval(() => {
      tries++
      if (target.ready() && !document.querySelector('.splash')) {
        clearInterval(timer)
        setTimeout(() => router.state.location.pathname === target.path && setScreen(target), 400)
      } else if (tries > 40) {
        clearInterval(timer)
      }
    }, 200)
    return () => clearInterval(timer)
  }, [path])

  useEffect(() => {
    const onOpen = (e: Event) => {
      const found = COACH_SCREENS.find((s) => s.id === (e as CustomEvent).detail)
      if (found) setScreen(found)
    }
    window.addEventListener(COACH_OPEN_EVENT, onOpen)
    return () => window.removeEventListener(COACH_OPEN_EVENT, onOpen)
  }, [])

  // 다른 화면으로 가면 안 보임
  const shown = screen && screen.path === path ? screen : null
  if (!shown) return null
  return (
    <CoachMarks
      marks={shown.marks}
      onClose={() => {
        markCoachSeen(shown.id)
        setScreen(null)
      }}
    />
  )
}
