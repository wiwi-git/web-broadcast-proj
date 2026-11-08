import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { BROADCAST_TITLE_MAX_LENGTH, deleteBroadcast, endBroadcast, renameBroadcast, type Broadcast } from '../api.ts'
import { loginPath, useAuth } from '../auth/useAuth.ts'
import ConfirmButton from '../components/ConfirmButton.tsx'
import DonationAlert from '../components/DonationAlert.tsx'
import HlsPlayer from '../components/HlsPlayer.tsx'
import LiveComments from '../components/LiveComments.tsx'
import { formatDate } from '../format.ts'
import { useBroadcast } from '../live/useBroadcast.ts'
import { useBroadcastDonations } from '../live/useBroadcastDonations.ts'
import { useElapsed } from '../live/useElapsed.ts'

export default function LiveWatchPage() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const { broadcast: polled, error } = useBroadcast(id)
  // Owner가 방금 바꾼 제목을 다음 조회 전까지 보여 준다
  const [renamed, setRenamed] = useState<Broadcast | null>(null)
  const broadcast = renamed && polled && renamed.id === polled.id && renamed.status === polled.status ? renamed : polled
  const elapsed = useElapsed(broadcast?.status === 'LIVE' ? broadcast.startedAt : null)
  const { donations, alert, dismissAlert } = useBroadcastDonations(
    broadcast?.id ?? null,
    !!broadcast && broadcast.status !== 'ENDED',
  )

  if (!broadcast) return <p className={error ? 'notice error' : 'notice'}>{error ?? '불러오는 중…'}</p>

  return (
    <div className="live-layout">
      <article className="live-main">
        <div className="stage">
        {broadcast.status === 'LIVE' && broadcast.playbackUrl ? (
          <HlsPlayer src={broadcast.playbackUrl} />
        ) : (
          <div className="player-placeholder">
            {broadcast.status === 'PENDING' ? (
              <>
                <div className="spinner" />
                <p>방송 준비 중입니다.</p>
              </>
            ) : (
              <p>방송이 종료되었습니다.</p>
            )}
          </div>
        )}
        {alert && broadcast.status === 'LIVE' && <DonationAlert key={alert.id} donation={alert} onDone={dismissAlert} />}
        </div>

        <div className="title-row">
          <h1 className="video-title">{broadcast.title}</h1>
          {broadcast.status === 'LIVE' && me?.id !== broadcast.owner.id && (
            <Link
              to={me ? `/live/${broadcast.id}/donate` : loginPath(`/live/${broadcast.id}/donate`)}
              className="button primary"
            >
              후원하기
            </Link>
          )}
        </div>
        <p className="meta">
          <span className="owner-name">{broadcast.owner.nickname}</span>
          {broadcast.status === 'LIVE' && (
            <>
              {' · '}
              <span className="badge live inline">LIVE</span> {elapsed} 방송 중
            </>
          )}
          {broadcast.status === 'ENDED' && broadcast.endedAt && ` · ${formatDate(broadcast.endedAt)} 종료`}
        </p>

        {me?.id === broadcast.owner.id && <OwnerControls broadcast={broadcast} onRenamed={setRenamed} />}
      </article>

      <LiveComments
        key={broadcast.id}
        broadcastId={broadcast.id}
        active={broadcast.status !== 'ENDED'}
        donations={donations}
      />
    </div>
  )
}

/** Owner에게만 보인다. 서버도 Owner인지 다시 확인한다. */
function OwnerControls({ broadcast, onRenamed }: { broadcast: Broadcast; onRenamed(b: Broadcast): void }) {
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(broadcast.title)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function handleRename(event: FormEvent) {
    event.preventDefault()
    await run(async () => {
      onRenamed(await renameBroadcast(broadcast.id, title.trim()))
      setEditing(false)
    })
  }

  return (
    <div className="owner-panel">
      {editing ? (
        <form className="owner-actions" onSubmit={handleRename}>
          <input
            type="text"
            className="text-input"
            maxLength={BROADCAST_TITLE_MAX_LENGTH}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            autoFocus
          />
          <button type="button" className="button small" onClick={() => setEditing(false)}>
            취소
          </button>
          <button type="submit" className="button primary small" disabled={busy || !title.trim()}>
            저장
          </button>
        </form>
      ) : (
        <div className="owner-actions">
          <button
            type="button"
            className="button small"
            onClick={() => {
              setTitle(broadcast.title)
              setEditing(true)
            }}
          >
            제목 수정
          </button>
          {broadcast.status !== 'ENDED' && (
            <ConfirmButton
              className="button small"
              disabled={busy}
              message="방송을 종료할까요?"
              confirmLabel="종료"
              onConfirm={() => run(() => endBroadcast(broadcast.id))}
            >
              방송 종료
            </ConfirmButton>
          )}
          <ConfirmButton
            className="button small danger"
            disabled={busy}
            message="방송을 삭제할까요? 댓글도 함께 삭제되며 되돌릴 수 없습니다. 받은 후원 기록은 남습니다."
            confirmLabel="삭제"
            onConfirm={() =>
              run(async () => {
                await deleteBroadcast(broadcast.id)
                navigate('/', { replace: true })
              })
            }
          >
            삭제
          </ConfirmButton>
        </div>
      )}
      {error && <p className="hint error">{error}</p>}
    </div>
  )
}
