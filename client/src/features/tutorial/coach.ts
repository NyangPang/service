/**
 * 코치마크 튜토리얼: 화면을 어둡게 덮고 실제 버튼마다 설명을 붙임 (네이버·피자헛 앱 첫 실행 안내 방식).
 * 화면마다 처음 한 번 자동으로 뜨고, 사장님 홈의 [사용법]으로 다시 볼 수 있음.
 *
 * 버튼은 data 속성 대신 화면의 글자·링크 주소로 찾는다 — 영상 만들기 화면 코드를 고치지 않아도 되게.
 */

/** left: 요소 왼쪽에 (위아래로 붙은 입력칸처럼 위·아래에 자리가 없을 때) */
export type CoachSide = 'top' | 'bottom' | 'left'
export type CoachAlign = 'start' | 'center' | 'end'

export interface CoachMark {
  /** 설명할 화면 요소 (없으면 그 설명만 건너뜀) */
  find: () => Element | null
  title: string
  desc?: string
  /** 설명을 요소 위/아래 어디에 둘지 */
  side: CoachSide
  /** 설명 상자를 요소의 왼쪽 끝/가운데/오른쪽 끝에 맞춤 */
  align?: CoachAlign
  /** 주 동작 (강조 테두리, 누르면 안내를 닫고 그 버튼을 누름) */
  primary?: boolean
}

export interface CoachScreen {
  id: 'owner-home' | 'create'
  /** 이 주소에서 자동으로 띄움 */
  path: string
  marks: CoachMark[]
  /** 이 요소가 보여야 화면이 다 그려진 것으로 봄 */
  ready: () => boolean
}

const q = (selector: string) => document.querySelector(selector)
const byText = (selector: string, text: string) =>
  [...document.querySelectorAll(selector)].find((el) => el.textContent?.trim() === text) ?? null
/** 사진 고르기 칸(제목 글자)의 [추가] 버튼 */
const addButton = (label: string) => byText('span', label)?.parentElement?.querySelector('button') ?? null

export const COACH_SCREENS: CoachScreen[] = [
  {
    id: 'owner-home',
    path: '/owner',
    ready: () => !!q('nav a[aria-label="제작"]') && !!byText('main h3', '이번 달 쿠폰'),
    marks: [
      { find: () => q('header button[aria-label="설정 열기"]'), title: '전체 메뉴', desc: '가게 프로필·리뷰,\n설정을 볼 수 있어요', side: 'bottom', align: 'start' },
      { find: () => q('header a[href="/owner/notifications"]'), title: '알림', desc: '새 리뷰가 오면\n알려 드려요', side: 'bottom', align: 'end' },
      { find: () => q('main a[href="/owner/quest-store"]'), title: '퀘스트 가게', desc: '등록하면 손님 방문 인증\nQR이 생겨요', side: 'bottom', align: 'start' },
      { find: () => q('main a[href="/owner/pro"]'), title: '잇츠 PRO', desc: '수정 무제한·\n피드 우선 노출', side: 'bottom', align: 'end' },
      { find: () => byText('main h3', '이번 달 쿠폰'), title: '쿠폰', desc: '발행한 쿠폰과 사용·정산을 한눈에', side: 'bottom', align: 'start' },
      { find: () => byText('main h3', '성과지표 조회'), title: '성과지표', desc: '영상 조회수·방문 인증을 확인해요', side: 'bottom', align: 'start' },
      { find: () => q('nav a[aria-label="제작"]'), title: '여기를 눌러\n홍보 영상 만들기', desc: '사진만 올리면 AI가 15초 숏폼을 만들어요', side: 'top', primary: true },
    ],
  },
  {
    id: 'create',
    path: '/create',
    ready: () => !!q('input[placeholder*="지도"]'),
    marks: [
      { find: () => q('input[placeholder*="지도"]'), title: '지도 링크', desc: '네이버지도·카카오맵에서 공유한\n가게 링크를 붙여 넣어요', side: 'top' },
      { find: () => addButton('메뉴판 사진'), title: '메뉴판 사진', desc: 'AI가 메뉴와\n가격을 읽어요', side: 'left' },
      { find: () => addButton('음식·가게 사진 (선택)'), title: '음식·가게 사진', desc: '영상에 나와요. 없으면\nAI가 메뉴 사진을 그려요', side: 'left' },
      { find: () => [...document.querySelectorAll('button')].filter((b) => b.textContent?.trim() === '다음').pop() ?? null, title: '다 넣었으면 다음', desc: '메뉴를 확인하고 영상을 만들어요', side: 'top', align: 'end', primary: true },
    ],
  },
]

const seenKey = (id: CoachScreen['id']) => `itda-coach-${id}`

export function coachSeen(id: CoachScreen['id']) {
  try {
    return !!localStorage.getItem(seenKey(id))
  } catch {
    return true
  }
}

export function markCoachSeen(id: CoachScreen['id']) {
  try {
    localStorage.setItem(seenKey(id), '1')
  } catch {
    // 저장이 안 되면 다음에 한 번 더 보여도 괜찮음
  }
}

/** 사장님 홈 [사용법] → 지금 화면의 안내 다시 열기 */
export const COACH_OPEN_EVENT = 'itda:coach-open'
export const openCoach = (id: CoachScreen['id']) => window.dispatchEvent(new CustomEvent(COACH_OPEN_EVENT, { detail: id }))
