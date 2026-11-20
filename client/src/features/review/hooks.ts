import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { questKeys } from '@/features/quest/hooks'
import { useMe } from '@/stores/authStore'
import {
  createReview,
  deleteReview,
  drawReviewReward,
  fetchMyReviews,
  fetchStoreReviews,
  fetchWritableVisits,
  setReviewHidden,
  updateReview,
} from './api'
import type { ReviewInput, ReviewMedia } from './schema'

export const reviewKeys = {
  mine: ['reviews', 'me'] as const,
  writable: ['reviews', 'writable'] as const,
  store: (storeId: number, asOwner: boolean) => ['reviews', 'store', storeId, asOwner ? 'owner' : 'public'] as const,
}

/** 로그인 전이면 부르지 않음 */
export function useMyReviews() {
  const me = useMe()
  return useQuery({ queryKey: reviewKeys.mine, queryFn: fetchMyReviews, enabled: !!me })
}

export function useWritableVisits() {
  const me = useMe()
  return useQuery({ queryKey: reviewKeys.writable, queryFn: fetchWritableVisits, enabled: !!me })
}

/** asOwner: 사장님 화면이면 숨긴·비공개 리뷰도 함께 */
export function useStoreReviews(storeId: number, asOwner = false) {
  return useQuery({ queryKey: reviewKeys.store(storeId, asOwner), queryFn: () => fetchStoreReviews(storeId, asOwner) })
}

/** 리뷰 화면은 모두 'reviews' 아래라 한 번에 새로고침 */
function useInvalidateReviews() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: ['reviews'] })
}

export function useCreateReview() {
  const qc = useQueryClient()
  const invalidate = useInvalidateReviews()
  return useMutation({
    mutationFn: (v: { visitId: number; input: ReviewInput; media: ReviewMedia[] }) => createReview(v.visitId, v.input, v.media),
    // 사장님 알림이 생겼을 수 있음 (같은 기기에서 사장님 모드로 바꿔 보는 경우)
    onSuccess: () => {
      invalidate()
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useUpdateReview() {
  return useMutation({
    mutationFn: (v: { reviewId: number; input: ReviewInput; media: ReviewMedia[] }) => updateReview(v.reviewId, v.input, v.media),
    onSuccess: useInvalidateReviews(),
  })
}

export function useDeleteReview() {
  return useMutation({ mutationFn: deleteReview, onSuccess: useInvalidateReviews() })
}

/** 보상 뽑기 1회 → 남은 횟수·보유 먹이·쿠폰함이 바뀜 */
export function useDrawReviewReward() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: drawReviewReward,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reviews'] })
      qc.invalidateQueries({ queryKey: questKeys.pigeon })
      qc.invalidateQueries({ queryKey: ['coupons'] })
    },
  })
}

/** 사장님: 리뷰 숨기기·다시 보이기 */
export function useSetReviewHidden(storeId: number) {
  return useMutation({
    mutationFn: (v: { reviewId: number; hidden: boolean }) => setReviewHidden(storeId, v.reviewId, v.hidden),
    onSuccess: useInvalidateReviews(),
  })
}
