/* ── 명세: ITs API 명세 – Store (가게 검색·동네 랭킹) ── */

/** 랭킹 집계 기간 (일). 첫 번째가 기본값 */
export const RANKING_PERIODS = [7, 14, 31] as const
export type RankingPeriod = (typeof RANKING_PERIODS)[number]

export function isRankingPeriod(n: number): n is RankingPeriod {
  return (RANKING_PERIODS as readonly number[]).includes(n)
}

export interface RankingParams {
  /** 자치구 (null이면 전체 지역) */
  district: string | null
  /** 업종 코드 (null이면 모든 업종) */
  category: string | null
  /** 가게 이름 검색어 (빈 값이면 검색 안 함) */
  q: string
  days: RankingPeriod
}

/** GET /api/stores/ranking 의 stores[] */
export interface RankedStore {
  /** 방문 수가 같으면 같은 순위 */
  rank: number
  storeId: number
  name: string
  category: string
  categoryName: string
  address: string
  district: string | null
  thumbnailUrl: string | null
  /** 기간 내 QR 방문 인증 수 */
  visitCount: number
}

export interface StoreRankingResult {
  district: string | null
  category: string | null
  q: string | null
  days: RankingPeriod
  /** YYYY-MM-DD (KST, 오늘 포함) */
  from: string
  to: string
  stores: RankedStore[]
}

/** GET /api/stores/districts 의 원소 */
export interface District {
  name: string
  storeCount: number
}

/** 주소에서 자치구 (서버 District.of 와 같은 규칙). 없으면 null */
export function districtOf(address: string): string | null {
  return address.trim().match(/(?:^|\s)([가-힣]{1,5}구)(?=\s|$)/)?.[1] ?? null
}

/** 2026-11-17 → 11.17 */
export function shortDate(date: string) {
  const [, m, d] = date.split('-')
  return `${Number(m)}.${Number(d)}`
}
