import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchVideos, type VideoPage } from '../api.ts'
import { formatDate, formatDuration } from '../format.ts'

export default function VideoListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(Number(searchParams.get('page')) || 0, 0)
  const [result, setResult] = useState<VideoPage | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchVideos(page)
      .then((data) => {
        if (cancelled) return
        setResult(data)
        setError(null)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [page])

  if (error) return <p className="notice error">{error}</p>
  if (!result) return <p className="notice">불러오는 중…</p>
  if (result.totalItems === 0) {
    return (
      <div className="notice">
        <p>아직 공개된 동영상이 없습니다.</p>
        <Link to="/upload" className="button primary">
          첫 동영상 업로드
        </Link>
      </div>
    )
  }

  const goTo = (next: number) => setSearchParams(next === 0 ? {} : { page: String(next) })

  return (
    <>
      <h1 className="page-title">동영상</h1>
      <ul className="video-grid">
        {result.items.map((video) => (
          <li key={video.id}>
            <Link to={`/videos/${video.id}`} className="video-card">
              <div className="thumbnail">
                {video.thumbnailUrl && <img src={video.thumbnailUrl} alt="" loading="lazy" />}
                {video.durationSeconds != null && (
                  <span className="duration">{formatDuration(video.durationSeconds)}</span>
                )}
              </div>
              <h2 className="video-card-title">{video.title}</h2>
              <p className="meta">{video.owner.nickname}</p>
              <p className="meta">{formatDate(video.createdAt)}</p>
            </Link>
          </li>
        ))}
      </ul>
      {result.totalPages > 1 && (
        <nav className="pagination">
          <button className="button" disabled={page === 0} onClick={() => goTo(page - 1)}>
            이전
          </button>
          <span>
            {page + 1} / {result.totalPages}
          </span>
          <button
            className="button"
            disabled={page + 1 >= result.totalPages}
            onClick={() => goTo(page + 1)}
          >
            다음
          </button>
        </nav>
      )}
    </>
  )
}
