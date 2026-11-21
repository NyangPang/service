import { useEffect, useRef, useState } from 'react'
import { create } from 'zustand'
import { PlayIcon, SoundIcon } from '@/shared/ui/icons'

/** 소리 켜기/끄기는 게시물마다가 아니라 피드 전체에 한 번 (한 번 켜면 다음 영상도 소리 켜고 재생) */
const useVideoSound = create<{ muted: boolean; toggle: () => void; mute: () => void }>()((set) => ({
  muted: true,
  toggle: () => set((s) => ({ muted: !s.muted })),
  mute: () => set({ muted: true }),
}))

/** 화면에 이만큼 보이면 재생, 덜 보이면 멈춤 */
const VISIBLE_RATIO = 0.6

const prefersReducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/**
 * 게시물 AI 홍보 영상 (세로 9:16 mp4). 부모가 크기를 정함 (relative + 크기 필요).
 * - 화면에 보이면 소리 끄고 자동재생, 위아래·옆으로 넘겨 벗어나면 멈춤 (피드는 누르기보다 내리는 방식)
 * - 영상을 누르면 멈춤/재생, 오른쪽 아래 버튼으로 소리 켜기/끄기
 * - 움직임 줄이기 설정(prefers-reduced-motion)이면 자동재생 없이 재생 버튼만
 * - 못 불러오면 onError → 부모가 사진으로 대신 보여줌
 */
export function PostVideo({
  url,
  poster,
  alt,
  frame,
  onError,
  autoPlay = true,
}: {
  url: string
  /** 영상 썸네일 (서버 imageUrl) — 불러오는 동안 보임 */
  poster?: string | null
  alt: string
  /** 목업 샘플 영상: 위아래 검은 띠 안의 실제 그림 구간(0~1)만 보이게 확대 */
  frame?: { top: number; height: number }
  onError?: () => void
  /** false면 화면에 보여도 자동재생하지 않고 재생 버튼 (사장님 미리보기: 소리까지 직접 확인) */
  autoPlay?: boolean
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const { muted, toggle, mute } = useVideoSound()
  const [autoplay] = useState(() => autoPlay && !prefersReducedMotion())
  const [visible, setVisible] = useState(false)
  /** 사람이 직접 멈췄으면 다시 보여도 자동재생하지 않음 */
  const [pausedByUser, setPausedByUser] = useState(!autoplay)
  const [playing, setPlaying] = useState(false)

  // 화면에 보이는지 (세로 피드 넘기기 + 가로 사진 넘기기 둘 다 잡힘)
  useEffect(() => {
    // 영상 자체가 아니라 보이는 틀(부모)을 봄 — frame 확대로 영상이 틀보다 커도 제대로 잡히게
    const el = videoRef.current?.parentElement
    if (!el || typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver(
      ([entry]) => {
        const shown = entry.intersectionRatio >= VISIBLE_RATIO
        setVisible(shown)
        // 벗어났다 돌아오면 다시 자동재생 (움직임 줄이기 설정이면 계속 버튼으로만)
        if (!shown) setPausedByUser(!autoplay)
      },
      { threshold: [0, VISIBLE_RATIO, 1] },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [autoplay])

  useEffect(() => {
    const el = videoRef.current
    if (!el) return
    if (visible && !pausedByUser) {
      // 브라우저가 막으면(소리 켠 자동재생 등) 소리 끄고 다시 시도, 그래도 안 되면 재생 버튼
      el.play().catch(() => {
        mute()
        el.muted = true
        el.play().catch(() => setPlaying(false))
      })
    } else {
      el.pause()
      // 다른 게시물로 넘어갔다 돌아오면 처음부터
      if (!visible) el.currentTime = 0
    }
  }, [visible, pausedByUser, mute])

  useEffect(() => {
    if (videoRef.current) videoRef.current.muted = muted
  }, [muted])

  const onTap = () => {
    const el = videoRef.current
    if (!el) return
    if (el.paused) {
      setPausedByUser(false)
      el.play().catch(() => undefined)
    } else {
      setPausedByUser(true)
      el.pause()
    }
  }

  return (
    <>
      {/* 흐린 배경: 9:16이 아닌 화면에서 남는 곳 채움 (사진과 같은 방식) */}
      {poster && !frame && <img src={poster} alt="" aria-hidden className="absolute inset-0 size-full scale-110 object-cover opacity-60 blur-2xl" />}
      <video
        ref={videoRef}
        src={url}
        poster={poster ?? undefined}
        muted={muted}
        loop
        playsInline
        preload="metadata"
        aria-label={`${alt} 홍보 영상`}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onError={onError}
        onClick={onTap}
        className={frame ? 'absolute left-1/2 max-w-none -translate-x-1/2' : 'relative size-full object-contain'}
        style={frame ? { height: `${100 / frame.height}%`, top: `${(-100 * frame.top) / frame.height}%` } : undefined}
      />

      {!playing && (
        <button
          type="button"
          onClick={onTap}
          aria-label="영상 재생"
          className="absolute top-1/2 left-1/2 flex size-16 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-black/45 pl-1 text-white backdrop-blur-sm"
        >
          <PlayIcon />
        </button>
      )}

      <button
        type="button"
        onClick={toggle}
        aria-label={muted ? '소리 켜기' : '소리 끄기'}
        aria-pressed={!muted}
        className="absolute right-3 bottom-8 flex size-10 items-center justify-center rounded-full bg-black/45 text-white backdrop-blur-sm"
      >
        <span className="scale-[0.8]">
          <SoundIcon muted={muted} />
        </span>
      </button>
    </>
  )
}
