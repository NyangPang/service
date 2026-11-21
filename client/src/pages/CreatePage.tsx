import { Navigate, useLocation, useParams } from 'react-router-dom'
import { CreateShortformFlow } from '@/features/generation/components/CreateShortformFlow'
import { OwnerOnlyNotice } from '@/features/generation/components/OwnerOnlyNotice'
import { ReviseShortformFlow } from '@/features/generation/components/ReviseShortformFlow'
import { useModeStore } from '@/stores/modeStore'

/** + 버튼 → 숏폼 만들기 (사장님만. 손님이면 안내 화면) */
export function CreatePage() {
  const mode = useModeStore((s) => s.mode)
  const { pathname } = useLocation()
  if (!mode) return <Navigate to={`/welcome?next=${encodeURIComponent(pathname)}`} replace />
  return mode === 'OWNER' ? <CreateShortformFlow /> : <OwnerOnlyNotice />
}

/** 내 영상 → 올린 영상 수정 (사장님만. 무료는 게시물마다 1번, PRO는 무제한 — 횟수는 수정 화면에서 확인) */
export function RevisePage() {
  const mode = useModeStore((s) => s.mode)
  const { pathname } = useLocation()
  const { shortformId } = useParams()
  if (!mode) return <Navigate to={`/welcome?next=${encodeURIComponent(pathname)}`} replace />
  if (mode !== 'OWNER') return <OwnerOnlyNotice />
  return <ReviseShortformFlow key={shortformId} shortformId={Number(shortformId)} />
}
