import { mockFor } from '@/mocks/db'
import { api, request } from '@/shared/api/client'
import { mockStoreMetrics } from './mock'
import type { MetricsPeriod, StoreMetrics } from './schema'

/** GET /api/stores/{storeId}/metrics — 내 가게 성과지표 (목업이면 예시 데이터) */
export async function fetchStoreMetrics(storeId: number, period: MetricsPeriod, storeName: string): Promise<StoreMetrics> {
  if (mockFor('metrics')) {
    await new Promise((r) => setTimeout(r, 300))
    return mockStoreMetrics(storeId, period, storeName)
  }
  return request(api.get(`/api/stores/${storeId}/metrics`, { params: { period } }))
}

/** POST /api/shortforms/{id}/views — 게시물을 2초 이상 봤을 때 (로그인 없어도 됨). 실패해도 화면엔 영향 없음 */
export async function postShortformView(shortformId: number): Promise<void> {
  if (mockFor('metrics')) return
  await request(api.post(`/api/shortforms/${shortformId}/views`)).catch(() => undefined)
}
