import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { loginPath, useAuth } from '../auth/useAuth.ts'
import { CONTACT_EMAIL } from '../legal.ts'

/**
 * 내 계정. 아이디·닉네임을 보여 주고 회원 탈퇴를 받는다.
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
        <dt>아이디</dt>
        <dd>{me.username}</dd>
        <dt>닉네임</dt>
        <dd>{me.nickname}</dd>
      </dl>

      <section className="withdraw-box">
        <h2>회원 탈퇴</h2>
        {me.admin ? (
          <p className="meta">운영자 계정은 탈퇴할 수 없습니다. 먼저 운영자 역할을 해제해 주세요.</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <p>탈퇴하면 아래 것들이 바로 지워지고 되돌릴 수 없습니다.</p>
            <ul>
              <li>계정(아이디, 닉네임). 같은 아이디로 다시 가입할 수는 있습니다</li>
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
