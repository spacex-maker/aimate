import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { ReactNode } from 'react'

/**
 * Wraps protected routes. Redirects to /login when user is not authenticated,
 * preserving the intended destination for post-login redirect.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useAuth()
  const location = useLocation()

  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  return <>{children}</>
}
