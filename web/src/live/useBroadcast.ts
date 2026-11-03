import { useEffect, useState } from 'react'
import { ApiError, fetchBroadcast, type Broadcast } from '../api.ts'

const POLL_INTERVAL_MS = 3000

/**
 * Broadcast를 주기적으로 다시 읽는다. 종료되면 멈춘다.
 * 서버는 미디어 서버 상태를 2초마다 반영하므로(ADR-0002) 3초면 충분히 따라간다.
 */
export function useBroadcast(id: string | null) {
  const [loaded, setLoaded] = useState<Broadcast | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined

    async function load() {
      try {
        const data = await fetchBroadcast(id!)
        if (cancelled) return
        setLoaded(data)
        setError(null)
        if (data.status !== 'ENDED') timer = setTimeout(load, POLL_INTERVAL_MS)
      } catch (e) {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) setError('방송을 찾을 수 없습니다.')
        else {
          setError((e as Error).message)
          timer = setTimeout(load, POLL_INTERVAL_MS)
        }
      }
    }

    load()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [id])

  // 다른 방송으로 이동한 직후에는 이전 방송을 보여주지 않는다
  return { broadcast: loaded?.id === id ? loaded : null, error }
}
