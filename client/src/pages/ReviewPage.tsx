import { Navigate, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { NotificationList } from '@/features/notification/components/NotificationList'
import { useMyStoreId } from '@/features/owner/hooks'
import { MyReviews } from '@/features/review/components/MyReviews'
import { ReviewDraw } from '@/features/review/components/ReviewDraw'
import { ReviewForm, ReviewNotAllowed } from '@/features/review/components/ReviewForm'
import { StoreReviews } from '@/features/review/components/StoreReviews'
import { useMyReviews, useWritableVisits } from '@/features/review/hooks'
import { useMe } from '@/stores/authStore'

const Loading = () => <div className="mx-5 mt-6 h-40 animate-pulse rounded-2xl bg-q-panel" />

/** 손님 마이페이지 > 내 리뷰 */
export const MyReviewsPage = () => <MyReviews />

/** 리뷰 쓰기 (?visitId=) — 아직 리뷰를 안 쓴 내 QR 방문 인증만 (인증 1건당 1개) */
export function ReviewWritePage() {
  const me = useMe()
  const { pathname, search } = useLocation()
  const visitId = Number(useSearchParams()[0].get('visitId'))
  const { data: writable, isLoading } = useWritableVisits()

  if (!me) return <Navigate to={`/login?next=${encodeURIComponent(pathname + search)}`} replace />
  if (isLoading) return <Loading />
  const visit = writable?.find((v) => v.visitId === visitId)
  if (visit) return <ReviewForm mode="create" visitId={visit.visitId} storeName={visit.storeName} />
  return <ReviewNotAllowed />
}

/** 내 리뷰 수정 */
export function ReviewEditPage() {
  const reviewId = Number(useParams().reviewId)
  const { data, isLoading } = useMyReviews()
  if (isLoading) return <Loading />
  const review = data?.reviews.find((r) => r.reviewId === reviewId)
  if (!review) return <Navigate to="/profile/reviews" replace />
  return <ReviewForm key={review.reviewId} mode="edit" review={review} />
}

/** 리뷰 보상 뽑기 (남은 횟수만큼) */
export function ReviewDrawPage() {
  const reviewId = Number(useParams().reviewId)
  const { data, isLoading } = useMyReviews()
  if (isLoading) return <Loading />
  const review = data?.reviews.find((r) => r.reviewId === reviewId)
  if (!review) return <Navigate to="/profile/reviews" replace />
  return <ReviewDraw key={review.reviewId} review={review} />
}

/** 사장님 가게 프로필 > 리뷰 전체 */
export function OwnerReviewsPage() {
  return <StoreReviews storeId={useMyStoreId()} />
}

/** 사장님 알림 (새 리뷰) */
export const OwnerNotificationsPage = () => <NotificationList />
