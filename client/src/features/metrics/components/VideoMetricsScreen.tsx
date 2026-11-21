import { Link, useLocation, useParams } from 'react-router-dom'
import { OwnerScreen } from '@/features/owner/components/OwnerUi'
import { useMyStoreId, useMyStoreName } from '@/features/owner/hooks'
import { useStoreMetrics } from '../hooks'
import { longDate, type MetricsPeriod } from '../schema'

/** 영상 하나의 조회수·스크랩 수 (Figma "지표 조회" 4번째 화면) */
export function VideoMetricsScreen() {
  const { shortformId } = useParams()
  const period = ((useLocation().state as { period?: MetricsPeriod } | null)?.period ?? '7d') as MetricsPeriod
  const storeId = useMyStoreId()
  const storeName = useMyStoreName()
  const { data, isLoading } = useStoreMetrics(storeId, period, storeName)
  const video = data?.videos.find((v) => v.shortformId === Number(shortformId))

  return (
    <OwnerScreen>
      {isLoading && <div className="mt-3 h-60 animate-pulse rounded-2xl bg-surface" />}
      {data && !video && (
        <p className="mt-10 text-center text-sm text-q-muted">
          게시물을 찾을 수 없어요.{' '}
          <Link to="/owner/metrics" className="font-bold text-fg-q-green underline">
            성과지표로
          </Link>
        </p>
      )}
      {video && (
        <section className="mt-2 rounded-2xl border border-q-green/60 bg-surface px-5 py-5">
          <h2 className="text-20 font-bold text-q-text">{video.title}</h2>
          {video.postedAt && <p className="mt-0.5 text-xs text-q-muted">{longDate(video.postedAt)} 게시</p>}
          <p className="mt-3 text-xs text-q-sub">게시 후 전체 조회수 · 지금 스크랩 수</p>
          <dl className="mt-2 grid gap-2.5">
            <BigStat label="조회수" value={`${video.views.toLocaleString()}회`} />
            <BigStat label="스크랩 수" value={`${video.scraps.toLocaleString()}건`} />
          </dl>
        </section>
      )}
    </OwnerScreen>
  )
}

function BigStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-q-green/50 px-4 py-3">
      <dt className="text-15 font-bold text-q-text">{label}</dt>
      <dd className="text-[34px] leading-tight font-bold text-fg-q-green">{value}</dd>
    </div>
  )
}
