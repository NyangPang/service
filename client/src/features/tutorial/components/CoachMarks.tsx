import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { CoachMark } from '../coach'

const PAD = 6
const GAP = 30
const EDGE = 12
const LABEL_W = 200

interface Placed {
  mark: CoachMark
  el: Element
  r: DOMRect
}

/** 화면을 어둡게 덮고 각 요소 자리만 뚫어 그 옆에 설명을 붙임 */
export function CoachMarks({ marks, onClose }: { marks: CoachMark[]; onClose: () => void }) {
  const [placed, setPlaced] = useState<Placed[]>([])
  const [vp, setVp] = useState({ w: window.innerWidth, h: window.innerHeight })
  const closeRef = useRef<HTMLButtonElement>(null)

  useLayoutEffect(() => {
    const measure = () => {
      setVp({ w: window.innerWidth, h: window.innerHeight })
      setPlaced(
        marks
          .map((mark) => ({ mark, el: mark.find() }))
          .filter((p): p is { mark: CoachMark; el: Element } => !!p.el)
          .map((p) => ({ ...p, r: p.el.getBoundingClientRect() }))
          // 화면 밖(스크롤해야 보이는 곳)이면 설명하지 않음
          .filter(({ r }) => r.width > 0 && r.bottom > 0 && r.top < window.innerHeight),
      )
    }
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [marks])

  useEffect(() => {
    closeRef.current?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const hole = (r: DOMRect) => ({ x: r.left - PAD, y: r.top - PAD, w: r.width + PAD * 2, h: r.height + PAD * 2 })

  return createPortal(
    <div role="dialog" aria-modal aria-label="사용법 안내" className="fixed inset-0 z-[60] animate-[fade_.3s] text-white">
      <svg className="absolute inset-0 size-full" aria-hidden>
        <defs>
          <mask id="coach-holes">
            <rect width="100%" height="100%" fill="white" />
            {placed.map(({ r }, i) => {
              const h = hole(r)
              return <rect key={i} x={h.x} y={h.y} width={h.w} height={h.h} rx={14} fill="black" />
            })}
          </mask>
        </defs>
        <rect width="100%" height="100%" fill="rgba(8,16,11,0.82)" mask="url(#coach-holes)" />
        {placed.map(({ r, mark }, i) => {
          const h = hole(r)
          return (
            <rect
              key={i}
              x={h.x}
              y={h.y}
              width={h.w}
              height={h.h}
              rx={14}
              fill="none"
              stroke={mark.primary ? '#5cc98f' : 'rgba(255,255,255,0.85)'}
              strokeWidth={mark.primary ? 3 : 1.5}
              strokeDasharray={mark.primary ? undefined : '5 4'}
              className={mark.primary ? 'animate-pulse' : ''}
            />
          )
        })}
      </svg>

      {placed.map(({ mark, r, el }, i) => {
        const cx = r.left + r.width / 2
        const textAlign = mark.align === 'start' ? 'text-left' : mark.align === 'end' ? 'text-right' : 'text-center'
        const label = (
          <>
            <p className={`leading-snug font-bold whitespace-pre-line break-keep ${mark.primary ? 'text-17 text-[#7ee0a8]' : 'text-15'}`}>{mark.title}</p>
            {mark.desc && <p className="mt-0.5 text-12 leading-snug whitespace-pre-line break-keep text-white/85">{mark.desc}</p>}
          </>
        )
        let callout
        if (mark.side === 'left') {
          // 요소 왼쪽: 가로 점선 + 오른쪽 정렬 설명
          const edgeX = r.left - PAD
          const cy = r.top + r.height / 2
          const w = Math.max(0, edgeX - GAP - EDGE)
          callout = (
            <>
              <span aria-hidden className="absolute border-t-2 border-dashed border-white/80" style={{ left: edgeX - GAP + 6, top: cy, width: GAP - 6 }} />
              <span aria-hidden className="absolute size-2 -translate-y-[3px] rounded-full bg-white" style={{ left: edgeX - GAP + 2, top: cy }} />
              <div className="absolute -translate-y-1/2 text-right" style={{ left: EDGE, width: w, top: cy }}>
                {label}
              </div>
            </>
          )
        } else {
          const w = Math.min(LABEL_W, vp.w - EDGE * 2)
          const left =
            mark.align === 'start'
              ? Math.max(EDGE, r.left - PAD)
              : mark.align === 'end'
                ? Math.min(vp.w - EDGE - w, r.right + PAD - w)
                : Math.min(vp.w - EDGE - w, Math.max(EDGE, cx - w / 2))
          const lineX = Math.min(left + w - 14, Math.max(left + 14, cx))
          const below = mark.side === 'bottom'
          const edgeY = below ? r.bottom + PAD : r.top - PAD
          callout = (
            <>
              {/* 요소 → 설명 점선 */}
              <span
                aria-hidden
                className="absolute border-l-2 border-dashed border-white/80"
                style={{ left: lineX, top: below ? edgeY : edgeY - GAP + 6, height: GAP - 6 }}
              />
              <span aria-hidden className="absolute size-2 -translate-x-[3px] rounded-full bg-white" style={{ left: lineX, top: below ? edgeY + GAP - 8 : edgeY - GAP + 2 }} />
              <div className={`absolute ${textAlign}`} style={{ left, width: w, ...(below ? { top: edgeY + GAP } : { bottom: vp.h - edgeY + GAP }) }}>
                {label}
              </div>
            </>
          )
        }
        return (
          <div key={i}>
            {callout}
            {mark.primary && (
              <button
                type="button"
                aria-label={mark.title.replace('\n', ' ')}
                className="absolute rounded-[14px]"
                style={{ left: r.left - PAD, top: r.top - PAD, width: r.width + PAD * 2, height: r.height + PAD * 2 }}
                onClick={() => {
                  onClose()
                  ;(el as HTMLElement).click()
                }}
              />
            )}
          </div>
        )
      })}

      {/* 닫기: 위쪽 가운데 (양옆 위 구석과 아래 탭은 설명하는 버튼이 있는 자리) */}
      <div className="absolute inset-x-0 top-0 flex justify-center pt-[max(14px,env(safe-area-inset-top))]">
        <button
          ref={closeRef}
          type="button"
          onClick={onClose}
          className="flex items-center gap-2 rounded-full bg-white px-4 py-2 text-13 font-bold text-[#1e6b45] shadow-lg"
        >
          확인했어요 <span aria-hidden>✕</span>
        </button>
      </div>
    </div>,
    document.body,
  )
}
