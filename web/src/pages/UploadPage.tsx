import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.ts'
import {
  ALLOWED_EXTENSIONS,
  DESCRIPTION_MAX_LENGTH,
  MAX_FILE_BYTES,
  TITLE_MAX_LENGTH,
  uploadVideo,
} from '../api.ts'
import { formatBytes } from '../format.ts'

/** 서버로 보내기 전에 막을 수 있는 문제를 먼저 걸러낸다. */
function fileProblem(file: File): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return `지원하지 않는 형식입니다. (${ALLOWED_EXTENSIONS.join(', ')})`
  }
  if (file.size > MAX_FILE_BYTES) return '파일은 500MB 이하여야 합니다.'
  return null
}

export default function UploadPage() {
  const { me } = useAuth()
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const uploading = progress !== null
  const problem = file ? fileProblem(file) : null
  const canSubmit = file !== null && problem === null && title.trim() !== '' && !uploading

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!file || !canSubmit) return
    setError(null)
    setProgress(0)
    try {
      const video = await uploadVideo({ file, title: title.trim(), description }, setProgress)
      navigate(`/videos/${video.id}`)
    } catch (e) {
      setError((e as Error).message)
      setProgress(null)
    }
  }

  if (me && !me.emailVerified) {
    return (
      <div className="notice">
        <p>이메일 인증을 마친 회원만 동영상을 올릴 수 있어요.</p>
        <Link to="/account" className="button">
          내 계정에서 인증하기
        </Link>
      </div>
    )
  }

  return (
    <form className="upload-form" onSubmit={handleSubmit}>
      <h1 className="page-title">동영상 업로드</h1>

      <label className="field">
        <span className="label">영상 파일</span>
        <input
          type="file"
          accept={ALLOWED_EXTENSIONS.map((ext) => `.${ext}`).join(',')}
          disabled={uploading}
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <span className={problem ? 'hint error' : 'hint'}>
          {problem ?? (file ? formatBytes(file.size) : `${ALLOWED_EXTENSIONS.join(', ')} · 최대 500MB`)}
        </span>
      </label>

      <label className="field">
        <span className="label">제목</span>
        <input
          type="text"
          value={title}
          maxLength={TITLE_MAX_LENGTH}
          disabled={uploading}
          placeholder="동영상 제목"
          onChange={(e) => setTitle(e.target.value)}
        />
        <span className="hint counter">
          {title.length} / {TITLE_MAX_LENGTH}
        </span>
      </label>

      <label className="field">
        <span className="label">설명 (선택)</span>
        <textarea
          rows={6}
          value={description}
          maxLength={DESCRIPTION_MAX_LENGTH}
          disabled={uploading}
          placeholder="동영상에 대한 설명"
          onChange={(e) => setDescription(e.target.value)}
        />
        <span className="hint counter">
          {description.length} / {DESCRIPTION_MAX_LENGTH}
        </span>
      </label>

      {error && <p className="hint error">{error}</p>}

      {uploading ? (
        <div className="progress" role="progressbar" aria-valuenow={Math.round(progress * 100)}>
          <div className="progress-bar" style={{ width: `${progress * 100}%` }} />
          <span>{progress < 1 ? `업로드 중 ${Math.floor(progress * 100)}%` : '파일 확인 중…'}</span>
        </div>
      ) : (
        <button type="submit" className="button primary" disabled={!canSubmit}>
          업로드
        </button>
      )}
    </form>
  )
}
