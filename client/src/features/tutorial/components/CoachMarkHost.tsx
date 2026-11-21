import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { router } from '@/app/router'
import { COACH_OPEN_EVENT, COACH_SCREENS, coachSeen, markCoachSeen, type CoachScreen } from '../coach'
import { CoachMarks } from './CoachMarks'

/** 처음 들어온 화면이면 코치마크를 띄움 (사장님 홈·영상 만들기 첫 화면). [사용법]으로 다시 열 수도 있음 */
export function CoachMarkHost() {
  const [path, setPath] = useState(router.state.location.pathname)
  const [screen, setScreen] = useState<CoachScreen | null>(null)
  const queryClient = useQueryClient()

  useEffect(() => router.subscribe((s) => setPath(s.location.pathname)), [])

  // 처음 온 화면: 버튼들이 다 그려지고(시작 화면 가림막이 걷힌 뒤), 서버 응답이 다 와서
  // 화면 높이가 더 안 바뀔 때 띄움. 응답이 느려도 15초 안에 준비되면 띄우고, 그 뒤에 밀려도 CoachMarks가 따라감
  useEffect(() => {
    const target = COACH_SCREENS.find((s) => s.path === path && !coachSeen(s.id))
    if (!target) return
    let tries = 0
    let lastHeight = -1
    let stableTicks = 0
    const timer = setInterval(() => {
      tries++
      const ready = target.ready() && !document.querySelector('.splash')
      const height = document.body.scrollHeight
      stableTicks = ready && queryClient.isFetching() === 0 && height === lastHeight ? stableTicks + 1 : 0
      lastHeight = height
      if (stableTicks >= 2 || (ready && tries > 75)) {
        clearInterval(timer)
        setTimeout(() => router.state.location.pathname === target.path && setScreen(target), 300)
      } else if (tries > 75) {
        clearInterval(timer)
      }
    }, 200)
    return () => clearInterval(timer)
  }, [path, queryClient])

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
