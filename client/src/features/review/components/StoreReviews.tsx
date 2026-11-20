import { useState } from 'react'
import { Link } from 'react-router-dom'
import pigeonCrying from '@/assets/generation/pigeon-crying.jpg'
import { Dialog } from '@/features/generation/components/Dialog'
import { errorMessage } from '@/shared/lib/error'
import { toast } from '@/stores/toastStore'
import { useSetReviewHidden, useStoreReviews } from '../hooks'
import type { Review } from '../schema'
import { RatingSummary, ReviewCard } from './ReviewUi'

/** 사장님 가게 프로필 > 리뷰 (전체 목록, 숨긴 리뷰 포함) */
export function StoreReviews({ storeId }: { storeId: number }) {
  const { data, isLoading } = useStoreReviews(storeId, true)

  return (
    <div className="h-full overflow-y-auto bg-surface pb-10">
      <div className="px-5 pt-5">
        {isLoading ? (
          <div className="h-[68px] animate-pulse rounded-2xl bg-q-panel" />
        ) : (
          data && <RatingSummary average={data.averageRating} count={data.count} hiddenCount={data.hiddenCount} privateCount={data.privateCount} />
        )}
      </div>
      {data && data.reviews.length === 0 && <EmptyReviews />}
      <OwnerReviewList storeId={storeId} reviews={data?.reviews ?? []} />
    </div>
  )
}

/** 사장님 프로필에 넣는 리뷰 요약: 평균 별점 + 최근 리뷰 2개 + 전체 보기 */
export function StoreReviewSection({ storeId }: { storeId: number }) {
  const { data } = useStoreReviews(storeId, true)
  return (
    <section className="mt-8">
      <div className="flex items-center justify-between px-5">
        <h2 className="text-13 font-bold text-ink">리뷰</h2>
        {data && data.reviews.length > 0 && (
          <Link to="/owner/reviews" className="text-xs font-medium text-green-4">
            전체 보기 ›
          </Link>
        )}
      </div>
      <div className="mt-3 px-5">{data && <RatingSummary average={data.averageRating} count={data.count} hiddenCount={data.hiddenCount} privateCount={data.privateCount} />}</div>
      {data && data.reviews.length === 0 ? <EmptyReviews /> : <OwnerReviewList storeId={storeId} reviews={data?.reviews.slice(0, 2) ?? []} />}
    </section>
  )
}

/** 손님에게 보이는 가게 페이지의 리뷰 (숨긴 리뷰는 서버가 빼고 줌) */
export function PublicStoreReviews({ storeId }: { storeId: number }) {
  const { data } = useStoreReviews(storeId)
  if (!data) return null
  return (
    <section className="mt-5 bg-surface">
      <h3 className="px-5 pt-4 text-15 font-bold text-q-text">
        리뷰 {data.count ? <span className="text-fg-q-green">{data.count}</span> : null}
      </h3>
      {data.count === 0 ? (
        <p className="py-10 text-center text-13 text-q-muted">아직 리뷰가 없어요</p>
      ) : (
        <>
          <div className="mt-3 px-5">
            <RatingSummary average={data.averageRating} count={data.count} />
          </div>
          {data.reviews.map((r) => (
            <ReviewCard key={r.reviewId} review={r} variant="store" />
          ))}
        </>
      )}
    </section>
  )
}

/** 리뷰마다 숨기기 / 다시 보이기 버튼. 숨길 때만 한 번 더 물어봄 */
function OwnerReviewList({ storeId, reviews }: { storeId: number; reviews: Review[] }) {
  const setHidden = useSetReviewHidden(storeId)
  const [hiding, setHiding] = useState<Review | null>(null)

  const change = (review: Review, hidden: boolean) =>
    setHidden.mutate(
      { reviewId: review.reviewId, hidden },
      {
        onSuccess: () => toast(hidden ? '리뷰를 숨겼어요' : '리뷰를 다시 보이게 했어요'),
        onError: (e) => toast(errorMessage(e), 'error'),
        onSettled: () => setHiding(null),
      },
    )

  return (
    <>
      {reviews.map((r) => (
        <ReviewCard
          key={r.reviewId}
          review={r}
          variant="store"
          actions={
            r.hidden ? (
              <button
                type="button"
                onClick={() => change(r, false)}
                disabled={setHidden.isPending}
                className="h-8 shrink-0 rounded-lg bg-green-4 px-3 text-12 font-bold text-white"
              >
                다시 보이기
              </button>
            ) : (
              <button type="button" onClick={() => setHiding(r)} className="h-8 shrink-0 rounded-lg bg-q-panel px-3 text-12 font-bold text-q-muted">
                숨기기
              </button>
            )
          }
        />
      ))}

      {hiding && (
        <Dialog
          title="이 리뷰를 숨길까요?"
          text="숨긴 리뷰는 손님에게 보이지 않고 평균 별점에서도 빠져요. 언제든 다시 보이게 할 수 있어요"
          image={pigeonCrying}
          primary={{ label: '아니요', onClick: () => setHiding(null) }}
          secondary={{ label: setHidden.isPending ? '숨기는 중...' : '네 숨길래요', onClick: () => change(hiding, true) }}
        />
      )}
    </>
  )
}

function EmptyReviews() {
  return (
    <div className="px-8 py-12 text-center">
      <p className="text-14 text-green-4">아직 리뷰가 없어요</p>
      <p className="mt-1 text-12 text-q-muted">퀘스트로 방문 인증한 손님이 리뷰를 남길 수 있어요</p>
    </div>
  )
}
