import { allMockShortforms } from '@/features/feed/mock'
import type { MetricsPeriod, StoreMetrics } from './schema'

const DAY = 86_400_000
const ymd = (t: number) => new Date(t + 9 * 3_600_000).toISOString().slice(0, 10)

/** 시연용 예시 성과 (Figma 예시 숫자와 비슷하게). 실제 서버를 쓰면 부르지 않음 */
export function mockStoreMetrics(storeId: number, period: MetricsPeriod, storeName: string): StoreMetrics {
  const now = Date.now()
  const days = period === '7d' ? 7 : period === '30d' ? 30 : new Date(now + 9 * 3_600_000).getUTCDate()
  const scale = days / 7
  const from = now - (days - 1) * DAY
  // 최근으로 갈수록 방문이 느는 모양 (마지막 날 = 오늘)
  const pattern = [3, 4, 3, 5, 4, 8, 9]
  const dailyVisits = Array.from({ length: days }, (_, i) => ({
    date: ymd(from + i * DAY),
    count: pattern[(i + 7 - (days % 7)) % 7] + (i >= days - 2 ? 1 : 0) - 1,
  }))
  const visits = dailyVisits.reduce((s, d) => s + d.count, 0)
  const views = Math.round(1248 * scale)
  const visitsAfterView = Math.round(visits * 0.85)
  const videos = allMockShortforms()
    .filter((s) => s.storeId === storeId)
    .slice(0, 9)
    .map((s, i) => ({
      shortformId: s.shortformId,
      title: s.description || s.storeName,
      imageUrl: s.posterUrl,
      videoUrl: s.videoUrl ?? null,
      postedAt: s.createdAt ?? new Date(now - (i + 1) * 3 * DAY).toISOString(),
      views: Math.max(12, Math.round(201 / (i + 1))),
      scraps: Math.max(0, 3 - i),
    }))
  return {
    storeId,
    // 가게 목록을 불러오기 전이면 이름이 '내 가게'로 와서, 샘플 게시물의 가게 이름을 씀
    storeName: allMockShortforms().find((s) => s.storeId === storeId)?.storeName ?? storeName,
    period,
    from: ymd(from),
    to: ymd(now),
    previousFrom: ymd(from - days * DAY),
    previousTo: ymd(from - DAY),
    views: { current: views, previous: Math.round(1021 * scale) },
    scraps: { current: Math.round(scale), previous: Math.round(scale) },
    visits: { current: visits, previous: Math.max(0, visits - 1) },
    viewToVisit: { views, visitsAfterView, rate: views ? Math.round((visitsAfterView * 1000) / views) / 10 : null },
    dailyVisits,
    videos,
  }
}
