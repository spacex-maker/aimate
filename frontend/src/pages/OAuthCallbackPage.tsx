import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthGateShell } from '../components/layout/AuthGateShell'
import { setAuthUser } from '../hooks/useAuth'
import { takeOidcCallbackParams } from '../lib/oidc'

export function OAuthCallbackPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const { code, codeVerifier, redirectUri, next } = takeOidcCallbackParams(window.location.search)
        const data = await authApi.oidcCallback({ code, codeVerifier, redirectUri })
        if (cancelled) return
        setAuthUser({
          userId: data.userId,
          username: data.username,
          displayName: data.displayName,
          avatarUrl: data.avatarUrl ?? null,
          role: data.role,
          token: data.token,
        })
        const raw = next && next.startsWith('/') && !next.startsWith('//') ? next : '/app'
        const safeNext = raw === '/' ? '/app' : raw
        navigate(safeNext, { replace: true })
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '统一登录失败')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [navigate])

  return (
    <AuthGateShell
      title={error ? '登录未完成' : '正在接通 Agent'}
      lead={
        error
          ? '统一登录回调失败，请返回后重试。'
          : '已拿到授权码，正在交换会话并回到 Chat…'
      }
      footer={
        error ? (
          <Link to="/login" className="auth-gate__link">
            返回登录
          </Link>
        ) : undefined
      }
    >
      {error ? (
        <p className="auth-gate__error">{error}</p>
      ) : (
        <div className="auth-gate__pulse" aria-hidden>
          <span />
          <span />
          <span />
        </div>
      )}
    </AuthGateShell>
  )
}
