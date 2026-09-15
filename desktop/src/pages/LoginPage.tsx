import { useState } from 'react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore } from '../stores/app'

export function LoginPage() {
  const { serverUrl, setBoot } = useAppStore()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [url, setUrl] = useState(serverUrl)
  const [busy, setBusy] = useState(false)

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      await hostApi.setConfig({ serverUrl: url })
      const auth = await hostApi.login(identifier.trim(), password)
      const cfg = await hostApi.getConfig()
      const ready = await hostApi.ensureWorkspaceReady()
      const sessions = await hostApi.listSessions(ready.currentProjectId)
      setBoot({
        auth,
        serverUrl: cfg.serverUrl,
        userDataRoot: cfg.userDataRoot,
        projects: ready.projects,
        currentProjectId: ready.currentProjectId,
      })
      useAppStore.setState({
        sessions,
        currentSessionId: ready.session.id,
        stream: [],
        nav: 'chat',
      })
      toast.success(`欢迎，${auth.displayName || auth.username}`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '登录失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="h-full flex items-center justify-center bg-[radial-gradient(ellipse_at_top,_#1a2740_0%,_#12141a_55%)]">
      <form
        onSubmit={onSubmit}
        className="w-full max-w-md px-8 py-10 rounded-2xl border border-white/10 bg-surface-raised/80 backdrop-blur"
      >
        <div className="mb-8">
          <h1 className="text-3xl font-semibold tracking-tight">AIMate</h1>
          <p className="text-sm text-white/50 mt-2">桌面 Agent · 登录后自动进入新会话</p>
        </div>
        <label className="block text-xs text-white/50 mb-1">服务端地址</label>
        <input
          className="w-full mb-4 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="http://localhost:9299"
        />
        <label className="block text-xs text-white/50 mb-1">用户名 / 邮箱</label>
        <input
          className="w-full mb-4 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          autoFocus
        />
        <label className="block text-xs text-white/50 mb-1">密码</label>
        <input
          type="password"
          className="w-full mb-6 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <button
          type="submit"
          disabled={busy || !identifier || !password}
          className="w-full py-2.5 rounded-lg bg-accent hover:bg-accent-muted disabled:opacity-40 text-sm font-medium transition-colors"
        >
          {busy ? '登录中…' : '登录'}
        </button>
      </form>
    </div>
  )
}
