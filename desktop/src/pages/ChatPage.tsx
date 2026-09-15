import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Plus, Square, Send, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore, type StreamItem } from '../stores/app'

function eventsToStream(events: Array<Record<string, unknown>>): StreamItem[] {
  return events
    .map((e): StreamItem | null => {
      if (e.type === 'user') return { kind: 'user', content: String(e.content) }
      if (e.type === 'assistant') return { kind: 'assistant', content: String(e.content) }
      if (e.type === 'thinking') return { kind: 'thinking', content: String(e.content) }
      if (e.type === 'tool_call')
        return {
          kind: 'tool_call',
          id: String(e.id),
          name: String(e.name),
          args: String(e.args),
        }
      if (e.type === 'tool_result')
        return { kind: 'tool_result', id: String(e.id), result: String(e.result) }
      return null
    })
    .filter((x): x is StreamItem => !!x)
}

function StreamView({ items }: { items: StreamItem[] }) {
  return (
    <div className="space-y-3">
      {items.map((item, idx) => {
        if (item.kind === 'user') {
          return (
            <div key={idx} className="flex justify-end">
              <div className="max-w-[80%] rounded-2xl rounded-br-md bg-accent/90 px-3 py-2 text-sm whitespace-pre-wrap">
                {item.content}
              </div>
            </div>
          )
        }
        if (item.kind === 'thinking') {
          return (
            <div key={idx} className="rounded-lg border border-white/10 bg-black/20 px-3 py-2 text-xs text-white/55 font-mono whitespace-pre-wrap">
              {item.content}
              <span className="inline-block w-1.5 h-3 ml-0.5 bg-white/40 animate-pulse" />
            </div>
          )
        }
        if (item.kind === 'tool_call') {
          return (
            <div key={idx} className="rounded-lg border border-amber-500/20 bg-amber-500/5 px-3 py-2 text-xs">
              <div className="text-amber-300 font-medium mb-1">工具调用 · {item.name}</div>
              <pre className="whitespace-pre-wrap text-white/50 font-mono">{item.args}</pre>
            </div>
          )
        }
        if (item.kind === 'tool_result') {
          return (
            <div key={idx} className="rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-xs">
              <div className="text-emerald-300 font-medium mb-1">工具结果</div>
              <pre className="whitespace-pre-wrap text-white/60 font-mono max-h-48 overflow-auto">{item.result}</pre>
            </div>
          )
        }
        if (item.kind === 'error') {
          return (
            <div key={idx} className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-200">
              {item.message}
            </div>
          )
        }
        return (
          <div key={idx} className="prose prose-invert prose-sm max-w-none rounded-xl border border-white/10 bg-surface-overlay/60 px-4 py-3">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.content}</ReactMarkdown>
          </div>
        )
      })}
    </div>
  )
}

export function ChatPage() {
  const {
    currentProjectId,
    sessions,
    currentSessionId,
    stream,
    running,
    setStream,
    appendStream,
    setRunning,
  } = useAppStore()
  const [input, setInput] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  const refreshSessions = async (selectId?: string) => {
    if (!currentProjectId) return
    const list = await hostApi.listSessions(currentProjectId)
    useAppStore.setState({
      sessions: list,
      currentSessionId: selectId ?? useAppStore.getState().currentSessionId ?? list[0]?.id,
    })
  }

  useEffect(() => {
    if (!currentProjectId) return
    let cancelled = false
    ;(async () => {
      try {
        let sessionId = useAppStore.getState().currentSessionId
        let list = await hostApi.listSessions(currentProjectId)
        if (cancelled) return
        if (!sessionId || !list.some((s) => s.id === sessionId)) {
          const created = await hostApi.createSession(currentProjectId, '新会话')
          if (cancelled) return
          sessionId = created.id
          list = await hostApi.listSessions(currentProjectId)
        }
        if (cancelled) return
        useAppStore.setState({ sessions: list, currentSessionId: sessionId })
        const { events } = await hostApi.getSession(currentProjectId, sessionId)
        if (!cancelled) setStream(eventsToStream(events))
      } catch (e) {
        if (!cancelled) toast.error(e instanceof Error ? e.message : '加载会话失败')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [currentProjectId, setStream])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [stream])

  const selectSession = async (id: string) => {
    if (!currentProjectId) return
    useAppStore.setState({ currentSessionId: id })
    const { events } = await hostApi.getSession(currentProjectId, id)
    setStream(eventsToStream(events))
  }

  const newSession = async () => {
    if (!currentProjectId) return
    const s = await hostApi.createSession(currentProjectId, '新会话')
    await refreshSessions(s.id)
    setStream([])
  }

  const deleteSession = async (id: string) => {
    if (!currentProjectId) return
    await hostApi.deleteSession(currentProjectId, id)
    const list = await hostApi.listSessions(currentProjectId)
    if (list.length === 0) {
      const created = await hostApi.createSession(currentProjectId, '新会话')
      useAppStore.setState({ sessions: [created], currentSessionId: created.id })
      setStream([])
      return
    }
    const nextId = list[0].id
    useAppStore.setState({ sessions: list, currentSessionId: nextId })
    const { events } = await hostApi.getSession(currentProjectId, nextId)
    setStream(eventsToStream(events))
  }

  const send = async () => {
    if (!currentProjectId || !currentSessionId || !input.trim() || running) return
    const message = input.trim()
    setInput('')
    appendStream({ kind: 'user', content: message })
    setRunning(true)
    try {
      await hostApi.sendMessage(currentProjectId, currentSessionId, message)
      // 标题可能已更新
      void refreshSessions(currentSessionId)
    } catch (e) {
      setRunning(false)
      toast.error(e instanceof Error ? e.message : '发送失败')
    }
  }

  const cancel = async () => {
    await hostApi.cancelSession()
    setRunning(false)
  }

  return (
    <div className="h-full flex">
      <div className="w-56 border-r border-white/10 flex flex-col">
        <div className="flex items-center justify-between px-3 py-3 border-b border-white/10">
          <span className="text-xs text-white/50">会话</span>
          <button type="button" onClick={() => void newSession()} className="p-1 rounded hover:bg-white/10 text-white/60" title="新建会话">
            <Plus className="w-4 h-4" />
          </button>
        </div>
        <div className="flex-1 overflow-auto p-2 space-y-0.5">
          {sessions.map((s) => (
            <div
              key={s.id}
              className={clsx(
                'group flex items-center gap-1 rounded px-2 py-1.5 text-xs cursor-pointer',
                s.id === currentSessionId ? 'bg-white/10 text-white' : 'text-white/60 hover:bg-white/5',
              )}
              onClick={() => void selectSession(s.id)}
            >
              <span className="flex-1 truncate">{s.title}</span>
              <button
                type="button"
                className="opacity-0 group-hover:opacity-100 p-0.5 hover:text-red-300"
                onClick={(e) => {
                  e.stopPropagation()
                  void deleteSession(s.id)
                }}
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1 flex flex-col min-w-0">
        <div className="flex-1 overflow-auto px-5 py-4">
          {stream.length === 0 ? (
            <div className="h-full flex items-center justify-center text-white/30 text-sm text-center px-6">
              当前是独立新会话：上下文与记忆仅属于本会话。
              <br />
              发送消息开始，或用 /hello-world 触发示例 Skill。
            </div>
          ) : (
            <StreamView items={stream} />
          )}
          <div ref={bottomRef} />
        </div>
        <div className="border-t border-white/10 p-4">
          <div className="flex gap-2 items-end">
            <textarea
              className="flex-1 min-h-[72px] max-h-40 resize-y rounded-xl bg-black/30 border border-white/10 px-3 py-2 text-sm outline-none focus:border-accent"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  void send()
                }
              }}
              disabled={running}
            />
            {running ? (
              <button
                type="button"
                onClick={() => void cancel()}
                className="h-10 px-3 rounded-xl border border-red-400/40 text-red-200 text-sm flex items-center gap-1"
              >
                <Square className="w-3.5 h-3.5" /> 停止
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void send()}
                disabled={!input.trim()}
                className="h-10 px-4 rounded-xl bg-accent disabled:opacity-40 text-sm flex items-center gap-1"
              >
                <Send className="w-3.5 h-3.5" /> 发送
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
