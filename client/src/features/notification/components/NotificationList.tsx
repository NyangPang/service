import { Link, useNavigate } from 'react-router-dom'
import pigeonWalk from '@/assets/map/pigeon-walk.png'
import { useCanSeeNotifications, useMarkAllRead, useMarkRead, useNotifications } from '../hooks'
import { notificationLink, timeAgo, type AppNotification } from '../schema'

/** 사장님 알림 목록: 누르면 읽음 처리 + 해당 화면으로 */
export function NotificationList() {
  const canSee = useCanSeeNotifications()
  const navigate = useNavigate()
  const { data, isLoading } = useNotifications()
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()

  if (!canSee) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <img src={pigeonWalk} alt="" className="h-24 w-auto object-contain" />
        <p className="mt-4 text-[16px] font-bold text-ink">로그인하면 알림을 받을 수 있어요</p>
        <Link to="/login?next=/owner/notifications" className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-green-4 text-[15px] font-bold text-white">
          로그인
        </Link>
      </div>
    )
  }

  const open = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.notificationId)
    navigate(notificationLink(n))
  }

  return (
    <div className="h-full overflow-y-auto bg-white pb-10">
      <div className="flex items-center justify-between px-5 pt-4 pb-2">
        <p className="text-[13px] text-q-muted">{data?.unreadCount ? `안 읽은 알림 ${data.unreadCount}개` : '새 알림이 없어요'}</p>
        {!!data?.unreadCount && (
          <button type="button" onClick={() => markAll.mutate()} disabled={markAll.isPending} className="text-[13px] font-bold text-green-4">
            모두 읽음
          </button>
        )}
      </div>

      {isLoading && <div className="mx-5 h-20 animate-pulse rounded-2xl bg-q-panel" />}
      {data && data.notifications.length === 0 && (
        <div className="px-8 py-16 text-center">
          <p className="text-[14px] text-green-4">아직 받은 알림이 없어요</p>
          <p className="mt-1 text-[12px] text-q-muted">손님이 우리 가게에 리뷰를 남기면 여기로 알려 드려요</p>
        </div>
      )}
      <ul>
        {data?.notifications.map((n) => (
          <li key={n.notificationId}>
            <button
              type="button"
              onClick={() => open(n)}
              className={`flex w-full gap-3 border-b border-q-line px-5 py-4 text-left ${n.read ? 'bg-white' : 'bg-q-mint'}`}
            >
              <span aria-hidden className="flex size-10 shrink-0 items-center justify-center rounded-full bg-white text-lg">
                ⭐
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1.5">
                  {!n.read && <span className="size-2 shrink-0 rounded-full bg-point-red" aria-label="안 읽음" />}
                  <span className="truncate text-[15px] font-bold text-q-text">{n.title}</span>
                </span>
                <span className="mt-0.5 line-clamp-2 block text-[13px] text-q-sub">{n.message}</span>
                <span className="mt-1 block text-[12px] text-q-muted">{timeAgo(n.createdAt)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
