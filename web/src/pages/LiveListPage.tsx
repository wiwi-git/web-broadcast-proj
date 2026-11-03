import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { fetchBroadcasts, type Broadcast, type BroadcastList } from '../api.ts'
import { formatDate, formatRelative } from '../format.ts'

const REFRESH_INTERVAL_MS = 10_000

export default function LiveListPage() {
  const [list, setList] = useState<BroadcastList | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 새로 시작하거나 끝난 방송이 반영되도록 주기적으로 다시 읽는다
  useEffect(() => {
    let cancelled = false
    const load = () =>
      fetchBroadcasts()
        .then((data) => {
          if (cancelled) return
          setList(data)
          setError(null)
        })
        .catch((e: Error) => {
          if (!cancelled) setError(e.message)
        })
    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  if (!list) return <p className={error ? 'notice error' : 'notice'}>{error ?? '불러오는 중…'}</p>

  return (
    <>
      <h1 className="page-title">라이브</h1>
      {list.live.length === 0 ? (
        <div className="notice compact">
          <p>지금 방송 중인 방송이 없습니다.</p>
          <Link to="/broadcast" className="button primary">
            방송하기
          </Link>
        </div>
      ) : (
        <ul className="video-grid">
          {list.live.map((broadcast) => (
            <li key={broadcast.id}>
              <BroadcastCard broadcast={broadcast} />
            </li>
          ))}
        </ul>
      )}

      {list.recentlyEnded.length > 0 && (
        <>
          <h2 className="section-title">종료된 방송</h2>
          <ul className="video-grid">
            {list.recentlyEnded.map((broadcast) => (
              <li key={broadcast.id}>
                <BroadcastCard broadcast={broadcast} />
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}

function BroadcastCard({ broadcast }: { broadcast: Broadcast }) {
  const live = broadcast.status === 'LIVE'
  return (
    <Link to={`/live/${broadcast.id}`} className={live ? 'video-card' : 'video-card ended'}>
      <div className="thumbnail live-thumbnail">
        <span className={live ? 'badge live' : 'badge'}>{live ? 'LIVE' : '종료'}</span>
      </div>
      <h3 className="video-card-title">{broadcast.title}</h3>
      <p className="meta">
        {live && broadcast.startedAt
          ? `${formatRelative(broadcast.startedAt)} 시작`
          : broadcast.endedAt && `${formatDate(broadcast.endedAt)} 종료`}
      </p>
    </Link>
  )
}
