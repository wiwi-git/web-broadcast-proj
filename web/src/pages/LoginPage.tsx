import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { safeNext, useAuth } from '../auth/useAuth.ts'

export default function LoginPage() {
  const { me, logIn } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNext(searchParams.get('next'))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (me && !submitting) return <Navigate to={next} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await logIn({ username: username.trim(), password })
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
        <span className="label">아이디</span>
        <input
          type="text"
          className="text-input"
          autoComplete="username"
          autoCapitalize="none"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
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
      <button type="submit" className="button primary" disabled={!username.trim() || !password || submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </button>
      <p className="meta auth-switch">
        아직 회원이 아니신가요? <Link to={`/signup?next=${encodeURIComponent(next)}`}>회원가입</Link>
      </p>
    </form>
  )
}
