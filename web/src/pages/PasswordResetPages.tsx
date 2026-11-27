import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, requestPasswordReset, resetPassword } from '../api.ts'

/** 비밀번호 찾기(/forgot-password). 가입한 이메일이면 재설정 링크를 보낸다. 가입 여부는 알려 주지 않는다 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <div className="notice">
        <p>
          <strong>{email.trim()}</strong>로 가입한 계정이 있으면 비밀번호 재설정 링크를 보냈습니다. 메일함을 확인해 주세요.
        </p>
        <p className="meta">링크는 30분 동안 쓸 수 있습니다. 메일이 없으면 스팸함도 확인해 주세요.</p>
        <Link to="/login" className="button">
          로그인
        </Link>
      </div>
    )
  }
  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h1 className="page-title">비밀번호 찾기</h1>
      <p className="meta">가입한 이메일을 넣으면 새 비밀번호를 정할 수 있는 링크를 보내 드립니다.</p>
      <label className="field">
        <span className="label">이메일</span>
        <input
          type="email"
          className="text-input"
          autoComplete="email"
          autoCapitalize="none"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      {error && <p className="hint error">{error}</p>}
      <button type="submit" className="button primary" disabled={!email.trim() || busy}>
        {busy ? '보내는 중…' : '재설정 링크 받기'}
      </button>
      <p className="meta auth-switch">
        <Link to="/login">로그인으로 돌아가기</Link>
      </p>
    </form>
  )
}

/** 재설정 메일의 링크가 여는 화면(/reset-password?token=). 새 비밀번호를 정하면 모든 기기에서 로그아웃된다 */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const lengthError =
    password && (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH)
      ? `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자`
      : null
  const confirmError = confirm && confirm !== password ? '비밀번호가 서로 다릅니다.' : null

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <div className="notice">
        <p>비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요. 다른 기기의 로그인은 모두 끊겼습니다.</p>
        <Link to="/login" className="button primary">
          로그인
        </Link>
      </div>
    )
  }
  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h1 className="page-title">새 비밀번호 정하기</h1>
      <label className="field">
        <span className="label">새 비밀번호</span>
        <input
          type="password"
          className="text-input"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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
      {error && (
        <p className="hint error">
          {error} <Link to="/forgot-password">비밀번호 찾기</Link>
        </p>
      )}
      <button
        type="submit"
        className="button primary"
        disabled={!token || !password || !!lengthError || !confirm || !!confirmError || busy}
      >
        {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
      </button>
    </form>
  )
}
