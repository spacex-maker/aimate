import { useCallback, useReducer, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pause, Play, StopCircle, ArrowLeft, Copy } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../api/agent'
import { useAgentSocket } from '../hooks/useAgentSocket'
import { ThinkingStream } from '../components/agent/ThinkingStream'
import { StatusBadge } from '../components/agent/StatusBadge'
import type { AgentEvent, StreamBlock, ToolCall } from '../types/agent'

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

  const { data: session, refetch } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => agentApi.getSession(sessionId!),
    refetchInterval: (query) => {
      const s = query.state.data?.status
      return s === 'RUNNING' || s === 'PENDING' ? 3000 : false
    },
    enabled: !!sessionId,
  })

  const pauseMutation = useMutation({
    mutationFn: () => agentApi.pauseSession(sessionId!),
    onSuccess: () => { refetch(); toast.success('已暂停') },
    onError: (e: Error) => toast.error(e.message),
  })
  const resumeMutation = useMutation({
    mutationFn: () => agentApi.resumeSession(sessionId!),
    onSuccess: () => { refetch(); toast.success('已恢复') },
    onError: (e: Error) => toast.error(e.message),
  })
  const abortMutation = useMutation({
    mutationFn: () => agentApi.abortSession(sessionId!),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['recent-sessions'] }); refetch() },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleEvent = useCallback((event: AgentEvent) => {
    switch (event.type) {
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

  useAgentSocket(sessionId ?? null, handleEvent)

  const isRunning = session?.status === 'RUNNING'
  const isPaused = session?.status === 'PAUSED'
  const isTerminal = session?.status === 'COMPLETED' || session?.status === 'FAILED'

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
            onClick={() => { navigator.clipboard.writeText(sessionId ?? ''); toast.success('已复制') }}
            className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors"
          >
            <Copy className="w-4 h-4" />
          </button>

          {isRunning && (
            <button
              onClick={() => pauseMutation.mutate()}
              disabled={pauseMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-orange-400 border border-orange-400/30 hover:bg-orange-400/10 rounded-lg transition-colors"
            >
              <Pause className="w-3.5 h-3.5" /> 暂停
            </button>
          )}
          {isPaused && (
            <button
              onClick={() => resumeMutation.mutate()}
              disabled={resumeMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-green-400 border border-green-400/30 hover:bg-green-400/10 rounded-lg transition-colors"
            >
              <Play className="w-3.5 h-3.5" /> 恢复
            </button>
          )}
          {!isTerminal && (
            <button
              onClick={() => abortMutation.mutate()}
              disabled={abortMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-400 border border-red-400/30 hover:bg-red-400/10 rounded-lg transition-colors"
            >
              <StopCircle className="w-3.5 h-3.5" /> 中止
            </button>
          )}
        </div>
      </div>

      {/* Stream */}
      <ThinkingStream blocks={blocks} isRunning={isRunning} />
    </div>
  )
}
