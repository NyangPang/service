/* ── 명세: server/docs/api-spec.md > Review ── */
import type { LevelUp } from '@/features/quest/schema'

/** 사진 + 영상 합쳐서 */
export const REVIEW_MEDIA_MAX = 3
export const REVIEW_VIDEO_MAX_SEC = 30
export const REVIEW_VIDEO_MAX_MB = 50
export const REVIEW_CONTENT_MAX = 200
/** QR 방문 인증으로 첫 리뷰를 쓰면 받는 뽑기 횟수 (인증당 한 번) */
export const REVIEW_DRAWS = 3
/** 리뷰 보상 뽑기 확률 (서버 ReviewService 와 같음) */
export const REVIEW_DRAW_ODDS = { coupon: 0.03, feed1: 0.7, feed2: 0.27 }

export interface Review {
  reviewId: number
  /** 이 리뷰를 쓰게 해 준 QR 방문 인증. 샘플 리뷰만 null */
  visitId: number | null
  storeId: number
  storeName: string
  storeThumbnailUrl: string | null
  memberId: number
  nickname: string
  profileImageUrl: string | null
  /** 1~5 */
  rating: number
  content: string
  photoUrls: string[]
  videoUrl: string | null
  /** 비공개: 사장님과 작성자 본인에게만 보임 */
  privateReview: boolean
  /** 사장님이 숨김 (사장님·작성자 본인에게만 보임) */
  hidden: boolean
  /** 남은 보상 뽑기 — 내 리뷰에서만, 그 외 null */
  drawsLeft: number | null
  createdAt: string
  updatedAt: string
}

/** GET /api/reviews/me */
export interface MyReviewList {
  count: number
  reviews: Review[]
}

/** GET /api/stores/{storeId}/reviews */
export interface StoreReviewList {
  /** 손님에게 보이는(숨김·비공개 아닌) 리뷰 수 */
  count: number
  /** 공개 리뷰 기준, 소수 첫째 자리. 없으면 0 */
  averageRating: number
  /** 그 가게 사장님에게만 (다른 사람에겐 0) */
  hiddenCount: number
  privateCount: number
  reviews: Review[]
}

/** GET /api/reviews/writable-visits — 아직 리뷰를 안 쓴 내 QR 방문 인증 (인증 1건당 리뷰 1개, 지운 인증도 빠짐) */
export interface WritableVisit {
  visitId: number
  storeId: number
  storeName: string
  storeThumbnailUrl: string | null
  visitedAt: string
  /** 항상 true (예전 화면 호환) */
  rewardAvailable: boolean
  /** 항상 0 (예전 화면 호환) */
  reviewCount: number
}

export interface ReviewInput {
  rating: number
  content: string
  privateReview: boolean
}

/** POST /api/reviews → result */
export interface CreateReviewResult {
  review: Review
  /** 이번에 받은 뽑기 (그 인증의 첫 리뷰면 3, 아니면 0) */
  drawsGranted: number
}

/** POST /api/reviews/{reviewId}/draws → result (reward 는 레벨업 뽑기와 같은 모양) */
export interface ReviewDrawResult {
  reward: LevelUp['reward']
  drawsLeft: number
  feedBalance: number
}

/** 작성·수정 화면의 사진/영상 한 개: 이미 올린 것(url) 또는 새로 고른 것(file + 미리보기) */
export type ReviewMedia =
  | { kind: 'saved'; type: 'photo' | 'video'; url: string }
  | { kind: 'new'; type: 'photo' | 'video'; file: File; preview: string }

export const mediaSrc = (m: ReviewMedia) => (m.kind === 'saved' ? m.url : m.preview)

/** "2026.11.17" */
export function formatReviewDate(iso: string) {
  return iso.slice(0, 10).replaceAll('-', '.')
}

/** 영상 길이(초) — 브라우저에서 메타데이터만 읽음 */
export function videoDuration(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      resolve(video.duration)
    }
    video.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('영상을 읽지 못했어요'))
    }
    video.src = url
  })
}
