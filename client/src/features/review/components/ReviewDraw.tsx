import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { RewardResult } from '@/features/quest/components/LevelUpSequence'
import { OutlineButton, PrimaryButton } from '@/features/quest/components/QuestUi'
import { RewardCardDraw } from '@/features/quest/components/RewardCardDraw'
import { errorMessage } from '@/shared/lib/error'
import { ConfettiBurst } from '@/shared/ui/Confetti'
import { useDrawReviewReward } from '../hooks'
import { REVIEW_DRAW_ODDS, REVIEW_DRAWS, type Review, type ReviewDrawResult } from '../schema'

/**
 * 리뷰 보상 뽑기: 남은 횟수만큼 카드 3장(먹이 1 / 먹이 2 / 쿠폰) 뽑기를 연달아.
 * 결과는 서버가 먼저 정하고(POST /draws), 카드 연출은 그 결과를 보여 줌. 중간에 나가도 남은 횟수는 내 리뷰에서 이어서
 */
export function ReviewDraw({ review }: { review: Review }) {
  const navigate = useNavigate()
  const draw = useDrawReviewReward()
  const [left, setLeft] = useState(review.drawsLeft ?? 0)
  const [round, setRound] = useState(0)
  const [result, setResult] = useState<ReviewDrawResult | null>(null)
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const start = () => {
    setError(null)
    draw.mutate(review.reviewId, {
      onSuccess: (res) => {
        setResult(res)
        setRevealed(false)
        setRound((r) => r + 1)
      },
      onError: (e) => setError(errorMessage(e)),
    })
  }

  const afterReveal = () => {
    setRevealed(true)
    if (result) setLeft(result.drawsLeft)
  }

  const done = () => navigate('/profile/reviews', { replace: true })
  const wonCoupon = revealed && result?.reward.type === 'COUPON' && !!result.reward.userCoupon

  if (left === 0 && !result) {
    return (
      <div className="flex h-full flex-col items-center justify-center px-8 text-center">
        <p className="text-[16px] font-bold text-ink">남은 보상 뽑기가 없어요</p>
        <Link to="/profile/reviews" className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-green-4 text-[15px] font-bold text-white">
          내 리뷰로
        </Link>
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto bg-white px-5 pt-6 pb-10 text-center">
      <p className="text-[13px] text-q-muted">{review.storeName} 리뷰 보상</p>
      <h2 className="mt-1 text-[21px] font-bold text-q-text">보상 뽑기</h2>
      <DrawDots total={REVIEW_DRAWS} used={REVIEW_DRAWS - left} />

      <div className="mt-5 rounded-3xl bg-q-panel px-3 pt-4 pb-3">
        {result ? (
          <>
            <p className="mb-2 text-xs font-medium text-q-muted">
              {REVIEW_DRAWS - (revealed ? left : left - 1)}번째 뽑기
            </p>
            <RewardCardDraw key={round} reward={result.reward} odds={REVIEW_DRAW_ODDS} onRevealed={afterReveal} />
            {revealed && <RewardResult reward={result.reward} />}
          </>
        ) : (
          <div className="py-8">
            <p className="text-[40px]">🎁</p>
            <p className="mt-2 text-[15px] font-bold text-q-text">리뷰를 남겨 줘서 고마워요!</p>
            <p className="mt-1 text-[13px] text-q-muted">
              카드 3장(먹이 1개 · 먹이 2개 · 쿠폰) 중 하나를 골라요
              <br />
              뽑기 {left}회가 남았어요
            </p>
          </div>
        )}
      </div>

      {error && (
        <p role="alert" className="mt-3 text-[13px] font-medium text-point-red-dark">
          {error}
        </p>
      )}

      <div className="mt-5 flex flex-col gap-2">
        {!result && (
          <PrimaryButton disabled={draw.isPending} onClick={start}>
            {draw.isPending ? '준비 중...' : '뽑기 시작'}
          </PrimaryButton>
        )}
        {result && revealed && left > 0 && (
          <PrimaryButton disabled={draw.isPending} onClick={start}>
            {draw.isPending ? '준비 중...' : `다음 뽑기 (${left}회 남음)`}
          </PrimaryButton>
        )}
        {result && revealed && left === 0 && <PrimaryButton onClick={done}>완료</PrimaryButton>}
        {wonCoupon && <OutlineButton onClick={() => navigate('/coupons')}>쿠폰함에서 보기</OutlineButton>}
        {(!result || revealed) && left > 0 && (
          <button type="button" onClick={done} className="mt-1 text-[13px] text-q-muted underline">
            나중에 뽑을래요 (내 리뷰에서 이어서 뽑을 수 있어요)
          </button>
        )}
      </div>
      {wonCoupon && <ConfettiBurst />}
    </div>
  )
}

/** ●●○ : 쓴 뽑기 / 전체 */
function DrawDots({ total, used }: { total: number; used: number }) {
  return (
    <div className="mt-2 flex justify-center gap-1.5" aria-label={`뽑기 ${total}회 중 ${used}회 사용`}>
      {Array.from({ length: total }, (_, i) => (
        <span key={i} className={`size-2.5 rounded-full ${i < used ? 'bg-q-green' : 'bg-q-track'}`} />
      ))}
    </div>
  )
}
