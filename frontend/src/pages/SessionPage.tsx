import { useCallback, useReducer, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pause, Play, StopCircle, ArrowLeft, Copy, Send } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../api/agent'
import { useAgentSocket } from '../hooks/useAgentSocket'
import { ThinkingStream } from '../components/agent/ThinkingStream'
import { StatusBadge } from '../components/agent/StatusBadge'
import type { AgentEvent, PlanState, StreamBlock, ToolCall } from '../types/agent'

// ── Block reducer ─────────────────────────────────────────────────────────────
type Action =
  | { type: 'ITERATION_START'; iteration: number }
  | { type: 'THINKING_TOKEN'; token: string }
  | { type: 'TOOL_CALL'; call: ToolCall }
  | { type: 'TOOL_RESULT'; toolCallId: string; result: string }
  | { type: 'FINAL_ANSWER'; content: string }
  | { type: 'ERROR'; message: string }
  | { type: 'CLEAR' }

let blockSeq = 0
const uid = () => String(++blockSeq)

function reducer(state: StreamBlock[], action: Action): StreamBlock[] {
  switch (action.type) {
    case 'ITERATION_START':
      return [...state, { kind: 'iteration', number: action.iteration, id: uid() }]

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
        { kind: 'toolCall', call: action.call, result: null, id: uid() },
      ]

    case 'TOOL_RESULT': {
      // Find the tool call block that has no result yet and matches or is last
      const idx = [...state].reverse().findIndex(b => b.kind === 'toolCall' && b.result === null)
      if (idx === -1) return state
      const realIdx = state.length - 1 - idx
      const updated = [...state]
      const block = updated[realIdx]
      if (block.kind === 'toolCall') {
        updated[realIdx] = { ...block, result: action.result }
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

// ── Component ─────────────────────────────────────────────────────────────────
export function SessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [blocks, dispatch] = useReducer(reducer, [])
  const statusRef = useRef<string>('PENDING')
  const [followup, setFollowup] = useState('')
  const [plan, setPlan] = useState<PlanState>({ steps: [], currentStepIndex: 0, stepSummaries: {} })

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
      return s === 'RUNNING' || s === 'PENDING' ? 3000 : false
    },
    enabled: !!effectiveSessionId,
  })

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
  const continueMutation = useMutation({
    mutationFn: (text: string) => agentApi.continueSession(effectiveSessionId!, text),
    onSuccess: () => {
      dispatch({ type: 'CLEAR' })
      setFollowup('')
      refetch()
      toast.success('已发送后续问题')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const retryMutation = useMutation({
    mutationFn: (task: string) => agentApi.startSession({ task }),
    onSuccess: (newSession) => {
      const ids: string[] = JSON.parse(localStorage.getItem('sessionIds') || '[]')
      localStorage.setItem('sessionIds', JSON.stringify([newSession.sessionId, ...ids].slice(0, 50)))
      queryClient.invalidateQueries({ queryKey: ['recent-sessions'] })
      navigate(`/session/${newSession.sessionId}`)
      toast.success('已用相同问题发起新会话')
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
      case 'TOOL_RESULT': {
        const p = event.payload as { toolName: string; output: string }
        dispatch({ type: 'TOOL_RESULT', toolCallId: '', result: p?.output ?? '' })
        break
      }
      case 'FINAL_ANSWER':
        dispatch({ type: 'FINAL_ANSWER', content: event.content ?? '' })
        break
      case 'ERROR':
        dispatch({ type: 'ERROR', message: event.content ?? 'Unknown error' })
        break
      case 'STATUS_CHANGE':
        statusRef.current = event.content ?? ''
        refetch()
        break
    }
  }, [refetch])

  useAgentSocket(effectiveSessionId ?? null, handleEvent)

  const isRunning = session?.status === 'RUNNING'
  const isPaused = session?.status === 'PAUSED'
  const isTerminal = session?.status === 'COMPLETED' || session?.status === 'FAILED'

  // 如果 WebSocket 没连上（没有任何流式 block），但后端已经把会话标记为 COMPLETED，
  // 直接用 session.result 作为一个兜底的最终答案展示出来，避免前端一直空白。
  const displayBlocks: StreamBlock[] =
    !isRunning && session?.status === 'COMPLETED' && session.result && blocks.length === 0
      ? [{
          kind: 'finalAnswer',
          content: session.result,
          id: 'session-result-fallback',
        }]
      : blocks

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
            <button
              onClick={() => pauseMutation.mutate()}
              disabled={pauseMutation.isPending || !effectiveSessionId}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-orange-400 border border-orange-400/30 hover:bg-orange-400/10 rounded-lg transition-colors disabled:opacity-50"
            >
              <Pause className="w-3.5 h-3.5" /> 暂停
            </button>
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

      {/* Plan + Stream */}
      <div className="flex-1 flex min-h-0">
        {plan.steps.length > 0 && (
          <div className="w-52 flex-shrink-0 border-r border-white/10 bg-black/20 flex flex-col">
            <div className="px-4 py-3 border-b border-white/10">
              <h3 className="text-xs font-semibold text-white/60 uppercase tracking-wider">执行计划</h3>
            </div>
            <ul className="p-3 space-y-2 overflow-y-auto">
              {plan.steps.map((title, i) => {
                const stepNum = i + 1
                const isCurrent = plan.currentStepIndex === stepNum
                const summary = plan.stepSummaries[stepNum]
                const isDone = !!summary
                return (
                  <li key={stepNum} className="flex gap-2">
                    <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                      isDone ? 'bg-green-500/20 text-green-400' : isCurrent ? 'bg-blue-500/30 text-blue-300' : 'bg-white/10 text-white/40'
                    }`}>
                      {isDone ? '✓' : stepNum}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className={`text-sm truncate ${isCurrent && !isDone ? 'text-white font-medium' : 'text-white/70'}`}>
                        {title}
                      </p>
                      {summary && <p className="text-xs text-white/40 mt-0.5 line-clamp-2">{summary}</p>}
                    </div>
                  </li>
                )
              })}
            </ul>
          </div>
        )}
        <div className="flex-1 min-w-0 flex flex-col">
          <ThinkingStream
            userMessage={session?.taskDescription ?? null}
            blocks={displayBlocks}
            isRunning={isRunning}
            canRetry={isTerminal}
            onRetry={(task) => retryMutation.mutate(task)}
            isRetrying={retryMutation.isPending}
          />
        </div>
      </div>

      {/* Follow-up input: Gemini 风格 — 单条圆角输入条，输入与发送同一行 */}
      {session && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!followup.trim() || !effectiveSessionId || session.status !== 'COMPLETED') return
            continueMutation.mutate(followup.trim())
          }}
          className="flex-shrink-0 px-4 py-4 sm:px-6"
        >
          <div className="max-w-3xl mx-auto w-full">
            <div className="flex items-end gap-0 rounded-2xl bg-white/[0.06] border border-white/10 shadow-lg focus-within:border-white/20 focus-within:ring-1 focus-within:ring-white/10 transition-all">
              <textarea
                value={followup}
                onChange={e => setFollowup(e.target.value)}
                rows={1}
                className="flex-1 min-h-[52px] max-h-32 py-3 pl-4 pr-2 bg-transparent text-sm text-white placeholder-white/35 resize-none focus:outline-none rounded-l-2xl rounded-r-none"
                placeholder={
                  session.status !== 'COMPLETED'
                    ? '当前正在执行中，等待本轮结束后可继续提问'
                    : '输入消息，Enter 发送 · Shift+Enter 换行'
                }
              />
              <button
                type="submit"
                disabled={
                  !followup.trim() ||
                  continueMutation.isPending ||
                  !effectiveSessionId ||
                  session.status !== 'COMPLETED'
                }
                className="flex-shrink-0 p-3 rounded-l-none rounded-r-2xl text-white/70 hover:text-white hover:bg-white/10 disabled:opacity-40 disabled:hover:bg-transparent disabled:cursor-not-allowed transition-colors"
                title="发送"
              >
                {continueMutation.isPending ? (
                  <span className="text-xs">发送中</span>
                ) : (
                  <Send className="w-5 h-5" />
                )}
              </button>
            </div>
            <p className="mt-1.5 text-center text-xs text-white/35">
              {session.status !== 'COMPLETED' ? '需等待本轮回答结束后再发送' : '继续提问将基于当前会话上下文'}
            </p>
          </div>
        </form>
      )}
    </div>
  )
}
