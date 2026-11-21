import { USE_MOCK } from '@/mocks/db'
import { api, request } from '@/shared/api/client'
import { mockExtractMenus, mockGenerationApi, mockShortformApi } from './mock'
import type { GenerationCreated, GenerationRequest, GenerationState, MenuItem, ShortformDetail } from './schema'

/**
 * 화면에서 고른 메뉴·수정 요청 → 서버 menuInfo 한 덩어리 (가게 어필은 appeal 로 따로 보냄 → 게시물 소개 글)
 * 예) "메뉴: 아메리카노 2,500원, 크로플 4,500원\n수정 요청(글·문구): 학생 할인을 넣어 주세요"
 */
function toMenuInfo(body: GenerationRequest) {
  const lines: string[] = []
  const menus = (body.menus ?? []).filter((m) => m.name.trim())
  if (menus.length) lines.push(`메뉴: ${menus.map((m) => (m.price ? `${m.name} ${m.price.toLocaleString()}원` : m.name)).join(', ')}`)
  if (body.mapUrl?.trim()) lines.push(`지도 링크: ${body.mapUrl.trim()}`)
  if (body.revision) lines.push(`수정 요청(${body.revision.target === 'VIDEO' ? '영상' : '대본 및 자막'}): ${body.revision.request}`)
  return lines.join('\n') || undefined
}

/** 화면에서 확정한 메뉴 → POST /api/generation menus (이름 없는 줄은 뺌, 없으면 보내지 않음 → 서버가 메뉴판에서 읽음) */
function confirmedMenus(body: GenerationRequest) {
  const menus = (body.menus ?? [])
    .filter((m) => m.name.trim())
    .map((m) => ({ name: m.name.trim(), price: m.price, description: m.description ?? null, signature: m.signature ?? false }))
  return menus.length ? menus : undefined
}

/** 게시물 한 개의 사장님 사진은 4장까지 (AI 사진과 합쳐 5장) */
export const MAX_POST_PHOTOS = 4
/** 메뉴 읽기에 쓰는 메뉴판 사진은 3장까지 (서버 MenuExtractor.MAX_MENU_BOARDS) */
export const MAX_MENU_BOARDS = 3

/** POST /api/stores/{storeId}/photos (multipart photos) — 게시물에 넣을 사진을 올리고 주소를 받음 (사장님 본인 가게만) */
export function uploadStorePhotos(storeId: number, files: File[]): Promise<string[]> {
  const form = new FormData()
  files.forEach((f) => form.append('photos', f))
  return request(api.post(`/api/stores/${storeId}/photos`, form, { timeout: 60_000 }))
}

/**
 * POST /api/generation { storeId, menuInfo, appeal, photoUrls, menuBoardUrls } — 사진은 먼저 올리고 주소로 보냄.
 * 서버는 음식·가게 사진으로 영상 장면을 만들고, 메뉴판은 메뉴 읽기에만 씀.
 * 게시물 사진(photoUrls)은 음식·가게 사진 먼저, 그다음 메뉴판 (합쳐 4장까지)
 */
export async function postGeneration(body: GenerationRequest): Promise<GenerationCreated> {
  const photos = (body.photos ?? []).slice(0, MAX_POST_PHOTOS)
  const boards = (body.menuBoards ?? []).slice(0, MAX_MENU_BOARDS)
  if (USE_MOCK) {
    // 목업: 올리지 않고 이 기기에서만 보이는 주소로
    const local = [...photos, ...boards].map((f) => URL.createObjectURL(f)).slice(0, MAX_POST_PHOTOS)
    return mockGenerationApi.create({ ...body, photoUrls: body.photoUrls ?? local })
  }
  const files = [...photos, ...boards]
  const uploaded = files.length ? await uploadStorePhotos(body.storeId, files) : []
  const photoUrls = files.length ? uploaded.slice(0, MAX_POST_PHOTOS) : (body.photoUrls ?? [])
  return request(
    api.post('/api/generation', {
      storeId: body.storeId,
      menuInfo: toMenuInfo(body),
      appeal: body.appeal?.trim() || undefined,
      photoUrls: photoUrls.slice(0, MAX_POST_PHOTOS),
      menuBoardUrls: uploaded.slice(photos.length),
      // 수정이면 고칠 게시물 (무료 3번까지, 넘으면 GENERATION403_2)
      revisionOf: body.revision?.shortformId,
      // 메뉴 확인 화면에서 확정한 메뉴 → 서버는 메뉴판을 다시 읽지 않고 이 메뉴로 영상을 만듦
      menus: confirmedMenus(body),
    }),
  )
}

/** GET /api/generation/{generationId} — COMPLETED/FAILED 될 때까지 폴링 */
export function fetchGeneration(generationId: number): Promise<GenerationState> {
  if (USE_MOCK) return mockGenerationApi.get(generationId)
  return request(api.get(`/api/generation/${generationId}`))
}

/** POST /api/generation/{id}/cancel — 만드는 중이면 결과를 저장하지 않고 끝냄 (이미 끝났으면 GENERATION409_2, 화면은 그냥 나감) */
export async function cancelGeneration(generationId: number) {
  if (USE_MOCK) return mockGenerationApi.cancel(generationId)
  await request(api.post(`/api/generation/${generationId}/cancel`)).catch(() => undefined)
}

/** GET /api/shortforms/{shortformId} */
export function fetchShortformDetail(shortformId: number): Promise<ShortformDetail> {
  if (USE_MOCK) return mockShortformApi.get(shortformId)
  return request(api.get(`/api/shortforms/${shortformId}`))
}

/**
 * OCR 결과 한 줄 → { name, price }
 * 서버 형식 "[아메리카노, 4500]" · "[크로플, 가격 미표기]" 와 "아메리카노 2,500원" / "치즈떡볶이 - 7000" 도 읽음.
 * 메뉴판이 천 원 단위로 적은 "4.5" 같은 값은 4500원으로
 */
export function parseMenuLine(line: string): MenuItem | null {
  const text = line
    .trim()
    .replace(/^(?:[-•·*]|\d+[.)])\s*/, '')
    .replace(/^\[|\]$/g, '')
    .trim()
  if (!text) return null
  const m = text.match(/^(.*?)[\s,:：\-–]+(\d{1,3}(?:,\d{3})+|\d+(?:\.\d+)?)\s*(천\s*원|원)?\s*$/)
  if (m && m[1].trim()) {
    const raw = m[2]
    let price = Number(raw.replace(/,/g, ''))
    if (m[3]?.startsWith('천') || (!raw.includes(',') && price < 100)) price = Math.round(price * 1000)
    return { name: m[1].trim(), price }
  }
  const name = text.replace(/[\s,]*가격\s*미표기\s*$/, '').trim()
  return name ? { name, price: null } : null
}

/**
 * 메뉴판 사진에서 메뉴 읽기 — POST /api/generation/menus (multipart storeId, menuBoards) → MenuItem[].
 * 영상 만들 때와 같은 AI 메뉴 추출기라, 여기서 확정한 메뉴가 그대로 영상에 들어감.
 * 그게 실패하면 예전 OCR(사진마다 POST /api/ocr)로 한 번 더. 지도 링크만 있으면 읽을 API가 없어 직접 입력.
 */
export async function extractMenus(storeId: number, files: File[], _mapUrl: string): Promise<MenuItem[]> {
  if (USE_MOCK) return mockExtractMenus(storeId)
  if (!files.length) return []
  const boards = files.slice(0, MAX_MENU_BOARDS)
  try {
    const form = new FormData()
    form.append('storeId', String(storeId))
    boards.forEach((f) => form.append('menuBoards', f))
    // 재시도까지 하면 수십 초 걸릴 수 있음
    return await request<MenuItem[]>(api.post('/api/generation/menus', form, { timeout: 120_000 }))
  } catch {
    return extractMenusByOcr(boards)
  }
}

/** 예전 방식: 사진마다 POST /api/ocr (multipart image) → { menuText } 를 줄 단위로 메뉴·가격으로 나눔 */
async function extractMenusByOcr(files: File[]): Promise<MenuItem[]> {
  const menus: MenuItem[] = []
  for (const file of files) {
    const form = new FormData()
    form.append('image', file)
    const { menuText } = await request<{ menuText: string }>(api.post('/api/ocr', form, { timeout: 60_000 }))
    menuText.split('\n').forEach((line) => {
      const item = parseMenuLine(line)
      if (item) menus.push(item)
    })
  }
  return menus
}

/** POST /api/shortforms/{id}/publish — 업로드(손님 피드에 공개). AI로 만든 게시물은 비공개로 생김 */
export async function publishShortform(shortformId: number) {
  if (USE_MOCK) return mockShortformApi.publish(shortformId)
  await request(api.post(`/api/shortforms/${shortformId}/publish`))
}

/** PUT /api/shortforms/{oldId}/replace — 올린 게시물을 재수정한 새 버전으로 바꾸기 (PRO). 새 버전 공개 + 옛 게시물 삭제 */
export async function replaceShortform(oldId: number, newId: number) {
  if (USE_MOCK) return mockShortformApi.replace(oldId, newId)
  return request(api.put(`/api/shortforms/${oldId}/replace`, { shortformId: newId }))
}

/** DELETE /api/shortforms/{id} — 만든(아직 안 올린) 게시물 지우기 */
export async function deleteShortform(shortformId: number) {
  if (USE_MOCK) return mockShortformApi.remove(shortformId)
  return request(api.delete(`/api/shortforms/${shortformId}`))
}
