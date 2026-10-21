import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  adminClearSession,
  adminFetchMe,
  adminGetStoredToken,
  adminPersistSession,
  type AdminMeResponse,
} from '../api/adminAuth'

type AuthState =
  | { status: 'loading' }
  | { status: 'authed'; admin: AdminMeResponse }
  | { status: 'anon' }

type Ctx = {
  state: AuthState
  loginWithToken: (token: string) => Promise<void>
  logout: () => void
}

const AdminAuthContext = createContext<Ctx | null>(null)

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const bootstrap = useCallback(async () => {
    const t = adminGetStoredToken()
    if (!t) {
      setState({ status: 'anon' })
      return
    }
    try {
      const admin = await adminFetchMe()
      setState({ status: 'authed', admin })
    } catch {
      adminClearSession()
      setState({ status: 'anon' })
    }
  }, [])

  useEffect(() => {
    void bootstrap()
  }, [bootstrap])

  const loginWithToken = useCallback(async (token: string) => {
    adminPersistSession(token)
    const admin = await adminFetchMe()
    setState({ status: 'authed', admin })
  }, [])

  const logout = useCallback(() => {
    adminClearSession()
    setState({ status: 'anon' })
  }, [])

  const value = useMemo(
    () => ({ state, loginWithToken, logout }),
    [state, loginWithToken, logout]
  )

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>
}

export function useAdminAuth() {
  const ctx = useContext(AdminAuthContext)
  if (!ctx) throw new Error('useAdminAuth outside provider')
  return ctx
}
