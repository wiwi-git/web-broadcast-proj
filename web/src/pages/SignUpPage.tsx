import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import {
  NICKNAME_MAX_LENGTH,
  NICKNAME_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  USERNAME_PATTERN,
} from '../api.ts'
import { safeNext, useAuth } from '../auth/useAuth.ts'

export default function SignUpPage() {
  const { me, signUp } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNext(searchParams.get('next'))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (me && !submitting) return <Navigate to={next} replace />

  const nicknameLength = [...nickname.trim()].length
  const usernameError = username && !USERNAME_PATTERN.test(username) ? '영문 소문자와 숫자로 4~20자' : null
  const passwordError =
    password && (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH)
      ? `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자`
      : null
  const confirmError = passwordConfirm && passwordConfirm !== password ? '비밀번호가 서로 다릅니다.' : null
  const nicknameError =
    nickname && (nicknameLength < NICKNAME_MIN_LENGTH || nicknameLength > NICKNAME_MAX_LENGTH)
      ? `${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자`
      : null
  const valid =
    username && password && passwordConfirm && nickname && !usernameError && !passwordError && !confirmError && !nicknameError

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!valid) return
    setSubmitting(true)
    setError(null)
    try {
      await signUp({ username, password, nickname: nickname.trim() })
      navigate(next, { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h1 className="page-title">회원가입</h1>
      <label className="field">
        <span className="label">아이디</span>
        <input
          type="text"
          className="text-input"
          autoComplete="username"
          autoCapitalize="none"
          maxLength={20}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <span className={usernameError ? 'hint error' : 'hint'}>
          {usernameError ?? '로그인할 때 씁니다. 영문 소문자와 숫자로 4~20자, 나중에 바꿀 수 없습니다.'}
        </span>
      </label>
      <label className="field">
        <span className="label">비밀번호</span>
        <input
          type="password"
          className="text-input"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <span className={passwordError ? 'hint error' : 'hint'}>
          {passwordError ?? `${PASSWORD_MIN_LENGTH}자 이상`}
        </span>
      </label>
      <label className="field">
        <span className="label">비밀번호 확인</span>
        <input
          type="password"
          className="text-input"
          autoComplete="new-password"
          value={passwordConfirm}
          onChange={(e) => setPasswordConfirm(e.target.value)}
        />
        {confirmError && <span className="hint error">{confirmError}</span>}
      </label>
      <label className="field">
        <span className="label">닉네임</span>
        <input
          type="text"
          className="text-input"
          maxLength={NICKNAME_MAX_LENGTH}
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
        />
        <span className={nicknameError ? 'hint error' : 'hint'}>
          {nicknameError ?? '방송과 댓글에 보이는 이름입니다. 다른 회원과 겹칠 수 없습니다.'}
        </span>
      </label>
      {error && <p className="hint error">{error}</p>}
      <button type="submit" className="button primary" disabled={!valid || submitting}>
        {submitting ? '가입 중…' : '가입하기'}
      </button>
      <p className="meta auth-switch">
        이미 회원이신가요? <Link to={`/login?next=${encodeURIComponent(next)}`}>로그인</Link>
      </p>
    </form>
  )
}
