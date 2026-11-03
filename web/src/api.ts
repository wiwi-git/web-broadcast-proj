export type VideoStatus = 'PROCESSING' | 'PUBLISHED' | 'FAILED'

export interface Video {
  id: string
  title: string
  description: string | null
  status: VideoStatus
  durationSeconds: number | null
  createdAt: string
  /** 공개 상태일 때만 존재 */
  playbackUrl: string | null
  thumbnailUrl: string | null
}

export interface VideoPage {
  items: Video[]
  page: number
  totalPages: number
  totalItems: number
}

// 서버(Video, VideoService)와 같은 제약
export const TITLE_MAX_LENGTH = 100
export const DESCRIPTION_MAX_LENGTH = 5000
export const MAX_FILE_BYTES = 2 * 1024 ** 3
export const ALLOWED_EXTENSIONS = ['mp4', 'mov', 'mkv', 'webm', 'avi']

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** 서버는 오류를 ProblemDetail로 보내며, detail이 사용자에게 보여줄 문구다. */
function errorFrom(status: number, body: string): ApiError {
  try {
    const detail = JSON.parse(body)?.detail
    if (typeof detail === 'string') return new ApiError(status, detail)
  } catch {
    // 본문이 JSON이 아니면 아래 기본 문구를 쓴다
  }
  return new ApiError(status, `요청에 실패했습니다. (${status})`)
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) throw errorFrom(response.status, await response.text())
  return response.status === 204 ? (undefined as T) : response.json()
}

const getJson = <T>(url: string) => request<T>(url)

export function fetchVideos(page: number): Promise<VideoPage> {
  return getJson(`/api/videos?page=${page}`)
}

export function fetchVideo(id: string): Promise<Video> {
  return getJson(`/api/videos/${encodeURIComponent(id)}`)
}

export interface UploadInput {
  file: File
  title: string
  description: string
}

/**
 * fetch는 업로드 진행률을 알려주지 않으므로 XMLHttpRequest를 쓴다.
 * onProgress에는 0~1 사이 값이 들어온다.
 */
export function uploadVideo(
  input: UploadInput,
  onProgress: (fraction: number) => void,
): Promise<Video> {
  const form = new FormData()
  form.append('file', input.file)
  form.append('title', input.title)
  form.append('description', input.description)

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/videos')
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total)
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve(JSON.parse(xhr.responseText))
      else reject(errorFrom(xhr.status, xhr.responseText))
    }
    xhr.onerror = () => reject(new ApiError(0, '네트워크 오류로 업로드에 실패했습니다.'))
    xhr.send(form)
  })
}

// 라이브

export type BroadcastStatus = 'PENDING' | 'LIVE' | 'ENDED'

export interface Broadcast {
  id: string
  title: string
  status: BroadcastStatus
  createdAt: string
  startedAt: string | null
  endedAt: string | null
  /** 방송 중일 때만 존재 */
  playbackUrl: string | null
}

export interface BroadcastList {
  live: Broadcast[]
  recentlyEnded: Broadcast[]
}

/** 시작 직후 Broadcaster에게만 오는 응답. streamKey는 다시 받을 수 없다. */
export interface StartedBroadcast {
  broadcast: Broadcast
  streamKey: string
  whipUrl: string
}

export const BROADCAST_TITLE_MAX_LENGTH = 100

export function fetchBroadcasts(): Promise<BroadcastList> {
  return getJson('/api/broadcasts')
}

export function fetchBroadcast(id: string): Promise<Broadcast> {
  return getJson(`/api/broadcasts/${encodeURIComponent(id)}`)
}

export function startBroadcast(title: string): Promise<StartedBroadcast> {
  return request('/api/broadcasts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
}

export function endBroadcast(id: string, streamKey: string): Promise<void> {
  return request(`/api/broadcasts/${encodeURIComponent(id)}/end`, {
    method: 'POST',
    headers: { 'X-Stream-Key': streamKey },
  })
}
