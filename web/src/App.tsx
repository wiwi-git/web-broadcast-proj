import { useEffect, useState } from 'react'
import { Link, NavLink, Route, Routes, useLocation } from 'react-router'
import VideoListPage from './pages/VideoListPage.tsx'
import UploadPage from './pages/UploadPage.tsx'
import VideoDetailPage from './pages/VideoDetailPage.tsx'
import LandingPage from './pages/LandingPage.tsx'
import LiveListPage from './pages/LiveListPage.tsx'
import LiveWatchPage from './pages/LiveWatchPage.tsx'
import BroadcastPage from './pages/BroadcastPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import SignUpPage from './pages/SignUpPage.tsx'
import DonatePage from './pages/DonatePage.tsx'
import { DonationFailPage, DonationSuccessPage } from './pages/DonationResultPage.tsx'
import DonationsPage from './pages/DonationsPage.tsx'
import CheckoutPage, { CheckoutFailPage, CheckoutSuccessPage } from './pages/CheckoutPage.tsx'
import RequireLogin from './auth/RequireLogin.tsx'
import { loginPath, useAuth } from './auth/useAuth.ts'
import {
  CameraIcon,
  CloseIcon,
  DownloadIcon,
  HeartIcon,
  LiveIcon,
  LoginIcon,
  LogoutIcon,
  UploadIcon,
  UserIcon,
  VideoIcon,
} from './components/Icons.tsx'

/** 데스크톱 앱 다운로드 페이지. 운영 서버의 Caddy가 내준다 (deploy/download/) */
const APP_DOWNLOAD_URL = 'https://live.duse.dev/download/'

export default function App() {
  return (
    <>
      <header className="app-header">
        <nav className="nav">
          <Link to="/" className="logo">
            Web Broadcast
          </Link>
          <NavLink to="/live" className="nav-link" title="라이브">
            <LiveIcon />
            <span className="nav-label">라이브</span>
          </NavLink>
          <NavLink to="/videos" className="nav-link" title="동영상">
            <VideoIcon />
            <span className="nav-label">동영상</span>
          </NavLink>
        </nav>
        <HeaderActions />
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/live" element={<LiveListPage />} />
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
          {/* 앱의 브라우저 결제 (ADR-0005). 로그인 없이 결제 토큰으로 결제한다 */}
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/checkout/success" element={<CheckoutSuccessPage />} />
          <Route path="/checkout/fail" element={<CheckoutFailPage />} />
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

/** 머리글 오른쪽. 로그인하지 않았으면 앱 다운로드와 로그인만, 로그인했으면 방송하기·업로드·앱 다운로드·닉네임을 보인다 */
function HeaderActions() {
  const { me, ready } = useAuth()
  const location = useLocation()
  if (!ready) return null

  const download = (
    <a href={APP_DOWNLOAD_URL} className="button download-button" title="앱 다운로드">
      <DownloadIcon />
      <span className="nav-label">앱 다운로드</span>
    </a>
  )

  if (!me) {
    // 로그인·회원가입 화면에서는 돌아갈 곳을 그대로 둔다
    const onAuthPage = location.pathname === '/login' || location.pathname === '/signup'
    return (
      <div className="header-actions">
        {download}
        <Link
          to={onAuthPage ? '/login' + location.search : loginPath(location.pathname)}
          className="button"
          title="로그인"
        >
          <LoginIcon />
          <span className="nav-label">로그인</span>
        </Link>
      </div>
    )
  }
  return (
    <div className="header-actions">
      <Link to="/broadcast" className="button primary" title="방송하기">
        <CameraIcon />
        <span className="nav-label">방송하기</span>
      </Link>
      <Link to="/upload" className="button" title="업로드">
        <UploadIcon />
        <span className="nav-label">업로드</span>
      </Link>
      {download}
      <AccountMenu nickname={me.nickname} />
    </div>
  )
}

/** 닉네임 버튼. 누르면 화면 오른쪽에서 후원 내역·로그아웃이 있는 패널이 열린다 */
function AccountMenu({ nickname }: { nickname: string }) {
  const { logOut } = useAuth()
  const location = useLocation()
  // 연 화면의 경로를 기억한다. 다른 화면으로 가면(후원 내역, 뒤로 가기) 저절로 닫힌다
  const [openedAt, setOpenedAt] = useState<string | null>(null)
  const open = openedAt === location.pathname
  const setOpen = (value: boolean) => setOpenedAt(value ? location.pathname : null)

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenedAt(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <>
      <button
        type="button"
        className="button account-button"
        title={nickname}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <UserIcon />
        <span className="nav-label">{nickname}</span>
      </button>
      {open && (
        <div className="drawer-backdrop" onClick={() => setOpen(false)}>
          <aside
            className="drawer"
            role="dialog"
            aria-label="내 계정"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="drawer-head">
              <span className="drawer-name">
                <UserIcon />
                <span>{nickname}</span>
              </span>
              <button type="button" className="drawer-close" title="닫기" onClick={() => setOpen(false)}>
                <CloseIcon />
              </button>
            </div>
            <nav className="drawer-menu">
              <Link to="/donations" className="drawer-item">
                <HeartIcon />
                후원 내역
              </Link>
              <button
                type="button"
                className="drawer-item"
                onClick={() => {
                  setOpen(false)
                  logOut()
                }}
              >
                <LogoutIcon />
                로그아웃
              </button>
            </nav>
          </aside>
        </div>
      )}
    </>
  )
}
