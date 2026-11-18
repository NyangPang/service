import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { mockFor } from '@/mocks/db'
import { useMe } from '@/stores/authStore'
import { fetchNotifications, fetchUnreadCount, markAllNotificationsRead, markNotificationRead } from './api'

/** 폰 푸시가 없어서 안 읽은 수를 이 간격으로 다시 물어봄 */
const POLL_MS = 20_000

/** 서버는 로그인해야 알림이 있음. 목업은 로그인 전 사장님 시연 화면에서도 보여 줌 */
export function useCanSeeNotifications() {
  const me = useMe()
  return mockFor('notification') || !!me
}

export function useUnreadCount() {
  const enabled = useCanSeeNotifications()
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: fetchUnreadCount,
    select: (d) => d.unreadCount,
    enabled,
    refetchInterval: POLL_MS,
  })
}

export function useNotifications() {
  const enabled = useCanSeeNotifications()
  return useQuery({ queryKey: ['notifications', 'list'], queryFn: fetchNotifications, enabled, refetchInterval: POLL_MS })
}

function useInvalidateNotifications() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: ['notifications'] })
}

export function useMarkRead() {
  return useMutation({ mutationFn: markNotificationRead, onSuccess: useInvalidateNotifications() })
}

export function useMarkAllRead() {
  return useMutation({ mutationFn: markAllNotificationsRead, onSuccess: useInvalidateNotifications() })
}
