import { Link, NavLink, Route, Routes, useLocation } from 'react-router'
import VideoListPage from './pages/VideoListPage.tsx'
import UploadPage from './pages/UploadPage.tsx'
import VideoDetailPage from './pages/VideoDetailPage.tsx'
import LiveListPage from './pages/LiveListPage.tsx'
import LiveWatchPage from './pages/LiveWatchPage.tsx'
import BroadcastPage from './pages/BroadcastPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import SignUpPage from './pages/SignUpPage.tsx'
import DonatePage from './pages/DonatePage.tsx'
import { DonationFailPage, DonationSuccessPage } from './pages/DonationResultPage.tsx'
import DonationsPage from './pages/DonationsPage.tsx'
import RequireLogin from './auth/RequireLogin.tsx'
import { loginPath, useAuth } from './auth/useAuth.ts'
import { CameraIcon, HeartIcon, LiveIcon, LoginIcon, LogoutIcon, UploadIcon, VideoIcon } from './components/Icons.tsx'

export default function App() {
  return (
    <>
      <header className="app-header">
        <nav className="nav">
          <Link to="/" className="logo">
            Web Broadcast
          </Link>
          <NavLink to="/" end className="nav-link" title="라이브">
            <LiveIcon />
            <span className="nav-label">라이브</span>
          </NavLink>
          <NavLink to="/videos" className="nav-link" title="동영상">
            <VideoIcon />
            <span className="nav-label">동영상</span>
          </NavLink>
        </nav>
        <div className="header-actions">
          <Link to="/broadcast" className="button primary" title="방송하기">
            <CameraIcon />
            <span className="nav-label">방송하기</span>
          </Link>
          <Link to="/upload" className="button" title="업로드">
            <UploadIcon />
            <span className="nav-label">업로드</span>
          </Link>
          <AccountMenu />
        </div>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<LiveListPage />} />
          <Route path="/live/:id" element={<LiveWatchPage />} />
          <Route
            path="/live/:id/donate"
            element={
              <RequireLogin>
                <DonatePage />
              </RequireLogin>
            }
          />
          <Route
            path="/donations"
            element={
              <RequireLogin>
                <DonationsPage />
              </RequireLogin>
            }
          />
          {/* 결제 서비스가 결제 인증을 마치고 돌려보내는 곳 */}
          <Route
            path="/donations/success"
            element={
              <RequireLogin>
                <DonationSuccessPage />
              </RequireLogin>
            }
          />
          <Route
            path="/donations/fail"
            element={
              <RequireLogin>
                <DonationFailPage />
              </RequireLogin>
            }
          />
          <Route
            path="/broadcast"
            element={
              <RequireLogin>
                <BroadcastPage />
              </RequireLogin>
            }
          />
          <Route path="/videos" element={<VideoListPage />} />
          <Route path="/videos/:id" element={<VideoDetailPage />} />
          <Route
            path="/upload"
            element={
              <RequireLogin>
                <UploadPage />
              </RequireLogin>
            }
          />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignUpPage />} />
          <Route path="*" element={<p className="notice">페이지를 찾을 수 없습니다.</p>} />
        </Routes>
      </main>
    </>
  )
}

function AccountMenu() {
  const { me, ready, logOut } = useAuth()
  const location = useLocation()
  if (!ready) return null
  if (!me) {
    // 로그인·회원가입 화면에서는 돌아갈 곳을 그대로 둔다
    const onAuthPage = location.pathname === '/login' || location.pathname === '/signup'
    return (
      <Link
        to={onAuthPage ? '/login' + location.search : loginPath(location.pathname)}
        className="button"
        title="로그인"
      >
        <LoginIcon />
        <span className="nav-label">로그인</span>
      </Link>
    )
  }
  return (
    <div className="account">
      <span className="account-name">{me.nickname}</span>
      <Link to="/donations" className="button" title="후원 내역">
        <HeartIcon />
        <span className="nav-label">후원 내역</span>
      </Link>
      <button type="button" className="button" title="로그아웃" onClick={() => logOut()}>
        <LogoutIcon />
        <span className="nav-label">로그아웃</span>
      </button>
    </div>
  )
}
