import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import pigeonCrying from '@/assets/generation/pigeon-crying.jpg'
import pigeonWalk from '@/assets/map/pigeon-walk.png'
import { Dialog } from '@/features/generation/components/Dialog'
import { errorMessage } from '@/shared/lib/error'
import { StoreAvatar } from '@/shared/ui/StoreAvatar'
import { useMe } from '@/stores/authStore'
import { toast } from '@/stores/toastStore'
import { useDeleteReview, useMyReviews, useWritableVisits } from '../hooks'
import { formatReviewDate, REVIEW_DRAWS, type Review } from '../schema'
import { ReviewCard } from './ReviewUi'

const TABS = [
  { key: 'visits', label: '내 방문' },
  { key: 'reviews', label: '내 리뷰' },
] as const

type TabKey = (typeof TABS)[number]['key']

/** 손님 내 활동 — 내 방문(QR 인증 기록) / 내 리뷰 */
export function MyActivityTabs() {
  const [params, setParams] = useSearchParams()
  const tab: TabKey = params.get('tab') === 'reviews' ? 'reviews' : 'visits'
  const me = useMe()

  if (!me) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <img src={pigeonWalk} alt="" className="h-24 w-auto object-contain" />
        <p className="mt-4 text-16 font-bold text-ink">로그인하면 내 활동을 볼 수 있어요</p>
        <Link
          to="/login?next=/my-activity"
          className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-green-4 text-15 font-bold text-white"
        >
          로그인
        </Link>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-q-panel">
      <div className="shrink-0 px-5 pt-4" role="tablist">
        <div className="grid grid-cols-2 rounded-full bg-surface p-1 shadow-[0_2px_8px_rgba(8,104,22,0.06)]">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setParams(t.key === 'visits' ? {} : { tab: t.key }, { replace: true })}
              className="h-9 rounded-full text-14 font-bold text-q-muted transition-colors aria-selected:bg-q-green aria-selected:text-white"
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      <div className="min-h-0 flex-1">
        {tab === 'visits' && <MyVisitsTab />}
        {tab === 'reviews' && <MyReviewsTab />}
      </div>
    </div>
  )
}

/** 내 방문 — 리뷰 미작성은 "리뷰 작성하기", 작성 완료는 "리뷰 보러가기" */
function MyVisitsTab() {
  const { data: writable, isLoading: wLoading } = useWritableVisits()
  const { data: mine, isLoading: mLoading } = useMyReviews()

  if (wLoading || mLoading) {
    return <div className="mx-5 mt-6 h-28 animate-pulse rounded-2xl bg-surface" />
  }

  const writableList = writable ?? []
  // 리뷰를 쓴 QR 방문 인증 (visitId가 있는 리뷰)
  const reviewedVisits = mine?.reviews.filter((r) => r.visitId != null) ?? []

  if (writableList.length === 0 && reviewedVisits.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <p className="text-14 text-green-4">아직 방문 인증한 가게가 없어요</p>
        <p className="mt-1 text-12 text-q-muted">QR 인증하면 리뷰를 쓰고 보상 뽑기를 받아요</p>
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto pb-10">
      <ul className="flex flex-col gap-2 px-5 pt-4">
        {writableList.map((v) => (
          <li key={`w-${v.visitId}`} className="flex items-center gap-3 rounded-2xl bg-surface px-4 py-3">
            <StoreAvatar url={v.storeThumbnailUrl} className="size-12 shrink-0" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-15 font-bold text-q-text">{v.storeName}</span>
              <span className="block text-12 text-q-muted">{formatReviewDate(v.visitedAt)} 방문 인증</span>
            </span>
            <Link
              to={`/profile/reviews/new?visitId=${v.visitId}`}
              className="shrink-0 rounded-lg bg-green-4 px-3 py-1.5 text-13 font-bold text-white"
            >
              리뷰 작성하기
            </Link>
          </li>
        ))}
        {reviewedVisits.map((r) => (
          <li key={`r-${r.reviewId}`} className="flex items-center gap-3 rounded-2xl bg-surface px-4 py-3">
            <StoreAvatar url={r.storeThumbnailUrl} className="size-12 shrink-0" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-15 font-bold text-q-text">{r.storeName}</span>
              <span className="block text-12 text-q-muted">리뷰 작성 완료</span>
            </span>
            <Link
              to={`/map/stores/${r.storeId}`}
              className="shrink-0 rounded-lg border border-q-line bg-surface px-3 py-1.5 text-13 font-bold text-q-text"
            >
              리뷰 보러가기
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 내 리뷰 — 내가 쓴 모든 리뷰 (수정·삭제·보상 뽑기) */
function MyReviewsTab() {
  const { data: mine, isLoading } = useMyReviews()
  const remove = useDeleteReview()
  const [deleting, setDeleting] = useState<Review | null>(null)

  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.reviewId, {
      onSuccess: ({ revokedDraws }) =>
        toast(revokedDraws > 0 ? `리뷰를 지웠어요. 남은 뽑기 ${revokedDraws}회가 사라졌어요` : '리뷰를 지웠어요'),
      onError: (e) => toast(errorMessage(e), 'error'),
      onSettled: () => setDeleting(null),
    })
  }

  if (isLoading) return <div className="mx-5 mt-6 h-28 animate-pulse rounded-2xl bg-surface" />

  if (!mine || mine.count === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <p className="text-14 text-green-4">아직 쓴 리뷰가 없어요</p>
        <p className="mt-1 text-12 text-q-muted">QR 방문 인증을 하면 리뷰를 쓰고 보상 뽑기 {REVIEW_DRAWS}회를 받아요</p>
      </div>
    )
  }

  const leftOf = (r: Review) => r.drawsLeft ?? 0

  return (
    <div className="h-full overflow-y-auto pb-10">
      {mine.reviews.map((r) => (
        <div key={r.reviewId}>
          <ReviewCard
            review={r}
            variant="mine"
            actions={
              <span className="flex shrink-0 gap-1">
                <Link
                  to={`/profile/reviews/${r.reviewId}/edit`}
                  className="flex h-8 items-center rounded-lg bg-q-panel px-3 text-12 font-bold text-green-4"
                >
                  수정
                </Link>
                <button
                  type="button"
                  onClick={() => setDeleting(r)}
                  className="h-8 rounded-lg bg-q-panel px-3 text-12 font-bold text-q-muted"
                >
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
