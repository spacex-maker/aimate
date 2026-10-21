import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '../api/auth'
import { AuthGateShell } from '../components/layout/AuthGateShell'
import { useAuth } from '../hooks/useAuth'
import { beginAimatexLogin, type OidcPublicConfig } from '../lib/oidc'

export function LoginPage() {
  const { isLoggedIn } = useAuth()
  const location = useLocation()
  const stateFrom = (location.state as { from?: string })?.from
  const queryFrom = new URLSearchParams(location.search).get('from') ?? undefined
  const rawFrom = stateFrom || queryFrom
  // 登录成功默认进控制台；官网 / 不算受保护回跳
  const from = rawFrom && rawFrom !== '/' ? rawFrom : '/app'
  const [config, setConfig] = useState<OidcPublicConfig | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [jumping, setJumping] = useState(false)
  const started = useRef(false)

  const go = useCallback(
    async (cfg: OidcPublicConfig) => {
      if (started.current) return
      started.current = true
      setJumping(true)
      setError(null)
      try {
        await beginAimatexLogin(cfg, from.startsWith('/') ? from : '/app')
      } catch (e) {
        started.current = false
        setJumping(false)
        const msg = e instanceof Error ? e.message : '跳转失败'
        setError(msg)
        toast.error(msg)
      }
    },
    [from],
  )

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const cfg = await authApi.oidcConfig()
        if (!cancelled) setConfig(cfg)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '无法加载统一登录配置')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  // 已有 Chat 会话：绝不重复走统一登录，直接回控制台 / 原目标页
  if (isLoggedIn) return <Navigate to={from || '/app'} replace />

  return (
    <AuthGateShell
      title="账号已统一"
      lead="前往 AIMATEX Account 登录或注册。完成后自动回到控制台。"
      footer={
        <p>
          不自动跳转，需手动点击 ·{' '}
          <Link to="/" className="auth-gate__link">
            返回首页
          </Link>
        </p>
      }
    >
      {error ? <p className="auth-gate__error">{error}</p> : null}
      <button
        type="button"
        disabled={!config?.enabled || jumping}
        onClick={() => config && void go(config)}
        className="auth-gate__cta"
      >
        <span>{jumping ? '正在跳转…' : '立即前往 AIMATEX Account'}</span>
        <ArrowRight className="w-4 h-4" />
      </button>
    </AuthGateShell>
  )
}
