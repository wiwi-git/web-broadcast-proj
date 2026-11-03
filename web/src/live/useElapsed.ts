import { useEffect, useState } from 'react'
import { formatDuration } from '../format.ts'

/** since부터 지금까지의 경과 시간("12:34")을 1초마다 갱신한다. */
export function useElapsed(since: string | null): string {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!since) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [since])

  if (!since) return ''
  return formatDuration(Math.max(0, (now - new Date(since).getTime()) / 1000))
}
