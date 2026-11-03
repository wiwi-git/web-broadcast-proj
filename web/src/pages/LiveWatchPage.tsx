import { useParams } from 'react-router'
import HlsPlayer from '../components/HlsPlayer.tsx'
import { formatDate } from '../format.ts'
import { useBroadcast } from '../live/useBroadcast.ts'
import { useElapsed } from '../live/useElapsed.ts'

export default function LiveWatchPage() {
  const { id = '' } = useParams()
  const { broadcast, error } = useBroadcast(id)
  const elapsed = useElapsed(broadcast?.status === 'LIVE' ? broadcast.startedAt : null)

  if (!broadcast) return <p className={error ? 'notice error' : 'notice'}>{error ?? '불러오는 중…'}</p>

  return (
    <article className="video-detail">
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

      <h1 className="video-title">{broadcast.title}</h1>
      <p className="meta">
        {broadcast.status === 'LIVE' && (
          <>
            <span className="badge live inline">LIVE</span> {elapsed} 방송 중
          </>
        )}
        {broadcast.status === 'ENDED' && broadcast.endedAt && `${formatDate(broadcast.endedAt)} 종료`}
      </p>
    </article>
  )
}
