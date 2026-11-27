import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { changePassword, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, resendVerification } from '../api.ts'
import { loginPath, useAuth } from '../auth/useAuth.ts'
import { CONTACT_EMAIL } from '../legal.ts'

/**
 * 내 계정. 이메일(인증 여부)·닉네임을 보여 주고, 인증 메일 다시 받기, 비밀번호 변경, 회원 탈퇴를 한다.
 * 탈퇴는 비밀번호를 다시 넣어야 하며, 지워지는 것을 먼저 보여 준다 (개인정보처리방침 3항, 7항).
 * 탈퇴하면 로그아웃되므로 RequireLogin으로 감싸지 않고, 탈퇴 완료 안내까지 이 화면이 직접 보여 준다.
 */
export default function AccountPage() {
  const { me, ready, withdraw } = useAuth()
  const location = useLocation()
  const [done, setDone] = useState(false)
  const [password, setPassword] = useState('')
  const [understood, setUnderstood] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (done) {
    return (
      <div className="notice">
        <p>탈퇴했습니다. 계정과 기록을 모두 지웠습니다. 그동안 이용해 주셔서 감사합니다.</p>
        <Link to="/" className="button">
          처음으로
        </Link>
      </div>
    )
  }
  if (!ready) return <p className="notice">불러오는 중…</p>
  if (!me) return <Navigate to={loginPath(location.pathname)} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // 먼저 완료 화면으로 바꿔 두어야 로그아웃된 순간 로그인 화면으로 넘어가지 않는다
      setDone(true)
      await withdraw(password)
    } catch (e) {
      setDone(false)
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <div className="account-page">
      <h1 className="page-title">내 계정</h1>
      <dl className="account-info">
        <dt>이메일</dt>
        <dd>
          {me.email}{' '}
          {me.emailVerified ? (
            <span className="badge verified inline">인증됨</span>
          ) : (
            <span className="badge danger inline">인증 전</span>
          )}
        </dd>
        <dt>닉네임</dt>
        <dd>{me.nickname}</dd>
      </dl>
      {!me.emailVerified && <ResendVerification />}

      <ChangePasswordForm />

      <section className="withdraw-box">
        <h2>회원 탈퇴</h2>
        {me.admin ? (
          <p className="meta">운영자 계정은 탈퇴할 수 없습니다. 먼저 운영자 역할을 해제해 주세요.</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <p>탈퇴하면 아래 것들이 바로 지워지고 되돌릴 수 없습니다.</p>
            <ul>
              <li>계정(이메일, 닉네임). 같은 이메일로 다시 가입할 수는 있습니다</li>
              <li>올린 동영상과 영상 파일, 내 방송 기록</li>
              <li>내가 쓴 댓글, 그리고 내 동영상·방송에 다른 사람이 단 댓글</li>
              <li>보내거나 받은 후원 기록 (상대방의 후원 내역에서도 사라집니다)</li>
              <li>내가 한 신고, 등록한 유튜브 키</li>
              <li>모든 기기의 로그인</li>
            </ul>
            <p className="meta">
              자세한 내용은 <Link to="/privacy">개인정보처리방침</Link>에 있습니다. 로그인할 수 없다면{' '}
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>로 요청해 주세요.
            </p>
            <label className="field">
              <span className="label">비밀번호 확인</span>
              <input
                type="password"
                className="text-input"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <label className="agree-check">
              <input type="checkbox" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
              <span>위 내용이 모두 지워지고 되돌릴 수 없다는 것을 확인했습니다.</span>
            </label>
            {error && <p className="hint error">{error}</p>}
            <button type="submit" className="button danger" disabled={!password || !understood || busy}>
              {busy ? '탈퇴하는 중…' : '탈퇴하기'}
            </button>
          </form>
        )}
      </section>
    </div>
  )
}

/** 인증 전인 회원이 인증 메일을 다시 받는다. 1분에 한 번 */
function ResendVerification() {
  const { refresh } = useAuth()
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function resend() {
    setBusy(true)
    try {
      await resendVerification()
      setMessage('인증 메일을 다시 보냈습니다. 메일의 링크를 누른 뒤 "인증했어요"를 눌러 주세요.')
    } catch (e) {
      setMessage((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="account-section">
      <p className="meta">인증하면 동영상을 올리고 댓글을 쓸 수 있습니다. 메일이 없으면 스팸함도 확인해 주세요.</p>
      <div className="account-actions">
        <button type="button" className="button small" disabled={busy} onClick={resend}>
          인증 메일 다시 받기
        </button>
        <button type="button" className="button small" onClick={() => refresh()}>
          인증했어요
        </button>
      </div>
      {message && <p className="hint">{message}</p>}
    </div>
  )
}

/** 지금 비밀번호를 알면 바꾼다. 이 기기의 로그인은 그대로, 다른 기기는 로그아웃된다 */
function ChangePasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

  const lengthError =
    next && (next.length < PASSWORD_MIN_LENGTH || next.length > PASSWORD_MAX_LENGTH)
      ? `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자`
      : null
  const confirmError = confirm && confirm !== next ? '새 비밀번호가 서로 다릅니다.' : null

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      setMessage({ text: '비밀번호를 바꿨습니다. 다른 기기의 로그인은 끊겼습니다.', error: false })
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="account-section">
      <h2>비밀번호 변경</h2>
      <form className="account-form" onSubmit={handleSubmit}>
        <label className="field">
          <span className="label">지금 비밀번호</span>
          <input
            type="password"
            className="text-input"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </label>
        <label className="field">
          <span className="label">새 비밀번호</span>
          <input
            type="password"
            className="text-input"
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <span className={lengthError ? 'hint error' : 'hint'}>{lengthError ?? `${PASSWORD_MIN_LENGTH}자 이상`}</span>
        </label>
        <label className="field">
          <span className="label">새 비밀번호 확인</span>
          <input
            type="password"
            className="text-input"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
          {confirmError && <span className="hint error">{confirmError}</span>}
        </label>
        {message && <p className={message.error ? 'hint error' : 'hint'}>{message.text}</p>}
        <button
          type="submit"
          className="button primary"
          disabled={!current || !next || !!lengthError || !confirm || !!confirmError || busy}
        >
          {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </form>
    </section>
  )
}
