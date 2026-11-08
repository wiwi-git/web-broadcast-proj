import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  fetchReceivedDonations,
  fetchSentDonations,
  type BroadcastRef,
  type DonationStatus,
  type ReceivedDonations,
  type SentDonation,
} from '../api.ts'
import { formatDate, formatWon } from '../format.ts'

const STATUS_LABEL: Record<DonationStatus, string> = {
  PENDING: '결제 대기',
  DONE: '완료',
  FAILED: '실패',
  CANCELED: '취소',
  EXPIRED: '만료',
}

/** 로그인한 본인의 후원 내역. 보낸 후원은 모든 상태, 받은 후원은 완료된 것만 보여 준다 */
export default function DonationsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'received' ? 'received' : 'sent'

  return (
    <div className="donations-page">
      <h1>후원 내역</h1>
      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'sent'}
          className={tab === 'sent' ? 'tab active' : 'tab'}
          onClick={() => setParams({}, { replace: true })}
        >
          보낸 후원
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'received'}
          className={tab === 'received' ? 'tab active' : 'tab'}
          onClick={() => setParams({ tab: 'received' }, { replace: true })}
        >
          받은 후원
        </button>
      </div>
      {tab === 'sent' ? <SentList /> : <ReceivedList />}
    </div>
  )
}

function SentList() {
  const [items, setItems] = useState<SentDonation[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchSentDonations()
      .then(setItems)
      .catch((e: Error) => setError(e.message))
  }, [])

  if (error) return <p className="notice error">{error}</p>
  if (!items) return <p className="notice">불러오는 중…</p>
  if (items.length === 0) return <p className="notice">보낸 후원이 없습니다.</p>
  return (
    <ul className="donation-list">
      {items.map((donation) => (
        <li key={donation.id} className="donation-row">
          <div className="donation-row-head">
            <strong>{donation.recipient.nickname}</strong>
            <span className="donation-amount">{formatWon(donation.amount)}</span>
            <span className={`status-chip ${donation.status.toLowerCase()}`}>{STATUS_LABEL[donation.status]}</span>
          </div>
          <BroadcastLink broadcast={donation.broadcast} />
          {donation.message && <p className="donation-message">{donation.message}</p>}
          {donation.failureMessage && <p className="hint error">{donation.failureMessage}</p>}
          <p className="meta">{formatDate(donation.createdAt)}</p>
        </li>
      ))}
    </ul>
  )
}

function ReceivedList() {
  const [data, setData] = useState<ReceivedDonations | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchReceivedDonations()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  if (error) return <p className="notice error">{error}</p>
  if (!data) return <p className="notice">불러오는 중…</p>
  return (
    <>
      <p className="donation-total">
        지금까지 <strong>{data.totalCount.toLocaleString('ko-KR')}건</strong>,{' '}
        <strong className="donation-amount">{formatWon(data.totalAmount)}</strong>을 받았습니다.
      </p>
      {data.items.length === 0 ? (
        <p className="notice">받은 후원이 없습니다.</p>
      ) : (
        <ul className="donation-list">
          {data.items.map((donation) => (
            <li key={donation.id} className="donation-row">
              <div className="donation-row-head">
                <strong>{donation.donor.nickname}</strong>
                <span className="donation-amount">{formatWon(donation.amount)}</span>
              </div>
              <BroadcastLink broadcast={donation.broadcast} />
              {donation.message && <p className="donation-message">{donation.message}</p>}
              <p className="meta">{formatDate(donation.completedAt)}</p>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function BroadcastLink({ broadcast }: { broadcast: BroadcastRef }) {
  return broadcast.id ? (
    <Link to={`/live/${broadcast.id}`} className="meta donation-broadcast">
      {broadcast.title}
    </Link>
  ) : (
    <span className="meta donation-broadcast">삭제된 방송 · {broadcast.title}</span>
  )
}
