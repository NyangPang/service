import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import pigeonCrying from '@/assets/generation/pigeon-crying.jpg'
import pigeonUpload from '@/assets/generation/pigeon-upload.jpg'
import { PostCarousel } from '@/features/feed/components/PostCarousel'
import { ShortformThumb } from '@/features/feed/components/ShortformThumb'
import { useDeleteShortform, useStoreShortforms } from '@/features/feed/hooks'
import type { Shortform } from '@/features/feed/schema'
import { Dialog } from '@/features/generation/components/Dialog'
import { REVISION_LIMIT_DIALOG, revisionsLeft, videoFileName } from '@/features/generation/schema'
import { useIsPro } from '@/features/pro/hooks'
import { errorMessage } from '@/shared/lib/error'
import { toast } from '@/stores/toastStore'
import { CloseIcon, PhotoIcon, PlusIcon } from '@/shared/ui/icons'
import { useMyStoreId } from '../hooks'
import { OWNER_ILLUST } from '../illustrations'
import { OwnerScreen } from './OwnerUi'

/** 사장님 내 영상 — 우리 가게가 올린 숏폼 목록, 눌러서 보기·수정(무료 1번)·다운로드·삭제 */
export function OwnerVideos() {
  const navigate = useNavigate()
  const storeId = useMyStoreId()
  const { data: videos, isLoading, isError } = useStoreShortforms(storeId)
  const [openId, setOpenId] = useState<number | null>(null)
  const open = videos?.find((v) => v.shortformId === openId) ?? null

  return (
    <OwnerScreen>
      {/* 제작 탭의 주 동작 (예전엔 우상단 + 로만 만들 수 있었음) */}
      <button
        type="button"
        onClick={() => navigate('/create')}
        className="flex w-full items-center gap-3 overflow-hidden rounded-2xl bg-q-green py-2 pr-4 pl-3 text-left text-white active:scale-[0.99]"
      >
        <img src={OWNER_ILLUST.videoEdit} alt="" className="-my-1 h-[84px] w-auto object-contain drop-shadow" />
        <span className="flex-1">
          <span className="block text-17 font-bold">새 게시물 만들기</span>
          <span className="block text-xs leading-relaxed break-keep opacity-85">
            사진·메뉴판만 있으면
            <br />
            AI가 홍보 게시물을 만들어요
          </span>
        </span>
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white/20">
          <span className="scale-[0.7]">
            <PlusIcon />
          </span>
        </span>
      </button>

      <div className="mt-5">
        <p className="text-13 text-q-muted">손님 피드에 공개 중</p>
        <h2 className="text-20 font-bold text-q-text">
          우리 가게 게시물 <span className="text-fg-q-green">{videos?.length ?? 0}</span>개
        </h2>
      </div>

      {isLoading && (
        <div className="mt-4 grid grid-cols-2 gap-3">
          {[0, 1].map((i) => (
            <div key={i} className="aspect-[9/14] animate-pulse rounded-2xl bg-surface" />
          ))}
        </div>
      )}
      {isError && <p className="py-10 text-center text-sm text-q-muted">게시물을 불러오지 못했어요</p>}

      {videos && videos.length === 0 && (
        <div className="mt-6 flex flex-col items-center rounded-2xl bg-surface px-6 py-8 text-center">
          <img src={OWNER_ILLUST.videoEdit} alt="" className="h-[120px] w-auto object-contain" />
          <p className="mt-3 text-16 font-bold text-q-text">아직 올린 게시물이 없어요</p>
          <p className="mt-1 text-13 leading-relaxed text-q-muted">지도 링크나 메뉴판만 있으면 홍보 게시물을 만들어 드려요</p>
          <button
            type="button"
            onClick={() => navigate('/create')}
            className="mt-5 h-11 w-full rounded-xl bg-green-4 text-15 font-bold text-white"
          >
            첫 게시물 만들기
          </button>
        </div>
      )}

      {videos && videos.length > 0 && (
        <ul className="mt-4 grid grid-cols-2 gap-x-3 gap-y-4">
          {videos.map((v, i) => (
            <li key={v.shortformId}>
              <button type="button" onClick={() => setOpenId(v.shortformId)} className="block w-full text-left active:scale-[0.98]">
                <span className="relative block aspect-[9/14] overflow-hidden rounded-2xl bg-q-mint">
                  <ShortformThumb video={v} />
                  {i === 0 && (
                    <span className="absolute top-2 left-2 rounded-full bg-point-orange px-2 py-0.5 text-10 font-bold text-white">NEW</span>
                  )}
                  <span className="absolute top-2 right-2 flex size-7 items-center justify-center rounded-full bg-black/35 text-white backdrop-blur-sm">
                    <span className="scale-[0.4]">
                      <PhotoIcon />
                    </span>
                  </span>
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-2.5 pt-6 pb-2 text-11 font-medium text-white/90">
                    {formatDate(v.createdAt)}
                    {v.updatedAt && <span className="ml-1 rounded bg-white/25 px-1 text-10">수정됨</span>}
                  </span>
                </span>
                <span className="mt-1.5 line-clamp-2 block text-13 leading-snug font-bold text-q-text">{v.description || v.storeName}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {open && <VideoDetail video={open} onClose={() => setOpenId(null)} />}
    </OwnerScreen>
  )
}

/** 영상 크게 보기 + 손님 화면에서 보기 / 수정(무료 1번, PRO 무제한) / 다운로드 / 삭제 */
function VideoDetail({ video, onClose }: { video: Shortform; onClose: () => void }) {
  const navigate = useNavigate()
  const isPro = useIsPro()
  const remove = useDeleteShortform()
  const [modal, setModal] = useState<'delete' | 'pro-edit' | null>(null)
  const left = revisionsLeft(video.revisionCount, isPro)
  // 영상 게시물이면 영상, 아니면 게시물 사진을 내려받음 (무료)
  const downloadUrl = video.videoUrl ?? video.posterUrl ?? undefined
  const downloadName = video.videoUrl
    ? videoFileName(video.storeName)
    : `${video.storeName}-게시물${video.posterUrl?.match(/\.\w+(?=$|\?)/)?.[0] ?? ''}`

  return (
    <div role="dialog" aria-modal aria-label="게시물 보기" className="fixed inset-0 z-40 flex items-end justify-center bg-black/50">
      <div className="max-h-full w-full max-w-[430px] animate-[rise_.3s_ease-out] overflow-y-auto rounded-t-3xl bg-surface px-5 pt-4 pb-[max(20px,env(safe-area-inset-bottom))]">
        <div className="flex items-center justify-between">
          <p className="text-13 text-q-muted">
            {formatDate(video.createdAt)} 업로드
            {video.updatedAt && <span className="text-fg-q-green"> · {formatDate(video.updatedAt)} 수정</span>}
          </p>
          <button type="button" aria-label="닫기" onClick={onClose} className="flex size-9 items-center justify-center rounded-full bg-q-panel text-q-text">
            <span className="scale-[0.8]">
              <CloseIcon />
            </span>
          </button>
        </div>

        <div className="relative mx-auto mt-2 aspect-[4/5] w-full max-w-[280px] overflow-hidden rounded-3xl shadow-[0_10px_30px_rgba(8,104,22,0.18)]">
          <PostCarousel
            images={video.videoUrl ? [video.posterUrl ?? ''] : (video.images ?? (video.posterUrl ? [video.posterUrl] : []))}
            video={video.videoUrl ? { url: video.videoUrl, onError: () => undefined, autoPlay: false } : undefined}
            alt={video.description || video.storeName}
            frame={video.frame}
          />
        </div>

        <p className="mt-4 text-16 font-bold text-q-text">{video.description || video.storeName}</p>
        {video.menus.length > 0 && <p className="mt-1 line-clamp-2 text-13 text-q-muted">{video.menus.join(' · ')}</p>}

        <div className="mt-5 grid grid-cols-4 gap-2">
          <Action label="손님 화면" onClick={() => navigate(`/map/stores/${video.storeId}/shortform?start=${video.shortformId}`)}>
            <span className="scale-[0.55] text-fg-q-green">
              <PhotoIcon />
            </span>
          </Action>
          {/* 수정: 무료는 게시물마다 1번 (다 쓰면 PRO 안내), PRO는 무제한. 다운로드는 무료 */}
          <Action label="수정" pro={left <= 0} onClick={() => (left > 0 ? navigate(`/owner/videos/${video.shortformId}/edit`) : setModal('pro-edit'))}>
            <img src={OWNER_ILLUST.videoEdit} alt="" className="size-9 object-contain" />
          </Action>
          <a
            href={downloadUrl}
            download={downloadName}
            className="flex flex-col items-center gap-1 rounded-xl bg-q-panel py-3 text-12 font-bold text-q-text active:bg-q-mint"
          >
            <img src={OWNER_ILLUST.videoDownload} alt="" className="size-9 object-contain" />
            다운로드
          </a>
          <Action label="삭제" danger onClick={() => setModal('delete')}>
            <TrashIcon />
          </Action>
        </div>
      </div>

      {modal === 'delete' && (
        <Dialog
          title="이 게시물을 삭제할까요?"
          text="손님 피드에서도 사라져요"
          image={pigeonCrying}
          primary={{ label: '아니요', onClick: () => setModal(null) }}
          secondary={{
            label: remove.isPending ? '삭제 중...' : '네 삭제할래요',
            onClick: () =>
              remove.mutate(video.shortformId, {
                onSuccess: () => {
                  onClose()
                  toast('게시물을 삭제했어요')
                },
                onError: (e) => toast(errorMessage(e), 'error'),
              }),
          }}
        />
      )}
      {modal === 'pro-edit' && (
        <Dialog
          title={REVISION_LIMIT_DIALOG.title}
          text={REVISION_LIMIT_DIALOG.text}
          image={pigeonUpload}
          primary={{ label: 'PRO 알아보기', onClick: () => navigate('/owner/pro') }}
          secondary={{ label: '닫기', onClick: () => setModal(null) }}
        />
      )}
    </div>
  )
}

function Action({
  label,
  onClick,
  danger = false,
  pro = false,
  children,
}: {
  label: string
  onClick: () => void
  danger?: boolean
  /** PRO 전용인데 아직 구독 안 함 → 오른쪽 위 PRO 표시 */
  pro?: boolean
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`relative flex flex-col items-center gap-1 rounded-xl bg-q-panel py-3 text-12 font-bold active:bg-q-mint ${danger ? 'text-fg-red' : 'text-q-text'}`}
    >
      {pro && <span className="absolute top-1 right-1 rounded-full bg-green-6 px-1.5 text-9 leading-4 font-bold text-white">PRO</span>}
      <span className="flex size-9 items-center justify-center">{children}</span>
      {label}
    </button>
  )
}


function TrashIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12M9 7V4h6v3" />
    </svg>
  )
}

function formatDate(iso?: string) {
  if (!iso) return ''
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${y}.${m}.${d}`
}
