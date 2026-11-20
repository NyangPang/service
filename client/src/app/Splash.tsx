import { useState } from 'react'
import logo from '@/assets/its-logo.svg'
import logoOnGreen from '@/assets/its-logo-white.svg'

const SHOWN_KEY = 'itda-splash-shown'

/** 배경 크림색 → 퍼지는 초록 (로고 색) */
const CREAM = '#fef9f3'
const GREEN = '#24734f'

/**
 * 타이밍 (ms): 로고 등장 → 잠깐 멈춤 → 로고 중심에서 초록 원이 퍼져 화면을 채움 → 초록 화면 잠깐 → 사라짐.
 * 퍼지는 원은 clip-path 라 레이아웃·그림 다시 그리기 없이 부드럽게 움직임
 */
const LOGO_IN = 500
const SPREAD_DELAY = 750
const SPREAD = 900
const FADE_DELAY = SPREAD_DELAY + SPREAD + 250
const FADE = 350

/** 이번 접속에서 이미 봤으면 다시 안 보여 줌 (새로고침·화면 이동마다 뜨지 않게) */
function shouldShow() {
  try {
    if (sessionStorage.getItem(SHOWN_KEY)) return false
    sessionStorage.setItem(SHOWN_KEY, '1')
  } catch {
    // 저장소를 못 쓰면 그냥 한 번 보여 줌
  }
  return true
}
// 앱을 열 때 한 번만 판단 (StrictMode에서 초기값 함수가 두 번 돌아도 결과가 같게)
const SHOW_ON_START = shouldShow()

/**
 * 시작 스플래시: 크림색 바탕 + 로고 → 로고 가운데서 초록 원이 바깥으로 퍼지며 화면 전체를 채움 → 앱으로.
 * 원이 지나가는 자리에서 로고도 밝은 색으로 바뀜 (같은 자리에 겹친 두 로고를 원 모양으로 잘라 보여 줌).
 * 누르면 바로 넘어감. 움직임 줄이기 설정이면 퍼짐 없이 짧게 보여 주고 사라짐 (index.css)
 */
export function Splash() {
  const [visible, setVisible] = useState(SHOW_ON_START)
  if (!visible) return null

  return (
    <div
      role="presentation"
      onClick={() => setVisible(false)}
      onAnimationEnd={(e) => e.animationName === 'splash-out' && setVisible(false)}
      className="splash fixed inset-0 z-[100] overflow-hidden"
      style={{ background: CREAM, animation: `splash-out ${FADE}ms ease-in ${FADE_DELAY}ms forwards` }}
    >
      {/* 아래층: 크림 바탕 + 원래 색 로고 */}
      <Logo />

      {/* 위층: 초록 바탕 + 밝은 로고. 가운데서 원 모양으로 점점 크게 잘라 보여 줌 */}
      <div
        aria-hidden
        className="splash-spread absolute inset-0"
        style={{ background: GREEN, animation: `splash-spread ${SPREAD}ms cubic-bezier(.65,0,.35,1) ${SPREAD_DELAY}ms both` }}
      >
        <Logo light />
      </div>
    </div>
  )
}

function Logo({ light = false }: { light?: boolean }) {
  return (
    <div className="absolute inset-0 flex items-center justify-center">
      <img
        // 밝은 로고: 초록 위에서 보이게 만든 초록 배경용 로고 (두 파일은 viewBox가 같아야 겹친 자리가 딱 맞음)
        src={light ? logoOnGreen : logo}
        alt={light ? '' : '잇츠'}
        className="splash-logo h-14 w-auto"
        style={{ animation: `splash-pop ${LOGO_IN}ms cubic-bezier(.3,1.4,.5,1) both` }}
      />
    </div>
  )
}
