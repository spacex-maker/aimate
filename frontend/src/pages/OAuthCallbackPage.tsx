import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthGateShell } from '../components/layout/AuthGateShell'
import { setAuthUser } from '../hooks/useAuth'
import { runOidcCallbackOnce } from '../lib/oidc'

export function OAuthCallbackPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const { user, next } = await runOidcCallbackOnce((p) =>
          authApi.oidcCallback({
            code: p.code,
            codeVerifier: p.codeVerifier,
            redirectUri: p.redirectUri,
          }),
        )
        // Persist even if this effect instance unmounted — remount must not drop the JWT.
        setAuthUser({
          userId: user.userId,
          username: user.username,
          displayName: user.displayName,
          avatarUrl: user.avatarUrl ?? null,
          role: user.role,
          token: user.token,
        })
        if (!alive) return
        const raw = next && next.startsWith('/') && !next.startsWith('//') ? next : '/app'
        const safeNext = raw === '/' ? '/app' : raw
        navigate(safeNext, { replace: true })
      } catch (e) {
        if (alive) {
          setError(e instanceof Error ? e.message : '统一登录失败')
        }
      }
    })()
    return () => {
      alive = false
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