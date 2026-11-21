/* ── server/docs/api-spec.md — Generation · Shortform 단건 ── */

export type GenerationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface MenuItem {
  name: string
  /** 원 단위. 모르면 null */
  price: number | null
  /** 메뉴판에 적힌 설명 (화면엔 안 보임, 생성 요청에 그대로 돌려보내 영상 문구에 씀) */
  description?: string | null
  /** 메뉴판의 대표·BEST·추천 표시 */
  signature?: boolean
}

/**
 * POST /api/generation body.
 * 명세는 storeId만 받음 — Figma 흐름의 지도 링크·메뉴·어필·수정 요청은 추가 필드로 제안 (서버가 모르면 무시)
 */
export interface GenerationRequest {
  storeId: number
  mapUrl?: string
  /** 메뉴 확인 화면의 가게명·주소가 등록된 가게 정보와 다를 때만 (지도 링크에서 읽었거나 사장님이 고침) */
  place?: PlaceInfo
  menus?: MenuItem[]
  appeal?: string
  /** 게시물에 같이 넣을 음식·가게 사진 (최대 4장) — 보내기 전에 서버에 올림 */
  photos?: File[]
  /** 메뉴판 사진 (최대 3장) — 영상 만들 때 메뉴 읽기에 쓰고, 게시물 사진에도 음식·가게 사진 뒤로 붙음 */
  menuBoards?: File[]
  /** 이미 올린 사장님 사진 주소 (재수정 때 원래 게시물 사진을 그대로) */
  photoUrls?: string[]
  /** 재수정(PRO): 무엇을 어떻게 고칠지 */
  revision?: { shortformId: number; target: 'VIDEO' | 'SCRIPT'; request: string }
  /** 생성 느낌: 영상 스타일 (3개 중 1개) */
  videoStyle?: VideoStyle
  /** 생성 느낌: 강조 포인트 (여러 개) */
  emphases?: Emphasis[]
}

/** GET /api/generation/place — 지도 링크에서 읽은 가게명·주소 (못 읽으면 null) */
export interface PlaceInfo {
  name: string | null
  address: string | null
}

/* ── 생성 느낌 (Figma 숏폼 생성 느낌) — 서버 VideoStyle·Emphasis 와 같은 값 ── */

export const VIDEO_STYLES = [
  { id: 'MENU_FOCUS', label: '메뉴가 잘 보이는 영상', desc: '음식 클로즈업, 짧은 자막' },
  { id: 'PERSON_INTRO', label: '인물이 소개하는 영상', desc: '친근한 말투, 차분한 전환' },
  { id: 'NEWS_PROMO', label: '신메뉴 및 할인 알림 영상', desc: '첫 장면에 핵심 소식, 빠른 전환' },
] as const
export type VideoStyle = (typeof VIDEO_STYLES)[number]['id']

export const EMPHASES = [
  { id: 'FAST_CUTS', label: '빠른 장면 전환' },
  { id: 'MENU_CLOSEUP', label: '메뉴 클로즈업' },
  { id: 'BIG_CAPTION', label: '큰 자막' },
  { id: 'FAST_TEMPO', label: '빠른 템포' },
  { id: 'SLOW_TEMPO', label: '느린 템포' },
] as const
export type Emphasis = (typeof EMPHASES)[number]['id']

export interface GenerationCreated {
  generationId: number
  storeId: number
  storeName: string
  status: GenerationStatus
  requestedAt: string
}

/** GET /api/generation/{generationId} */
export interface GenerationState extends GenerationCreated {
  shortformId: number | null
  errorMessage: string | null
  /** AI 홍보 영상 (영상을 못 만들어 이미지 게시물이 됐으면 null) */
  videoUrl?: string | null
}

/** GET /api/shortforms/{shortformId} */
export interface ShortformDetail {
  shortformId: number
  storeId: number
  storeName: string
  storeCategory: string
  storeCategoryName: string
  /** AI 생성 게시물 사진 (첫 장). 영상 게시물이면 영상 썸네일 */
  imageUrl: string | null
  /** AI 홍보 영상 (세로 9:16 mp4, 내려받기 가능). 사진만 있는 게시물은 null */
  videoUrl?: string | null
  /** 옆으로 넘겨 볼 사진 전체 (AI 사진 + 사장님 사진) */
  imageUrls?: string[]
  title: string
  /** 게시물 본문 (가게 정보·메뉴·사장님 한마디로 AI가 쓴 3~5문장, 줄바꿈 있음) */
  caption?: string | null
  createdAt: string
  /** 처음 게시물에서 고쳐 만든 횟수 (0 = 처음). 무료는 FREE_REVISIONS 번까지 */
  revisionCount?: number
  /** 목업 전용: 샘플 사진의 실제 그림 구간 (feed/schema 의 frame 과 같음) */
  frame?: { top: number; height: number }
}

/** 수정에서 무엇을 고칠지: 영상 / 대본 및 자막 (Figma 숏폼 수정. 값 VIDEO·SCRIPT 는 서버와 맞춘 이름이라 그대로) */
export const EDIT_TARGETS = [
  ['VIDEO', '영상'],
  ['SCRIPT', '대본 및 자막'],
] as const
export type EditTarget = (typeof EDIT_TARGETS)[number][0]

export const editPlaceholder = (target: EditTarget) =>
  target === 'VIDEO' ? '예) 음식 사진을 더 크게 보여주세요' : '예) 마지막에 "학생 10% 할인" 자막을 넣어주세요'

/**
 * 네이버·카카오 지도 공유 링크인지
 * - 네이버: naver.me 단축 링크, (m.)map.naver.com
 * - 카카오: kko.kakao.com 단축 링크, (m.)map.kakao.com, place.map.kakao.com
 */
export function isMapUrl(url: string) {
  return /^https?:\/\/(naver\.me|(m\.)?map\.naver\.com|kko\.kakao\.com|(m\.|place\.)?map\.kakao\.com)\//.test(url.trim())
}

/** 내려받을 파일 이름: "가게이름_잇츠.mp4" (서버 ShortformVideoService.downloadName 과 같은 규칙) */
export function videoFileName(storeName: string) {
  const name = storeName.replace(/^\[샘플\]\s*/, '').replace(/[\\/:*?"<>|\s]+/g, '_').replace(/^_+|_+$/g, '')
  return `${name || '홍보영상'}_잇츠.mp4`
}

/** 게시물마다 무료로 고칠 수 있는 횟수 (서버 Shortform.FREE_REVISIONS 와 같음). PRO는 무제한 */
export const FREE_REVISIONS = 1

/** 남은 무료 수정 횟수. PRO면 Infinity */
export function revisionsLeft(revisionCount: number | undefined, isPro: boolean) {
  return isPro ? Infinity : Math.max(0, FREE_REVISIONS - (revisionCount ?? 0))
}

/** 수정 화면에 보일 남은 횟수 안내 */
export function revisionNote(left: number) {
  if (left === Infinity) return 'PRO · 횟수 제한 없이 고칠 수 있어요'
  return left > 0 ? `무료 수정 ${left}번 남았어요` : `무료 수정 ${FREE_REVISIONS}번을 다 썼어요`
}

/** 무료 수정을 다 썼을 때 PRO 안내 문구 */
export const REVISION_LIMIT_DIALOG = {
  title: `무료 수정 ${FREE_REVISIONS}번을 다 썼어요`,
  text: 'PRO를 구독하면 횟수 제한 없이 계속 고칠 수 있어요',
} as const
