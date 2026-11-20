import { useEffect, useRef } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { toast } from '@/stores/toastStore'
import { useCanSeeNotifications, useUnreadCount } from '../hooks'

/**
 * 상단 바 종 아이콘 + 안 읽은 수. 앱을 켜 둔 동안 새 알림이 오면 토스트로도 알려 줌
 * (폰 푸시가 아니라 20초마다 안 읽은 수를 다시 물어봄)
 */
export function NotificationBell({ className }: { className: string }) {
  const canSee = useCanSeeNotifications()
  const { data: unread = 0 } = useUnreadCount()
  const { pathname } = useLocation()
  const prev = useRef<number | null>(null)

  useEffect(() => {
    if (prev.current !== null && unread > prev.current && pathname !== '/owner/notifications') toast('🔔 우리 가게에 새 리뷰가 등록됐어요')
    prev.current = unread
  }, [unread, pathname])

  if (!canSee) return null
  return (
    <Link to="/owner/notifications" aria-label={unread ? `알림 ${unread}개 안 읽음` : '알림'} className={`relative ${className}`}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
        <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6.5 2 6.5H4S6 14 6 9Z" />
        <path d="M10 19a2 2 0 0 0 4 0" />
      </svg>
      {unread > 0 && (
        <span className="absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-point-red px-1 text-[11px] font-bold text-white">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  )
}
