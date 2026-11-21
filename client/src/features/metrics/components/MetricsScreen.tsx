import { useState } from 'react'
import { Link } from 'react-router-dom'
import guideScrap from '@/assets/feed/guide-scrap.png'
import { OwnerCard, OwnerScreen } from '@/features/owner/components/OwnerUi'
import { useMyStoreId, useMyStoreName } from '@/features/owner/hooks'
import { OWNER_ILLUST } from '@/features/owner/illustrations'
import { mockFor } from '@/mocks/db'
import { useStoreMetrics } from '../hooks'
import { chartBars, compareText, METRICS_PERIODS, periodLabel, shortDate, type MetricsPeriod, type StoreMetrics } from '../schema'

/** 성과지표 조회 (Figma "지표 조회"): 요약 3개 → 조회 후 방문율 → 일별 방문 인증 → 영상별 조회·스크랩 */
export function MetricsScreen() {
  const storeId = useMyStoreId()
  const storeName = useMyStoreName()
  const [period, setPeriod] = useState<MetricsPeriod>('7d')
  const { data, isLoading, isError } = useStoreMetrics(storeId, period, storeName)

  return (
    <OwnerScreen>
      <div className="flex items-center justify-between gap-2">
        <h2 className="truncate text-20 font-bold text-q-text">{data?.storeName ?? storeName}</h2>
        {mockFor('metrics') && <span className="shrink-0 rounded-full bg-q-mint px-2.5 py-1 text-11 font-bold text-fg-q-green">예시 데이터</span>}
      </div>

      <div role="tablist" aria-label="기간" className="mt-3 grid grid-cols-3 gap-1 rounded-2xl bg-q-mint p-1">
        {METRICS_PERIODS.map((p) => (
          <button
            key={p.value}
            type="button"
            role="tab"
            aria-selected={period === p.value}
            onClick={() => setPeriod(p.value)}
            className={`h-10 rounded-xl text-sm font-bold ${period === p.value ? 'bg-q-green text-white' : 'text-q-sub'}`}
          >
            {p.label}
          </button>
        ))}
      </div>
      {data && <p className="mt-2 text-xs text-q-muted">{`${shortDate(data.from)} – ${shortDate(data.to)}`}</p>}

      {isLoading && <div className="mt-3 h-40 animate-pulse rounded-2xl bg-surface" />}
      {isError && <p className="mt-6 text-center text-sm text-fg-red">성과지표를 불러오지 못했어요. 잠시 후 다시 시도해 주세요</p>}
      {data && <MetricsBody data={data} period={period} />}
    </OwnerScreen>
  )
}

function MetricsBody({ data, period }: { data: StoreMetrics; period: MetricsPeriod }) {
  const { viewToVisit: vtv } = data
  return (
    <>
      <OwnerCard title="성과 한눈에 보기" className="mt-3">
        <div className="grid grid-cols-3 gap-2">
          <SummaryTile image={OWNER_ILLUST.videoDownload} label="게시물 조회" value={`${data.views.current.toLocaleString()}회`} sub={compareText(data.views, '회', period)} />
          <SummaryTile image={guideScrap} label="스크랩 수" value={`${data.scraps.current.toLocaleString()}건`} sub={compareText(data.scraps, '건', period)} />
          <SummaryTile image={OWNER_ILLUST.qr} label="방문 인증" value={`${data.visits.current.toLocaleString()}건`} sub={compareText(data.visits, '건', period)} />
        </div>
      </OwnerCard>

      <section className="mt-3 rounded-2xl border border-q-green/60 bg-surface px-5 py-4">
        <header className="flex items-baseline gap-2">
          <h3 className="text-16 font-bold text-q-text">조회 후 방문율</h3>
          <span className="text-11 text-q-muted">{periodLabel(period)} 기준</span>
        </header>
        <p className="mt-1 text-[40px] leading-tight font-bold text-fg-q-green">{vtv.rate == null ? '-' : `${vtv.rate}%`}</p>
        <p className="text-xs text-q-sub">
          {vtv.views === 0
            ? '아직 게시물 조회가 없어요. 게시물을 올리면 손님 반응이 쌓여요'
            : `게시물 조회 ${vtv.views.toLocaleString()}회 중 ${vtv.visitsAfterView.toLocaleString()}건이 방문 인증으로 이어졌어요.`}
        </p>
        <p className="mt-1 text-11 text-q-muted">게시물을 본 손님이 7일 안에 매장 QR로 방문 인증한 경우만 세요</p>
      </section>

      <OwnerCard title={data.dailyVisits.length > 31 ? '주별 방문 인증' : '일별 방문 인증'} className="mt-3">
        <p className="-mt-2 text-xs text-q-muted">
          매장 QR로 인증된 방문 횟수
        </p>
        <VisitChart days={chartBars(data.dailyVisits)} />
      </OwnerCard>

      <section className="mt-5">
        <h3 className="mb-2 text-sm font-bold text-q-text">영상별 조회수 · 스크랩 수 확인하기 ›</h3>
        {data.videos.length === 0 ? (
          <p className="rounded-2xl bg-surface px-5 py-6 text-center text-sm text-q-muted">
            아직 올린 게시물이 없어요.{' '}
            <Link to="/owner/create" className="font-bold text-fg-q-green underline">
              게시물 만들기
            </Link>
          </p>
        ) : (
          <ul className="grid grid-cols-3 gap-0.5 overflow-hidden rounded-xl">
            {data.videos.map((v) => (
              <li key={v.shortformId}>
                <Link to={`/owner/metrics/videos/${v.shortformId}`} state={{ period }} className="relative block aspect-[9/16] bg-q-card" aria-label={`${v.title} 성과 보기`}>
                  {/* 사진을 못 불러오면 사진을 숨기고 뒤에 깔린 제목이 보이게 */}
                  <span className="absolute top-2 right-8 left-2 line-clamp-3 text-11 font-bold text-q-sub">{v.title}</span>
                  {v.imageUrl && (
                    <img
                      src={v.imageUrl}
                      alt=""
                      className="relative size-full object-cover"
                      loading="lazy"
                      onError={(e) => (e.currentTarget.style.visibility = 'hidden')}
                    />
                  )}
                  {v.videoUrl && (
                    <span aria-hidden className="absolute top-1.5 right-1.5 flex size-6 items-center justify-center rounded-full bg-black/40 text-[10px] text-white">
                      ▶
                    </span>
                  )}
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent px-2 pt-4 pb-1.5 text-11 font-bold text-white">
                    조회 {v.views.toLocaleString()}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function SummaryTile({ image, label, value, sub }: { image: string; label: string; value: string; sub: string | null }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-q-green/40 bg-surface px-1 pt-2 pb-2.5 text-center">
      <img src={image} alt="" className="size-10 object-contain" />
      <span className="mt-1 text-11 font-bold text-q-sub">{label}</span>
      <span className="text-15 font-bold text-q-text">{value}</span>
      {sub && <span className="mt-0.5 text-[10px] leading-tight text-q-muted">{sub}</span>}
    </div>
  )
}

/** 날짜별 방문 막대 (오늘 = 진한 초록). 30일 이상이면 날짜 글씨는 몇 개만 */
function VisitChart({ days }: { days: StoreMetrics['dailyVisits'] }) {
  const max = Math.max(3, ...days.map((d) => d.count))
  const top = Math.ceil(max / 3) * 3
  const labelEvery = days.length > 10 ? Math.ceil(days.length / 6) : 1
  return (
    <figure aria-label="일별 방문 인증 막대그래프" className="mt-5">
      <div className="relative h-40">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="absolute inset-x-0 flex items-center gap-2" style={{ bottom: `${(i / 3) * 100}%` }}>
            <span className="w-4 -translate-y-1/2 text-right text-[10px] text-q-muted">{(top / 3) * i}</span>
            <span className="h-px flex-1 bg-q-line" />
          </div>
        ))}
        <div className="absolute inset-y-0 right-0 left-6 flex items-end gap-1">
          {days.map((d, i) => (
            <div
              key={d.date}
              title={`${shortDate(d.date)} ${d.count}건`}
              className={`flex-1 rounded-t-md ${i === days.length - 1 ? 'bg-q-green' : 'bg-q-green/30'}`}
              style={{ height: `${(d.count / top) * 100}%` }}
            />
          ))}
        </div>
      </div>
      <div className="mt-1.5 ml-6 flex gap-1">
        {days.map((d, i) => (
          <span key={d.date} className="flex-1 text-center text-[10px] text-q-muted">
            {i % labelEvery === 0 || i === days.length - 1 ? shortDate(d.date) : ''}
          </span>
        ))}
      </div>
      <figcaption className="mt-2 text-[10px] text-q-muted">단위 건</figcaption>
    </figure>
  )
}
