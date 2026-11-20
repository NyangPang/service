import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import pigeonCrying from '@/assets/generation/pigeon-crying.jpg'
import pigeonWalk from '@/assets/map/pigeon-walk.png'
import { Dialog } from '@/features/generation/components/Dialog'
import { errorMessage } from '@/shared/lib/error'
import { useMe } from '@/stores/authStore'
import { toast } from '@/stores/toastStore'
import { useDeleteReview, useMyReviews, useWritableVisits } from '../hooks'
import { formatReviewDate, REVIEW_DRAWS, type Review } from '../schema'
import { ReviewCard } from './ReviewUi'

/**
 * 손님 마이페이지 > 내 리뷰.
 * 위: 리뷰를 쓸 수 있는 QR 방문 인증 (인증 1건당 리뷰 1개, 쓰면 뽑기 3회) / 아래: 내가 쓴 리뷰 (수정·삭제·남은 뽑기)
 */
export function MyReviews() {
  const me = useMe()
  const navigate = useNavigate()
  const { data: mine, isLoading } = useMyReviews()
  const { data: writable } = useWritableVisits()
  const remove = useDeleteReview()
  const [deleting, setDeleting] = useState<Review | null>(null)

  if (!me) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <img src={pigeonWalk} alt="" className="h-24 w-auto object-contain" />
        <p className="mt-4 text-16 font-bold text-ink">로그인하면 내 리뷰를 볼 수 있어요</p>
        <Link to="/login?next=/profile/reviews" className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-green-4 text-15 font-bold text-white">
          로그인
        </Link>
      </div>
    )
  }

  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.reviewId, {
      onSuccess: ({ revokedDraws }) => toast(revokedDraws > 0 ? `리뷰를 지웠어요. 남은 뽑기 ${revokedDraws}회가 사라졌어요` : '리뷰를 지웠어요'),
      onError: (e) => toast(errorMessage(e), 'error'),
      onSettled: () => setDeleting(null),
    })
  }

  const leftOf = (r: Review) => r.drawsLeft ?? 0
  // 아직 리뷰를 안 쓴 QR 방문 인증만 옴 (인증 1건당 리뷰 1개)
  const rewardVisits = writable ?? []

  return (
    <div className="h-full overflow-y-auto bg-surface pb-10">
      {rewardVisits.length > 0 && (
        <section className="px-5 pt-5">
          <h2 className="text-13 font-bold text-ink">리뷰 쓰고 보상 받기</h2>
          <ul className="mt-2 flex flex-col gap-2">
            {rewardVisits.map((v) => (
              <li key={v.visitId}>
                <Link to={`/profile/reviews/new?visitId=${v.visitId}`} className="flex items-center gap-3 rounded-2xl bg-q-mint px-4 py-3">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-15 font-bold text-q-text">{v.storeName}</span>
                    <span className="block text-12 text-fg-q-green">
                      {formatReviewDate(v.visitedAt)} 방문 인증 · 리뷰 쓰면 뽑기 {REVIEW_DRAWS}회 🎁
                    </span>
                  </span>
                  <span className="shrink-0 rounded-lg bg-green-4 px-3 py-1.5 text-13 font-bold text-white">리뷰 쓰기</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="mt-6">
        <h2 className="px-5 text-13 font-bold text-ink">
          내가 쓴 리뷰 {mine?.count ? <span className="text-fg-q-green">{mine.count}</span> : null}
        </h2>
        {isLoading && <div className="mx-5 mt-3 h-28 animate-pulse rounded-2xl bg-q-panel" />}
        {mine && mine.count === 0 && (
          <div className="px-8 py-14 text-center">
            <p className="text-14 text-green-4">아직 쓴 리뷰가 없어요</p>
            <p className="mt-1 text-12 text-q-muted">QR 방문 인증을 하면 리뷰를 쓰고 보상 뽑기 {REVIEW_DRAWS}회를 받아요</p>
          </div>
        )}
        <div className="mt-1">
          {mine?.reviews.map((r) => (
            <div key={r.reviewId}>
              <ReviewCard
                review={r}
                variant="mine"
                actions={
                  <span className="flex shrink-0 gap-1">
                    <button
                      type="button"
                      onClick={() => navigate(`/profile/reviews/${r.reviewId}/edit`)}
                      className="h-8 rounded-lg bg-q-panel px-3 text-12 font-bold text-green-4"
                    >
                      수정
                    </button>
                    <button type="button" onClick={() => setDeleting(r)} className="h-8 rounded-lg bg-q-panel px-3 text-12 font-bold text-q-muted">
                      삭제
                    </button>
                  </span>
                }
              />
              {leftOf(r) > 0 && (
                <Link
                  to={`/profile/reviews/${r.reviewId}/draw`}
                  className="mx-5 -mt-1 mb-3 flex h-10 items-center justify-center gap-1.5 rounded-xl bg-green-6 text-14 font-bold text-white"
                >
                  🎁 보상 뽑기 {leftOf(r)}회 남음
                </Link>
              )}
            </div>
          ))}
        </div>
      </section>

      {deleting && (
        <Dialog
          title="리뷰를 지울까요?"
          text={
            leftOf(deleting) > 0
              ? `남은 보상 뽑기 ${leftOf(deleting)}회가 사라지고, 이 방문 인증으로는 리뷰를 다시 쓸 수 없어요`
              : `${deleting.storeName} 리뷰가 지워지고, 이 방문 인증으로는 리뷰를 다시 쓸 수 없어요`
          }
          image={pigeonCrying}
          primary={{ label: '아니요', onClick: () => setDeleting(null) }}
          secondary={{ label: remove.isPending ? '지우는 중...' : '네 지울래요', onClick: confirmDelete }}
        />
      )}
    </div>
  )
}
