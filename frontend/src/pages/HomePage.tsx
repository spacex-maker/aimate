import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Clock, KeyRound, AlertTriangle, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../api/agent'
import { apikeyApi } from '../api/apikey'
import { useAuth } from '../hooks/useAuth'
import { StatusBadge } from '../components/agent/StatusBadge'
import type { SessionResponse } from '../types/agent'
import { useModelSelection } from '../state/modelSelection.ts'
import { DeleteSessionModal } from '../components/agent/DeleteSessionModal'
import { AgentInputBox } from '../components/agent/AgentInputBox'

function formatSessionTime(iso: string): string {
  try {
    const d = new Date(iso)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    if (diff < 60_000) return '刚刚'
    if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
    if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
    return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
  } catch {
    return ''
  }
}

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
  const [selectedSession, setSelectedSession] = useState<SessionResponse | null>(null)
  const { selection, setSelection } = useModelSelection()

  const { data: apiKeys = [] } = useQuery({
    queryKey: ['api-keys', user?.userId],
    queryFn: () => (user?.userId != null ? apikeyApi.list(user.userId) : Promise.resolve([])),
    enabled: !!user?.userId,
  })
  const hasDefaultLlmKey = apiKeys.some(k => k.keyType === 'LLM' && k.isDefault)

  // 最近会话：请求接口获取当前用户详细会话列表（状态、轮数、创建时间等）
  const { data: recentSessions = [], isLoading: recentLoading } = useQuery({
    queryKey: ['recent-sessions', user?.userId],
    queryFn: () => agentApi.getRecentSessions(10),
    enabled: !!user?.userId,
    refetchInterval: 30_000,
  })

  const startMutation = useMutation({
    mutationFn: (payload: {
      task: string
      modelSource?: 'SYSTEM' | 'USER_KEY'
      systemModelId?: number | null
      userApiKeyId?: number | null
    }) => agentApi.startSession(payload),
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ['recent-sessions'] })
      navigate(`/session/${session.sessionId}`)
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (params: { session: SessionResponse; options: { deleteMessages: boolean; deleteMemories: boolean; hideOnly: boolean } }) =>
      agentApi.deleteSession(params.session.sessionId, params.options),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recent-sessions'] })
      toast.success('会话已更新')
      setSelectedSession(null)
    },
    onError: (err: Error) => toast.error(err.message),
  })

  return (
    <div className="min-h-screen">
      <div className="max-w-3xl mx-auto px-6 py-10 pb-48 space-y-10 w-full">
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

        {/* Recent sessions：来自接口的详细会话列表（移到中部区域，支持上下滚动） */}
        {(recentLoading || recentSessions.length > 0) && (
          <div className="space-y-2">
            <p className="text-xs text-white/30 font-medium uppercase tracking-wider">最近会话</p>
            {recentLoading ? (
              <div className="text-center py-8 text-white/25 text-sm">加载中...</div>
            ) : (
              <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                {recentSessions.map(s => (
                  <div
                    key={s.sessionId}
                    className="w-full flex items-center gap-3 px-4 py-3 bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.06] rounded-lg transition-colors group"
                  >
                    <button
                      type="button"
                      onClick={() => navigate(`/session/${s.sessionId}`)}
                      className="flex-1 flex items-center gap-4 text-left"
                    >
                      <StatusBadge status={s.status} />
                      <div className="flex-1 min-w-0">
                        <div className="text-sm text-white/75 truncate">{s.taskDescription || '会话'}</div>
                        <div className="flex items-center gap-3 mt-0.5">
                          <span className="text-[10px] text-white/25 font-mono">{s.sessionId.slice(0, 8)}…</span>
                          <span className="flex items-center gap-1 text-[10px] text-white/25">
                            <Clock className="w-2.5 h-2.5" />
                            {s.iterationCount} 轮
                          </span>
                          {s.createTime && (
                            <span className="text-[10px] text-white/20">{formatSessionTime(s.createTime)}</span>
                          )}
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-white/20 group-hover:text-white/40 flex-shrink-0" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedSession(s)}
                      className="p-2 rounded-full text-white/30 hover:text-red-400 hover:bg-red-500/10 flex-shrink-0 transition-colors"
                      title="删除/隐藏会话"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
        {selectedSession && (
          <DeleteSessionModal
            session={selectedSession}
            loading={deleteMutation.isPending}
            onClose={() => setSelectedSession(null)}
            onConfirm={(options) => deleteMutation.mutate({ session: selectedSession, options })}
          />
        )}
      </div>

      <AgentInputBox
        value={task}
        onChange={setTask}
        onSubmit={(opts) => {
          if (!task.trim()) return
          if (opts?.modelSource) {
            setSelection({
              source: opts.modelSource,
              systemModelId: opts.systemModelId ?? null,
              userApiKeyId: opts.userApiKeyId ?? null,
            })
          }
          startMutation.mutate({
            task: task.trim(),
            modelSource: opts?.modelSource,
            systemModelId: opts?.systemModelId ?? null,
            userApiKeyId: opts?.userApiKeyId ?? null,
          })
        }}
        selectedSystemModelId={selection?.source === 'SYSTEM' ? selection.systemModelId ?? null : null}
        placeholder="描述你想让 Agent 完成的任务，Enter 发送 · Shift+Enter 换行"
        hintText="启动后，你可以在会话页底部继续追加问题，布局与此处保持一致。"
        isPending={startMutation.isPending}
        pendingLabel="启动中"
        submitTitle="启动 Agent"
      />
    </div>
  )
}
