import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, BROADCAST_TITLE_MAX_LENGTH, endBroadcast, startBroadcast, type StartedBroadcast } from '../api.ts'
import { publish, type WhipSession } from '../live/whip.ts'
import ConfirmButton from '../components/ConfirmButton.tsx'
import DonationAlert from '../components/DonationAlert.tsx'
import LiveComments from '../components/LiveComments.tsx'
import { useBroadcast } from '../live/useBroadcast.ts'
import { useBroadcastDonations } from '../live/useBroadcastDonations.ts'
import { useElapsed } from '../live/useElapsed.ts'
import { useWakeLock } from '../live/useWakeLock.ts'

type Facing = 'user' | 'environment'
/** 송출할 대상. 화면 공유는 지원하는 기기(데스크톱)에서만 고를 수 있다 */
type Source = 'camera' | 'screen'

/**
 * - preview: 카메라 미리보기, 제목 입력
 * - starting: Broadcast를 만들고 미디어 서버에 연결하는 중
 * - on-air: 송출 중. 서버 상태가 준비 → 방송 중으로 바뀌는 것을 지켜본다
 * - ended: Broadcaster가 직접 종료함
 */
type Phase =
  | { kind: 'preview' }
  | { kind: 'starting' }
  | { kind: 'on-air'; id: string }
  | { kind: 'ended' }

const secureContext = window.isSecureContext && !!navigator.mediaDevices?.getUserMedia
const canShareScreen = secureContext && !!navigator.mediaDevices?.getDisplayMedia

function cameraErrorMessage(error: unknown): string {
  const name = (error as DOMException)?.name
  if (name === 'NotAllowedError') return '카메라와 마이크 권한을 허용해 주세요.'
  if (name === 'NotFoundError') return '사용할 수 있는 카메라나 마이크가 없습니다.'
  if (name === 'NotReadableError') return '다른 앱이 카메라를 사용 중입니다.'
  return '카메라를 열 수 없습니다.'
}

function screenErrorMessage(error: unknown): string {
  const name = (error as DOMException)?.name
  if (name === 'NotAllowedError') return '화면 공유를 취소했거나 권한이 없습니다. 다시 골라 주세요.'
  return '화면을 공유할 수 없습니다.'
}

/** 화면을 고른다. 소리는 마이크로 보내고, 마이크를 못 열면 화면 소리를 보낸다 */
async function openScreen(): Promise<MediaStream> {
  const screen = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: { ideal: 30 } }, audio: true })
  const mic = await navigator.mediaDevices
    .getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
    .catch(() => null)
  if (mic) screen.getAudioTracks().forEach((track) => track.stop())
  return new MediaStream([...screen.getVideoTracks(), ...(mic ?? screen).getAudioTracks()])
}

function stopTracks(stream: MediaStream) {
  stream.getTracks().forEach((track) => track.stop())
}

export default function BroadcastPage() {
  const videoRef = useRef<HTMLVideoElement>(null)
  const sessionRef = useRef<WhipSession | null>(null)
  const [facing, setFacing] = useState<Facing>('user')
  const [source, setSource] = useState<Source>('camera')
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [phase, setPhase] = useState<Phase>({ kind: 'preview' })
  const [error, setError] = useState<string | null>(null)
  /** 이미 진행 중인 내 방송이 있어 시작하지 못했을 때 그 방송 */
  const [blockingId, setBlockingId] = useState<string | null>(null)

  const { broadcast } = useBroadcast(phase.kind === 'on-air' ? phase.id : null)
  // 송출이 끊기면 서버가 먼저 종료로 바꾼다
  const droppedByServer = phase.kind === 'on-air' && broadcast?.status === 'ENDED'
  const isLive = phase.kind === 'on-air' && broadcast?.status === 'LIVE'
  const elapsed = useElapsed(isLive ? broadcast!.startedAt : null)
  const transmitting = (phase.kind === 'starting' || phase.kind === 'on-air') && !droppedByServer
  // 방송자도 휴대폰 화면에서 바로 후원을 볼 수 있게 미리보기 위에 띄운다
  const { donations, alert, dismissAlert } = useBroadcastDonations(phase.kind === 'on-air' ? phase.id : null, isLive)
  const cameraWanted = secureContext && phase.kind !== 'ended' && !droppedByServer

  useWakeLock(transmitting)

  // 카메라나 화면 열기. 전환하면 기존 트랙을 닫고 새로 연다
  useEffect(() => {
    if (!cameraWanted) return
    let cancelled = false
    let acquired: MediaStream | null = null
    const opening =
      source === 'screen'
        ? openScreen()
        : navigator.mediaDevices.getUserMedia({
            video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } },
            audio: { echoCancellation: true, noiseSuppression: true },
          })
    opening
      .then((opened) => {
        if (cancelled) return stopTracks(opened)
        acquired = opened
        setStream(opened)
        setCameraError(null)
      })
      .catch((e) => {
        if (!cancelled) setCameraError(source === 'screen' ? screenErrorMessage(e) : cameraErrorMessage(e))
      })
    return () => {
      cancelled = true
      if (acquired) stopTracks(acquired)
      setStream(null)
    }
  }, [facing, source, cameraWanted])

  useEffect(() => {
    if (videoRef.current) videoRef.current.srcObject = stream
  }, [stream])

  useEffect(() => {
    if (droppedByServer) {
      sessionRef.current?.close()
      sessionRef.current = null
    }
  }, [droppedByServer])

  // 페이지를 떠나면 송출을 끊는다. 서버는 끊김을 감지해 종료로 바꾼다
  useEffect(() => () => sessionRef.current?.close(), [])

  useEffect(() => {
    if (!transmitting) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [transmitting])

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    if (!stream || !title.trim()) return
    setError(null)
    setBlockingId(null)
    setPhase({ kind: 'starting' })
    let started: StartedBroadcast | null = null
    try {
      started = await startBroadcast(title.trim())
      const session = await publish(`${started.whipUrl}?key=${encodeURIComponent(started.streamKey)}`, stream)
      sessionRef.current = session
      session.connection.addEventListener('connectionstatechange', () => {
        if (session.connection.connectionState === 'failed') setError('송출 연결이 끊겼습니다.')
      })
      // 공유하던 화면을 브라우저·시스템에서 멈추면 더 보낼 영상이 없다
      stream.getVideoTracks()[0]?.addEventListener('ended', () => setError('화면 공유를 멈춰 영상이 나가지 않습니다.'))
      setPhase({ kind: 'on-air', id: started.broadcast.id })
    } catch (e) {
      // 만들어 둔 Broadcast는 바로 끝낸다. 실패해도 준비 시간이 지나면 서버가 종료한다
      if (started) endBroadcast(started.broadcast.id).catch(() => {})
      const active = e instanceof ApiError ? e.problem.activeBroadcastId : null
      setBlockingId(typeof active === 'string' ? active : null)
      setError((e as Error).message)
      setPhase({ kind: 'preview' })
    }
  }

  /** 다른 탭이나 기기에서 진행 중이던 내 방송을 끝낸다 */
  async function endBlocking() {
    if (!blockingId) return
    try {
      await endBroadcast(blockingId)
      setBlockingId(null)
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function handleEnd() {
    if (phase.kind !== 'on-air') return
    sessionRef.current?.close()
    sessionRef.current = null
    try {
      await endBroadcast(phase.id)
    } catch {
      // 송출은 이미 끊었으므로 서버가 곧 종료로 처리한다
    }
    setPhase({ kind: 'ended' })
  }

  function restart() {
    setTitle('')
    setError(null)
    setPhase({ kind: 'preview' })
  }

  if (!secureContext) {
    return <p className="notice error">카메라를 쓰려면 HTTPS 주소로 접속해야 합니다.</p>
  }

  if (phase.kind === 'ended' || droppedByServer) {
    return (
      <div className="notice">
        <p>{droppedByServer ? '송출이 끊겼거나 다른 화면에서 종료해 방송이 끝났습니다.' : '방송을 종료했습니다.'}</p>
        <div className="actions">
          <button className="button primary" onClick={restart}>
            새 방송 시작
          </button>
          <Link to="/" className="button">
            라이브 목록
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="broadcaster">
      <div className="camera">
        <video
          ref={videoRef}
          className={source === 'camera' && facing === 'user' ? 'mirrored' : undefined}
          autoPlay
          muted
          playsInline
        />
        {cameraError && <p className="camera-message error">{cameraError}</p>}
        {transmitting && (
          <span className={isLive ? 'badge live camera-badge' : 'badge camera-badge'}>
            {isLive ? `LIVE ${elapsed}` : '연결 중…'}
          </span>
        )}
        {isLive && alert && <DonationAlert key={alert.id} donation={alert} onDone={dismissAlert} />}
        {phase.kind === 'preview' && source === 'camera' && (
          <button
            type="button"
            className="button camera-switch"
            onClick={() => setFacing(facing === 'user' ? 'environment' : 'user')}
          >
            카메라 전환
          </button>
        )}
      </div>

      {phase.kind === 'on-air' ? (
        <>
          <div className="broadcaster-controls">
            <p className="broadcaster-title">{title.trim()}</p>
            {error && <p className="hint error">{error}</p>}
            <ConfirmButton className="button danger" message="방송을 종료할까요?" confirmLabel="종료" onConfirm={handleEnd}>
              방송 종료
            </ConfirmButton>
          </div>
          {/* 방송하면서 시청자 댓글과 후원을 본다 */}
          <LiveComments key={phase.id} broadcastId={phase.id} active donations={donations} />
        </>
      ) : (
        <form className="broadcaster-controls" onSubmit={handleStart}>
          {canShareScreen && (
            <div className="source-switch" role="group" aria-label="송출할 대상">
              {(['camera', 'screen'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  className={source === option ? 'button primary' : 'button'}
                  aria-pressed={source === option}
                  disabled={phase.kind === 'starting'}
                  onClick={() => setSource(option)}
                >
                  {option === 'camera' ? '카메라' : '화면'}
                </button>
              ))}
            </div>
          )}
          <input
            type="text"
            className="text-input"
            value={title}
            maxLength={BROADCAST_TITLE_MAX_LENGTH}
            disabled={phase.kind === 'starting'}
            placeholder="방송 제목"
            onChange={(e) => setTitle(e.target.value)}
          />
          {error && <p className="hint error">{error}</p>}
          {blockingId && (
            <ConfirmButton
              message="진행 중인 이전 방송을 종료할까요?"
              confirmLabel="종료"
              onConfirm={endBlocking}
            >
              이전 방송 종료
            </ConfirmButton>
          )}
          <button
            type="submit"
            className="button primary"
            disabled={!stream || !title.trim() || phase.kind === 'starting'}
          >
            {phase.kind === 'starting' ? '연결 중…' : '방송 시작'}
          </button>
        </form>
      )}
    </div>
  )
}
