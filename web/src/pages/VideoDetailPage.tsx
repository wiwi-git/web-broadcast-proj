import ConfirmButton from '../components/ConfirmButton.tsx'
import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  ApiError,
  DESCRIPTION_MAX_LENGTH,
  deleteVideo,
  editVideo,
  fetchVideo,
  TITLE_MAX_LENGTH,
  type Video,
} from '../api.ts'
import { useAuth } from '../auth/useAuth.ts'
import { formatDate, formatDuration } from '../format.ts'
import HlsPlayer from '../components/HlsPlayer.tsx'
import VideoComments from '../components/VideoComments.tsx'

const POLL_INTERVAL_MS = 3000

export default function VideoDetailPage() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const navigate = useNavigate()
  const [loaded, setLoaded] = useState<Video | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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
  const owned = me?.id === video.owner.id

  async function handleDelete() {
    setDeleteError(null)
    try {
      await deleteVideo(id)
      navigate('/videos', { replace: true })
    } catch (e) {
      setDeleteError((e as Error).message)
    }
  }

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

      {editing ? (
        <EditVideoForm
          video={video}
          onSaved={(saved) => {
            setLoaded(saved)
            setEditing(false)
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <>
          <div className="title-row">
            <h1 className="video-title">{video.title}</h1>
            {owned && (
              <div className="owner-actions">
                <button type="button" className="button small" onClick={() => setEditing(true)}>
                  수정
                </button>
                <ConfirmButton
                  className="button small danger"
                  message="동영상을 삭제할까요? 댓글도 함께 삭제되며 되돌릴 수 없습니다."
                  confirmLabel="삭제"
                  onConfirm={handleDelete}
                >
                  삭제
                </ConfirmButton>
              </div>
            )}
          </div>
          {deleteError && <p className="hint error">{deleteError}</p>}
          <p className="meta">
            <span className="owner-name">{video.owner.nickname}</span> · {formatDate(video.createdAt)}
            {video.durationSeconds != null && ` · ${formatDuration(video.durationSeconds)}`}
          </p>
          {video.description && <p className="description">{video.description}</p>}
        </>
      )}

      <VideoComments key={video.id} videoId={video.id} />
    </article>
  )
}

/** Owner만 제목과 설명을 바꿀 수 있다. 영상 파일은 바꿀 수 없다. */
function EditVideoForm({
  video,
  onSaved,
  onCancel,
}: {
  video: Video
  onSaved(video: Video): void
  onCancel(): void
}) {
  const [title, setTitle] = useState(video.title)
  const [description, setDescription] = useState(video.description ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(await editVideo(video.id, { title: title.trim(), description }))
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <form className="edit-form" onSubmit={handleSubmit}>
      <label className="field">
        <span className="label">제목</span>
        <input
          type="text"
          className="text-input"
          maxLength={TITLE_MAX_LENGTH}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </label>
      <label className="field">
        <span className="label">설명</span>
        <textarea
          className="text-input"
          rows={5}
          maxLength={DESCRIPTION_MAX_LENGTH}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>
      {error && <p className="hint error">{error}</p>}
      <div className="owner-actions">
        <button type="button" className="button" onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="button primary" disabled={busy || !title.trim()}>
          저장
        </button>
      </div>
    </form>
  )
}
