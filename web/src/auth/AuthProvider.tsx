import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, logIn, logOut, signUp, type Me } from '../api.ts'
import { AuthContext, type Auth } from './useAuth.ts'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setReady(true))
  }, [])

  const auth = useMemo<Auth>(
    () => ({
      me,
      ready,
      logIn: async (input) => setMe(await logIn(input)),
      signUp: async (input) => setMe(await signUp(input)),
      logOut: async () => {
        try {
          await logOut()
        } finally {
          setMe(null)
          // 로그아웃하면 CSRF 토큰도 지워지므로 새로 받는다
          await fetchMe().catch(() => null)
        }
      },
    }),
    [me, ready],
  )

  return <AuthContext value={auth}>{children}</AuthContext>
}
