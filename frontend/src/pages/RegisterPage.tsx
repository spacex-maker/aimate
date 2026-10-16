import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/** 注册已并入 AIMATEX Account；已登录则直接进控制台。 */
export function RegisterPage() {
  const { isLoggedIn } = useAuth()
  return <Navigate to={isLoggedIn ? '/app' : '/login'} replace />
}
