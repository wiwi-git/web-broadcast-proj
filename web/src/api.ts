/** 다른 사람에게 보이는 회원 정보 */
export interface UserSummary {
  id: string
  nickname: string
}

export type VideoStatus = 'PROCESSING' | 'PUBLISHED' | 'FAILED'

export interface Video {
  id: string
  title: string
  description: string | null
  status: VideoStatus
  owner: UserSummary
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
export const MAX_FILE_BYTES = 500 * 1024 ** 2
export const ALLOWED_EXTENSIONS = ['mp4', 'mov', 'mkv', 'webm', 'avi']

export class ApiError extends Error {
  readonly status: number
  /** ProblemDetail의 나머지 필드 (예: activeBroadcastId) */
  readonly problem: Record<string, unknown>

  constructor(status: number, message: string, problem: Record<string, unknown> = {}) {
    super(message)
    this.status = status
    this.problem = problem
  }
}

/** 서버는 오류를 ProblemDetail로 보내며, detail이 사용자에게 보여줄 문구다. */
function errorFrom(status: number, body: string): ApiError {
  try {
    const problem = JSON.parse(body)
    if (typeof problem?.detail === 'string') return new ApiError(status, problem.detail, problem)
  } catch {
    // 본문이 JSON이 아니면 아래 기본 문구를 쓴다
  }
  return new ApiError(status, `요청에 실패했습니다. (${status})`)
}

/**
 * 로그인은 세션 쿠키로 유지된다 (ADR-0003). 상태를 바꾸는 요청에는
 * 서버가 XSRF-TOKEN 쿠키로 준 CSRF 토큰을 X-XSRF-TOKEN 헤더로 돌려보낸다.
 */
function csrfHeaders(): Record<string, string> {
  const token = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length)
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET'
  const headers = method === 'GET' ? init.headers : { ...init.headers, ...csrfHeaders() }
  const response = await fetch(url, { ...init, headers })
  if (!response.ok) throw errorFrom(response.status, await response.text())
  return response.status === 204 ? (undefined as T) : response.json()
}

const getJson = <T>(url: string) => request<T>(url)

function sendJson<T>(method: string, url: string, body?: unknown): Promise<T> {
  return request<T>(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

// 회원

export interface Me {
  id: string
  username: string
  nickname: string
  /** 운영자면 신고 관리 화면을 쓸 수 있다 */
  admin: boolean
}

// 서버(UserService)와 같은 제약
export const USERNAME_PATTERN = /^[a-z0-9]{4,20}$/
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64
export const NICKNAME_MIN_LENGTH = 2
export const NICKNAME_MAX_LENGTH = 20

/** 로그인하지 않았으면 null. 이 요청으로 CSRF 토큰 쿠키도 받는다. */
export async function fetchMe(): Promise<Me | null> {
  return (await getJson<{ user: Me | null }>('/api/auth/me')).user
}

/** agreeTerms: 만 14세 이상이며 이용약관·개인정보처리방침에 동의함. false면 서버가 거절한다 */
export async function signUp(input: { username: string; password: string; nickname: string; agreeTerms: boolean }): Promise<Me> {
  return (await sendJson<{ user: Me }>('POST', '/api/auth/signup', input)).user
}

export async function logIn(input: { username: string; password: string }): Promise<Me> {
  return (await sendJson<{ user: Me }>('POST', '/api/auth/login', input)).user
}

export function logOut(): Promise<void> {
  return request('/api/auth/logout', { method: 'POST' })
}

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
    Object.entries(csrfHeaders()).forEach(([name, value]) => xhr.setRequestHeader(name, value))
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

export function editVideo(id: string, input: { title: string; description: string }): Promise<Video> {
  return sendJson('PATCH', `/api/videos/${encodeURIComponent(id)}`, input)
}

export function deleteVideo(id: string): Promise<void> {
  return request(`/api/videos/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// 라이브

export type BroadcastStatus = 'PENDING' | 'LIVE' | 'ENDED'

export interface Broadcast {
  id: string
  title: string
  status: BroadcastStatus
  owner: UserSummary
  createdAt: string
  startedAt: string | null
  endedAt: string | null
  /** 방송 중일 때만 존재 */
  playbackUrl: string | null
  /** 방송 중일 때만 존재. WebRTC(WHEP) 시청 주소 (ADR-0006) */
  webrtcUrl: string | null
  /** Owner에게만, 유튜브 동시 송출을 켠 방송일 때만 존재 (ADR-0007) */
  simulcastStatus?: SimulcastStatus | null
}

/** 유튜브 동시 송출 중계가 마지막으로 알린 상태 */
export type SimulcastStatus = 'CONNECTING' | 'LIVE' | 'FAILED'

/** 내 YouTube Key와 동시 송출 기본값. 키는 끝 네 자리만 온다. available이 false면 서버에 동시 송출 설정이 없다 */
export interface SimulcastSetting {
  available: boolean
  youtubeKeyLast4: string | null
  onByDefault: boolean
}

export interface BroadcastList {
  live: Broadcast[]
  recentlyEnded: Broadcast[]
}

/** 시작 직후 Owner에게만 오는 응답. streamKey는 서버에 남지 않으므로 다시 필요하면 새로 발급받는다. */
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

/** simulcast면 내 유튜브 채널로도 내보낸다 (ADR-0007) */
export function startBroadcast(title: string, simulcast = false): Promise<StartedBroadcast> {
  return sendJson('POST', '/api/broadcasts', { title, simulcast })
}

export function fetchSimulcast(): Promise<SimulcastSetting> {
  return getJson('/api/simulcast')
}

export function saveYouTubeKey(key: string): Promise<SimulcastSetting> {
  return sendJson('PUT', '/api/simulcast/youtube-key', { key })
}

export function deleteYouTubeKey(): Promise<void> {
  return request('/api/simulcast/youtube-key', { method: 'DELETE' })
}

export function renameBroadcast(id: string, title: string): Promise<Broadcast> {
  return sendJson('PATCH', `/api/broadcasts/${encodeURIComponent(id)}`, { title })
}

export function endBroadcast(id: string): Promise<void> {
  return request(`/api/broadcasts/${encodeURIComponent(id)}/end`, { method: 'POST' })
}

export function deleteBroadcast(id: string): Promise<void> {
  return request(`/api/broadcasts/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// 댓글

export interface Comment {
  id: string
  body: string
  author: UserSummary
  createdAt: string
  /** 수정한 적이 있을 때만 존재 */
  updatedAt: string | null
}

export interface VideoComments {
  items: Comment[]
  /** 다음 요청의 before로 넘긴다. null이면 더 없다 */
  nextCursor: string | null
  totalCount: number
}

export const COMMENT_MAX_LENGTH = 500

export type CommentTarget = { kind: 'video' | 'broadcast'; id: string }

const commentsUrl = (target: CommentTarget) =>
  `/api/${target.kind === 'video' ? 'videos' : 'broadcasts'}/${encodeURIComponent(target.id)}/comments`

/** 최신순 20개씩 */
export function fetchVideoComments(videoId: string, before?: string): Promise<VideoComments> {
  const query = before ? `?before=${encodeURIComponent(before)}` : ''
  return getJson(commentsUrl({ kind: 'video', id: videoId }) + query)
}

/** 최근 100개, 오래된 순 */
export function fetchBroadcastComments(broadcastId: string): Promise<Comment[]> {
  return getJson(commentsUrl({ kind: 'broadcast', id: broadcastId }))
}

/** 운영자가 숨긴 동영상 댓글의 ID들. 열어 둔 화면이 주기적으로 받아 화면에서 뺀다 */
export function fetchHiddenVideoComments(videoId: string): Promise<string[]> {
  return getJson(`/api/videos/${encodeURIComponent(videoId)}/comments/hidden`)
}

export function writeComment(target: CommentTarget, body: string): Promise<Comment> {
  return sendJson('POST', commentsUrl(target), { body })
}

export function editComment(id: string, body: string): Promise<Comment> {
  return sendJson('PATCH', `/api/comments/${encodeURIComponent(id)}`, { body })
}

export function deleteComment(id: string): Promise<void> {
  return request(`/api/comments/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// 신고

export type ReportTargetType = 'BROADCAST' | 'VIDEO' | 'COMMENT'
export type ReportReason = 'SPAM' | 'HATE' | 'SEXUAL' | 'VIOLENCE' | 'COPYRIGHT' | 'OTHER'

/** 화면에 보여 주는 순서와 문구 */
export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'HATE', label: '욕설·혐오' },
  { value: 'SEXUAL', label: '성적인 콘텐츠' },
  { value: 'VIOLENCE', label: '폭력·위험한 행동' },
  { value: 'COPYRIGHT', label: '저작권 침해' },
  { value: 'OTHER', label: '기타' },
]

// 서버(Report)와 같은 제약
export const REPORT_DETAIL_MAX_LENGTH = 200

/** 같은 대상을 다시 신고하면 409로 거절된다 */
export function fileReport(input: {
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  detail: string
}): Promise<{ id: string }> {
  return sendJson('POST', '/api/reports', input)
}

// 후원

export type DonationStatus = 'PENDING' | 'DONE' | 'FAILED' | 'CANCELED' | 'EXPIRED'

/** 방송 화면에 공개되는 완료된 후원 */
export interface PublicDonation {
  id: string
  donor: UserSummary
  amount: number
  message: string | null
  completedAt: string
}

/** 후원한 방송이 삭제되었으면 id가 null이고 제목만 남는다 */
export interface BroadcastRef {
  id: string | null
  title: string
}

/** 후원자 본인에게 보이는 후원 */
export interface SentDonation {
  id: string
  recipient: UserSummary
  broadcast: BroadcastRef
  amount: number
  message: string | null
  status: DonationStatus
  /** 실패했을 때만 존재 */
  failureMessage: string | null
  createdAt: string
  completedAt: string | null
}

export interface ReceivedDonation {
  id: string
  donor: UserSummary
  broadcast: BroadcastRef
  amount: number
  message: string | null
  completedAt: string
}

export interface ReceivedDonations {
  items: ReceivedDonation[]
  totalCount: number
  totalAmount: number
}

/** 결제위젯으로 결제를 요청할 때 쓰는 값. orderId는 후원의 id다 */
export interface DonationCheckout {
  donation: SentDonation
  orderId: string
  orderName: string
  customerKey: string
}

// 서버(Donation)와 같은 제약
export const DONATION_MIN_AMOUNT = 1_000
export const DONATION_MAX_AMOUNT = 500_000
export const DONATION_AMOUNT_UNIT = 1_000
export const DONATION_PRESETS = [1_000, 5_000, 10_000, 50_000]
export const DONATION_MESSAGE_MAX_LENGTH = 100

/** 서버에 토스페이먼츠 키가 설정되지 않았으면 null */
export async function fetchPaymentClientKey(): Promise<string | null> {
  return (await getJson<{ clientKey: string | null }>('/api/payments/config')).clientKey
}

/** 최근 완료된 후원 100개, 오래된 순 */
export function fetchBroadcastDonations(broadcastId: string): Promise<PublicDonation[]> {
  return getJson(`/api/broadcasts/${encodeURIComponent(broadcastId)}/donations`)
}

export function startDonation(broadcastId: string, amount: number, message: string): Promise<DonationCheckout> {
  return sendJson('POST', `/api/broadcasts/${encodeURIComponent(broadcastId)}/donations`, { amount, message })
}

/** 결과를 아직 모르면 503 ApiError. 같은 요청을 다시 보내면 된다 */
export function approveDonation(id: string, paymentKey: string, amount: number): Promise<SentDonation> {
  return sendJson('POST', `/api/donations/${encodeURIComponent(id)}/approve`, { paymentKey, amount })
}

/** 결제창을 닫았거나 결제 인증에 실패했을 때 결제 서비스가 준 code, message를 그대로 넘긴다 */
export function abandonDonation(id: string, code: string | null, message: string | null): Promise<SentDonation> {
  return sendJson('POST', `/api/donations/${encodeURIComponent(id)}/abandon`, { code, message })
}

export function fetchSentDonations(): Promise<SentDonation[]> {
  return getJson('/api/donations/sent')
}

export function fetchReceivedDonations(): Promise<ReceivedDonations> {
  return getJson('/api/donations/received')
}

// 앱의 브라우저 결제 (ADR-0005). 로그인 없이 앱이 넘긴 결제 토큰으로 Donation 하나를 결제한다

export interface CheckoutView {
  donation: SentDonation
  orderId: string
  orderName: string
  customerKey: string
  clientKey: string
}

const checkoutUrl = (token: string) => `/api/checkout/${encodeURIComponent(token)}`

/** 토큰이 없거나 결제 화면을 열 시간이 지났으면 404, 이미 결과가 정해졌으면 409(problem.status에 현재 상태) */
export function fetchCheckout(token: string): Promise<CheckoutView> {
  return getJson(checkoutUrl(token))
}

/** 결과를 아직 모르면 503 ApiError. 같은 요청을 다시 보내면 된다 */
export function approveCheckout(token: string, paymentKey: string, amount: number): Promise<SentDonation> {
  return sendJson('POST', `${checkoutUrl(token)}/approve`, { paymentKey, amount })
}

export function abandonCheckout(token: string, code: string | null, message: string | null): Promise<SentDonation> {
  return sendJson('POST', `${checkoutUrl(token)}/abandon`, { code, message })
}

// 운영자 (role이 ADMIN인 User만)

export interface ReportItem {
  reporterNickname: string
  reason: ReportReason
  detail: string | null
  createdAt: string
}

/** 대상 하나에 걸린 처리 전 신고. 대상이 지워졌으면 summary·parent·owner가 null이다 */
export interface ReportGroup {
  targetType: ReportTargetType
  targetId: string
  /** 방송·동영상 제목이나 댓글 본문 */
  summary: string | null
  /** 화면에서 열 Content. 댓글이면 달린 방송이나 동영상이다 */
  parentType: 'BROADCAST' | 'VIDEO' | null
  parentId: string | null
  owner: { id: string; nickname: string; suspended: boolean } | null
  hidden: boolean
  reportCount: number
  reasons: Partial<Record<ReportReason, number>>
  reports: ReportItem[]
}

export function fetchOpenReports(): Promise<ReportGroup[]> {
  return getJson('/api/admin/reports')
}

/** 대상을 숨기고 걸린 신고를 닫는다. 방송은 진행 중이면 끝난다 */
export function hideReported(targetType: ReportTargetType, targetId: string): Promise<void> {
  return request(`/api/admin/reports/${targetType}/${encodeURIComponent(targetId)}/hide`, { method: 'POST' })
}

/** 문제없다고 보고 신고만 닫는다 */
export function dismissReports(targetType: ReportTargetType, targetId: string): Promise<void> {
  return request(`/api/admin/reports/${targetType}/${encodeURIComponent(targetId)}/dismiss`, { method: 'POST' })
}

/** 로그인을 막고 세션을 끊고 진행 중인 방송을 끝낸다 */
export function suspendUser(userId: string): Promise<void> {
  return request(`/api/admin/users/${encodeURIComponent(userId)}/suspend`, { method: 'POST' })
}

export function unsuspendUser(userId: string): Promise<void> {
  return request(`/api/admin/users/${encodeURIComponent(userId)}/unsuspend`, { method: 'POST' })
}

/** 운영자 회원 목록의 한 사람. 아이디는 운영자에게만 보인다 */
export interface AdminUser {
  id: string
  username: string
  nickname: string
  admin: boolean
  suspended: boolean
  suspendedAt: string | null
  createdAt: string
}

export interface AdminUserPage {
  items: AdminUser[]
  page: number
  totalPages: number
  totalItems: number
}

/** query는 아이디나 닉네임 일부. suspended가 undefined면 상태와 상관없이. 최근 가입한 순 20명씩 */
export function fetchAdminUsers(input: { query: string; suspended?: boolean; page: number }): Promise<AdminUserPage> {
  const params = new URLSearchParams({ page: String(input.page) })
  if (input.query.trim()) params.set('q', input.query.trim())
  if (input.suspended !== undefined) params.set('suspended', String(input.suspended))
  return getJson(`/api/admin/users?${params}`)
}

/** 운영자가 내린 것 하나. parent는 화면에서 열 Content(댓글이면 달린 곳)다 */
export interface HiddenItem {
  type: ReportTargetType
  id: string
  /** 방송·동영상 제목이나 댓글 본문 */
  summary: string
  parentType: 'BROADCAST' | 'VIDEO'
  parentId: string
  owner: { id: string; nickname: string; suspended: boolean }
  createdAt: string
  hiddenAt: string
  /** 방송·동영상의 상태. 댓글이면 null */
  status: string | null
  /** 이것을 내리게 만든 신고 */
  reportCount: number
  reasons: Partial<Record<ReportReason, number>>
  reports: ReportItem[]
  /** 공개 상태까지 변환된 동영상만. 파일이 남아 있어 운영자는 재생할 수 있다 */
  playbackUrl: string | null
  thumbnailUrl: string | null
}

export interface HiddenPage {
  items: HiddenItem[]
  page: number
  totalPages: number
  totalItems: number
}

/** 최근에 내린 순 20개씩 */
export function fetchHidden(type: ReportTargetType, page: number): Promise<HiddenPage> {
  return getJson(`/api/admin/hidden?type=${type}&page=${page}`)
}
