import { mockFor, mockRankingApi } from '@/mocks/db'
import { api, request } from '@/shared/api/client'
import type { District, RankingParams, StoreRankingResult } from './schema'

/**
 * GET /api/stores/ranking — 실제 방문(QR 인증) 많은 순.
 * 방문 기록은 퀘스트 데이터라 퀘스트가 목업이면 랭킹도 목업으로 계산
 */
export function fetchRanking({ district, category, q, days }: RankingParams): Promise<StoreRankingResult> {
  if (mockFor('quest')) return mockRankingApi.ranking({ district, category, q, days })
  const params = {
    days,
    ...(district ? { district } : {}),
    ...(category ? { category } : {}),
    ...(q.trim() ? { q: q.trim() } : {}),
  }
  return request(api.get('/api/stores/ranking', { params }))
}

/** GET /api/stores/districts — 가게가 있는 자치구 */
export function fetchDistricts(): Promise<District[]> {
  if (mockFor('quest')) return mockRankingApi.districts()
  return request(api.get('/api/stores/districts'))
}
