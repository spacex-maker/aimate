import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Bot, Eye, EyeOff } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '../api/auth'
import { setAuthUser } from '../hooks/useAuth'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string })?.from ?? '/'

  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)

  const mutation = useMutation({
    mutationFn: (creds?: { identifier: string; password: string }) =>
      authApi.login(creds ?? { identifier, password }),
    onSuccess: (data) => {
      setAuthUser({ userId: data.userId, username: data.username, displayName: data.displayName, token: data.token })
      toast.success(`欢迎回来，${data.displayName ?? data.username ?? '用户'}`)
      navigate(from, { replace: true })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!identifier.trim() || !password) return
    mutation.mutate()
  }

  const handleTestLogin = () => {
    mutation.mutate({ identifier: 'test', password: 'test1234' })
  }

  return (
    <div className="min-h-screen bg-[#0d0d0d] flex items-center justify-center px-4">
      <div className="w-full max-w-sm">

        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-10">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center">
            <Bot className="w-6 h-6 text-white" />
          </div>
          <div>
            <div className="text-lg font-bold text-white leading-none">OpenForgeX</div>
            <div className="text-[11px] text-white/35 mt-0.5">Autonomous Agent Platform</div>
          </div>
        </div>

        {/* Card */}
        <div className="bg-[#1a1a1a] border border-white/10 rounded-2xl p-8">
          <h1 className="text-xl font-semibold text-white mb-1">登录</h1>
          <p className="text-sm text-white/40 mb-7">使用用户名或邮箱登录</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Identifier */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">用户名 / 邮箱</label>
              <input
                type="text"
                value={identifier}
                onChange={e => setIdentifier(e.target.value)}
                placeholder="输入用户名或邮箱"
                autoFocus
                autoComplete="username"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/60 transition-colors"
              />
            </div>

            {/* Password */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">密码</label>
              <div className="relative">
                <input
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="输入密码"
                  autoComplete="current-password"
                  className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2.5 pr-10 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/60 transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60"
                >
                  {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={mutation.isPending || !identifier.trim() || !password}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed mt-2"
            >
              {mutation.isPending ? '登录中...' : '登录'}
            </button>

            <button
              type="button"
              onClick={handleTestLogin}
              disabled={mutation.isPending}
              className="w-full py-2 rounded-lg text-sm text-white/50 hover:text-white/80 hover:bg-white/5 border border-white/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed mt-2"
            >
              测试登录（test / test1234）
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-white/35 mt-6">
          还没有账号？{' '}
          <Link to="/register" className="text-blue-400 hover:text-blue-300 transition-colors">
            立即注册
          </Link>
        </p>
      </div>
    </div>
  )
}
