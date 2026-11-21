import { useSearchParams } from 'react-router-dom'
import { OwnerCoupons } from './OwnerCoupons'
import { OwnerRedeem } from './OwnerRedeem'
import { OwnerSettlement } from './OwnerSettlement'

const TABS = [
  { key: 'redeem', label: '사용 처리' },
  { key: 'issue', label: '발행' },
] as const

type TabKey = (typeof TABS)[number]['key']

/**
 * 쿠폰 탭: 사용 처리(기본) / 발행 전환.
 * ?view=settlement 로 직접 진입하면 정산 화면 (홈의 "정산 보기 ›" 링크용).
 */
export function OwnerCouponTabs() {
  const [params, setParams] = useSearchParams()

  const rawView = params.get('view')
  const view: TabKey | 'settlement' =
    rawView === 'issue' ? 'issue' : rawView === 'settlement' ? 'settlement' : 'redeem'

  const activeTab: TabKey = view === 'settlement' ? 'issue' : view

  return (
    <div className="flex h-full flex-col bg-q-panel">
      <div className="shrink-0 px-5 pt-4" role="tablist">
        <div className="grid grid-cols-2 rounded-full bg-surface p-1 shadow-[0_2px_8px_rgba(8,104,22,0.06)]">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={activeTab === t.key}
              onClick={() => setParams(t.key === 'redeem' ? {} : { view: t.key }, { replace: true })}
              className="h-9 rounded-full text-14 font-bold text-q-muted transition-colors aria-selected:bg-q-green aria-selected:text-white"
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      <div className="min-h-0 flex-1">
        {view === 'redeem' && <OwnerRedeem />}
        {view === 'issue' && <OwnerCoupons />}
        {view === 'settlement' && <OwnerSettlement />}
      </div>
    </div>
  )
}
