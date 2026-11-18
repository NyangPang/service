import { mockFor, mockNotificationApi } from '@/mocks/db'
import { api, request } from '@/shared/api/client'
import type { NotificationList } from './schema'

/** GET /api/notifications — 최신순 최대 50개 + 안 읽은 수 */
export function fetchNotifications(): Promise<NotificationList> {
  if (mockFor('notification')) return mockNotificationApi.list()
  return request(api.get('/api/notifications'))
}

/** GET /api/notifications/unread-count — 상단 종 배지 */
export function fetchUnreadCount(): Promise<{ unreadCount: number }> {
  if (mockFor('notification')) return mockNotificationApi.unreadCount()
  return request(api.get('/api/notifications/unread-count'))
}

/** PATCH /api/notifications/{id}/read */
export async function markNotificationRead(notificationId: number): Promise<void> {
  if (mockFor('notification')) {
    await mockNotificationApi.read(notificationId)
    return
  }
  await request(api.patch(`/api/notifications/${notificationId}/read`))
}

/** PATCH /api/notifications/read-all */
export async function markAllNotificationsRead(): Promise<void> {
  if (mockFor('notification')) {
    await mockNotificationApi.readAll()
    return
  }
  await request(api.patch('/api/notifications/read-all'))
}
