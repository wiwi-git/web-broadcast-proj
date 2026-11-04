import { createContext, useContext } from 'react'
import type { Me } from '../api.ts'

export interface Auth {
  /** 로그인하지 않았으면 null */
  me: Me | null
  /** 첫 로그인 상태 확인이 끝났는지 */
  ready: boolean
  logIn(input: { username: string; password: string }): Promise<void>
  signUp(input: { username: string; password: string; nickname: string }): Promise<void>
  logOut(): Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('AuthProvider 안에서만 쓸 수 있습니다.')
  return auth
}

/** 로그인 뒤 돌아갈 주소. 다른 사이트로 보내지 않도록 이 사이트의 경로만 받는다. */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function loginPath(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`
}
