import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import {
  ApiError,
  fileReport,
  REPORT_DETAIL_MAX_LENGTH,
  REPORT_REASONS,
  type ReportReason,
  type ReportTargetType,
} from '../api.ts'
import { loginPath, useAuth } from '../auth/useAuth.ts'

interface Props {
  targetType: ReportTargetType
  targetId: string
  /** 대상을 만든 User. 내 것에는 버튼을 보이지 않는다 (서버도 거절한다) */
  ownerId: string
}

type State = 'closed' | 'open' | 'done'

/**
 * 남의 Broadcast·Video·Comment를 신고한다. 사유를 고르고 내용을 덧붙일 수 있다.
 * 로그인하지 않았으면 로그인 화면으로 보낸다.
 */
export default function ReportButton({ targetType, targetId, ownerId }: Props) {
  const { me } = useAuth()
  const location = useLocation()
  const [state, setState] = useState<State>('closed')
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [doneMessage, setDoneMessage] = useState('')

  if (me?.id === ownerId) return null
  if (!me) {
    return (
      <Link className="link-button report-button" to={loginPath(location.pathname + location.search)}>
        신고
      </Link>
    )
  }
  if (state === 'done') return <span className="report-done">{doneMessage}</span>
  if (state === 'closed') {
    return (
      <button type="button" className="link-button report-button" onClick={() => setState('open')}>
        신고
      </button>
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!reason) return
    setBusy(true)
    setError(null)
    try {
      await fileReport({ targetType, targetId, reason, detail: detail.trim() })
      setDoneMessage('신고했습니다. 운영자가 확인할게요.')
      setState('done')
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setDoneMessage('이미 신고했습니다.')
        setState('done')
      } else {
        setError((e as Error).message)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="report-form" onSubmit={handleSubmit} aria-label="신고하기">
      <p className="report-title">신고 사유를 골라 주세요</p>
      <div className="report-reasons" role="radiogroup">
        {REPORT_REASONS.map((r) => (
          <label key={r.value} className="report-reason">
            <input
              type="radio"
              name={`report-reason-${targetId}`}
              value={r.value}
              checked={reason === r.value}
              onChange={() => setReason(r.value)}
            />
            {r.label}
          </label>
        ))}
      </div>
      <textarea
        className="text-input"
        rows={2}
        maxLength={REPORT_DETAIL_MAX_LENGTH}
        placeholder="자세한 내용 (선택)"
        value={detail}
        onChange={(e) => setDetail(e.target.value)}
      />
      {error && <p className="hint error">{error}</p>}
      <div className="report-actions">
        <button type="button" className="button small" onClick={() => setState('closed')}>
          취소
        </button>
        <button type="submit" className="button small danger" disabled={busy || !reason}>
          신고하기
        </button>
      </div>
    </form>
  )
}
