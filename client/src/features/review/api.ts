import { mockFor, mockReviewApi, type MockMedia } from '@/mocks/db'
import { api, ApiError, request } from '@/shared/api/client'
import { useAuthStore } from '@/stores/authStore'
import type {
  CreateReviewResult,
  MyReviewList,
  Review,
  ReviewDrawResult,
  ReviewInput,
  ReviewMedia,
  StoreReviewList,
  WritableVisit,
} from './schema'

/** 목업 리뷰에 보일 작성자 (로그인한 회원) */
function author() {
  const member = useAuthStore.getState().member
  if (!member) throw new ApiError('COMMON401', '로그인이 필요해요')
  return { memberId: member.memberId, nickname: member.nickname, profileImageUrl: member.profileImageUrl ?? null }
}

const authorIfLoggedIn = () => (useAuthStore.getState().member ? author() : undefined)

/** multipart: data(JSON) + photos(새 사진 여러 장) + video(새 영상 1개) */
function reviewForm(data: object, media: ReviewMedia[]) {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  media.forEach((m) => m.kind === 'new' && form.append(m.type === 'video' ? 'video' : 'photos', m.file))
  return form
}

/** 기존(saved) 사진 주소 / 영상을 남기는지 */
const savedPhotoUrls = (media: ReviewMedia[]) => media.flatMap((m) => (m.kind === 'saved' && m.type === 'photo' ? [m.url] : []))
const keepsVideo = (media: ReviewMedia[]) => media.some((m) => m.kind === 'saved' && m.type === 'video')

/** 목업 저장용: 사진은 미리보기 data URL, 영상은 브라우저 임시 주소 */
function mockMedia(media: ReviewMedia[], kind: 'saved' | 'new'): MockMedia {
  const src = (m: ReviewMedia) => (m.kind === 'saved' ? m.url : m.preview)
  const pick = media.filter((m) => m.kind === kind)
  const video = pick.find((m) => m.type === 'video')
  return { photoUrls: pick.filter((m) => m.type === 'photo').map(src), videoUrl: video ? src(video) : null }
}

/**
 * GET /api/stores/{storeId}/reviews — 숨긴·비공개 리뷰는 빠짐.
 * 서버는 토큰으로 사장님을 알아봐서 숨긴·비공개 리뷰도 줌. 목업은 asOwner(사장님 화면에서 부름)로 구분
 */
export function fetchStoreReviews(storeId: number, asOwner = false): Promise<StoreReviewList> {
  if (mockFor('review')) return mockReviewApi.storeList(storeId, asOwner, authorIfLoggedIn())
  return request(api.get(`/api/stores/${storeId}/reviews`))
}

/** PATCH /api/stores/{storeId}/reviews/{reviewId}/visibility — 사장님 리뷰 숨기기·다시 보이기 */
export function setReviewHidden(storeId: number, reviewId: number, hidden: boolean): Promise<Review> {
  if (mockFor('review')) return mockReviewApi.setHidden(storeId, reviewId, hidden, authorIfLoggedIn())
  return request(api.patch(`/api/stores/${storeId}/reviews/${reviewId}/visibility`, { hidden }))
}

/** GET /api/reviews/me — 손님 마이페이지 > 내 리뷰 */
export function fetchMyReviews(): Promise<MyReviewList> {
  if (mockFor('review')) return mockReviewApi.mine(author())
  return request(api.get('/api/reviews/me'))
}

/** GET /api/reviews/writable-visits — 아직 리뷰가 없는 QR 방문 인증 */
export function fetchWritableVisits(): Promise<WritableVisit[]> {
  if (mockFor('review')) return mockReviewApi.writableVisits()
  return request(api.get('/api/reviews/writable-visits'))
}

/** POST /api/reviews — 이 인증으로 처음 쓰는 리뷰면 뽑기 3회 */
export function createReview(visitId: number, input: ReviewInput, media: ReviewMedia[]): Promise<CreateReviewResult> {
  if (mockFor('review')) return mockReviewApi.create(visitId, input, mockMedia(media, 'new'), author())
  return request(api.post('/api/reviews', reviewForm({ visitId, ...input }, media)))
}

/** PATCH /api/reviews/{reviewId} — 남길 기존 사진·영상 + 새 사진·영상 */
export function updateReview(reviewId: number, input: ReviewInput, media: ReviewMedia[]): Promise<Review> {
  if (mockFor('review')) return mockReviewApi.update(reviewId, input, mockMedia(media, 'saved'), mockMedia(media, 'new'), author())
  const data = { ...input, keepPhotoUrls: savedPhotoUrls(media), keepVideo: keepsVideo(media) }
  return request(api.patch(`/api/reviews/${reviewId}`, reviewForm(data, media)))
}

/** DELETE /api/reviews/{reviewId} — 남은 뽑기는 사라짐 (revokedDraws) */
export function deleteReview(reviewId: number): Promise<{ revokedDraws: number }> {
  if (mockFor('review')) return mockReviewApi.remove(reviewId)
  return request(api.delete(`/api/reviews/${reviewId}`))
}

/** POST /api/reviews/{reviewId}/draws — 보상 뽑기 1회 */
export function drawReviewReward(reviewId: number): Promise<ReviewDrawResult> {
  if (mockFor('review')) return mockReviewApi.draw(reviewId)
  return request(api.post(`/api/reviews/${reviewId}/draws`))
}
