import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { fetchBroadcasts, fetchVideos, type Broadcast, type Video } from '../api.ts'
import { formatDate, formatDuration } from '../format.ts'
import { CameraIcon, DownloadIcon, HeartIcon, LiveIcon, VideoIcon } from '../components/Icons.tsx'
import { BroadcastCard } from './LiveListPage.tsx'
import './LandingPage.css'

/** 랜딩의 "지금 방송 중"에 보여 줄 카드 수 */
const MAX_CARDS = 4
const REFRESH_INTERVAL_MS = 10_000
/** App.tsx와 같다. 운영 서버의 Caddy가 내주므로 개발 서버에서도 운영 주소로 보낸다 */
const APP_DOWNLOAD_URL = 'https://live.duse.dev/download/'
const GITHUB_URL = 'https://github.com/wiwi-git/web-broadcast-proj'

/** 랜딩에 필요한 방송·동영상. 불러오기 전이면 null, 실패하면 error만 채운다 */
interface Showcase {
  live: Broadcast[] | null
  videos: Video[] | null
  error: boolean
}

/** 방송 중인 방송을 주기적으로 읽고, 방송이 하나도 없을 때만 최근 동영상을 읽는다 */
function useShowcase(): Showcase {
  const [live, setLive] = useState<Broadcast[] | null>(null)
  const [videos, setVideos] = useState<Video[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    const load = () =>
      fetchBroadcasts()
        .then((data) => {
          if (cancelled) return
          setLive(data.live)
          setError(false)
        })
        .catch(() => {
          if (!cancelled) setError(true)
        })
    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  const noLive = live !== null && live.length === 0
  useEffect(() => {
    if (!noLive || videos !== null) return
    let cancelled = false
    fetchVideos(0)
      .then((page) => {
        if (!cancelled) setVideos(page.items)
      })
      .catch(() => {
        if (!cancelled) setVideos([])
      })
    return () => {
      cancelled = true
    }
  }, [noLive, videos])

  return { live, videos, error }
}

export default function LandingPage() {
  const showcase = useShowcase()

  return (
    <div className="landing">
      <Hero live={showcase.live} />
      <NowLive showcase={showcase} />
      <Features />
      <DesktopApp />
      <footer className="landing-footer">
        <span>Web Broadcast</span>
        <a href={GITHUB_URL} target="_blank" rel="noreferrer">
          GitHub 저장소
        </a>
      </footer>
    </div>
  )
}

function Hero({ live }: { live: Broadcast[] | null }) {
  return (
    <section className="landing-hero" aria-labelledby="landing-title">
      <LiveCount live={live} />
      <h1 id="landing-title" className="landing-title">
        휴대폰 하나로
        <br />
        바로 시작하는 라이브 방송
      </h1>
      <p className="landing-lead">
        앱 설치 없이 브라우저에서 방송을 켜고, 보고, 댓글과 후원으로 함께해요. 녹화한 동영상도
        올려 둘 수 있어요.
      </p>
      <div className="landing-actions">
        <Link to="/live" className="button primary landing-cta">
          <LiveIcon />
          지금 라이브 보기
        </Link>
        <Link to="/broadcast" className="button landing-cta">
          <CameraIcon />
          방송 시작하기
        </Link>
      </div>
      <p className="landing-note">시청은 로그인 없이 누구나 할 수 있어요.</p>
    </section>
  )
}

function LiveCount({ live }: { live: Broadcast[] | null }) {
  // 불러오기 전이나 실패했을 때는 숫자를 지어내지 않고 자리를 비워 둔다
  if (live === null) return <p className="live-count" aria-hidden="true">&nbsp;</p>
  if (live.length === 0) return <p className="live-count">지금은 방송이 없어요</p>
  return (
    <p className="live-count on-air">
      <span className="live-dot" aria-hidden="true" />
      지금 {live.length}개 방송 중
    </p>
  )
}

function NowLive({ showcase }: { showcase: Showcase }) {
  const { live, videos, error } = showcase
  let body: ReactNode
  if (live === null) {
    body = <p className="landing-empty">{error ? '방송 목록을 불러오지 못했어요.' : '불러오는 중…'}</p>
  } else if (live.length > 0) {
    body = (
      <ul className="video-grid">
        {live.slice(0, MAX_CARDS).map((broadcast) => (
          <li key={broadcast.id}>
            <BroadcastCard broadcast={broadcast} />
          </li>
        ))}
      </ul>
    )
  } else {
    body = (
      <>
        <p className="landing-empty">
          지금은 방송이 없어요. <Link to="/broadcast">첫 방송을 시작</Link>하거나 최근 동영상을
          둘러보세요.
        </p>
        {videos && videos.length > 0 && (
          <ul className="video-grid">
            {videos.slice(0, MAX_CARDS).map((video) => (
              <li key={video.id}>
                <VideoCard video={video} />
              </li>
            ))}
          </ul>
        )}
      </>
    )
  }

  const showingVideos = live !== null && live.length === 0
  return (
    <section className="landing-section" aria-labelledby="now-live-title">
      <div className="landing-section-head">
        <h2 id="now-live-title">{showingVideos ? '최근 동영상' : '지금 방송 중'}</h2>
        <Link to={showingVideos ? '/videos' : '/live'} className="landing-more">
          전체 보기
        </Link>
      </div>
      {body}
    </section>
  )
}

function VideoCard({ video }: { video: Video }) {
  return (
    <Link to={`/videos/${video.id}`} className="video-card">
      <div className="thumbnail">
        {video.thumbnailUrl && <img src={video.thumbnailUrl} alt="" loading="lazy" />}
        {video.durationSeconds != null && (
          <span className="duration">{formatDuration(video.durationSeconds)}</span>
        )}
      </div>
      <h3 className="video-card-title">{video.title}</h3>
      <p className="meta">{video.owner.nickname}</p>
      <p className="meta">{formatDate(video.createdAt)}</p>
    </Link>
  )
}

const FEATURES = [
  {
    icon: <CameraIcon />,
    title: '휴대폰 카메라로 바로 방송',
    body: '방송 제목만 정하면 휴대폰이나 PC 카메라로 곧바로 송출해요. 시청자는 링크 하나로 들어와요.',
  },
  {
    icon: <VideoIcon />,
    title: '동영상 올리고 보기',
    body: '녹화해 둔 영상을 올리면 휴대폰, PC, 데스크톱 앱 어디서나 볼 수 있게 변환해 드려요.',
  },
  {
    icon: <HeartIcon />,
    title: '방송자에게 후원',
    body: '방송 중인 방송자에게 메시지와 함께 후원하면 방송 화면에 바로 알림이 떠요.',
    note: '테스트 결제라 실제로 돈이 나가지 않아요.',
  },
]

function Features() {
  return (
    <section className="landing-section" aria-labelledby="features-title">
      <h2 id="features-title">이런 걸 할 수 있어요</h2>
      <ul className="feature-grid">
        {FEATURES.map((feature) => (
          <li key={feature.title} className="feature-card">
            <span className="feature-icon" aria-hidden="true">
              {feature.icon}
            </span>
            <h3>{feature.title}</h3>
            <p>{feature.body}</p>
            {feature.note && <p className="feature-note">{feature.note}</p>}
          </li>
        ))}
      </ul>
    </section>
  )
}

function DesktopApp() {
  return (
    <section className="landing-section landing-app" aria-labelledby="app-title">
      <div>
        <h2 id="app-title">데스크톱 앱으로도 볼 수 있어요</h2>
        <p>Windows와 macOS 앱에서 라이브와 동영상을 바로 시청하세요.</p>
      </div>
      <a href={APP_DOWNLOAD_URL} className="button landing-cta">
        <DownloadIcon />
        앱 다운로드
      </a>
    </section>
  )
}
