import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore } from '../stores/app'

type MemoryItem = {
  id: string
  content: string
  importance: number
  noCompress: boolean
  createdAt: string
}

export function MemoryPage() {
  const { currentProjectId, currentSessionId, sessions } = useAppStore()
  const [items, setItems] = useState<MemoryItem[]>([])
  const [content, setContent] = useState('')
  const [plan, setPlan] = useState<{
    deleteIds: string[]
    newMemories: Array<{ content: string; importance: number }>
  } | null>(null)
  const [busy, setBusy] = useState(false)

  const sessionTitle = sessions.find((s) => s.id === currentSessionId)?.title ?? '当前会话'

  const reload = async () => {
    if (!currentProjectId || !currentSessionId) {
      setItems([])
      return
    }
    setItems(await hostApi.listMemory(currentProjectId, currentSessionId))
  }

  useEffect(() => {
    void reload().catch((e) => toast.error(e instanceof Error ? e.message : '加载失败'))
  }, [currentProjectId, currentSessionId])

  const add = async () => {
    if (!currentProjectId || !currentSessionId || !content.trim()) return
    await hostApi.addMemory(currentProjectId, currentSessionId, content.trim())
    setContent('')
    await reload()
    toast.success('已添加到当前会话记忆')
  }

  const toggleProtect = async (m: MemoryItem) => {
    if (!currentProjectId || !currentSessionId) return
    await hostApi.updateMemory(currentProjectId, currentSessionId, m.id, { noCompress: !m.noCompress })
    await reload()
  }

  const remove = async (id: string) => {
    if (!currentProjectId || !currentSessionId) return
    await hostApi.deleteMemory(currentProjectId, currentSessionId, [id])
    await reload()
  }

  const prepare = async () => {
    if (!currentProjectId || !currentSessionId) return
    setBusy(true)
    try {
      const p = await hostApi.prepareCompress(currentProjectId, currentSessionId)
      setPlan(p)
      toast.success('压缩方案已生成')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '压缩失败')
    } finally {
      setBusy(false)
    }
  }

  const execute = async () => {
    if (!currentProjectId || !currentSessionId || !plan) return
    setBusy(true)
    try {
      const r = await hostApi.executeCompress(currentProjectId, currentSessionId, plan)
      setPlan(null)
      await reload()
      toast.success(`压缩完成：删除 ${r.deleted}，新增 ${r.added}`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '执行失败')
    } finally {
      setBusy(false)
    }
  }

  if (!currentSessionId) {
    return (
      <div className="h-full flex items-center justify-center text-sm text-white/40">
        请先选择或创建一个会话
      </div>
    )
  }

  return (
    <div className="h-full overflow-auto p-6">
      <div className="flex items-start justify-between gap-4 mb-5">
        <div>
          <h2 className="text-base font-medium">会话记忆</h2>
          <p className="text-xs text-white/40 mt-1">
            仅属于「{sessionTitle}」：上下文 transcript 与记忆文件相互独立，切换会话不会串数据。
          </p>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={() => void prepare()}
          className="px-3 py-1.5 rounded-lg border border-white/15 text-xs hover:bg-white/5 disabled:opacity-40"
        >
          准备压缩
        </button>
      </div>

      <div className="flex gap-2 mb-5">
        <input
          className="flex-1 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
          placeholder="添加一条本会话记忆…"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void add()
          }}
        />
        <button type="button" onClick={() => void add()} className="px-3 py-2 rounded-lg bg-accent text-sm">
          添加
        </button>
      </div>

      {plan && (
        <div className="mb-5 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4 text-xs space-y-2">
          <div className="font-medium text-amber-200">压缩预览</div>
          <div>
            将删除 {plan.deleteIds.length} 条，新增 {plan.newMemories.length} 条
          </div>
          <ul className="list-disc pl-4 text-white/60 space-y-1">
            {plan.newMemories.map((m, i) => (
              <li key={i}>{m.content}</li>
            ))}
          </ul>
          <div className="flex gap-2 pt-1">
            <button
              type="button"
              disabled={busy}
              onClick={() => void execute()}
              className="px-3 py-1.5 rounded bg-accent text-white"
            >
              确认执行
            </button>
            <button type="button" onClick={() => setPlan(null)} className="px-3 py-1.5 rounded border border-white/15">
              取消
            </button>
          </div>
        </div>
      )}

      <div className="space-y-2">
        {items.length === 0 && <div className="text-sm text-white/30">本会话暂无记忆</div>}
        {items.map((m) => (
          <div key={m.id} className="rounded-xl border border-white/10 bg-surface-overlay/40 px-4 py-3">
            <div className="text-sm whitespace-pre-wrap">{m.content}</div>
            <div className="mt-2 flex items-center gap-3 text-[11px] text-white/40">
              <span>重要性 {m.importance}</span>
              <span>{new Date(m.createdAt).toLocaleString()}</span>
              <button type="button" className="hover:text-white" onClick={() => void toggleProtect(m)}>
                {m.noCompress ? '已保护' : '保护不被压缩'}
              </button>
              <button type="button" className="hover:text-red-300" onClick={() => void remove(m.id)}>
                删除
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
