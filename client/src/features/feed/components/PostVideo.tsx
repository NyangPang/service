import { useEffect, useRef, useState, type ReactNode } from 'react'
import { SoundIcon } from '@/shared/ui/icons'
import { PostImage } from './PostImage'

/**
 * 게시물 홍보 영상. 부모가 크기를 정함 (relative + 크기 필요).
 * active 일 때만 재생 (피드에서 화면에 보이는 게시물만), 처음엔 소리 없이 — 모바일 자동 재생은 음소거여야 함.
 * 영상을 못 불러오면 포스터 사진(imageUrl)으로 대신 보여 줌 (평소엔 포스터를 깔지 않고 영상 첫 화면부터)
 */
export function PostVideo({
  url,
  posterUrl,
  alt,
  active,
  frame,
  fallback,
}: {
  url: string
  posterUrl: string | null | undefined
  alt: string
  active: boolean
  /** 목업 샘플 영상: 위아래 검은 띠 안의 실제 그림 구간(0~1)만 보이게 확대 */
  frame?: { top: number; height: number }
  fallback?: ReactNode
}) {
  const ref = useRef<HTMLVideoElement>(null)
  const [muted, setMuted] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const video = ref.current
    if (!video) return
    if (active) {
      // 브라우저가 자동 재생을 막아도(절전 모드 등) 포스터가 보이므로 무시
      video.play().catch(() => {})
    } else {
      video.pause()
    }
  }, [active, failed])

  if (failed) return <PostImage url={posterUrl} alt={alt} frame={frame} fallback={fallback} />

  return (
    <div className="absolute inset-0 overflow-hidden bg-black">
      <video
        ref={ref}
        src={url}
        aria-label={alt}
        muted={muted}
        loop
        playsInline
        preload={active ? 'auto' : 'metadata'}
        onError={() => setFailed(true)}
        onClick={() => setMuted((m) => !m)}
        className={frame ? 'absolute left-1/2 max-w-none -translate-x-1/2' : 'size-full object-contain'}
        style={frame ? { height: `${100 / frame.height}%`, top: `${(-100 * frame.top) / frame.height}%` } : undefined}
      />
      <button
        type="button"
        onClick={() => setMuted((m) => !m)}
        aria-label={muted ? '소리 켜기' : '소리 끄기'}
        className="absolute right-3 bottom-3 flex size-9 items-center justify-center rounded-full bg-black/45 text-white backdrop-blur-sm"
      >
        <span className="scale-[0.8]">
          <SoundIcon muted={muted} />
        </span>
      </button>
    </div>
  )
}
