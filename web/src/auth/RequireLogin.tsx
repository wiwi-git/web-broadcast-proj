import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { loginPath, useAuth } from './useAuth.ts'

/** 로그인하지 않았으면 로그인 화면으로 보내고, 로그인하면 이 화면으로 돌아온다. */
export default function RequireLogin({ children }: { children: ReactNode }) {
  const { me, ready } = useAuth()
  const location = useLocation()
  if (!ready) return <p className="notice">불러오는 중…</p>
  if (!me) return <Navigate to={loginPath(location.pathname + location.search)} replace />
  return children
}
