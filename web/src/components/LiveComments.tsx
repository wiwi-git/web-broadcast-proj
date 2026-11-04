import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { fetchBroadcastComments, type Comment } from '../api.ts'
import CommentForm from './CommentForm.tsx'
import CommentItem from './CommentItem.tsx'

const POLL_INTERVAL_MS = 3000

interface Props {
  broadcastId: string
  /** 진행 중일 때만 주기적으로 새로 불러온다 */
  active: boolean
}

/**
 * 채팅처럼 최근 100개를 오래된 순으로 보여 주고 새 댓글을 아래에 쌓는다.
 * 실시간 채팅이 아니라 3초마다 다시 불러오는 Comment다.
 */
export default function LiveComments({ broadcastId, active }: Props) {
  const [items, setItems] = useState<Comment[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const listRef = useRef<HTMLUListElement>(null)
  // 사용자가 위로 올려 읽는 중이면 아래로 끌어내리지 않는다
  const stickToBottom = useRef(true)

  const load = useCallback(
    () =>
      fetchBroadcastComments(broadcastId)
        .then((data) => {
          setItems(data)
          setError(null)
        })
        .catch((e: Error) => setError(e.message)),
    [broadcastId],
  )

  useEffect(() => {
    load()
    if (!active) return
    const timer = setInterval(load, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [load, active])

  useLayoutEffect(() => {
    const list = listRef.current
    if (list && stickToBottom.current) list.scrollTop = list.scrollHeight
  }, [items])

  return (
    <aside className="live-comments">
      <h2 className="comments-title">댓글</h2>
      <ul
        ref={listRef}
        className="comment-list live"
        onScroll={(e) => {
          const list = e.currentTarget
          stickToBottom.current = list.scrollHeight - list.scrollTop - list.clientHeight < 40
        }}
      >
        {items?.length === 0 && <li className="meta comments-empty">아직 댓글이 없습니다.</li>}
        {items?.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            onEdited={(edited) => setItems((current) => current!.map((c) => (c.id === edited.id ? edited : c)))}
            onDeleted={(id) => setItems((current) => current!.filter((c) => c.id !== id))}
          />
        ))}
      </ul>
      {error && <p className="hint error">{error}</p>}
      <CommentForm
        compact
        target={{ kind: 'broadcast', id: broadcastId }}
        onWritten={(comment) => {
          stickToBottom.current = true
          setItems((current) => [...(current ?? []), comment])
        }}
      />
    </aside>
  )
}
