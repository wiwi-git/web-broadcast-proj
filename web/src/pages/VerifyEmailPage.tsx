import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { verifyEmail } from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'

/**
 * 인증 메일의 링크가 여는 화면(/verify-email?token=). 열리자마자 인증을 요청한다.
 * 로그인하지 않은 브라우저(앱 회원이 메일을 연 경우 등)에서도 된다.
 */
export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { me, ready, refresh } = useAuth()
  const [result, setResult] = useState<'working' | 'done' | string>('working')
  // 개발 모드에서 효과가 두 번 돌아도 한 번만 보낸다 (두 번째는 이미 쓴 링크라 실패한다)
  const sent = useRef(false)

  useEffect(() => {
    // 로그인 상태 확인이 끝나야 CSRF 토큰 쿠키가 있다
    if (!ready || sent.current) return
    sent.current = true
    verifyEmail(token)
      .then(async () => {
        setResult('done')
        await refresh()
      })
      .catch((e: Error) => setResult(e.message))
  }, [ready, token, refresh])

  if (result === 'working') return <p className="notice">인증하는 중…</p>
  if (result === 'done') {
    return (
      <div className="notice">
        <p>이메일 인증을 마쳤습니다. 이제 동영상을 올리고 댓글을 쓸 수 있어요.</p>
        <p className="meta">앱에서 가입했다면 앱으로 돌아가 "인증했어요"를 누르세요.</p>
        <Link to={me ? '/' : '/login'} className="button">
          {me ? '처음으로' : '로그인'}
        </Link>
      </div>
    )
  }
  return (
    <div className="notice">
      <p className="error">{result}</p>
      <Link to={me ? '/account' : '/login'} className="button">
        {me ? '내 계정' : '로그인'}
      </Link>
    </div>
  )
}
