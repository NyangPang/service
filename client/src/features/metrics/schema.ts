/** 사장님 성과지표 (GET /api/stores/{storeId}/metrics) */

export type MetricsPeriod = '7d' | '30d' | 'all'

export const METRICS_PERIODS: { value: MetricsPeriod; label: string; previous: string | null }[] = [
  { value: '7d', label: '최근 7일', previous: '이전 7일' },
  { value: '30d', label: '최근 30일', previous: '이전 30일' },
  // 전체 기간 = 가게 등록일부터, 비교할 앞 기간이 없음
  { value: 'all', label: '전체 기간', previous: null },
]

export interface MetricsCount {
  /** 이번 기간 */
  current: number
  /** 바로 앞 같은 길이 기간 (전체 기간이면 null) */
  previous: number | null
}

export interface VideoMetrics {
  shortformId: number
  title: string
  imageUrl: string | null
  videoUrl: string | null
  postedAt: string | null
  /** 게시 후 전체 조회수 */
  views: number
  /** 지금 스크랩 수 */
  scraps: number
}

export interface StoreMetrics {
  storeId: number
  storeName: string
  period: MetricsPeriod
  /** "YYYY-MM-DD" (KST, 양 끝 포함) */
  from: string
  to: string
  previousFrom: string | null
  previousTo: string | null
  views: MetricsCount
  scraps: MetricsCount
  visits: MetricsCount
  /** 조회 후 방문율: 방문 전 7일 안에 게시물을 본 손님의 방문 ÷ 조회수. rate = %, 조회 0이면 null */
  viewToVisit: { views: number; visitsAfterView: number; rate: number | null }
  dailyVisits: { date: string; count: number }[]
  videos: VideoMetrics[]
}

export const periodLabel = (period: MetricsPeriod) => METRICS_PERIODS.find((p) => p.value === period)?.label ?? ''
export const previousLabel = (period: MetricsPeriod) => METRICS_PERIODS.find((p) => p.value === period)?.previous ?? null

/** "2026-11-15" → "11.15" */
export const shortDate = (ymd: string) => {
  const [, m, d] = ymd.split('-')
  return `${m}.${d}`
}

/** "2026-11-21T10:00:00+09:00" → "2026년 11월 21일" */
export const longDate = (iso: string | null) => {
  if (!iso) return ''
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return `${y}년 ${m}월 ${d}일`
}

/** 이전 기간 비교 줄 (Figma: "▲1,021회 · 이전 7일", 같으면 "-0건 · 이전 7일"). 전체 기간이면 비교 없이 null */
export function compareText({ current, previous }: MetricsCount, unit: string, period: MetricsPeriod) {
  const label = previousLabel(period)
  if (previous == null || !label) return null
  const mark = current > previous ? '▲' : current < previous ? '▼' : '-'
  return `${mark}${previous.toLocaleString()}${unit} · ${label}`
}

/** 막대가 너무 많으면(31일 넘게) 주 단위로 묶음. 라벨은 그 주의 첫 날 */
export function chartBars(days: { date: string; count: number }[]) {
  if (days.length <= 31) return days
  const weeks: { date: string; count: number }[] = []
  for (let i = 0; i < days.length; i += 7) {
    const chunk = days.slice(i, i + 7)
    weeks.push({ date: chunk[0].date, count: chunk.reduce((s, d) => s + d.count, 0) })
  }
  return weeks
}
