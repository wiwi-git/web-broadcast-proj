import { useEffect } from 'react'
import type { PublicDonation } from '../api.ts'
import { formatWon } from '../format.ts'

const SHOW_MS = 5000

/** 플레이어나 카메라 미리보기 위에 5초 동안 뜨는 후원 알림. 부모가 key로 후원마다 새로 그린다 */
export default function DonationAlert({ donation, onDone }: { donation: PublicDonation; onDone(): void }) {
  useEffect(() => {
    const timer = setTimeout(onDone, SHOW_MS)
    return () => clearTimeout(timer)
  }, [onDone])

  return (
    <div className="donation-alert" role="status">
      <p className="donation-alert-title">
        <strong>{donation.donor.nickname}</strong>님이 <strong className="donation-amount">{formatWon(donation.amount)}</strong>{' '}
        후원했습니다
      </p>
      {donation.message && <p className="donation-alert-message">{donation.message}</p>}
    </div>
  )
}
