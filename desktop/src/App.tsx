import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { hostApi } from './lib/host-api'
import { useAppStore } from './stores/app'
import { LoginPage } from './pages/LoginPage'
import { Shell } from './pages/Shell'

export default function App() {
  const { bootstrapped, auth, setBoot } = useAppStore()
  const [loading, setLoading] = useState(true)
  const [bootError, setBootError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        if (!hostApi.isAvailable()) {
          // preload 偶发稍晚于首帧，短暂重试
          for (let i = 0; i < 20 && !hostApi.isAvailable(); i++) {
            await new Promise((r) => setTimeout(r, 50))
          }
        }
        if (!hostApi.isAvailable()) {
          throw new Error('Host API 不可用：preload 未加载，请完全退出后重新 npm run dev')
        }

        const cfg = await hostApi.getConfig()
        if (cancelled) return

        if (cfg.auth) {
          const ready = await hostApi.ensureWorkspaceReady()
          if (cancelled) return
          const sessions = await hostApi.listSessions(ready.currentProjectId)
          if (cancelled) return
          setBoot({
            auth: cfg.auth,
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
        } else {
          const projects = await hostApi.listProjects()
          if (cancelled) return
          setBoot({
            auth: null,
            serverUrl: cfg.serverUrl,
            userDataRoot: cfg.userDataRoot,
            projects,
            currentProjectId: cfg.currentProjectId,
          })
        }
        setBootError(null)
      } catch (e) {
        const message = e instanceof Error ? e.message : '启动失败'
        console.error('[AIMate] boot failed:', e)
        if (!cancelled) {
          setBootError(message)
          setBoot({ auth: null })
          toast.error(message)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [setBoot])

  useEffect(() => {
    return hostApi.onEvent((event) => {
      const store = useAppStore.getState()
      const payload = (event.payload ?? {}) as Record<string, unknown>
      switch (event.type) {
        case 'THINKING':
          store.patchThinking(String(payload.token ?? ''))
          break
        case 'TOOL_CALL':
          store.appendStream({
            kind: 'tool_call',
            id: String(payload.id ?? ''),
            name: String(payload.name ?? ''),
            args: String(payload.args ?? ''),
          })
          break
        case 'TOOL_RESULT':
          store.appendStream({
            kind: 'tool_result',
            id: String(payload.id ?? ''),
            result: String(payload.result ?? ''),
          })
          break
        case 'FINAL_ANSWER':
          store.appendStream({ kind: 'assistant', content: String(payload.content ?? '') })
          store.setRunning(false)
          break
        case 'ERROR':
          store.appendStream({ kind: 'error', message: String(payload.message ?? 'error') })
          store.setRunning(false)
          break
        case 'STATUS_CHANGE':
          if (payload.status === 'DONE' || payload.status === 'ERROR' || payload.status === 'CANCELLED') {
            store.setRunning(false)
          }
          if (payload.status === 'RUNNING') store.setRunning(true)
          break
        default:
          break
      }
    })
  }, [])

  if (loading || !bootstrapped) {
    return (
      <div className="h-full min-h-screen flex items-center justify-center text-white/50 text-sm bg-[#12141a]">
        正在启动 AIMate…
      </div>
    )
  }

  if (bootError && !hostApi.isAvailable()) {
    return (
      <div className="h-full min-h-screen flex flex-col items-center justify-center gap-3 text-sm bg-[#12141a] text-white/80 px-6 text-center">
        <p className="text-red-300">界面桥接失败</p>
        <p className="text-white/50 max-w-md">{bootError}</p>
        <p className="text-white/35 text-xs">请关闭所有 AIMate 窗口后重新执行 npm run dev</p>
      </div>
    )
  }

  if (!auth) return <LoginPage />
  return <Shell />
}
