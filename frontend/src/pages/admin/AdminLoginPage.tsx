import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminFetchCaptcha, adminLogin } from '../../api/adminAuth'
import { useAdminAuth } from '../../auth/AdminAuthContext'

export function AdminLoginPage() {
  const { state, loginWithToken } = useAdminAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [captchaId, setCaptchaId] = useState('')
  const [captchaImg, setCaptchaImg] = useState('')
  const [captchaCode, setCaptchaCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (state.status === 'authed') navigate('/admin', { replace: true })
  }, [state.status, navigate])

  const refreshCaptcha = useCallback(async () => {
    try {
      const c = await adminFetchCaptcha()
      setCaptchaId(c.captchaId)
      setCaptchaImg(c.imageBase64)
      setCaptchaCode('')
    } catch (e) {
      setError(e instanceof Error ? e.message : '验证码加载失败')
    }
  }, [])

  useEffect(() => {
    void refreshCaptcha()
  }, [refreshCaptcha])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res = await adminLogin(email, password, captchaId, captchaCode)
      await loginWithToken(res.accessToken)
      navigate('/admin', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
      await refreshCaptcha()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="admin-shell admin-shell--login">
      <div className="admin-login">
        <p className="admin-login__title">AIMATEX Chat</p>
        <p className="admin-login__sub">管理控制台</p>
        <p className="admin-login__hint">运营账号登录（与用户账号隔离）</p>
        <form onSubmit={(e) => void onSubmit(e)} className="admin-login__form">
          <label className="admin-field">
            <span>管理员邮箱</span>
            <input
              required
              type="email"
              className="admin-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
            />
          </label>
          <label className="admin-field">
            <span>密码</span>
            <input
              required
              type="password"
              minLength={6}
              className="admin-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>
          <label className="admin-field">
            <span>图形验证码</span>
            <div className="admin-captcha-row">
              <input
                required
                className="admin-input flex-1"
                value={captchaCode}
                onChange={(e) => setCaptchaCode(e.target.value)}
              />
              {captchaImg ? (
                <img src={captchaImg} alt="captcha" onClick={() => void refreshCaptcha()} />
              ) : null}
            </div>
          </label>
          {error ? <p className="admin-error">{error}</p> : null}
          <button type="submit" className="admin-btn" disabled={loading}>
            {loading ? '登录中…' : '登录'}
          </button>
        </form>
      </div>
    </div>
  )
}
