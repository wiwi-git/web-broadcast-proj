import { loadTossPayments, type TossPaymentsPayment } from '@tosspayments/tosspayments-sdk'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import {
  abandonDonation,
  DONATION_AMOUNT_UNIT,
  DONATION_MAX_AMOUNT,
  DONATION_MESSAGE_MAX_LENGTH,
  DONATION_MIN_AMOUNT,
  DONATION_PRESETS,
  fetchBroadcast,
  fetchPaymentClientKey,
  startDonation,
  type Broadcast,
} from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'
import { formatWon } from '../format.ts'

function amountError(amount: number | null): string | null {
  if (amount == null) return '금액을 입력해 주세요.'
  if (amount < DONATION_MIN_AMOUNT || amount > DONATION_MAX_AMOUNT || amount % DONATION_AMOUNT_UNIT !== 0) {
    return '1,000원부터 500,000원까지 1,000원 단위로 정할 수 있습니다.'
  }
  return null
}

/**
 * 방송 중인 Broadcast의 Broadcaster에게 후원한다.
 * 1. 금액과 메시지를 고른다
 * 2. 후원하기를 누르면 서버에 후원을 결제 대기로 만들고, 그 id를 orderId로 토스페이먼츠 통합결제창을 연다
 *    (API 개별 연동 키). 결제수단은 결제창에서 고른다
 * 3. 결제 서비스가 /donations/success 또는 /donations/fail로 돌려보낸다
 */
export default function DonatePage() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const [broadcast, setBroadcast] = useState<Broadcast | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    fetchBroadcast(id)
      .then(setBroadcast)
      .catch((e: Error) => setLoadError(e.message))
  }, [id])

  if (loadError) return <p className="notice error">{loadError}</p>
  if (!broadcast || !me) return <p className="notice">불러오는 중…</p>
  const back = (
    <Link to={`/live/${broadcast.id}`} className="button">
      방송으로 돌아가기
    </Link>
  )
  if (broadcast.owner.id === me.id) {
    return (
      <div className="notice">
        <p>자기 방송에는 후원할 수 없습니다.</p>
        <div className="actions">{back}</div>
      </div>
    )
  }
  if (broadcast.status !== 'LIVE') {
    return (
      <div className="notice">
        <p>방송 중일 때만 후원할 수 있습니다.</p>
        <div className="actions">{back}</div>
      </div>
    )
  }
  return <DonateForm broadcast={broadcast} customerKey={me.id} customerName={me.nickname} />
}

function DonateForm({
  broadcast,
  customerKey,
  customerName,
}: {
  broadcast: Broadcast
  customerKey: string
  customerName: string
}) {
  const [amountText, setAmountText] = useState(String(DONATION_PRESETS[1]))
  const [message, setMessage] = useState('')
  const [payment, setPayment] = useState<TossPaymentsPayment | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [canceled, setCanceled] = useState(false)

  const amount = /^\d+$/.test(amountText) ? Number(amountText) : null
  const invalidAmount = amountError(amount)

  // 결제창은 "후원하기"를 누를 때 열린다. SDK만 미리 불러 둔다
  useEffect(() => {
    let cancelled = false
    fetchPaymentClientKey()
      .then(async (clientKey) => {
        if (!clientKey) {
          if (!cancelled) setError('후원 결제가 아직 설정되지 않았습니다. 서버의 토스페이먼츠 키를 확인해 주세요.')
          return
        }
        const tossPayments = await loadTossPayments(clientKey)
        if (!cancelled) setPayment(tossPayments.payment({ customerKey }))
      })
      .catch((e: Error) => {
        if (!cancelled) setError(`결제 모듈을 불러오지 못했습니다. ${e.message}`)
      })
    return () => {
      cancelled = true
    }
  }, [customerKey])

  async function handlePay(event: FormEvent) {
    event.preventDefault()
    if (!payment || amount == null || invalidAmount) return
    setBusy(true)
    setError(null)
    setCanceled(false)
    let orderId: string | null = null
    try {
      const checkout = await startDonation(broadcast.id, amount, message.trim())
      orderId = checkout.orderId
      const origin = window.location.origin
      // 결제창을 닫으면 결제 서비스는 failUrl에 orderId를 붙이지 않으므로 직접 붙여 둔다
      // 카드·간편결제 통합결제창. 결제수단은 결제창에서 고른다
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: amount },
        orderId,
        orderName: checkout.orderName,
        customerName,
        successUrl: `${origin}/donations/success`,
        failUrl: `${origin}/donations/fail?donationId=${encodeURIComponent(orderId)}`,
      })
    } catch (e) {
      // 결제창으로 넘어가지 못했거나 결제창을 닫은 경우. 결제를 마치지 않았으므로 취소로 끝낸다
      // PC에서는 결제창을 닫아도 failUrl로 이동하지 않고 USER_CANCEL 오류가 온다
      const failure = e as { code?: string; message?: string }
      if (orderId) abandonDonation(orderId, 'PAY_PROCESS_CANCELED', failure.message ?? null).catch(() => {})
      if (failure.code === 'USER_CANCEL') setCanceled(true)
      else setError(failure.message ?? '결제를 요청하지 못했습니다.')
      setBusy(false)
    }
  }

  return (
    <form className="donate-form" onSubmit={handlePay}>
      <h1>후원하기</h1>
      <p className="meta">
        <strong className="owner-name">{broadcast.owner.nickname}</strong>님의 방송 · {broadcast.title}
      </p>

      <div className="field">
        <span className="label">금액</span>
        <div className="amount-presets">
          {DONATION_PRESETS.map((preset) => (
            <button
              key={preset}
              type="button"
              className={amount === preset ? 'button small selected' : 'button small'}
              onClick={() => setAmountText(String(preset))}
            >
              {formatWon(preset)}
            </button>
          ))}
        </div>
        <label className="amount-input">
          <input
            type="text"
            inputMode="numeric"
            className="text-input"
            aria-label="직접 입력한 금액"
            value={amountText}
            onChange={(e) => setAmountText(e.target.value.replace(/[^\d]/g, ''))}
          />
          <span>원</span>
        </label>
        <span className={invalidAmount && amountText ? 'hint error' : 'hint'}>
          {invalidAmount && amountText ? invalidAmount : '1,000원 단위, 최대 500,000원'}
        </span>
      </div>

      <label className="field">
        <span className="label">메시지 (선택)</span>
        <textarea
          rows={2}
          maxLength={DONATION_MESSAGE_MAX_LENGTH}
          value={message}
          placeholder="방송 화면에 함께 표시됩니다"
          onChange={(e) => setMessage(e.target.value)}
        />
        <span className="hint">
          {message.length}/{DONATION_MESSAGE_MAX_LENGTH} · 후원자 닉네임은 항상 공개됩니다
        </span>
      </label>

      {error && <p className="hint error">{error}</p>}
      {canceled && <p className="hint">후원을 취소했습니다. 결제되지 않았습니다.</p>}
      <p className="hint">결제수단은 다음에 열리는 토스페이먼츠 결제창에서 고릅니다. 테스트 결제라 실제로 돈이 빠져나가지 않습니다.</p>
      <div className="actions">
        <Link to={`/live/${broadcast.id}`} className="button">
          취소
        </Link>
        <button type="submit" className="button primary" disabled={!payment || !!invalidAmount || busy}>
          {busy ? '결제 진행 중…' : amount && !invalidAmount ? `${formatWon(amount)} 후원하기` : '후원하기'}
        </button>
      </div>
    </form>
  )
}
