import { Link, NavLink, Route, Routes } from 'react-router'
import VideoListPage from './pages/VideoListPage.tsx'
import UploadPage from './pages/UploadPage.tsx'
import VideoDetailPage from './pages/VideoDetailPage.tsx'
import LiveListPage from './pages/LiveListPage.tsx'
import LiveWatchPage from './pages/LiveWatchPage.tsx'
import BroadcastPage from './pages/BroadcastPage.tsx'

export default function App() {
  return (
    <>
      <header className="app-header">
        <nav className="nav">
          <Link to="/" className="logo">
            Web Broadcast
          </Link>
          <NavLink to="/" end className="nav-link">
            라이브
          </NavLink>
          <NavLink to="/videos" className="nav-link">
            동영상
          </NavLink>
        </nav>
        <div className="header-actions">
          <Link to="/broadcast" className="button primary">
            방송하기
          </Link>
          <Link to="/upload" className="button">
            업로드
          </Link>
        </div>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<LiveListPage />} />
          <Route path="/live/:id" element={<LiveWatchPage />} />
          <Route path="/broadcast" element={<BroadcastPage />} />
          <Route path="/videos" element={<VideoListPage />} />
          <Route path="/videos/:id" element={<VideoDetailPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="*" element={<p className="notice">페이지를 찾을 수 없습니다.</p>} />
        </Routes>
      </main>
    </>
  )
}
