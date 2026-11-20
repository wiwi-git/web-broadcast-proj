import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  abandonCheckout,
  ApiError,
  approveCheckout,
  fetchCheckout,
  type CheckoutView,
  type DonationStatus,
  type SentDonation,
} from '../api.ts'
import { APP_DONATIONS_LINK, appLink } from '../appLink.ts'
import { formatWon } from '../format.ts'

/**
 * 앱(데스크톱·모바일)의 후원 결제 화면 (ADR-0005).
 * 앱은 결제창을 앱 안에 띄울 수 없어서, 결제 토큰을 받아 기본 브라우저로 이 화면을 연다.
 * 로그인하지 않아도 토큰으로 그 후원 하나만 결제한다. 후원자는 토큰을 받은 앱의 회원이다.
 * 결제가 끝나면 후원한 방송 화면(webbroadcast://live/{id})으로 앱을 다시 연다.
 */

const STATUS_TEXT: Record<Exclude<DonationStatus, 'PENDING'>, string> = {
  DONE: '이미 결제를 마친 후원입니다.',
  FAILED: '결제에 실패한 후원입니다. 앱에서 다시 후원해 주세요.',
  CANCELED: '취소한 후원입니다. 앱에서 다시 후원해 주세요.',
  EXPIRED: '결제 시간이 지난 후원입니다. 앱에서 다시 후원해 주세요.',
}

/** 결제 결과 화면이 두 번 그려져도 승인·취소 요청은 한 번만 보낸다. 서버도 같은 요청에는 같은 결과를 준다 */
const inFlight = new Map<string, Promise<SentDonation>>()

function once(key: string, send: () => Promise<SentDonation>): Promise<SentDonation> {
  let pending = inFlight.get(key)
  if (!pending) {
    pending = send().finally(() => inFlight.delete(key))
    inFlight.set(key, pending)
  }
  return pending
}

function BackToApp({ href = APP_DONATIONS_LINK, primary = false }: { href?: string; primary?: boolean }) {
  return (
    <a href={href} className={primary ? 'button primary' : 'button'}>
      앱으로 돌아가기
    </a>
  )
}

/** /checkout?token= */
export default function CheckoutPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [checkout, setCheckout] = useState<CheckoutView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [canceled, setCanceled] = useState(false)

  useEffect(() => {
    if (!token) return
    fetchCheckout(token)
      .then(setCheckout)
      .catch((e: Error) => {
        const status = e instanceof ApiError ? e.problem.status : null
        setError(typeof status === 'string' && status in STATUS_TEXT ? STATUS_TEXT[status as keyof typeof STATUS_TEXT] : e.message)
      })
  }, [token])

  async function pay() {
    if (!checkout) return
    setBusy(true)
    setError(null)
    setCanceled(false)
    const origin = window.location.origin
    const query = `token=${encodeURIComponent(token)}`
    try {
      const tossPayments = await loadTossPayments(checkout.clientKey)
      await tossPayments.payment({ customerKey: checkout.customerKey }).requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: checkout.donation.amount },
        orderId: checkout.orderId,
        orderName: checkout.orderName,
        successUrl: `${origin}/checkout/success?${query}`,
        failUrl: `${origin}/checkout/fail?${query}`,
      })
    } catch (e) {
      // PC에서 결제창을 닫으면 failUrl로 가지 않고 USER_CANCEL 오류가 온다. 다시 결제할 수 있게 후원은 그대로 둔다
      const failure = e as { code?: string; message?: string }
      if (failure.code === 'USER_CANCEL') setCanceled(true)
      else setError(failure.message ?? '결제를 요청하지 못했습니다.')
      setBusy(false)
    }
  }

  if (!token) return <p className="notice error">결제 정보가 없습니다. 앱에서 후원을 다시 시작해 주세요.</p>
  if (error && !checkout) {
    return (
      <div className="notice">
        <p className="error">{error}</p>
        <div className="actions">
          <BackToApp />
        </div>
      </div>
    )
  }
  if (!checkout) return <p className="notice">불러오는 중…</p>

  const { donation } = checkout
  return (
    <div className="donate-form">
      <h1>후원 결제</h1>
      <p className="meta">
        <strong className="owner-name">{donation.recipient.nickname}</strong>님에게 · {donation.broadcast.title}
      </p>
      <p className="donation-total">
        <strong className="donation-amount">{formatWon(donation.amount)}</strong>
      </p>
      {donation.message && <p className="donation-message">{donation.message}</p>}
      {error && <p className="hint error">{error}</p>}
      {canceled && <p className="hint">결제창을 닫았습니다. 다시 결제하거나 앱으로 돌아갈 수 있습니다.</p>}
      <p className="hint">앱에서 시작한 후원입니다. 이 브라우저의 로그인과 상관없이 앱의 계정으로 후원됩니다.</p>
      <p className="hint">결제수단은 다음에 열리는 토스페이먼츠 결제창에서 고릅니다. 테스트 결제라 실제로 돈이 빠져나가지 않습니다.</p>
      <div className="actions">
        <BackToApp href={appLink(donation.broadcast.id)} />
        <button type="button" className="button primary" disabled={busy} onClick={pay}>
          {busy ? '결제 진행 중…' : `${formatWon(donation.amount)} 결제하기`}
        </button>
      </div>
    </div>
  )
}

type View = { kind: 'loading' } | { kind: 'done'; donation: SentDonation } | { kind: 'error'; message: string; retry: boolean }

/** /checkout/success?token=&paymentKey=&orderId=&amount= — 결제 서비스가 결제 인증을 마치고 돌려보내는 곳 */
export function CheckoutSuccessPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const paymentKey = params.get('paymentKey')
  const amount = Number(params.get('amount'))
  const [view, setView] = useState<View>({ kind: 'loading' })
  const valid = !!token && !!paymentKey && Number.isFinite(amount)

  const approve = useCallback(() => {
    if (!token || !paymentKey) return
    once(`approve:${token}`, () => approveCheckout(token, paymentKey, amount))
      .then((donation) => setView({ kind: 'done', donation }))
      .catch((e: Error) =>
        // 503이면 결과를 아직 모르는 것이다. 다시 확인하면 같은 승인을 다시 요청한다
        setView({ kind: 'error', message: e.message, retry: e instanceof ApiError && e.status === 503 }),
      )
  }, [token, paymentKey, amount])

  useEffect(() => {
    if (valid) approve()
  }, [valid, approve])

  if (!valid) return <p className="notice error">결제 정보가 올바르지 않습니다.</p>
  if (view.kind === 'loading') return <p className="notice">결제를 확인하는 중…</p>
  if (view.kind === 'error') {
    return (
      <div className="notice">
        <p className="error">{view.message}</p>
        <div className="actions">
          {view.retry && (
            <button
              type="button"
              className="button primary"
              onClick={() => {
                setView({ kind: 'loading' })
                approve()
              }}
            >
              다시 확인
            </button>
          )}
          <BackToApp />
        </div>
      </div>
    )
  }
  return <CheckoutOutcome donation={view.donation} />
}

/** /checkout/fail?token=&code=&message= — 결제 인증에 실패했거나 결제창을 닫은 경우 */
export function CheckoutFailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const code = params.get('code')
  const message = params.get('message')
  const [view, setView] = useState<View>({ kind: 'loading' })

  useEffect(() => {
    if (!token) return
    once(`abandon:${token}`, () => abandonCheckout(token, code, message))
      .then((donation) => setView({ kind: 'done', donation }))
      .catch((e: Error) => setView({ kind: 'error', message: e.message, retry: false }))
  }, [token, code, message])

  if (!token) return <p className="notice error">{message ?? '결제에 실패했습니다.'}</p>
  if (view.kind === 'loading') return <p className="notice">결과를 정리하는 중…</p>
  if (view.kind === 'error') return <p className="notice error">{view.message}</p>
  return <CheckoutOutcome donation={view.donation} />
}

/** 결과를 보여 주고 앱을 다시 연다. 브라우저가 막으면 버튼으로 연다 */
function CheckoutOutcome({ donation }: { donation: SentDonation }) {
  const backToApp = appLink(donation.broadcast.id)
  useEffect(() => {
    window.location.href = backToApp
  }, [backToApp])

  const who = `${donation.recipient.nickname}님에게 ${formatWon(donation.amount)}`
  const text = {
    DONE: `${who}을 후원했습니다. 방송 화면에 후원이 표시됩니다.`,
    FAILED: `${who} 후원에 실패했습니다. ${donation.failureMessage ?? ''}`,
    CANCELED: '후원을 취소했습니다. 결제되지 않았습니다.',
    EXPIRED: '결제 시간이 지나 후원이 만료되었습니다. 결제되지 않았습니다.',
    PENDING: '결제 결과를 아직 확인하지 못했습니다. 잠시 후 앱의 후원 내역에서 확인해 주세요.',
  }[donation.status]

  return (
    <div className="notice">
      <p className={donation.status === 'FAILED' ? 'error' : undefined}>{text}</p>
      <div className="actions">
        <BackToApp href={backToApp} primary />
      </div>
    </div>
  )
}
