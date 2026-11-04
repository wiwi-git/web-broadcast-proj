import { useState, type FormEvent } from 'react'
import { COMMENT_MAX_LENGTH, deleteComment, editComment, type Comment } from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'
import { formatRelative } from '../format.ts'

interface Props {
  comment: Comment
  onEdited(comment: Comment): void
  onDeleted(id: string): void
}

/** Author에게만 수정·삭제 버튼이 보인다. 서버도 Author인지 다시 확인한다. */
export default function CommentItem({ comment, onEdited, onDeleted }: Props) {
  const { me } = useAuth()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(comment.body)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const mine = me?.id === comment.author.id

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onEdited(await editComment(comment.id, draft.trim()))
      setEditing(false)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    if (!window.confirm('댓글을 삭제할까요?')) return
    setBusy(true)
    setError(null)
    try {
      await deleteComment(comment.id)
      onDeleted(comment.id)
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <li className="comment">
      <div className="comment-head">
        <span className="comment-author">{comment.author.nickname}</span>
        <span className="meta">
          {formatRelative(comment.createdAt)}
          {comment.updatedAt && ' (수정됨)'}
        </span>
        {mine && !editing && (
          <span className="comment-actions">
            <button
              type="button"
              className="link-button"
              disabled={busy}
              onClick={() => {
                setDraft(comment.body)
                setEditing(true)
              }}
            >
              수정
            </button>
            <button type="button" className="link-button" disabled={busy} onClick={handleDelete}>
              삭제
            </button>
          </span>
        )}
      </div>
      {editing ? (
        <form className="comment-edit" onSubmit={handleSave}>
          <textarea
            className="text-input"
            rows={2}
            maxLength={COMMENT_MAX_LENGTH}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            autoFocus
          />
          <div className="comment-edit-actions">
            <button type="button" className="button small" onClick={() => setEditing(false)}>
              취소
            </button>
            <button type="submit" className="button primary small" disabled={busy || !draft.trim()}>
              저장
            </button>
          </div>
        </form>
      ) : (
        <p className="comment-body">{comment.body}</p>
      )}
      {error && <p className="hint error">{error}</p>}
    </li>
  )
}
