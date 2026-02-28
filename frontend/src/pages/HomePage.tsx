import { useState, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send, ChevronRight, Clock, KeyRound, AlertTriangle } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../api/agent'
import { apikeyApi } from '../api/apikey'
import { useAuth } from '../hooks/useAuth'
import { StatusBadge } from '../components/agent/StatusBadge'
import type { SessionResponse } from '../types/agent'

const EXAMPLES = [
  '用 Python 写一个能批量压缩图片的脚本，并解释每一步',
  '研究一下 Spring Boot 3.5 的新特性，给我一份简洁的总结',
  '帮我分析 Milvus 向量数据库的核心架构，和传统数据库的差异',
]

export function HomePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const [task, setTask] = useState('')

  const { data: apiKeys = [] } = useQuery({
    queryKey: ['api-keys', user?.userId],
    queryFn: () => apikeyApi.list(user!.userId),
    enabled: !!user?.userId,
  })
  const hasDefaultLlmKey = apiKeys.some(k => k.key_type === 'LLM' && k.is_default)

  const { data: recentSessions } = useQuery({
    queryKey: ['recent-sessions'],
    queryFn: async (): Promise<SessionResponse[]> => {
      // The backend doesn't have a list endpoint yet; load from localStorage
      const raw = localStorage.getItem('sessionIds') || '[]'
      let ids: unknown[]
      try { ids = JSON.parse(raw) } catch { ids = [] }
      // 过滤掉 null/undefined/空字符串 等非法值，避免请求 /api/agent/sessions/null 或 /undefined
      const cleanIds = (ids as unknown[])
        .filter((id): id is string =>
          typeof id === 'string' && id.length > 0 && id !== 'null' && id !== 'undefined'
        )

      // 如果修复前曾经写入过无效 ID，这里顺便把本地存储纠正一下
      if (cleanIds.length !== ids.length) {
        localStorage.setItem('sessionIds', JSON.stringify(cleanIds))
      }
      const results = await Promise.allSettled(
        cleanIds.slice(0, 10).map(id => agentApi.getSession(id))
      )
      return results
        .filter((r): r is PromiseFulfilledResult<SessionResponse> => r.status === 'fulfilled')
        .map(r => r.value)
        .sort((a, b) => b.createTime.localeCompare(a.createTime))
    },
    refetchInterval: 5000,
  })

  const startMutation = useMutation({
    mutationFn: (t: string) => agentApi.startSession({ task: t }),
    onSuccess: (session) => {
      // Persist session ID locally
      const ids: string[] = JSON.parse(localStorage.getItem('sessionIds') || '[]')
      localStorage.setItem('sessionIds', JSON.stringify([session.sessionId, ...ids].slice(0, 50)))
      queryClient.invalidateQueries({ queryKey: ['recent-sessions'] })
      navigate(`/session/${session.sessionId}`)
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (!task.trim()) return
      startMutation.mutate(task.trim())
    },
    [task, startMutation]
  )

  return (
    <div className="max-w-3xl mx-auto px-6 py-10 space-y-10">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white">新建 Agent 任务</h1>
        <p className="text-white/40 text-sm mt-1">描述你的目标，Agent 将自主规划并执行</p>
      </div>

      {/* No LLM key warning */}
      {!hasDefaultLlmKey && (
        <Link to="/api-keys"
          className="flex items-center gap-3 px-4 py-3 rounded-xl border border-yellow-500/30 bg-yellow-500/5 hover:bg-yellow-500/10 transition-colors group">
          <AlertTriangle className="w-4 h-4 text-yellow-400 flex-shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-sm text-yellow-300 font-medium">尚未配置默认 LLM API 密钥</p>
            <p className="text-xs text-yellow-400/60 mt-0.5">Agent 将使用系统密钥运行，点击前往配置你自己的密钥</p>
          </div>
          <KeyRound className="w-4 h-4 text-yellow-400/50 group-hover:text-yellow-400 transition-colors" />
        </Link>
      )}

      {/* Input */}
      <form onSubmit={handleSubmit} className="space-y-3">
        <textarea
          value={task}
          onChange={e => setTask(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) handleSubmit(e)
          }}
          placeholder="描述你想让 Agent 完成的任务..."
          rows={5}
          className="w-full bg-[#1a1a1a] border border-white/10 rounded-xl px-4 py-3.5 text-sm text-white placeholder-white/20 resize-none focus:outline-none focus:border-blue-500/50 transition-colors"
        />
        <div className="flex items-center justify-between">
          <span className="text-xs text-white/25">Ctrl+Enter 快速发送</span>
          <button
            type="submit"
            disabled={!task.trim() || startMutation.isPending}
            className="flex items-center gap-2 px-5 py-2.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Send className="w-4 h-4" />
            {startMutation.isPending ? '启动中...' : '启动 Agent'}
          </button>
        </div>
      </form>

      {/* Examples */}
      <div className="space-y-2">
        <p className="text-xs text-white/30 font-medium uppercase tracking-wider">示例任务</p>
        <div className="space-y-2">
          {EXAMPLES.map(ex => (
            <button
              key={ex}
              onClick={() => setTask(ex)}
              className="w-full text-left px-4 py-3 bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.06] rounded-lg text-sm text-white/60 hover:text-white/80 transition-colors"
            >
              {ex}
            </button>
          ))}
        </div>
      </div>

      {/* Recent sessions */}
      {recentSessions && recentSessions.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs text-white/30 font-medium uppercase tracking-wider">最近会话</p>
          <div className="space-y-2">
            {recentSessions.map(s => (
              <button
                key={s.sessionId}
                onClick={() => navigate(`/session/${s.sessionId}`)}
                className="w-full flex items-center gap-4 px-4 py-3 bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.06] rounded-lg transition-colors group"
              >
                <StatusBadge status={s.status} />
                <div className="flex-1 min-w-0 text-left">
                  <div className="text-sm text-white/75 truncate">{s.taskDescription}</div>
                  <div className="flex items-center gap-3 mt-0.5">
                    <span className="text-[10px] text-white/25 font-mono">{s.sessionId.slice(0, 8)}…</span>
                    <span className="flex items-center gap-1 text-[10px] text-white/25">
                      <Clock className="w-2.5 h-2.5" />
                      {s.iterationCount} 轮
                    </span>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-white/20 group-hover:text-white/40 flex-shrink-0" />
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
