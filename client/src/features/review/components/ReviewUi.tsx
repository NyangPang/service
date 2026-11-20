import { useState, type ReactNode } from 'react'
import pigeonWalk from '@/assets/map/pigeon-walk.png'
import { formatReviewDate, type Review } from '../schema'

function StarShape({ filled, className }: { filled: boolean; className: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden>
      <path
        d="M12 2.8l2.8 5.7 6.3.9-4.6 4.4 1.1 6.2L12 17.1 6.4 20l1.1-6.2L2.9 9.4l6.3-.9L12 2.8Z"
        className={filled ? 'fill-point-yellow' : 'fill-q-track'}
      />
    </svg>
  )
}

/** 별점 표시 (읽기 전용) */
export function Stars({ rating, size = 'size-4' }: { rating: number; size?: string }) {
  return (
    <span role="img" aria-label={`별점 5점 중 ${rating}점`} className="inline-flex gap-px">
      {[1, 2, 3, 4, 5].map((n) => (
        <StarShape key={n} filled={n <= Math.round(rating)} className={size} />
      ))}
    </span>
  )
}

/** 별점 고르기 (1~5) */
export function StarPicker({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div role="radiogroup" aria-label="별점" className="flex justify-center gap-1.5">
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n}점`} onClick={() => onChange(n)} className="p-0.5">
          <StarShape filled={n <= value} className="size-10" />
        </button>
      ))}
    </div>
  )
}

/**
 * 리뷰 한 건. variant="mine"(내 리뷰: 가게 이름이 제목) / "store"(가게 리뷰: 작성자가 제목).
 * actions: 수정·삭제 버튼 자리
 */
export function ReviewCard({ review, variant, actions }: { review: Review; variant: 'mine' | 'store'; actions?: ReactNode }) {
  const [expanded, setExpanded] = useState(false)
  const long = review.content.length > 120
  const edited = review.updatedAt.slice(0, 16) !== review.createdAt.slice(0, 16)
  const avatar = variant === 'mine' ? review.storeThumbnailUrl : review.profileImageUrl

  return (
    <article className="border-b border-q-line px-5 py-4">
      {review.hidden && (
        <p className="mb-2.5 rounded-lg bg-q-panel px-3 py-2 text-[12px] text-q-muted">
          {variant === 'mine' ? '사장님이 숨긴 리뷰예요. 다른 손님에게는 보이지 않아요' : '숨긴 리뷰예요. 손님에게는 보이지 않아요'}
        </p>
      )}
      {review.privateReview && !review.hidden && (
        <p className="mb-2.5 rounded-lg bg-q-cream px-3 py-2 text-[12px] text-q-sub">
          🔒 {variant === 'mine' ? '비공개 리뷰예요. 사장님만 볼 수 있어요' : '비공개 리뷰예요. 사장님에게만 보여요'}
        </p>
      )}
      <header className={`flex items-center gap-3 ${review.hidden ? 'opacity-60' : ''}`}>
        <span className="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-full bg-q-panel">
          {avatar ? <img src={avatar} alt="" className="size-full object-cover" /> : <img src={pigeonWalk} alt="" className="size-8 object-contain" />}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[15px] font-bold text-q-text">{variant === 'mine' ? review.storeName : review.nickname}</p>
          <p className="mt-0.5 flex items-center gap-1.5 text-[12px] text-q-muted">
            <Stars rating={review.rating} size="size-3.5" />
            <span>
              {formatReviewDate(review.createdAt)}
              {edited && ' · 수정됨'}
            </span>
          </p>
        </div>
        {actions}
      </header>

      <div className={review.hidden ? 'opacity-60' : undefined}>
      {(review.photoUrls.length > 0 || review.videoUrl) && (
        <ul className="mt-3 flex gap-1.5 overflow-x-auto">
          {review.videoUrl && (
            <li className="shrink-0">
              <video src={review.videoUrl} controls playsInline preload="metadata" className="size-[104px] rounded-xl bg-ink object-cover" />
            </li>
          )}
          {review.photoUrls.map((url) => (
            <li key={url} className="shrink-0">
              <img src={url} alt="리뷰 사진" className="size-[104px] rounded-xl object-cover" />
            </li>
          ))}
        </ul>
      )}

      <p className={`mt-3 text-[14px] leading-relaxed whitespace-pre-line text-q-sub ${long && !expanded ? 'line-clamp-3' : ''}`}>{review.content}</p>
      {long && (
        <button type="button" onClick={() => setExpanded((v) => !v)} className="mt-1 text-[12px] font-medium text-q-muted">
          {expanded ? '접기' : '더 보기'}
        </button>
      )}
      </div>
    </article>
  )
}

/** 별점 평균 + 리뷰 수 */
export function RatingSummary({
  average,
  count,
  hiddenCount = 0,
  privateCount = 0,
}: {
  average: number
  count: number
  hiddenCount?: number
  privateCount?: number
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-q-panel px-4 py-3.5">
      <span className="text-[28px] leading-none font-bold text-q-text">{count ? average.toFixed(1) : '-'}</span>
      <span className="flex flex-col gap-1">
        <Stars rating={average} />
        <span className="text-[12px] text-q-muted">
          공개 리뷰 {count}개{privateCount > 0 && ` · 비공개 ${privateCount}개`}{hiddenCount > 0 && ` · 숨김 ${hiddenCount}개`}
        </span>
      </span>
    </div>
  )
}
