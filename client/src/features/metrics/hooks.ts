import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { fetchStoreMetrics, postShortformView } from './api'
import type { MetricsPeriod } from './schema'

export const metricsKeys = {
  store: (storeId: number, period: MetricsPeriod) => ['metrics', storeId, period] as const,
}

export function useStoreMetrics(storeId: number, period: MetricsPeriod, storeName: string, enabled = true) {
  return useQuery({
    queryKey: metricsKeys.store(storeId, period),
    queryFn: () => fetchStoreMetrics(storeId, period, storeName),
    enabled,
    staleTime: 30_000,
  })
}

/** 앱을 켜 둔 동안 이미 보낸 게시물 (같은 게시물을 다시 넘겨 봐도 한 번만 보냄. 서버도 로그인 손님은 하루 1번만 셈) */
const reported = new Set<number>()

/** 게시물을 delayMs 이상 보고 있으면 조회 1회 기록 (사장님 성과지표의 조회수) */
export function useRecordShortformView(shortformId: number | null | undefined, delayMs = 2000) {
  useEffect(() => {
    if (shortformId == null || reported.has(shortformId)) return
    const timer = setTimeout(() => {
      reported.add(shortformId)
      void postShortformView(shortformId)
    }, delayMs)
    return () => clearTimeout(timer)
  }, [shortformId, delayMs])
}
