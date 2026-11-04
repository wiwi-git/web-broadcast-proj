import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { COMMENT_MAX_LENGTH, writeComment, type Comment, type CommentTarget } from '../api.ts'
import { loginPath, useAuth } from '../auth/useAuth.ts'

interface Props {
  target: CommentTarget
  onWritten(comment: Comment): void
  /** 라이브 화면처럼 한 줄 입력칸으로 보여 준다 */
  compact?: boolean
}

/** 로그인한 User만 쓸 수 있다. 로그인하지 않았으면 로그인 안내를 보여 준다. */
export default function CommentForm({ target, onWritten, compact = false }: Props) {
  const { me } = useAuth()
  const location = useLocation()
  const [body, setBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!me) {
    return (
      <p className="comment-login">
        <Link to={loginPath(location.pathname)}>로그인</Link>하고 댓글을 남겨 보세요.
      </p>
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!body.trim()) return
    setBusy(true)
    setError(null)
    try {
      onWritten(await writeComment(target, body.trim()))
      setBody('')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={compact ? 'comment-form compact' : 'comment-form'} onSubmit={handleSubmit}>
      {compact ? (
        <input
          type="text"
          className="text-input"
          placeholder="댓글 입력"
          maxLength={COMMENT_MAX_LENGTH}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
      ) : (
        <textarea
          className="text-input"
          rows={3}
          placeholder="댓글을 남겨 보세요"
          maxLength={COMMENT_MAX_LENGTH}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
      )}
      <div className="comment-form-actions">
        {!compact && (
          <span className="hint">
            {body.length} / {COMMENT_MAX_LENGTH}
          </span>
        )}
        <button type="submit" className="button primary small" disabled={busy || !body.trim()}>
          등록
        </button>
      </div>
      {error && <p className="hint error">{error}</p>}
    </form>
  )
}
