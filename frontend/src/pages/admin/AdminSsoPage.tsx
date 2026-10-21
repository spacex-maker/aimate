import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { adminStaffHandoff } from '../../api/adminAuth'
import { useAdminAuth } from '../../auth/AdminAuthContext'

export function AdminSsoPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { loginWithToken } = useAdminAuth()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const ticket = params.get('ticket')
    if (!ticket) {
      setError('缺少登录票据')
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const data = await adminStaffHandoff(ticket)
        if (cancelled) return
        await loginWithToken(data.accessToken)
        navigate('/admin', { replace: true })
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '进入后台失败')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [params, navigate, loginWithToken])

  return (
    <div className="admin-shell admin-shell--login">
      <div className="admin-login">
        <p className="admin-login__title">AIMATEX Chat</p>
        <p className="admin-login__sub">
          {error ? '没能进入后台' : '正在进入运营后台'}
        </p>
        <p className="admin-login__hint">
          {error
            ? '超管票据无效或已过期，请回到账号中心重新点「后台」。'
            : '正在核销账号中心签发的超管票据…'}
        </p>
        {error ? (
          <>
            <p className="admin-error mt-4">{error}</p>
            <Link to="/login" className="admin-btn admin-btn--ghost mt-4 inline-block text-center">
              返回登录
            </Link>
          </>
        ) : (
          <div className="mt-6 text-sm text-[var(--admin-dim)]">请稍候…</div>
        )}
      </div>
    </div>
  )
}
