import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { safeNext, useAuth } from '../auth/useAuth.ts'

export default function LoginPage() {
  const { me, logIn } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNext(searchParams.get('next'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (me && !submitting) return <Navigate to={next} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await logIn({ email: email.trim(), password })
      navigate(next, { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h1 className="page-title">로그인</h1>
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
      <label className="field">
        <span className="label">비밀번호</span>
        <input
          type="password"
          className="text-input"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {error && <p className="hint error">{error}</p>}
      <button type="submit" className="button primary" disabled={!email.trim() || !password || submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </button>
      <p className="meta auth-switch">
        <Link to="/forgot-password">비밀번호를 잊으셨나요?</Link>
      </p>
      <p className="meta auth-switch">
        아직 회원이 아니신가요? <Link to={`/signup?next=${encodeURIComponent(next)}`}>회원가입</Link>
      </p>
    </form>
  )
}
