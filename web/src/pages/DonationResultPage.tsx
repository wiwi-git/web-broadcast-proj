import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { abandonDonation, ApiError, approveDonation, type SentDonation } from '../api.ts'
import { formatWon } from '../format.ts'

/**
 * 같은 결과 화면이 두 번 그려져도(개발 모드, 빠른 새로고침) 승인·취소 요청은 한 번만 보낸다.
 * 서버도 같은 요청을 다시 받으면 이미 정해진 결과를 돌려준다.
 */
const inFlight = new Map<string, Promise<SentDonation>>()

function once(key: string, send: () => Promise<SentDonation>): Promise<SentDonation> {
  let pending = inFlight.get(key)
  if (!pending) {
    pending = send().finally(() => inFlight.delete(key))
    inFlight.set(key, pending)
  }
  return pending
}

type View = { kind: 'loading' } | { kind: 'done'; donation: SentDonation } | { kind: 'error'; message: string; retry: boolean }

/** 결제 서비스가 결제 인증을 마치고 돌려보내는 곳(successUrl). 서버에 승인을 요청한다. */
export function DonationSuccessPage() {
  const [params] = useSearchParams()
  const paymentKey = params.get('paymentKey')
  const orderId = params.get('orderId')
  const amount = Number(params.get('amount'))
  const [view, setView] = useState<View>({ kind: 'loading' })

  const valid = !!paymentKey && !!orderId && Number.isFinite(amount)

  const approve = useCallback(() => {
    if (!paymentKey || !orderId) return
    once(`approve:${orderId}`, () => approveDonation(orderId, paymentKey, amount))
      .then((donation) => setView({ kind: 'done', donation }))
      .catch((e: Error) =>
        // 503이면 결과를 아직 모르는 것이다. 다시 확인하면 같은 승인을 다시 요청한다
        setView({ kind: 'error', message: e.message, retry: e instanceof ApiError && e.status === 503 }),
      )
  }, [paymentKey, orderId, amount])

  useEffect(() => {
    if (valid) approve()
  }, [valid, approve])

  function retry() {
    setView({ kind: 'loading' })
    approve()
  }

  if (!valid) return <p className="notice error">결제 정보가 올바르지 않습니다.</p>
  if (view.kind === 'loading') return <p className="notice">결제를 확인하는 중…</p>
  if (view.kind === 'error') {
    return (
      <div className="notice">
        <p className="error">{view.message}</p>
        <div className="actions">
          {view.retry && (
            <button type="button" className="button primary" onClick={retry}>
              다시 확인
            </button>
          )}
          <Link to="/donations" className="button">
            후원 내역
          </Link>
        </div>
      </div>
    )
  }
  return <DonationOutcome donation={view.donation} />
}

/** 결제창을 닫았거나 결제 인증에 실패했을 때 결제 서비스가 돌려보내는 곳(failUrl) */
export function DonationFailPage() {
  const [params] = useSearchParams()
  const donationId = params.get('donationId')
  const code = params.get('code')
  const message = params.get('message')
  const [view, setView] = useState<View>({ kind: 'loading' })

  useEffect(() => {
    if (!donationId) return
    once(`abandon:${donationId}`, () => abandonDonation(donationId, code, message))
      .then((donation) => setView({ kind: 'done', donation }))
      .catch((e: Error) => setView({ kind: 'error', message: e.message, retry: false }))
  }, [donationId, code, message])

  if (!donationId) return <p className="notice error">{message ?? '결제에 실패했습니다.'}</p>
  if (view.kind === 'loading') return <p className="notice">결과를 정리하는 중…</p>
  if (view.kind === 'error') return <p className="notice error">{view.message}</p>
  return <DonationOutcome donation={view.donation} />
}

function DonationOutcome({ donation }: { donation: SentDonation }) {
  const who = `${donation.recipient.nickname}님에게 ${formatWon(donation.amount)}`
  const text = {
    DONE: `${who}을 후원했습니다. 방송 화면에 후원이 표시됩니다.`,
    FAILED: `${who} 후원에 실패했습니다. ${donation.failureMessage ?? ''}`,
    CANCELED: '후원을 취소했습니다. 결제되지 않았습니다.',
    EXPIRED: '결제 시간이 지나 후원이 만료되었습니다. 결제되지 않았습니다.',
    PENDING: '결제 결과를 아직 확인하지 못했습니다. 잠시 후 후원 내역에서 확인해 주세요.',
  }[donation.status]

  return (
    <div className="notice">
      <p className={donation.status === 'FAILED' ? 'error' : undefined}>{text}</p>
      <div className="actions">
        {donation.broadcast.id && (
          <Link to={`/live/${donation.broadcast.id}`} className="button primary">
            방송으로 돌아가기
          </Link>
        )}
        <Link to="/donations" className="button">
          후원 내역
        </Link>
      </div>
    </div>
  )
}
