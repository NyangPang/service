import { useState } from 'react'
import type { Shortform } from '../schema'

/**
 * 게시물 썸네일 (목록·그리드용). 부모가 크기를 정함 (relative + overflow-hidden 필요).
 * 영상 게시물은 영상의 첫 화면, 사진 게시물은 첫 사진. 영상을 못 불러오면 포스터 사진으로 대신.
 * 샘플 영상처럼 위아래 검은 띠가 있으면(frame) 그림 구간만 보이게 확대
 */
export function ShortformThumb({ video }: { video: Shortform }) {
  const [videoFailed, setVideoFailed] = useState(false)
  const f = video.frame
  const style = f ? { height: `${100 / f.height}%`, top: `${(-100 * f.top) / f.height}%` } : undefined
  const className = f ? 'absolute left-1/2 max-w-none -translate-x-1/2' : 'absolute inset-0 size-full object-cover'

  if (video.videoUrl && !videoFailed) {
    return (
      <video
        // #t=0.1: 재생하지 않아도 첫 화면을 그리게 (iOS Safari 는 0초면 검은 화면)
        src={`${video.videoUrl}#t=0.1`}
        muted
        playsInline
        preload="metadata"
        aria-hidden
        tabIndex={-1}
        onError={() => setVideoFailed(true)}
        className={`pointer-events-none ${className}`}
        style={style}
      />
    )
  }
  if (!video.posterUrl) return null
  return <img src={video.posterUrl} alt="" className={className} style={style} />
}
