import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchBroadcastDonations, type PublicDonation } from '../api.ts'

const POLL_INTERVAL_MS = 3000

/**
 * Broadcast의 완료된 후원을 댓글과 같은 주기로 다시 불러온다.
 * 화면을 연 뒤에 새로 들어온 후원만 alert로 하나씩 내보낸다. 그 전의 후원은 목록에만 있다.
 */
export function useBroadcastDonations(broadcastId: string | null, active: boolean) {
  const [donations, setDonations] = useState<PublicDonation[]>([])
  const [arrived, setArrived] = useState<PublicDonation[]>([])
  // 처음 불러오기 전에는 null. 처음 받은 후원은 알림 없이 본 것으로 둔다
  const seen = useRef<Set<string> | null>(null)

  useEffect(() => {
    if (!broadcastId) return
    let cancelled = false
    const load = () =>
      fetchBroadcastDonations(broadcastId)
        .then((list) => {
          if (cancelled) return
          const known = seen.current
          if (known) {
            const fresh = list.filter((donation) => !known.has(donation.id))
            if (fresh.length > 0) setArrived((queue) => [...queue, ...fresh])
          }
          seen.current = new Set([...(known ?? []), ...list.map((donation) => donation.id)])
          setDonations(list)
        })
        .catch(() => {
          // 다음 주기에 다시 시도한다. 후원 목록은 댓글보다 덜 중요하므로 오류를 따로 보여 주지 않는다
        })
    load()
    if (!active) {
      return () => {
        cancelled = true
      }
    }
    const timer = setInterval(load, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [broadcastId, active])

  const dismissAlert = useCallback(() => setArrived((queue) => queue.slice(1)), [])

  return { donations, alert: arrived[0] ?? null, dismissAlert }
}
