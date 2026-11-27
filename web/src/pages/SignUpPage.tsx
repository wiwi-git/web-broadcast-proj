import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import {
  EMAIL_MAX_LENGTH,
  EMAIL_PATTERN,
  NICKNAME_MAX_LENGTH,
  NICKNAME_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
} from '../api.ts'
import { safeNext, useAuth } from '../auth/useAuth.ts'

export default function SignUpPage() {
  const { me, signUp } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNext(searchParams.get('next'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (me && !submitting) return <Navigate to={next} replace />

  const nicknameLength = [...nickname.trim()].length
  const emailError = email && !EMAIL_PATTERN.test(email.trim()) ? '이메일 주소 형식이 아닙니다.' : null
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
    email &&
    password &&
    passwordConfirm &&
    nickname &&
    agreed &&
    !emailError &&
    !passwordError &&
    !confirmError &&
    !nicknameError

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!valid) return
    setSubmitting(true)
    setError(null)
    try {
      await signUp({ email: email.trim(), password, nickname: nickname.trim(), agreeTerms: agreed })
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
        <span className="label">이메일</span>
        <input
          type="email"
          className="text-input"
          autoComplete="email"
          autoCapitalize="none"
          maxLength={EMAIL_MAX_LENGTH}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <span className={emailError ? 'hint error' : 'hint'}>
          {emailError ?? '로그인할 때 씁니다. 가입하면 인증 메일을 보냅니다. 다른 회원에게는 보이지 않습니다.'}
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
      <label className="agree-check">
        <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
        <span>
          만 14세 이상이며{' '}
          <a href="/terms" target="_blank" rel="noreferrer">
            이용약관
          </a>
          과{' '}
          <a href="/privacy" target="_blank" rel="noreferrer">
            개인정보처리방침
          </a>
          에 동의합니다. (필수)
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
