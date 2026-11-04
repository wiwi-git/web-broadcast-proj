import { useEffect, useState } from 'react'
import { fetchVideoComments, type Comment } from '../api.ts'
import CommentForm from './CommentForm.tsx'
import CommentItem from './CommentItem.tsx'

/** 최신순으로 20개씩 "더 보기". 다른 동영상으로 바뀌면 key로 새로 만든다. */
export default function VideoComments({ videoId }: { videoId: string }) {
  const [items, setItems] = useState<Comment[] | null>(null)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchVideoComments(videoId)
      .then((page) => {
        if (cancelled) return
        setItems(page.items)
        setNextCursor(page.nextCursor)
        setTotal(page.totalCount)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [videoId])

  async function loadMore() {
    if (!nextCursor) return
    setLoadingMore(true)
    try {
      const page = await fetchVideoComments(videoId, nextCursor)
      // 그 사이 새로 쓴 댓글이 있어도 겹치지 않게 ID로 거른다
      setItems((current) => {
        const seen = new Set(current?.map((c) => c.id))
        return [...(current ?? []), ...page.items.filter((c) => !seen.has(c.id))]
      })
      setNextCursor(page.nextCursor)
      setTotal(page.totalCount)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <section className="comments">
      <h2 className="comments-title">댓글 {items ? total : ''}</h2>
      <CommentForm
        target={{ kind: 'video', id: videoId }}
        onWritten={(comment) => {
          setItems((current) => [comment, ...(current ?? [])])
          setTotal((n) => n + 1)
        }}
      />
      {error && <p className="hint error">{error}</p>}
      {items && items.length === 0 && <p className="meta comments-empty">첫 댓글을 남겨 보세요.</p>}
      {items && items.length > 0 && (
        <ul className="comment-list">
          {items.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              onEdited={(edited) => setItems((current) => current!.map((c) => (c.id === edited.id ? edited : c)))}
              onDeleted={(id) => {
                setItems((current) => current!.filter((c) => c.id !== id))
                setTotal((n) => n - 1)
              }}
            />
          ))}
        </ul>
      )}
      {nextCursor && (
        <button type="button" className="button more-button" disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? '불러오는 중…' : '댓글 더 보기'}
        </button>
      )}
    </section>
  )
}
