import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pause, Play, StopCircle, ArrowLeft, Copy, RefreshCw, BookOpen } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../api/agent'
import { useAgentSocket } from '../hooks/useAgentSocket'
import { ThinkingStream } from '../components/agent/ThinkingStream'
import { StatusBadge } from '../components/agent/StatusBadge'
import { AgentInputBox } from '../components/agent/AgentInputBox'
import { useModelSelection } from '../state/modelSelection.ts'
import { useChatInput } from '../state/chatInput.ts'
import type { AgentEvent, ChatMessageDto, PlanState, SessionResponse, StreamBlock, ToolCall } from '../types/agent'

// ── Block reducer ─────────────────────────────────────────────────────────────
type Action =
  | { type: 'ITERATION_START'; iteration: number }
  | { type: 'THINKING_TOKEN'; token: string }
  | { type: 'TOOL_CALL'; call: ToolCall }
  | { type: 'TOOL_OUTPUT_CHUNK'; chunk: string }
  | { type: 'TOOL_RESULT'; toolCallId: string; result: string; durationMs?: number | null }
  | { type: 'FINAL_ANSWER'; content: string }
  | { type: 'ERROR'; message: string }
  | { type: 'CLEAR' }

let blockSeq = 0
const uid = () => String(++blockSeq)

function reducer(state: StreamBlock[], action: Action): StreamBlock[] {
  switch (action.type) {
    case 'ITERATION_START': {
      const last = state[state.length - 1]
      if (last?.kind === 'iteration' && last.number === action.iteration) return state
      return [...state, { kind: 'iteration', number: action.iteration, id: uid() }]
    }

    case 'THINKING_TOKEN': {
      const last = state[state.length - 1]
      if (last?.kind === 'thinking' && !last.complete) {
        return [
          ...state.slice(0, -1),
          { ...last, content: last.content + action.token },
        ]
      }
      return [...state, { kind: 'thinking', content: action.token, complete: false, id: uid() }]
    }

    case 'TOOL_CALL':
      // Close current thinking block first
      return [
        ...closeThinking(state),
        { kind: 'toolCall', call: action.call, result: null, streamingOutput: '', durationMs: undefined, id: uid() },
      ]

    case 'TOOL_OUTPUT_CHUNK': {
      const outIdx = [...state].reverse().findIndex(b => b.kind === 'toolCall' && b.result === null)
      if (outIdx === -1) return state
      const realIdx = state.length - 1 - outIdx
      const updated = [...state]
      const block = updated[realIdx]
      if (block.kind === 'toolCall') {
        updated[realIdx] = { ...block, streamingOutput: (block.streamingOutput ?? '') + action.chunk }
      }
      return updated
    }

    case 'TOOL_RESULT': {
      // Find the tool call block that has no result yet and matches or is last
      const idx = [...state].reverse().findIndex(b => b.kind === 'toolCall' && b.result === null)
      if (idx === -1) return state
      const realIdx = state.length - 1 - idx
      const updated = [...state]
      const block = updated[realIdx]
      if (block.kind === 'toolCall') {
        updated[realIdx] = { ...block, result: action.result, durationMs: action.durationMs ?? undefined }
      }
      return updated
    }

    case 'FINAL_ANSWER':
      return [
        ...closeThinking(state),
        { kind: 'finalAnswer', content: action.content, id: uid() },
      ]

    case 'ERROR':
      return [...closeThinking(state), { kind: 'error', message: action.message, id: uid() }]

    case 'CLEAR':
      return []

    default:
      return state
  }
}

function closeThinking(blocks: StreamBlock[]): StreamBlock[] {
  const last = blocks[blocks.length - 1]
  if (last?.kind === 'thinking' && !last.complete) {
    return [...blocks.slice(0, -1), { ...last, complete: true }]
  }
  return blocks
}

// ── Plan Panel（右下角小面板，仅显示执行计划） ────────────────────────────────

function PlanPanel({ plan }: { plan: PlanState }) {
  if (!plan.steps || plan.steps.length === 0) return null
  const currentIndex = (plan.currentStepIndex ?? 1) - 1

  return (
    <div className="fixed right-4 bottom-28 z-30 w-60 max-h-40 rounded-xl border border-white/10 bg-gray-900/95 backdrop-blur shadow-xl overflow-hidden text-xs">
      <div className="px-3 py-2 border-b border-white/10 flex items-center justify-between">
        <span className="text-[11px] font-semibold text-white/70">执行计划</span>
        <span className="text-[10px] text-white/40">
          第 {Math.min(Math.max(plan.currentStepIndex || 1, 1), plan.steps.length)} / {plan.steps.length} 步
        </span>
      </div>
      <div className="max-h-28 overflow-y-auto px-2 py-2 space-y-1">
        {plan.steps.map((title, i) => {
          const stepNum = i + 1
          const isCurrent = currentIndex === i
          const summary = plan.stepSummaries[stepNum]
          const isDone = !!summary
          return (
            <div
              key={stepNum}
              className={`rounded-lg px-2 py-1.5 transition-colors ${
                isCurrent
                  ? 'bg-blue-500/20 text-white'
                  : isDone
                    ? 'bg-green-500/10 text-green-100/90'
                    : 'bg-white/5 text-white/70'
              }`}
            >
              <div className="flex items-center gap-2 mb-0.5">
                <span className="text-[10px] font-mono opacity-80">Step {stepNum}</span>
                {isDone && <span className="text-[10px] text-green-300">✓ 已完成</span>}
                {isCurrent && !isDone && <span className="text-[10px] text-blue-300">进行中</span>}
              </div>
              <div className="text-[11px] truncate">{title}</div>
              {summary && (
                <div className="text-[10px] text-white/50 mt-0.5 line-clamp-2">
                  {summary}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── Left panel: session list ──────────────────────────────────────────────────

interface SessionListPanelProps {
  sessions: SessionResponse[]
  loading: boolean
  selectedSessionId?: string
  onSelect: (id: string) => void
}

function SessionListPanel({ sessions, loading, selectedSessionId, onSelect }: SessionListPanelProps) {
  return (
    <aside className="w-64 border-r border-white/10 bg-black/20 flex-shrink-0 flex flex-col min-h-0">
      <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
        <span className="text-xs font-semibold text-white/60 uppercase tracking-wider">最近会话</span>
      </div>
      {loading ? (
        <div className="flex-1 flex items-center justify-center text-xs text-white/30">加载中...</div>
      ) : sessions.length === 0 ? (
        <div className="flex-1 flex items-center justify-center px-3 text-xs text-white/25 text-center">
          暂无会话
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto py-2 space-y-1">
          {sessions.map((s) => {
            const active = s.sessionId === selectedSessionId
            return (
              <button
                key={s.sessionId}
                type="button"
                onClick={() => onSelect(s.sessionId)}
                className={`w-full px-3 py-2 text-left text-xs flex items-center gap-2 transition-colors ${
                  active ? 'bg-white/10 text-white' : 'bg-transparent text-white/70 hover:bg-white/5'
                }`}
              >
                <StatusBadge status={s.status} />
                <div className="flex-1 min-w-0">
                  <div className="truncate">{s.taskDescription || '会话'}</div>
                  <div className="text-[10px] text-white/30 truncate">{s.sessionId}</div>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </aside>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────
export function SessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [blocks, dispatch] = useReducer(reducer, [])
  const statusRef = useRef<string>('IDLE')
  const { value: followup, setValue: setFollowup } = useChatInput()
  const [plan, setPlan] = useState<PlanState>({ steps: [], currentStepIndex: 0, stepSummaries: {} })
  /** 重试时：被重试的那条用户消息 id，用于在前端把流式内容插到该条下方/下一条 assistant 位置 */
  const [retryTargetUserMessageId, setRetryTargetUserMessageId] = useState<number | null>(null)
  /** Docker 安装说明弹窗 */
  const [dockerInstallModalOpen, setDockerInstallModalOpen] = useState(false)
  const { selection: globalModelSelection, setSelection: setGlobalModelSelection } = useModelSelection()

  // 显式传入 /session/undefined 或 /session/null 才视为真正的非法 ID；
  // 其他情况（正常 UUID）一律放行。
  const isExplicitInvalid =
    sessionId === 'undefined' || sessionId === 'null'
  const effectiveSessionId = !isExplicitInvalid && sessionId ? sessionId : undefined

  const { data: session, refetch } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => agentApi.getSession(effectiveSessionId!),
    refetchInterval: (query) => {
      const s = query.state.data?.status
      return s === 'ACTIVE' || s === 'RUNNING' || s === 'PAUSED' ? 5_000 : false
    },
    enabled: !!effectiveSessionId,
  })

  const { data: historyMessages } = useQuery({
    queryKey: ['session-messages', sessionId],
    queryFn: () => agentApi.getSessionMessages(effectiveSessionId!, 10),
    enabled: !!effectiveSessionId && !!session,
  })

  const { data: recentSessions = [], isLoading: recentLoading } = useQuery({
    queryKey: ['recent-sessions'],
    queryFn: () => agentApi.getRecentSessions(20),
  })

  // 往上滚动加载更多：已加载的「更早」消息，与接口返回的最近 N 条合并展示
  const [olderMessages, setOlderMessages] = useState<ChatMessageDto[]>([])
  const [hasNoMoreOlder, setHasNoMoreOlder] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  useEffect(() => {
    setOlderMessages([])
    setHasNoMoreOlder(false)
  }, [sessionId])

  const mergedMessages: ChatMessageDto[] = [...olderMessages, ...(historyMessages ?? [])]
  const oldestSeq = mergedMessages[0]?.seq
  const canLoadMore = mergedMessages.length > 0 && !hasNoMoreOlder && oldestSeq != null && !loadingMore

  const loadMoreOlder = useCallback(async () => {
    if (!effectiveSessionId || oldestSeq == null || loadingMore) return
    setLoadingMore(true)
    try {
      const list = await agentApi.getSessionMessages(effectiveSessionId, 10, oldestSeq)
      setOlderMessages(prev => [...list, ...prev])
      if (list.length < 10) setHasNoMoreOlder(true)
    } finally {
      setLoadingMore(false)
    }
  }, [effectiveSessionId, oldestSeq, loadingMore])

  // 初始只拉最近 10 条时，若不足 10 条说明没有更早消息
  useEffect(() => {
    if (olderMessages.length > 0) return
    if (historyMessages != null && historyMessages.length < 10) setHasNoMoreOlder(true)
  }, [historyMessages, olderMessages.length])

  const { data: scriptEnv, refetch: refetchScriptStatus } = useQuery({
    queryKey: ['script-status', effectiveSessionId],
    queryFn: () => agentApi.getScriptStatus(),
    enabled: !!effectiveSessionId,
    staleTime: 0,
    refetchOnMount: 'always',
    refetchInterval: 30_000,
  })

  const refreshDockerMutation = useMutation({
    mutationFn: () => agentApi.refreshScriptStatus(),
    onSuccess: () => {
      refetchScriptStatus()
      toast.success('已重新检测 Docker 环境')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const { data: dockerInstallInfo } = useQuery({
    queryKey: ['docker-install-info'],
    queryFn: () => agentApi.getDockerInstallInfo(),
    enabled: dockerInstallModalOpen,
  })

  // 从「运行中」变为「静默」时刷新历史消息（含轮询到 IDLE 的情况，避免漏 WS 时界面不更新）
  const prevStatusRef = useRef<string | undefined>(undefined)
  useEffect(() => {
    const prev = prevStatusRef.current
    prevStatusRef.current = session?.status
    if ((prev === 'ACTIVE' || prev === 'RUNNING') && (session?.status === 'IDLE' || session?.status === 'COMPLETED')) {
      queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
    }
  }, [session?.status, sessionId, queryClient])

  const pauseMutation = useMutation({
    mutationFn: () => agentApi.pauseSession(effectiveSessionId!),
    onSuccess: () => { refetch(); toast.success('已暂停') },
    onError: (e: Error) => toast.error(e.message),
  })
  const resumeMutation = useMutation({
    mutationFn: () => agentApi.resumeSession(effectiveSessionId!),
    onSuccess: () => { refetch(); toast.success('已恢复') },
    onError: (e: Error) => toast.error(e.message),
  })
  const abortMutation = useMutation({
    mutationFn: () => agentApi.abortSession(effectiveSessionId!),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['recent-sessions'] }); refetch() },
    onError: (e: Error) => toast.error(e.message),
  })
  const interruptMutation = useMutation({
    mutationFn: (assistantMessageId: number) => agentApi.interruptSession(effectiveSessionId!, assistantMessageId),
    onSuccess: () => { refetch(); queryClient.invalidateQueries({ queryKey: ['session-messages', sessionId] }); toast.success('已中断该条回答') },
    onError: (e: Error) => toast.error(e.message),
  })
  const continueMutation = useMutation({
    mutationFn: (payload: {
      text: string
      modelSource?: 'SYSTEM' | 'USER_KEY'
      systemModelId?: number | null
      userApiKeyId?: number | null
    }) =>
      agentApi.continueSession(effectiveSessionId!, {
        message: payload.text,
        modelSource: payload.modelSource,
        systemModelId: payload.systemModelId ?? null,
        userApiKeyId: payload.userApiKeyId ?? null,
      }),
    onSuccess: () => {
      dispatch({ type: 'CLEAR' })
      setFollowup('')
      refetch()
      // 立即拉取消息列表，否则「回答中」占位条可能不显示（后端已写入，前端仅 invalidate 不会马上请求）
      queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
      toast.success('已发送后续问题')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const retryMutation = useMutation({
    mutationFn: (params: { sessionId: string; userMessageId: number }) =>
      agentApi.retryMessage(params.sessionId, {
        userMessageId: params.userMessageId,
        modelSource: globalModelSelection?.source,
        systemModelId:
          globalModelSelection?.source === 'SYSTEM'
            ? globalModelSelection.systemModelId ?? null
            : null,
        userApiKeyId:
          globalModelSelection?.source === 'USER_KEY'
            ? globalModelSelection.userApiKeyId ?? null
            : null,
      }),
    onSuccess: () => {
      refetch()
      // 立即拉取消息列表，使该条 assistant 显示为「回答中」
      queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
      toast.success('已重试该条消息，将用此前上下文重新生成回复')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleEvent = useCallback((event: AgentEvent) => {
    switch (event.type) {
      case 'PLAN_READY': {
        const steps = Array.isArray(event.payload) ? (event.payload as string[]) : []
        setPlan(prev => ({ ...prev, steps, currentStepIndex: 0, stepSummaries: {} }))
        break
      }
      case 'STEP_START': {
        const p = event.payload as { stepIndex: number; title: string }
        if (p?.stepIndex) setPlan(prev => ({ ...prev, currentStepIndex: p.stepIndex }))
        break
      }
      case 'STEP_COMPLETE': {
        const p = event.payload as { stepIndex: number; title: string; summary?: string }
        const summary = p?.summary ?? event.content ?? ''
        if (p?.stepIndex) setPlan(prev => ({
          ...prev,
          stepSummaries: { ...prev.stepSummaries, [p.stepIndex]: summary },
        }))
        break
      }
      case 'ITERATION_START':
        dispatch({ type: 'ITERATION_START', iteration: event.iteration })
        break
      case 'THINKING':
        if (event.content) dispatch({ type: 'THINKING_TOKEN', token: event.content })
        break
      case 'TOOL_CALL':
        dispatch({ type: 'TOOL_CALL', call: event.payload as ToolCall })
        break
      case 'TOOL_OUTPUT_CHUNK': {
        const p = event.payload as { toolCallId: string; chunk: string }
        if (p?.chunk != null) dispatch({ type: 'TOOL_OUTPUT_CHUNK', chunk: p.chunk })
        break
      }
      case 'TOOL_RESULT': {
        const p = event.payload as { toolName: string; output: string; durationMs?: number }
        dispatch({ type: 'TOOL_RESULT', toolCallId: '', result: p?.output ?? '', durationMs: p?.durationMs ?? undefined })
        break
      }
      case 'FINAL_ANSWER': {
        const content = event.content ?? ''
        setRetryTargetUserMessageId(null)
        queryClient.setQueryData(['session', effectiveSessionId!], (prev: SessionResponse | undefined) =>
          prev ? { ...prev, status: 'IDLE' as const, result: content || prev.result } : prev
        )
        refetch()
        const payload = event.payload as { assistantMessageId?: number } | null
        queryClient.setQueryData(
          ['session-messages', sessionId],
          (prev: ChatMessageDto[] | undefined) => {
            const list = prev ?? []
            const last = list[list.length - 1]
            const msgId = payload?.assistantMessageId
            const timeStr = event.timestamp ? new Date(event.timestamp).toISOString() : null
            // 若最后一条就是本条 assistant（如先 STATUS_CHANGE 已 refetch 得到占位条），则只更新不追加，避免重复
            if (last?.role === 'assistant' && msgId != null && last.id === msgId) {
              return [...list.slice(0, -1), { ...last, content, messageStatus: 'DONE' as const, createTime: last.createTime ?? timeStr }]
            }
            return [
              ...list,
              {
                role: 'assistant' as const,
                content,
                id: payload?.assistantMessageId ?? null,
                messageStatus: 'DONE' as const,
                createTime: timeStr,
              },
            ]
          }
        )
        dispatch({ type: 'CLEAR' })
        break
      }
      case 'ERROR':
        dispatch({ type: 'ERROR', message: event.content ?? 'Unknown error' })
        refetch()
        break
      case 'STATUS_CHANGE': {
        statusRef.current = event.content ?? ''
        const p = event.payload as Record<string, unknown> | null
        if (p && typeof p === 'object' && 'sessionId' in p && 'status' in p) {
          queryClient.setQueryData(['session', effectiveSessionId!], p)
          const status = p.status as string
          if (status === 'IDLE' || status === 'COMPLETED') {
            setRetryTargetUserMessageId(null)
            queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
          } else if (status === 'ACTIVE' || status === 'RUNNING') {
            // 后端刚进入执行，拉一次消息列表以显示「回答中」占位（避免漏显）
            queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
          }
        } else {
          refetch()
        }
        break
      }
    }
  }, [refetch, queryClient, effectiveSessionId, sessionId])

  // 连接/重连后同步会话+消息，避免订阅晚于 Agent 导致漏事件、界面不更新
  const onWsConnected = useCallback(() => {
    refetch()
    queryClient.refetchQueries({ queryKey: ['session-messages', sessionId] })
  }, [refetch, queryClient, sessionId])

  useAgentSocket(effectiveSessionId ?? null, handleEvent, onWsConnected)

  const isRunning = session?.status === 'ACTIVE' || session?.status === 'RUNNING'
  const isPaused = session?.status === 'PAUSED'
  const canContinue = session?.status === 'IDLE' || session?.status === 'COMPLETED' || session?.status === 'FAILED' || session?.status === 'PENDING'
  const isTerminal = canContinue

  // 对话过程完全依赖 WS，不再用 session.result 兜底
  const displayBlocks: StreamBlock[] = blocks

  // 只有显式传入字符串 "undefined" 或 "null" 时才提示非法 ID，
  // 正常 UUID 不会被误判，避免有效会话也被挡住。
  if (isExplicitInvalid) {
    return (
      <div className="h-full flex flex-col items-center justify-center gap-4 text-white/70">
        <p>无效的会话 ID，请返回首页重新开始。</p>
        <button
          onClick={() => navigate('/')}
          className="px-4 py-2 text-sm bg-white/10 hover:bg-white/20 rounded-lg transition-colors"
        >
          返回首页
        </button>
      </div>
    )
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex-shrink-0 border-b border-white/10 px-6 py-4 flex items-center gap-4">
        <button
          onClick={() => navigate('/')}
          className="text-white/30 hover:text-white/70 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            {session && <StatusBadge status={session.status} />}
            <span className="text-xs font-mono text-white/25">
              {sessionId?.slice(0, 8)}…
            </span>
            {session && (
              <span className="text-xs text-white/30">
                {session.iterationCount} 轮迭代
              </span>
            )}
          </div>
          {session && (
            <p className="text-sm text-white/70 mt-0.5 truncate">{session.taskDescription}</p>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 flex-shrink-0">
          <button
            onClick={() => { navigator.clipboard.writeText(effectiveSessionId ?? ''); toast.success('已复制') }}
            disabled={!effectiveSessionId}
            className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Copy className="w-4 h-4" />
          </button>

          {isRunning && (
            <>
              <button
                onClick={() => pauseMutation.mutate()}
                disabled={pauseMutation.isPending || !effectiveSessionId}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-orange-400 border border-orange-400/30 hover:bg-orange-400/10 rounded-lg transition-colors disabled:opacity-50"
              >
                <Pause className="w-3.5 h-3.5" /> 暂停
              </button>
            </>
          )}
          {isPaused && (
            <button
              onClick={() => resumeMutation.mutate()}
              disabled={resumeMutation.isPending || !effectiveSessionId}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-green-400 border border-green-400/30 hover:bg-green-400/10 rounded-lg transition-colors disabled:opacity-50"
            >
              <Play className="w-3.5 h-3.5" /> 恢复
            </button>
          )}
          {!isTerminal && (
            <button
              onClick={() => abortMutation.mutate()}
              disabled={abortMutation.isPending || !effectiveSessionId}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-400 border border-red-400/30 hover:bg-red-400/10 rounded-lg transition-colors disabled:opacity-50"
            >
              <StopCircle className="w-3.5 h-3.5" /> 中止
            </button>
          )}
        </div>
      </div>

      {/* 脚本执行环境状态（独立隔离虚拟机）+ 自动检测与安装说明 */}
      {scriptEnv && (
        <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
          <span className="font-medium text-white/70">脚本执行环境：</span>
          {scriptEnv.dockerEnabled ? (
            <>
              {scriptEnv.dockerAvailable === false ? (
                <>
                  <span className="text-amber-400/90">未检测到 Docker</span>
                  <button
                    type="button"
                    onClick={() => refreshDockerMutation.mutate()}
                    disabled={refreshDockerMutation.isPending}
                    className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
                  >
                    <RefreshCw className={`w-3 h-3 ${refreshDockerMutation.isPending ? 'animate-spin' : ''}`} />
                    重新检测
                  </button>
                  <button
                    type="button"
                    onClick={() => setDockerInstallModalOpen(true)}
                    className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
                  >
                    <BookOpen className="w-3 h-3" />
                    安装说明
                  </button>
                </>
              ) : (
                <>
                  <span className="text-green-400/90">独立隔离环境</span>
                  <span className="text-white/50">— 您拥有专属 Linux 虚拟机，脚本与命令在隔离容器中运行</span>
                  {scriptEnv.dockerVersion && (
                    <span className="text-white/40">Docker v{scriptEnv.dockerVersion}</span>
                  )}
                  {scriptEnv.image && (
                    <span className="font-mono text-white/45" title="基础镜像">镜像 {scriptEnv.image}</span>
                  )}
                  {scriptEnv.containerStatus === 'running' && scriptEnv.containerName && (
                    <span className="font-mono text-white/50" title="当前容器">· {scriptEnv.containerName}</span>
                  )}
                  {scriptEnv.containerStatus === 'running' && (scriptEnv.memoryLimit || scriptEnv.cpuLimit != null) && (
                    <span className="text-white/40">
                      资源 {[scriptEnv.memoryLimit, scriptEnv.cpuLimit != null ? `CPU ${scriptEnv.cpuLimit} 核` : null].filter(Boolean).join(' · ')}
                    </span>
                  )}
                  {scriptEnv.containerStatus === 'none' && (
                    <span className="text-amber-400/80">· 首次执行时自动创建</span>
                  )}
                  {scriptEnv.idleMinutes != null && scriptEnv.idleMinutes <= 0 ? (
                    <span className="text-white/40">· 不自动回收（常驻/定时任务可一直运行）</span>
                  ) : scriptEnv.idleMinutes != null ? (
                    <span className="text-white/40">· 空闲 {scriptEnv.idleMinutes} 分钟后自动回收（回收即暂停，数据保留；容器内 cron 等定时任务需配置为不回收）</span>
                  ) : null}
                </>
              )}
            </>
          ) : (
            <>
              <span>本机执行（Docker 未启用）</span>
              {scriptEnv.dockerAvailable === true && scriptEnv.dockerVersion && (
                <span className="text-white/40">· 已检测到 Docker v{scriptEnv.dockerVersion}</span>
              )}
              {scriptEnv.dockerAvailable === false && (
                <>
                  <span className="text-white/40">· 未检测到 Docker</span>
                  <button
                    type="button"
                    onClick={() => refreshDockerMutation.mutate()}
                    disabled={refreshDockerMutation.isPending}
                    className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
                  >
                    <RefreshCw className={`w-3 h-3 ${refreshDockerMutation.isPending ? 'animate-spin' : ''}`} />
                    重新检测
                  </button>
                  <button
                    type="button"
                    onClick={() => setDockerInstallModalOpen(true)}
                    className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
                  >
                    <BookOpen className="w-3 h-3" />
                    安装说明
                  </button>
                </>
              )}
            </>
          )}
        </div>
      )}

      {/* Docker 安装说明弹窗 */}
      {dockerInstallModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setDockerInstallModalOpen(false)}>
          <div className="bg-gray-900 border border-white/20 rounded-xl shadow-xl max-w-md w-full mx-4 p-5 text-left" onClick={e => e.stopPropagation()}>
            <h3 className="text-sm font-semibold text-white/90 mb-3">安装 Docker</h3>
            {dockerInstallInfo ? (
              <>
                <p className="text-xs text-white/70 whitespace-pre-wrap mb-3">{dockerInstallInfo.instructions}</p>
                {dockerInstallInfo.copyCommand && (
                  <div className="flex gap-2 mb-3">
                    <code className="flex-1 px-3 py-2 rounded bg-black/40 text-xs text-green-300 font-mono break-all">
                      {dockerInstallInfo.copyCommand}
                    </code>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(dockerInstallInfo.copyCommand ?? '')
                        toast.success('已复制命令')
                      }}
                      className="flex-shrink-0 px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-white/80 text-xs"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </div>
                )}
                {dockerInstallInfo.docUrl && (
                  <a
                    href={dockerInstallInfo.docUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs text-blue-400 hover:underline"
                  >
                    官方安装文档 →
                  </a>
                )}
              </>
            ) : (
              <p className="text-xs text-white/50">加载中…</p>
            )}
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={() => setDockerInstallModalOpen(false)}
                className="px-3 py-1.5 text-xs rounded bg-white/10 hover:bg-white/20 text-white/80"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 会话列表 + Stream 主区域（执行计划通过右下角 PlanPanel 展示） */}
      <div className="flex-1 flex min-h-0">
        <SessionListPanel
          sessions={recentSessions}
          loading={recentLoading}
          selectedSessionId={effectiveSessionId}
          onSelect={(id) => navigate(`/session/${id}`)}
        />
        <div className="flex-1 min-w-0 flex flex-col relative">
          <ThinkingStream
            userMessage={mergedMessages.length ? null : (session?.taskDescription ?? null)}
            historyMessages={mergedMessages.length ? mergedMessages : null}
            topSlot={canLoadMore ? (
              <div className="flex justify-center py-3">
                <button
                  type="button"
                  onClick={loadMoreOlder}
                  disabled={loadingMore}
                  className="px-4 py-2 text-sm text-white/70 hover:text-white bg-white/10 hover:bg-white/15 rounded-lg transition-colors disabled:opacity-50"
                >
                  {loadingMore ? '加载中…' : '加载更多历史消息'}
                </button>
              </div>
            ) : undefined}
            blocks={displayBlocks}
            isRunning={isRunning}
            retryTargetUserMessageId={retryTargetUserMessageId}
            canRetry
            onRetry={(userMessageId) => {
              setRetryTargetUserMessageId(userMessageId)
              if (!effectiveSessionId) return
              retryMutation.mutate({
                sessionId: effectiveSessionId,
                userMessageId,
              })
            }}
            isRetrying={retryMutation.isPending}
            sessionId={effectiveSessionId ?? undefined}
            onInterrupt={(id) => interruptMutation.mutate(id)}
          />
        </div>
      </div>

      {/* 与首页完全一致的底部输入框：固定视口底部、安全区、大圆角 */}
      {session && (
        <AgentInputBox
          value={followup}
          onChange={setFollowup}
          onSubmit={(opts) => {
            if (!followup.trim() || !effectiveSessionId) return
            continueMutation.mutate({
              text: followup.trim(),
              modelSource: opts?.modelSource,
              systemModelId: opts?.systemModelId,
              userApiKeyId: opts?.userApiKeyId,
            })
          }}
          selectedSystemModelId={
            globalModelSelection?.source === 'SYSTEM' ? globalModelSelection.systemModelId ?? null : null
          }
          className="ml-64"
          onModelChange={(sel) => setGlobalModelSelection(sel)}
          placeholder={
            isRunning || isPaused
              ? '可随时发送，新消息将并行处理…'
              : '输入消息，Enter 发送 · Shift+Enter 换行'
          }
          hintText={
            isRunning || isPaused ? '新消息会立即开始回复，可与上一条并行' : '继续提问将基于当前会话上下文'
          }
          isPending={continueMutation.isPending}
          pendingLabel="发送中"
          submitTitle="发送"
          disabled={!effectiveSessionId}
        />
      )}
      <PlanPanel plan={plan} />
    </div>
  )
}
