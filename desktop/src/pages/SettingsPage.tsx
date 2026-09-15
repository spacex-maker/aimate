import { useState } from 'react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore } from '../stores/app'

export function SettingsPage() {
  const auth = useAppStore((s) => s.auth)
  const serverUrl = useAppStore((s) => s.serverUrl)
  const userDataRoot = useAppStore((s) => s.userDataRoot)
  const setBoot = useAppStore((s) => s.setBoot)
  const [url, setUrl] = useState(serverUrl)
  const [model, setModel] = useState('')

  const save = async () => {
    try {
      await hostApi.setConfig({ serverUrl: url, preferredModel: model || undefined })
      const cfg = await hostApi.getConfig()
      setBoot({
        auth: useAppStore.getState().auth,
        serverUrl: cfg.serverUrl,
        userDataRoot: cfg.userDataRoot,
        projects: useAppStore.getState().projects,
        currentProjectId: cfg.currentProjectId,
      })
      toast.success('已保存')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  const logout = async () => {
    await hostApi.logout()
    setBoot({
      auth: null,
      serverUrl: url,
      userDataRoot,
      projects: useAppStore.getState().projects,
      currentProjectId: useAppStore.getState().currentProjectId,
    })
  }

  return (
    <div className="h-full overflow-auto p-6 max-w-xl">
      <h2 className="text-base font-medium mb-1">设置</h2>
      <p className="text-xs text-white/40 mb-6">服务端用于鉴权、LLM/Embedding 转发与用量计费</p>

      <label className="block text-xs text-white/50 mb-1">当前账号</label>
      <div className="mb-4 text-sm text-white/80">
        {auth?.displayName || auth?.username}（{auth?.role}）
      </div>

      <label className="block text-xs text-white/50 mb-1">服务端 Base URL</label>
      <input
        className="w-full mb-4 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
      />

      <label className="block text-xs text-white/50 mb-1">默认模型偏好（可选，由服务端解析）</label>
      <input
        className="w-full mb-4 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
        value={model}
        onChange={(e) => setModel(e.target.value)}
        placeholder="例如 deepseek-chat"
      />

      <label className="block text-xs text-white/50 mb-1">本地数据目录</label>
      <div className="mb-6 text-xs font-mono text-white/45 break-all">{userDataRoot || '—'}</div>

      <div className="flex gap-2">
        <button type="button" onClick={() => void save()} className="px-4 py-2 rounded-lg bg-accent text-sm">
          保存
        </button>
        <button type="button" onClick={() => void logout()} className="px-4 py-2 rounded-lg border border-white/15 text-sm">
          退出登录
        </button>
      </div>
    </div>
  )
}
