import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, AlertTriangle } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { agentApi } from '../api/agent'
import { apikeyApi } from '../api/apikey'
import { multiAgentApi } from '../api/multiAgent'
import { useAuth } from '../hooks/useAuth'
import type { SessionResponse } from '../types/agent'
import { useModelSelection } from '../state/modelSelection.ts'
import { DeleteSessionModal } from '../components/agent/DeleteSessionModal'
import { AgentInputBox } from '../components/agent/AgentInputBox'

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
  const [userAgentId, setUserAgentId] = useState<number | null>(null)
  const { selection, setSelection } = useModelSelection()

  const { data: apiKeys = [] } = useQuery({
    queryKey: ['api-keys', user?.userId],
    queryFn: () => (user?.userId != null ? apikeyApi.list(user.userId) : Promise.resolve([])),
    enabled: !!user?.userId,
  })
  const hasDefaultLlmKey = apiKeys.some(k => k.keyType === 'LLM' && k.isDefault)

  const { data: myAgents = [] } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId,
  })
  const { data: profile } = useQuery({
    queryKey: ['user-profile', user?.userId],
    queryFn: () => multiAgentApi.getProfile(),
    enabled: !!user?.userId,
  })

  useEffect(() => {
    if (userAgentId != null) return
    const sec = myAgents.find((a) => a.kind === 'SECRETARY' && a.status === 'ACTIVE')
    if (sec) setUserAgentId(sec.id)
  }, [myAgents, userAgentId])

  const startMutation = useMutation({
    mutationFn: (payload: {
      task: string
      modelSource?: 'SYSTEM' | 'USER_KEY'
      systemModelId?: number | null
      userApiKeyId?: number | null
      userAgentId?: number | null
      images?: string[]
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

        {profile && !profile.completed && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 px-4 py-3 text-sm text-amber-100/90">
            请先在个人中心完善职业、年龄、性别等画像，以生成默认秘书 Agent。
            <button
              type="button"
              className="ml-2 text-amber-300 underline"
              onClick={() => navigate('/agents/mine')}
            >
              查看我的 Agent
            </button>
          </div>
        )}

        {myAgents.length > 0 && (
          <div>
            <p className="mb-2 text-xs text-white/30 font-medium uppercase tracking-wider">对话 Agent</p>
            <div className="flex flex-wrap gap-2">
              {myAgents
                .filter((a) => a.status === 'ACTIVE')
                .map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() => setUserAgentId(a.id)}
                    className={clsx(
                      'rounded-lg border px-3 py-1.5 text-xs transition-colors',
                      userAgentId === a.id
                        ? 'border-cyan-400/40 bg-cyan-400/10 text-cyan-100'
                        : 'border-white/10 text-white/50 hover:border-white/20'
                    )}
                  >
                    {a.name}
                    {a.kind === 'SECRETARY' ? ' · 秘书' : ''}
                  </button>
                ))}
            </div>
          </div>
        )}

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

        {/* 最近会话模块已移动到左侧导航栏中显示，这里不再单独展示 */}
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
          const text = task.trim()
          if (!text && !(opts?.images && opts.images.length > 0)) return
          if (opts?.modelSource) {
            setSelection({
              source: opts.modelSource,
              systemModelId: opts.systemModelId ?? null,
              userApiKeyId: opts.userApiKeyId ?? null,
            })
          }
          startMutation.mutate({
            task: text,
            modelSource: opts?.modelSource,
            systemModelId: opts?.systemModelId ?? null,
            userApiKeyId: opts?.userApiKeyId ?? null,
            userAgentId,
            images: opts?.images,
          })
          setTask('')
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
