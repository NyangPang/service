import pigeonCouponBox from '@/assets/user/pigeon-coupon-box.png'
import pigeonGrowth from '@/assets/user/pigeon-growth.png'
import pigeonQuest from '@/assets/user/pigeon-quest.png'
import { OWNER_ILLUST } from '@/features/owner/illustrations'
import { FlagNavIcon, MapNavIcon, PhotoNavIcon, SearchNavIcon } from '@/shared/ui/icons'
import { AppShell, type TabItem } from './AppShell'
import type { MenuItem } from './SettingsDrawer'

export type { RouteHandle } from './AppShell'

const TABS: TabItem[] = [
  { to: '/', label: '피드', Icon: PhotoNavIcon, end: true },
  { to: '/map', label: '지도', Icon: MapNavIcon },
  { to: '/search', label: '검색', Icon: SearchNavIcon },
  { to: '/quest', label: '퀘스트', Icon: FlagNavIcon },
]

/** 바로가기 (계정 영역에는 프로필·로그아웃만) */
const MENU: MenuItem[] = [
  { to: '/coupons', label: '내 쿠폰함', icon: '🎟', image: pigeonCouponBox },
  { to: '/quest', label: '내 비둘기·퀘스트', icon: '🕊', image: pigeonQuest },
  { to: '/quest/qr', label: 'QR 인식하기', icon: '📷', image: OWNER_ILLUST.qr },
  { to: '/profile/reviews', label: '내 리뷰', icon: '✍️', image: pigeonGrowth },
  { to: '/scraps', label: '스크랩한 게시물', icon: '🔖', image: OWNER_ILLUST.videoDownload },
]

/** 손님 모드: 숏폼 / 지도 / 퀘스트 */
export function AppLayout() {
  return <AppShell tabs={TABS} menuItems={MENU} />
}
