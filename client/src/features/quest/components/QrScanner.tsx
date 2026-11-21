import jsQR from 'jsqr'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { OutlineButton } from './QuestUi'

type CameraState = 'starting' | 'scanning' | 'denied' | 'unavailable'
/** environment: 후면 / user: 전면 */
type Facing = 'environment' | 'user'

/** 0.2초마다 한 장씩 읽음 (매 프레임 읽으면 휴대폰이 뜨거워짐) */
const SCAN_INTERVAL_MS = 200
/** 읽을 때 줄이는 가로 크기 (QR 인식엔 충분하고 빠름) */
const SCAN_WIDTH = 640

/**
 * 가게 QR 주소(visitQrUrl)에서 이동할 앱 안 주소를 꺼냄. 잇츠 가게 QR이 아니면 null.
 * 도메인은 보지 않음 — 개발 중에 만든 QR엔 localhost 주소가 들어 있어서
 */
function visitPathFromQr(text: string): string | null {
  try {
    const url = new URL(text)
    const storeId = url.searchParams.get('storeId')
    const qrToken = url.searchParams.get('qrToken')
    if (url.pathname !== '/quest/scan' || !storeId || !qrToken) return null
    return `/quest/scan?${new URLSearchParams({ storeId, qrToken }).toString()}`
  } catch {
    return null
  }
}

/** 바로가기 > QR 인식하기: 앱 안에서 카메라로 가게 QR을 찍으면 방문 인증으로 (/quest/scan 흐름 그대로) */
export function QrScanner() {
  const navigate = useNavigate()
  const videoRef = useRef<HTMLVideoElement>(null)
  const [state, setState] = useState<CameraState>('starting')
  const [notice, setNotice] = useState<string | null>(null)
  const [facing, setFacing] = useState<Facing>('environment')
  // 카메라가 2대 이상일 때만 전환 버튼 (PC 웹캠 1대면 숨김)
  const [canSwitch, setCanSwitch] = useState(false)

  useEffect(() => {
    let stream: MediaStream | null = null
    let timer: number | undefined
    let stopped = false
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })

    const scan = () => {
      const video = videoRef.current
      if (stopped || !video || !ctx) return
      if (video.readyState >= video.HAVE_ENOUGH_DATA && video.videoWidth) {
        const scale = Math.min(1, SCAN_WIDTH / video.videoWidth)
        canvas.width = Math.round(video.videoWidth * scale)
        canvas.height = Math.round(video.videoHeight * scale)
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
        const code = jsQR(ctx.getImageData(0, 0, canvas.width, canvas.height).data, canvas.width, canvas.height, {
          inversionAttempts: 'dontInvert',
        })
        if (code?.data) {
          const path = visitPathFromQr(code.data)
          if (path) {
            stopped = true
            navigate(path, { replace: true })
            return
          }
          setNotice('잇츠 가게 QR이 아니에요 · 가게에 있는 방문 QR을 찍어 주세요')
        }
      }
      timer = window.setTimeout(scan, SCAN_INTERVAL_MS)
    }

    const start = async () => {
      if (!navigator.mediaDevices?.getUserMedia) return setState('unavailable')
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing }, audio: false })
        // 권한을 묻는 사이 화면을 나갔거나 카메라를 바꿨으면 바로 끔
        if (stopped) return stream.getTracks().forEach((t) => t.stop())
        const video = videoRef.current!
        video.srcObject = stream
        await video.play()
        setState('scanning')
        scan()
        // 권한을 받은 뒤에야 카메라 목록이 정확히 나옴
        const devices = await navigator.mediaDevices.enumerateDevices().catch(() => [])
        if (!stopped) setCanSwitch(devices.filter((d) => d.kind === 'videoinput').length > 1)
      } catch (e) {
        if (stopped) return
        setState(e instanceof DOMException && e.name === 'NotAllowedError' ? 'denied' : 'unavailable')
      }
    }
    start()

    return () => {
      stopped = true
      window.clearTimeout(timer)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [navigate, facing])

  const failed = state === 'denied' || state === 'unavailable'
  const switchCamera = () => {
    setState('starting')
    setFacing((f) => (f === 'environment' ? 'user' : 'environment'))
  }

  return (
    <div className="flex h-full flex-col bg-surface px-5 pt-5 pb-8">
      <p className="text-center text-15 font-bold text-q-text">가게에 있는 방문 QR을 네모 안에 맞춰 주세요</p>

      <div className="relative mx-auto mt-5 aspect-square w-full max-w-[340px] overflow-hidden rounded-3xl bg-black">
        {/* 전면 카메라는 거울처럼 보이게 (보이는 화면만 뒤집음 — QR 읽기엔 영향 없음) */}
        <video
          ref={videoRef}
          muted
          playsInline
          className={`size-full object-cover ${facing === 'user' ? '-scale-x-100' : ''} ${failed ? 'hidden' : ''}`}
        />
        {state === 'scanning' && <div aria-hidden className="absolute inset-[14%] rounded-2xl border-4 border-white/90" />}
        {canSwitch && !failed && (
          <button
            type="button"
            aria-label={facing === 'environment' ? '전면 카메라로 바꾸기' : '후면 카메라로 바꾸기'}
            onClick={switchCamera}
            disabled={state === 'starting'}
            className="absolute top-3 right-3 flex size-11 items-center justify-center rounded-full bg-black/50 text-white disabled:opacity-40"
          >
            <SwitchCameraIcon />
          </button>
        )}
        {state === 'starting' && <p className="absolute inset-0 flex items-center justify-center text-14 text-white/80">카메라를 켜는 중...</p>}
        {failed && (
          <div className="absolute inset-0 flex flex-col items-center justify-center px-6 text-center text-white">
            <p className="text-15 font-bold">{state === 'denied' ? '카메라 권한이 꺼져 있어요' : '카메라를 쓸 수 없어요'}</p>
            <p className="mt-1.5 text-12 text-white/70">
              {state === 'denied' ? '브라우저 설정에서 카메라를 허용하거나, QR 값을 직접 입력해 주세요' : 'QR 값을 직접 입력해 주세요'}
            </p>
          </div>
        )}
      </div>

      <p role="status" className="mt-4 min-h-10 text-center text-13 text-fg-red">
        {notice}
      </p>

      <OutlineButton className="mt-auto" onClick={() => navigate('/quest/scan')}>
        직접 입력하기
      </OutlineButton>
    </div>
  )
}

/** 카메라 전환 (화살표 두 개가 도는 모양) */
function SwitchCameraIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 11a8 8 0 0 0-14.3-4.9L4 8" />
      <path d="M4 4v4h4" />
      <path d="M4 13a8 8 0 0 0 14.3 4.9L20 16" />
      <path d="M20 20v-4h-4" />
    </svg>
  )
}
