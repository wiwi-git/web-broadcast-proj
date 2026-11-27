import { useState } from 'react'
import { useLocation } from 'react-router'
import { resendVerification } from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'

/**
 * 로그인했지만 이메일 인증을 하지 않은 회원에게 머리글 아래에 띄운다.
 * 인증 전에는 동영상 업로드와 댓글을 할 수 없다. 인증 메일 화면에서는 띄우지 않는다.
 */
export default function VerifyBanner() {
  const { me, refresh } = useAuth()
  const location = useLocation()
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!me || me.emailVerified || location.pathname === '/verify-email') return null

  async function resend() {
    setBusy(true)
    try {
      await resendVerification()
      setMessage('인증 메일을 다시 보냈습니다.')
    } catch (e) {
      setMessage((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="verify-banner" role="status">
      <span>
        <strong>{me.email}</strong>로 보낸 메일의 링크를 눌러 이메일을 인증해 주세요. 인증하면 동영상을 올리고 댓글을 쓸 수
        있어요.
      </span>
      <span className="verify-banner-actions">
        <button type="button" className="button small" disabled={busy} onClick={resend}>
          메일 다시 받기
        </button>
        <button type="button" className="button small" onClick={() => refresh()}>
          인증했어요
        </button>
      </span>
      {message && <span className="hint">{message}</span>}
    </div>
  )
}
