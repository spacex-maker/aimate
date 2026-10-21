import { Pause, Play, StopCircle, ArrowLeft, Copy, SquarePen } from 'lucide-react'
import type { SessionResponse } from '../../types/agent'

interface SessionHeaderProps {
  session?: SessionResponse
  sessionId?: string
  effectiveSessionId?: string
  isRunning: boolean
  isPaused: boolean
  isTerminal: boolean
  pausePending: boolean
  resumePending: boolean
  abortPending: boolean
  onBack: () => void
  onNewSession: () => void
  onCopyId: () => void
  onPause: () => void
  onResume: () => void
  onAbort: () => void
}

export function SessionHeader({
  session,
  sessionId,
  effectiveSessionId,
  isRunning,
  isPaused,
  isTerminal,
  pausePending,
  resumePending,
  abortPending,
  onBack,
  onNewSession,
  onCopyId,
  onPause,
  onResume,
  onAbort,
}: SessionHeaderProps) {
  return (
    <div className="flex-shrink-0 border-b border-white/10 px-6 py-4 flex items-center gap-4">
      <button
        onClick={onBack}
        className="text-white/30 hover:text-white/70 transition-colors"
        title="返回控制台"
      >
        <ArrowLeft className="w-4 h-4" />
      </button>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-3">
          {session && <span className="inline-flex"><span className="sr-only">{session.status}</span></span>}
          {/* StatusBadge 由外层负责展示，这里仅展示 ID 与迭代信息 */}
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

      <div className="flex items-center gap-2 flex-shrink-0">
        <button
          type="button"
          onClick={onNewSession}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-blue-300 border border-blue-500/30 hover:bg-blue-500/10 rounded-lg transition-colors"
          title="新建会话"
        >
          <SquarePen className="w-3.5 h-3.5" /> 新建会话
        </button>
        <button
          onClick={onCopyId}
          disabled={!effectiveSessionId}
          className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Copy className="w-4 h-4" />
        </button>

        {isRunning && (
          <button
            onClick={onPause}
            disabled={pausePending || !effectiveSessionId}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-orange-400 border border-orange-400/30 hover:bg-orange-400/10 rounded-lg transition-colors disabled:opacity-50"
          >
            <Pause className="w-3.5 h-3.5" /> 暂停
          </button>
        )}

        {isPaused && (
          <button
            onClick={onResume}
            disabled={resumePending || !effectiveSessionId}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-green-400 border border-green-400/30 hover:bg-green-400/10 rounded-lg transition-colors disabled:opacity-50"
          >
            <Play className="w-3.5 h-3.5" /> 恢复
          </button>
        )}

        {!isTerminal && (
          <button
            onClick={onAbort}
            disabled={abortPending || !effectiveSessionId}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-400 border border-red-400/30 hover:bg-red-400/10 rounded-lg transition-colors disabled:opacity-50"
          >
            <StopCircle className="w-3.5 h-3.5" /> 中止
          </button>
        )}
      </div>
    </div>
  )
}

