/** 125 → "2:05", 3725 → "1:02:05" */
export function formatDuration(seconds: number | null): string {
  if (seconds == null) return ''
  const total = Math.floor(seconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = String(total % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

const dateFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GB`
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${Math.ceil(bytes / 1024)} KB`
}

/** "방금 전", "5분 전", "3시간 전", 하루가 넘으면 날짜 */
export function formatRelative(iso: string, now = Date.now()): string {
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return formatDate(iso)
}

/** 10000 → "10,000원" */
export function formatWon(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`
}
