import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import pigeonWalk from '@/assets/map/pigeon-walk.png'
import { STORE_CATEGORIES } from '@/features/map/schema'
import { errorMessage } from '@/shared/lib/error'
import { CloseIcon, SearchIcon } from '@/shared/ui/icons'
import { StoreAvatar } from '@/shared/ui/StoreAvatar'
import { useDebounced, useDistricts, useRanking } from '../hooks'
import { isRankingPeriod, RANKING_PERIODS, shortDate, type RankedStore, type RankingPeriod } from '../schema'

/**
 * 가게 검색 · 동네 랭킹.
 * 검색어가 없으면 업종을 골라(또는 모든 업종) 실제 방문 많은 순으로, 검색어가 있으면 이름이 맞는 가게를 같은 순서로.
 * 조건은 주소(?district&category&days&q)에 남겨서 가게를 보고 돌아와도 그대로.
 */
export function StoreSearch() {
  const [params, setParams] = useSearchParams()
  const district = params.get('district') || null
  const category = params.get('category') || null
  const daysParam = Number(params.get('days'))
  const days: RankingPeriod = isRankingPeriod(daysParam) ? daysParam : RANKING_PERIODS[0]

  const [input, setInput] = useState(() => params.get('q') ?? '')
  const q = useDebounced(input.trim())
  const searching = q.length > 0

  // 검색 중에는 업종 칩이 안 보이므로 업종으로 거르지 않음 (지역·기간만)
  const { data, isLoading, isFetching, error } = useRanking({
    district,
    category: searching ? null : category,
    q,
    days,
  })
  const { data: districts } = useDistricts()

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    setParams(next, { replace: true })
  }
  const changeInput = (value: string) => {
    setInput(value)
    update({ q: value.trim() || null })
  }

  const categoryName = STORE_CATEGORIES.find((c) => c.code === category)?.name
  const scope = [district ?? '전체 지역', searching ? null : (categoryName ?? '모든 업종')].filter(Boolean).join(' · ')

  return (
    <div className="h-full overflow-y-auto pb-8">
      <div className="sticky top-0 z-10 bg-white px-5 pt-3 pb-3">
        <label className="flex h-12 items-center gap-2 rounded-full border border-q-line bg-q-panel px-4 text-q-muted focus-within:border-q-green">
          <SearchIcon />
          <input
            value={input}
            onChange={(e) => changeInput(e.target.value)}
            type="search"
            enterKeyHint="search"
            placeholder="가게 이름을 검색해 보세요"
            aria-label="가게 이름 검색"
            className="min-w-0 flex-1 bg-transparent text-[15px] text-q-text outline-none placeholder:text-q-muted [&::-webkit-search-cancel-button]:hidden"
          />
          {input && (
            <button type="button" aria-label="검색어 지우기" onClick={() => changeInput('')} className="flex size-7 items-center justify-center">
              <CloseIcon />
            </button>
          )}
        </label>

        <div className="mt-2.5 flex items-center gap-2">
          <select
            value={district ?? ''}
            onChange={(e) => update({ district: e.target.value || null })}
            aria-label="지역 선택"
            className="h-9 min-w-0 flex-1 rounded-full border border-q-line bg-white px-3 text-[13px] font-medium text-q-text outline-none focus:border-q-green"
          >
            <option value="">전체 지역</option>
            {districts?.map((d) => (
              <option key={d.name} value={d.name}>
                {d.name} ({d.storeCount})
              </option>
            ))}
            {/* 주소로 들어온 지역이 목록에 없을 때도 선택된 값이 보이게 */}
            {district && districts && !districts.some((d) => d.name === district) && <option value={district}>{district}</option>}
          </select>
          <div role="radiogroup" aria-label="집계 기간" className="flex shrink-0 rounded-full bg-q-panel p-0.5">
            {RANKING_PERIODS.map((p) => (
              <button
                key={p}
                type="button"
                role="radio"
                aria-checked={days === p}
                onClick={() => update({ days: p === RANKING_PERIODS[0] ? null : String(p) })}
                className={`h-8 rounded-full px-3 text-[13px] font-bold transition-colors ${
                  days === p ? 'bg-q-green text-white' : 'text-q-sub'
                }`}
              >
                {p}일
              </button>
            ))}
          </div>
        </div>
      </div>

      {!searching && (
        <ul className="flex gap-2 overflow-x-auto px-5 pb-3" aria-label="업종 선택">
          {[{ code: null, name: '모든 업종' }, ...STORE_CATEGORIES].map((c) => {
            const selected = c.code === category
            return (
              <li key={c.code ?? 'all'} className="shrink-0">
                <button
                  type="button"
                  aria-pressed={selected}
                  onClick={() => update({ category: c.code })}
                  className={`rounded-full border px-3 py-1.5 text-[13px] font-medium whitespace-nowrap ${
                    selected ? 'border-q-green bg-q-green text-white' : 'border-q-line bg-white text-q-sub'
                  }`}
                >
                  {c.name}
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <section className="px-5" aria-busy={isFetching}>
        <div className="mb-2.5">
          <h2 className="text-[17px] font-bold text-q-text">
            {searching ? `'${q}' 검색 결과` : `${scope} 인기 순위`}
            {data && <span className="ml-1.5 text-sm font-medium text-q-muted">{data.stores.length}곳</span>}
          </h2>
          {data && (
            <p className="mt-0.5 text-xs text-q-muted">
              {searching && `${scope} · `}최근 {data.days}일({shortDate(data.from)}~{shortDate(data.to)}) 실제 방문 많은 순
            </p>
          )}
        </div>

        {isLoading && <div className="h-40 animate-pulse rounded-2xl bg-q-mint" />}
        {error && <p className="py-8 text-center text-sm text-q-muted">{errorMessage(error)}</p>}
        {data && data.stores.length === 0 && (
          <div className="flex flex-col items-center py-10 text-center">
            <img src={pigeonWalk} alt="" className="size-20 object-contain opacity-80" />
            <p className="mt-2 text-sm text-q-muted">
              {searching ? '이름이 맞는 가게가 없어요' : '이 조건에 맞는 가게가 아직 없어요'}
            </p>
          </div>
        )}
        {data && data.stores.length > 0 && (
          <ol className={`flex flex-col gap-2 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
            {data.stores.map((s) => (
              <RankingRow key={s.storeId} store={s} showRank={!searching} />
            ))}
          </ol>
        )}
      </section>
    </div>
  )
}

const MEDAL = ['bg-[#f5b301] text-white', 'bg-[#a7b0ba] text-white', 'bg-[#c98a4b] text-white']

function RankingRow({ store, showRank }: { store: RankedStore; showRank: boolean }) {
  return (
    <li>
      <Link to={`/map/stores/${store.storeId}`} className="flex items-center gap-3 rounded-2xl bg-q-panel px-3.5 py-3 active:bg-q-mint">
        {showRank && (
          <span
            aria-label={`${store.rank}위`}
            className={`flex size-7 shrink-0 items-center justify-center rounded-full text-[13px] font-bold ${
              store.visitCount > 0 && store.rank <= 3 ? MEDAL[store.rank - 1] : 'text-q-sub'
            }`}
          >
            {store.rank}
          </span>
        )}
        <StoreAvatar url={store.thumbnailUrl} className="size-11" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[15px] font-bold text-q-text">{store.name}</p>
          <p className="truncate text-xs text-q-muted">
            <span className="font-medium text-q-green">{store.categoryName}</span>
            {store.district && ` · ${store.district}`}
          </p>
        </div>
        <span className={`shrink-0 text-right text-xs ${store.visitCount > 0 ? 'font-bold text-q-text' : 'text-q-muted'}`}>
          {store.visitCount > 0 ? `방문 ${store.visitCount}` : '방문 없음'}
        </span>
      </Link>
    </li>
  )
}
