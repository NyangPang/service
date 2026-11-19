import { useState } from 'react'
import { Sheet } from '@/shared/ui/Sheet'
import { useDistricts } from '../hooks'
import { SEOUL_DISTRICTS } from '../schema'

/** 지역 선택: 핀 버튼 → 아래에서 올라오는 시트 (서울 전체 + 자치구 25곳) */
export function DistrictPicker({ value, onChange }: { value: string | null; onChange: (district: string | null) => void }) {
  const [open, setOpen] = useState(false)
  const { data } = useDistricts()
  // 서버 응답 전에도 25곳을 바로 보여 줌 (가게 수만 나중에 채움)
  const counts = new Map(data?.map((d) => [d.name, d.storeCount]))
  const total = data?.reduce((sum, d) => sum + d.storeCount, 0)

  const pick = (district: string | null) => {
    onChange(district)
    setOpen(false)
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        aria-label={`지역 선택: ${value ?? '서울 전체'}`}
        className={`flex h-9 min-w-0 items-center gap-1.5 rounded-full border pr-2.5 pl-3 text-[13px] font-bold transition-colors ${
          value ? 'border-q-green bg-q-mint text-q-green' : 'border-q-line bg-white text-q-text'
        }`}
      >
        <PinIcon />
        <span className="truncate">{value ?? '서울 전체'}</span>
        <ChevronIcon />
      </button>

      {open && (
        <Sheet title="지역 선택" onClose={() => setOpen(false)}>
          <button
            type="button"
            onClick={() => pick(null)}
            aria-pressed={value === null}
            className={`flex h-12 w-full items-center justify-between rounded-2xl px-4 text-[15px] font-bold transition-colors ${
              value === null ? 'bg-q-green text-white' : 'bg-q-panel text-q-text active:bg-q-mint'
            }`}
          >
            <span className="flex items-center gap-2">
              <PinIcon />
              서울 전체
            </span>
            {total !== undefined && (
              <span className={`text-xs font-medium ${value === null ? 'text-white/85' : 'text-q-muted'}`}>가게 {total}곳</span>
            )}
          </button>

          <p className="mt-5 mb-2 text-xs font-bold text-q-muted">자치구</p>
          <ul className="grid grid-cols-3 gap-2">
            {SEOUL_DISTRICTS.map((name) => {
              const selected = value === name
              const count = counts.get(name)
              return (
                <li key={name}>
                  <button
                    type="button"
                    onClick={() => pick(name)}
                    aria-pressed={selected}
                    className={`flex h-[58px] w-full flex-col items-center justify-center rounded-xl border transition-colors ${
                      selected
                        ? 'border-q-green bg-q-green text-white'
                        : count
                          ? 'border-q-line bg-white text-q-text active:bg-q-mint'
                          : 'border-q-line/70 bg-white text-q-sub active:bg-q-mint'
                    }`}
                  >
                    <span className="text-[14px] font-bold">{name}</span>
                    <span
                      className={`mt-0.5 text-[11px] ${
                        selected ? 'text-white/85' : count ? 'font-medium text-q-green' : 'text-q-muted'
                      }`}
                    >
                      {count === undefined ? ' ' : count > 0 ? `가게 ${count}` : '아직 없음'}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </Sheet>
      )}
    </>
  )
}

function PinIcon() {
  return (
    <svg width="13" height="16" viewBox="0 0 22 26" fill="none" aria-hidden className="shrink-0">
      <path
        d="M11 24.5s8.5-7.6 8.5-14A8.5 8.5 0 0 0 2.5 10.5c0 6.4 8.5 14 8.5 14Z"
        stroke="currentColor"
        strokeWidth={2.6}
        strokeLinejoin="round"
      />
      <circle cx="11" cy="10.5" r="3" fill="currentColor" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg width="10" height="6" viewBox="0 0 10 6" fill="none" aria-hidden className="shrink-0 opacity-70">
      <path d="M1 1l4 4 4-4" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
