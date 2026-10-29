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
import { useModelSelection } from '../state/modelSelection.ts'
import { AgentInputBox } from '../components/agent/AgentInputBox'
import { AgentAvatar, formatAgentDisplayTitle } from '../components/agent/AgentAvatar'
import { openUserCenter } from '../lib/userCenter'

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
  const [userAgentId, setUserAgentId] = useState<number | null>(null)
  const { selection, setSelection } = useModelSelection()

  const { data: apiKeys = [] } = useQuery({
    queryKey: ['api-keys', user?.userId],
    queryFn: () => (user?.userId != null ? apikeyApi.list(user.userId) : Promise.resolve([])),
    enabled: !!user?.userId,
  })
  const hasDefaultLlmKey = apiKeys.some((k) => k.keyType === 'LLM' && k.isDefault)

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

  const activeAgents = myAgents.filter((a) => a.status === 'ACTIVE')
  const selectedAgent = activeAgents.find((a) => a.id === userAgentId) ?? null

  useEffect(() => {
    if (userAgentId != null) return
    const sec = myAgents.find((a) => a.kind === 'SECRETARY' && a.status === 'ACTIVE')
    if (sec) setUserAgentId(sec.id)
  }, [myAgents, userAgentId])

  // 画像未完成：本会话自动打开一次「画像」引导
  useEffect(() => {
    if (!user?.userId || !profile || profile.completed) return
    const key = `aimate_profile_guide_${user.userId}`
    try {
      if (sessionStorage.getItem(key) === '1') return
      sessionStorage.setItem(key, '1')
    } catch {
      /* ignore */
    }
    openUserCenter('profile')
  }, [user?.userId, profile])

  const openThenMaybeSend = useMutation({
    mutationFn: async (payload: {
      agentId: number
      message?: string
      modelSource?: 'SYSTEM' | 'USER_KEY'
      systemModelId?: number | null
      userApiKeyId?: number | null
      images?: string[]
    }) => {
      const session = await multiAgentApi.openChat(payload.agentId)
      const text = (payload.message || '').trim()
      const hasImages = !!(payload.images && payload.images.length > 0)
      if (text || hasImages) {
        await agentApi.continueSession(session.sessionId, {
          message: text,
          modelSource: payload.modelSource,
          systemModelId: payload.systemModelId ?? null,
          userApiKeyId: payload.userApiKeyId ?? null,
          images: payload.images,
        })
      }
      return session
    },
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ['recent-sessions'] })
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
      navigate(`/session/${session.sessionId}`)
    },
    onError: (err: Error) => toast.error(err.message),
  })

  return (
    <div className="min-h-screen">
      <div className="max-w-3xl mx-auto px-6 py-10 pb-48 space-y-10 w-full">
        <div>
          <h1 className="text-2xl font-bold text-white">和伙伴聊天</h1>
          <p className="text-white/40 text-sm mt-1">
            每位伙伴只有一个会话窗口，像微信联系人一样继续聊
          </p>
        </div>

        {profile && !profile.completed && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 px-4 py-3.5 text-sm text-amber-100/90 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-medium text-amber-100">还差一步：完善个人画像</p>
              <p className="mt-0.5 text-xs text-amber-100/65">
                填写职业、年龄、性别后，系统会自动为你生成默认秘书 Agent（含独立 Soul）。
              </p>
            </div>
            <button
              type="button"
              className="shrink-0 rounded-lg bg-amber-400/90 px-3.5 py-2 text-xs font-semibold text-black hover:bg-amber-300"
              onClick={() => openUserCenter('profile')}
            >
              去完善画像
            </button>
          </div>
        )}

        {activeAgents.length > 0 && (
          <div>
            <p className="mb-2 text-xs text-white/30 font-medium uppercase tracking-wider">选择伙伴</p>
            <div className="flex flex-wrap gap-2">
              {activeAgents.map((a) => (
                <button
                  key={a.id}
                  type="button"
                  onClick={() => setUserAgentId(a.id)}
                  className={clsx(
                    'inline-flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs transition-colors',
                    userAgentId === a.id
                      ? 'border-cyan-400/40 bg-cyan-400/10 text-cyan-100'
                      : 'border-white/10 text-white/50 hover:border-white/20',
                  )}
                >
                  <AgentAvatar agent={a} size={20} />
                  <span>{formatAgentDisplayTitle(a)}</span>
                </button>
              ))}
            </div>
            {selectedAgent && (
              <div className="mt-3 flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  disabled={openThenMaybeSend.isPending}
                  onClick={() => openThenMaybeSend.mutate({ agentId: selectedAgent.id })}
                  className="rounded-lg border border-white/15 bg-white/[0.04] px-3 py-1.5 text-xs text-white/75 hover:bg-white/[0.08] disabled:opacity-40"
                >
                  {openThenMaybeSend.isPending
                    ? '打开中…'
                    : `进入与 ${formatAgentDisplayTitle(selectedAgent)} 的对话`}
                </button>
                <p className="text-[11px] text-white/35">
                  每位伙伴只有一个窗口；已聊过会直接回到最近对话。
                </p>
              </div>
            )}
          </div>
        )}

        {!hasDefaultLlmKey && (
          <Link
            to="/api-keys"
            className="flex items-center gap-3 px-4 py-3 rounded-xl border border-yellow-500/30 bg-yellow-500/5 hover:bg-yellow-500/10 transition-colors group"
          >
            <AlertTriangle className="w-4 h-4 text-yellow-400 flex-shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm text-yellow-300 font-medium">尚未配置默认 LLM API 密钥</p>
              <p className="text-xs text-yellow-400/60 mt-0.5">
                Agent 将使用系统密钥运行，点击前往配置你自己的密钥
              </p>
            </div>
            <KeyRound className="w-4 h-4 text-yellow-400/50 group-hover:text-yellow-400 transition-colors" />
          </Link>
        )}

        <div className="space-y-2">
          <p className="text-xs text-white/30 font-medium uppercase tracking-wider">示例开场</p>
          <div className="space-y-2">
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => setTask(ex)}
                className="w-full text-left px-4 py-3 bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.06] rounded-lg text-sm text-white/60 hover:text-white/80 transition-colors"
              >
                {ex}
              </button>
            ))}
          </div>
        </div>
      </div>

      <AgentInputBox
        value={task}
        onChange={setTask}
        onSubmit={(opts) => {
          const text = task.trim()
          if (!text && !(opts?.images && opts.images.length > 0)) return
          if (userAgentId == null) {
            toast.error('请先选择一位伙伴')
            return
          }
          if (opts?.modelSource) {
            setSelection({
              source: opts.modelSource,
              systemModelId: opts.systemModelId ?? null,
              userApiKeyId: opts.userApiKeyId ?? null,
            })
          }
          openThenMaybeSend.mutate({
            agentId: userAgentId,
            message: text,
            modelSource: opts?.modelSource,
            systemModelId: opts?.systemModelId ?? null,
            userApiKeyId: opts?.userApiKeyId ?? null,
            images: opts?.images,
          })
          setTask('')
        }}
        selectedSystemModelId={selection?.source === 'SYSTEM' ? selection.systemModelId ?? null : null}
        userAgentId={userAgentId}
        placeholder={
          selectedAgent
            ? `给 ${selectedAgent.name} 发消息，Enter 发送 · Shift+Enter 换行`
            : '选择伙伴后输入消息…'
        }
        hintText="每位伙伴只有一个会话；发送后进入同一聊天窗口继续对话。"
        isPending={openThenMaybeSend.isPending}
        pendingLabel="打开中"
        submitTitle="发送"
      />
    </div>
  )
}
