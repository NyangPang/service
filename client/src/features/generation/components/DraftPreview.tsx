import { useState } from 'react'
import { PostCarousel } from '@/features/feed/components/PostCarousel'
import type { ShortformDetail } from '../schema'

/**
 * 생성 결과 미리보기 (업로드·교체 전 확인).
 * 영상이 있으면 9:16 영상 — 자동재생 없이 재생 버튼, 소리 버튼으로 소리까지 확인. 영상을 못 불러오면 사진 넘겨보기
 */
export function DraftPreview({ detail, maxWidth = 300 }: { detail: ShortformDetail | undefined; maxWidth?: number }) {
  const [videoFailed, setVideoFailed] = useState(false)
  const showVideo = !!detail?.videoUrl && !videoFailed
  const images = detail ? (detail.imageUrls ?? (detail.imageUrl ? [detail.imageUrl] : [])) : []

  return (
    <div
      className={`relative mx-auto w-full overflow-hidden rounded-3xl bg-green-1 shadow-[0_10px_30px_rgba(8,104,22,0.18)] ${showVideo || !detail ? 'aspect-[9/16]' : 'aspect-[4/5]'}`}
      style={{ maxWidth: showVideo || !detail ? Math.round(maxWidth * 0.8) : maxWidth }}
    >
      {detail ? (
        <PostCarousel
          key={showVideo ? 'video' : 'photo'}
          video={showVideo ? { url: detail.videoUrl!, onError: () => setVideoFailed(true), autoPlay: false } : undefined}
          images={showVideo ? images.slice(0, 1) : images}
          alt={detail.title}
          frame={detail.frame}
        />
      ) : (
        <p className="flex h-full items-center justify-center text-sm text-q-muted">게시물을 불러오는 중...</p>
      )}
    </div>
  )
}
