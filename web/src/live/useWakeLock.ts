import { useEffect } from 'react'

/**
 * active인 동안 화면이 꺼지지 않게 한다. 휴대폰 화면이 꺼지면 송출이 끊기기 때문이다.
 * 탭이 가려지면 브라우저가 잠금을 풀기 때문에, 다시 보일 때 새로 요청한다.
 */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || !('wakeLock' in navigator)) return
    let sentinel: WakeLockSentinel | null = null
    let released = false

    const acquire = async () => {
      try {
        sentinel = await navigator.wakeLock.request('screen')
        if (released) sentinel.release()
      } catch {
        // 배터리 절약 모드 등으로 거부될 수 있다. 송출은 계속한다
      }
    }
    const onVisibility = () => document.visibilityState === 'visible' && acquire()

    acquire()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      released = true
      document.removeEventListener('visibilitychange', onVisibility)
      sentinel?.release()
    }
  }, [active])
}
