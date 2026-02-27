import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Bot, Eye, EyeOff, CheckCircle, XCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { authApi } from '../api/auth'
import { setAuthUser } from '../hooks/useAuth'

function PasswordRule({ pass, label }: { pass: boolean; label: string }) {
  return (
    <div className={clsx('flex items-center gap-1.5 text-[11px]', pass ? 'text-green-400' : 'text-white/30')}>
      {pass ? <CheckCircle className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
      {label}
    </div>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail]       = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm]   = useState('')
  const [showPwd, setShowPwd]   = useState(false)

  const rules = {
    length:  password.length >= 6,
    match:   password.length > 0 && password === confirm,
  }

  const mutation = useMutation({
    mutationFn: () => authApi.register({
      username,
      email:       email || undefined,
      password,
      displayName: displayName || undefined,
    }),
    onSuccess: (data) => {
      setAuthUser({ userId: data.user_id, username: data.username, displayName: data.display_name, token: data.token })
      toast.success('注册成功，欢迎！')
      navigate('/', { replace: true })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password || !rules.match) return
    mutation.mutate()
  }

  const canSubmit = username.trim().length >= 3 && rules.length && rules.match && !mutation.isPending

  return (
    <div className="min-h-screen bg-[#0d0d0d] flex items-center justify-center px-4 py-10">
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
          <h1 className="text-xl font-semibold text-white mb-1">注册账号</h1>
          <p className="text-sm text-white/40 mb-7">创建你的 OpenForgeX 账号</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Username */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">
                用户名 <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="至少 3 个字符"
                autoFocus
                autoComplete="username"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/60 transition-colors"
              />
            </div>

            {/* Display name */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">昵称（可选）</label>
              <input
                type="text"
                value={displayName}
                onChange={e => setDisplayName(e.target.value)}
                placeholder="不填则使用用户名"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/60 transition-colors"
              />
            </div>

            {/* Email */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">邮箱（可选）</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="用于找回密码"
                autoComplete="email"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/60 transition-colors"
              />
            </div>

            {/* Password */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">
                密码 <span className="text-red-400">*</span>
              </label>
              <div className="relative">
                <input
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="至少 6 个字符"
                  autoComplete="new-password"
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
              {/* Rules */}
              {password && (
                <div className="mt-2 space-y-1 pl-0.5">
                  <PasswordRule pass={rules.length} label="至少 6 个字符" />
                </div>
              )}
            </div>

            {/* Confirm */}
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">
                确认密码 <span className="text-red-400">*</span>
              </label>
              <input
                type={showPwd ? 'text' : 'password'}
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
                placeholder="再输一次密码"
                autoComplete="new-password"
                className={clsx(
                  'w-full bg-black/30 border rounded-lg px-3 py-2.5 text-sm text-white placeholder-white/20 focus:outline-none transition-colors',
                  confirm && !rules.match
                    ? 'border-red-500/50 focus:border-red-500/70'
                    : 'border-white/10 focus:border-blue-500/60'
                )}
              />
              {confirm && !rules.match && (
                <p className="text-[11px] text-red-400 mt-1.5">两次密码不一致</p>
              )}
              {confirm && rules.match && (
                <div className="mt-1.5">
                  <PasswordRule pass label="密码一致" />
                </div>
              )}
            </div>

            <button
              type="submit"
              disabled={!canSubmit}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed mt-2"
            >
              {mutation.isPending ? '注册中...' : '创建账号'}
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-white/35 mt-6">
          已有账号？{' '}
          <Link to="/login" className="text-blue-400 hover:text-blue-300 transition-colors">
            登录
          </Link>
        </p>
      </div>
    </div>
  )
}
