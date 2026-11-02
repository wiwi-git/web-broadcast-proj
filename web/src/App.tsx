import { Link, Route, Routes } from 'react-router'
import VideoListPage from './pages/VideoListPage.tsx'
import UploadPage from './pages/UploadPage.tsx'
import VideoDetailPage from './pages/VideoDetailPage.tsx'

export default function App() {
  return (
    <>
      <header className="app-header">
        <Link to="/" className="logo">
          Web Broadcast
        </Link>
        <Link to="/upload" className="button primary">
          업로드
        </Link>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<VideoListPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/videos/:id" element={<VideoDetailPage />} />
          <Route path="*" element={<p className="notice">페이지를 찾을 수 없습니다.</p>} />
        </Routes>
      </main>
    </>
  )
}
