/* ── 명세: server/docs/api-spec.md > Notification ── */

export type NotificationType = 'REVIEW_CREATED'

export interface AppNotification {
  notificationId: number
  type: NotificationType
  title: string
  message: string
  storeId: number | null
  reviewId: number | null
  read: boolean
  createdAt: string
}

/** GET /api/notifications */
export interface NotificationList {
  unreadCount: number
  notifications: AppNotification[]
}

/** 알림을 누르면 갈 곳 */
export function notificationLink(n: AppNotification) {
  switch (n.type) {
    case 'REVIEW_CREATED':
      return '/owner/reviews'
  }
}

/** "방금 전" · "5분 전" · "3시간 전" · "2026.11.15" */
export function timeAgo(iso: string, now = Date.now()) {
  const sec = Math.max(0, (now - Date.parse(iso)) / 1000)
  if (sec < 60) return '방금 전'
  if (sec < 3600) return `${Math.floor(sec / 60)}분 전`
  if (sec < 86_400) return `${Math.floor(sec / 3600)}시간 전`
  if (sec < 7 * 86_400) return `${Math.floor(sec / 86_400)}일 전`
  return iso.slice(0, 10).replaceAll('-', '.')
}
