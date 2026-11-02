import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError, fetchVideo, type Video } from '../api.ts'
import { formatDate, formatDuration } from '../format.ts'
import HlsPlayer from '../components/HlsPlayer.tsx'

const POLL_INTERVAL_MS = 3000

export default function VideoDetailPage() {
  const { id = '' } = useParams()
  const [loaded, setLoaded] = useState<Video | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 처리 중이면 공개 또는 실패로 바뀔 때까지 주기적으로 다시 확인한다
  useEffect(() => {
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined

    async function load() {
      try {
        const data = await fetchVideo(id)
        if (cancelled) return
        setLoaded(data)
        setError(null)
        if (data.status === 'PROCESSING') timer = setTimeout(load, POLL_INTERVAL_MS)
      } catch (e) {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) setError('동영상을 찾을 수 없습니다.')
        else {
          setError((e as Error).message)
          timer = setTimeout(load, POLL_INTERVAL_MS)
        }
      }
    }

    load()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [id])

  // 다른 동영상으로 이동한 직후에는 이전 동영상을 보여주지 않는다
  const video = loaded?.id === id ? loaded : null
  if (!video) return <p className={error ? 'notice error' : 'notice'}>{error ?? '불러오는 중…'}</p>

  return (
    <article className="video-detail">
      {video.status === 'PUBLISHED' && video.playbackUrl ? (
        <HlsPlayer src={video.playbackUrl} poster={video.thumbnailUrl ?? undefined} />
      ) : (
        <div className="player-placeholder">
          {video.status === 'PROCESSING' ? (
            <>
              <div className="spinner" />
              <p>동영상을 처리하고 있습니다.</p>
              <p className="meta">처리가 끝나면 자동으로 재생할 수 있게 됩니다.</p>
            </>
          ) : (
            <p className="error">동영상 처리에 실패했습니다.</p>
          )}
        </div>
      )}

      <h1 className="video-title">{video.title}</h1>
      <p className="meta">
        {formatDate(video.createdAt)}
        {video.durationSeconds != null && ` · ${formatDuration(video.durationSeconds)}`}
      </p>
      {video.description && <p className="description">{video.description}</p>}
    </article>
  )
}
