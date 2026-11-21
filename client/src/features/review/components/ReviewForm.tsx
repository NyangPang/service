import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '@/shared/lib/error'
import { squareThumbnail } from '@/shared/lib/image'
import { PhotoIcon } from '@/shared/ui/icons'
import { toast } from '@/stores/toastStore'
import { useCreateReview, useUpdateReview } from '../hooks'
import {
  mediaSrc,
  REVIEW_CONTENT_MAX,
  REVIEW_DRAWS,
  REVIEW_MEDIA_MAX,
  REVIEW_VIDEO_MAX_MB,
  REVIEW_VIDEO_MAX_SEC,
  videoDuration,
  type Review,
  type ReviewMedia,
} from '../schema'
import { StarPicker } from './ReviewUi'

type Props =
  | { mode: 'create'; visitId: number; storeName: string }
  | { mode: 'edit'; review: Review }

function initialMedia(review: Review | null): ReviewMedia[] {
  if (!review) return []
  const photos = review.photoUrls.map((url) => ({ kind: 'saved' as const, type: 'photo' as const, url }))
  return review.videoUrl ? [{ kind: 'saved', type: 'video', url: review.videoUrl }, ...photos] : photos
}

/** 리뷰 쓰기·수정: 별점(5점 중 선택) · 사진/영상(합쳐 3개, 영상 1개) · 본문 · 공개/비공개 */
export function ReviewForm(props: Props) {
  const navigate = useNavigate()
  const initial = props.mode === 'edit' ? props.review : null
  const [rating, setRating] = useState(initial?.rating ?? 0)
  const [content, setContent] = useState(initial?.content ?? '')
  const [privateReview, setPrivateReview] = useState(initial?.privateReview ?? false)
  const [media, setMedia] = useState<ReviewMedia[]>(() => initialMedia(initial))
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const create = useCreateReview()
  const update = useUpdateReview()
  const saving = create.isPending || update.isPending

  const storeName = props.mode === 'create' ? props.storeName : props.review.storeName
  const canSave = rating > 0 && content.trim().length > 0 && !saving
  const hasVideo = media.some((m) => m.type === 'video')

  const addFiles = async (files: FileList | null) => {
    if (!files) return
    setError(null)
    const next = [...media]
    for (const file of files) {
      if (next.length >= REVIEW_MEDIA_MAX) {
        toast(`사진·영상은 합쳐서 ${REVIEW_MEDIA_MAX}개까지 올릴 수 있어요`)
        break
      }
      try {
        if (file.type.startsWith('video/')) {
          if (next.some((m) => m.type === 'video')) {
            toast('영상은 1개까지 올릴 수 있어요')
            continue
          }
          if (file.size > REVIEW_VIDEO_MAX_MB * 1024 * 1024) {
            toast(`영상은 ${REVIEW_VIDEO_MAX_MB}MB 이하만 올릴 수 있어요`)
            continue
          }
          if ((await videoDuration(file)) > REVIEW_VIDEO_MAX_SEC + 0.5) {
            toast(`영상은 ${REVIEW_VIDEO_MAX_SEC}초 이하만 올릴 수 있어요`)
            continue
          }
          next.push({ kind: 'new', type: 'video', file, preview: URL.createObjectURL(file) })
        } else if (file.type.startsWith('image/')) {
          next.push({ kind: 'new', type: 'photo', file, preview: await squareThumbnail(file, 480) })
        }
      } catch (e) {
        setError(errorMessage(e))
      }
    }
    setMedia(next)
  }

  const submit = async () => {
    setError(null)
    const input = { rating, content: content.trim(), privateReview }
    try {
      if (props.mode === 'create') {
        const res = await create.mutateAsync({ visitId: props.visitId, input, media })
        // 이 인증으로 처음 쓴 리뷰면 바로 보상 뽑기로
        if (res.drawsGranted > 0) {
          navigate(`/profile/reviews/${res.review.reviewId}/draw`, { replace: true })
          return
        }
        toast('리뷰를 남겼어요')
      } else {
        await update.mutateAsync({ reviewId: props.review.reviewId, input, media })
        toast('리뷰를 수정했어요')
      }
      navigate('/profile/reviews', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="h-full overflow-y-auto bg-surface px-5 pt-6 pb-10">
      <p className="text-center text-13 text-q-muted">방문 인증한 가게</p>
      <h2 className="mt-1 text-center text-21 font-bold text-q-text">{storeName}</h2>
      {props.mode === 'create' && (
        <p className="mx-auto mt-2 w-fit rounded-full bg-q-mint px-3 py-1 text-12 font-bold text-fg-q-green">
          🎁 리뷰를 남기면 보상 뽑기 {REVIEW_DRAWS}회
        </p>
      )}

      <section className="mt-6">
        <p className="mb-2 text-center text-14 font-bold text-q-text">어떠셨나요?</p>
        <StarPicker value={rating} onChange={setRating} />
        <p className={`mt-1.5 text-center text-13 ${rating ? 'font-bold text-fg-q-green' : 'text-q-muted'}`}>{RATING_LABEL[rating]}</p>
      </section>

      <section className="mt-7">
        <div className="flex items-baseline justify-between">
          <h3 className="text-14 font-bold text-q-text">사진 · 영상</h3>
          <span className="text-12 text-q-muted">
            {media.length} / {REVIEW_MEDIA_MAX}
          </span>
        </div>
        <p className="mt-0.5 text-12 text-q-muted">영상은 1개, {REVIEW_VIDEO_MAX_SEC}초 이하</p>
        <ul className="mt-2 flex gap-2">
          {media.map((m, i) => (
            <li key={mediaSrc(m)} className="relative">
              {m.type === 'video' ? (
                <span className="relative block">
                  <video src={mediaSrc(m)} muted playsInline preload="metadata" className="size-[88px] rounded-xl bg-ink object-cover dark:bg-black" />
                  <span className="absolute bottom-1 left-1 rounded bg-black/60 px-1.5 py-px text-10 font-bold text-white">영상</span>
                </span>
              ) : (
                <img src={mediaSrc(m)} alt={`사진 ${i + 1}`} className="size-[88px] rounded-xl object-cover" />
              )}
              <button
                type="button"
                aria-label={`${m.type === 'video' ? '영상' : `사진 ${i + 1}`} 빼기`}
                onClick={() => setMedia((prev) => prev.filter((x) => x !== m))}
                className="absolute -top-1.5 -right-1.5 flex size-6 items-center justify-center rounded-full bg-ink text-13 leading-none text-white dark:bg-q-track dark:text-q-text"
              >
                ×
              </button>
            </li>
          ))}
          {media.length < REVIEW_MEDIA_MAX && (
            <li>
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="flex size-[88px] flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-mint-line bg-q-panel text-12 text-green-4"
              >
                <span className="scale-75">
                  <PhotoIcon />
                </span>
                {hasVideo ? '사진 추가' : '사진·영상'}
              </button>
            </li>
          )}
        </ul>
        <input
          ref={fileRef}
          type="file"
          accept={hasVideo ? 'image/*' : 'image/*,video/*'}
          multiple
          hidden
          onChange={(e) => {
            addFiles(e.target.files)
            e.target.value = ''
          }}
        />
      </section>

      <section className="mt-7">
        <label htmlFor="review-content" className="text-14 font-bold text-q-text">
          리뷰
        </label>
        <textarea
          id="review-content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={REVIEW_CONTENT_MAX}
          rows={6}
          placeholder="맛, 분위기, 친절함 등 방문 후기를 남겨 주세요"
          className="mt-2 w-full resize-none rounded-xl border border-q-line px-4 py-3 text-15 leading-relaxed text-q-text outline-none focus:border-q-green"
        />
        <p className="text-right text-12 text-q-muted">
          {content.length} / {REVIEW_CONTENT_MAX}
        </p>
      </section>

      <section className="mt-4">
        <h3 className="text-14 font-bold text-q-text">공개 여부</h3>
        <div role="radiogroup" aria-label="공개 여부" className="mt-2 grid grid-cols-2 gap-2">
          {[
            { value: false, label: '공개', caption: '모든 손님이 볼 수 있어요' },
            { value: true, label: '비공개', caption: '사장님만 볼 수 있어요' },
          ].map((o) => (
            <button
              key={o.label}
              type="button"
              role="radio"
              aria-checked={privateReview === o.value}
              onClick={() => setPrivateReview(o.value)}
              className={`rounded-xl border px-3 py-2.5 text-left ${privateReview === o.value ? 'border-q-green bg-q-mint' : 'border-q-line bg-surface'}`}
            >
              <span className={`block text-14 font-bold ${privateReview === o.value ? 'text-fg-q-green' : 'text-q-text'}`}>
                {o.value ? '🔒 ' : ''}
                {o.label}
              </span>
              <span className="mt-0.5 block text-12 text-q-muted">{o.caption}</span>
            </button>
          ))}
        </div>
      </section>

      {error && (
        <p role="alert" className="mt-4 text-center text-13 font-medium text-fg-red">
          {error}
        </p>
      )}
      <button
        type="button"
        onClick={submit}
        disabled={!canSave}
        className="mt-5 h-12 w-full rounded-xl bg-green-4 text-16 font-bold text-white disabled:bg-green-1"
      >
        {saving ? '저장 중...' : props.mode === 'create' ? '리뷰 남기기' : '수정 완료'}
      </button>
    </div>
  )
}

const RATING_LABEL = ['별점을 골라 주세요', '별로예요', '그저 그래요', '괜찮아요', '좋아요', '최고예요']

/** 리뷰를 쓸 수 있는 방문 인증이 아닐 때 */
export function ReviewNotAllowed() {
  return (
    <div className="flex h-full flex-col items-center justify-center px-8 text-center">
      <p className="text-16 font-bold text-ink">QR 방문 인증을 하면 리뷰를 쓸 수 있어요</p>
      <p className="mt-1.5 text-13 text-q-muted">방문 인증마다 첫 리뷰를 쓰면 보상 뽑기 {REVIEW_DRAWS}회를 받아요</p>
      <Link to="/quest" className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-green-4 text-15 font-bold text-white">
        퀘스트 하러 가기
      </Link>
    </div>
  )
}
