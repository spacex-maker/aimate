import { Pause, Play, StopCircle, ArrowLeft, Copy, Users } from 'lucide-react'
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
  effectiveSessionId,
  isRunning,
  isPaused,
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
  const title =
    session?.agentDisplayTitle ||
    (session?.agentName && session?.agentCareerName && session.agentName !== session.agentCareerName
      ? `${session.agentName} · ${session.agentCareerName}`
      : null) ||
    session?.agentName ||
    session?.taskDescription ||
    '对话'

  return (
    <div className="flex-shrink-0 border-b border-white/10 px-4 sm:px-6 py-3 flex items-center gap-3">
      <button
        onClick={onBack}
        className="text-white/30 hover:text-white/70 transition-colors p-1"
        title="返回"
      >
        <ArrowLeft className="w-4 h-4" />
      </button>

      <div className="flex-1 min-w-0">
        {/* 微信式：对方名字 · 招聘职业 */}
        <h1 className="text-base font-semibold text-white truncate leading-tight">{title}</h1>
        <div className="mt-0.5 flex items-center gap-2 text-[11px] text-white/35">
          {session && (
            <span>
              {isRunning ? '对方正在输入…' : isPaused ? '已暂停' : '点击输入框继续对话'}
            </span>
          )}
          {session && session.iterationCount > 0 && (
            <span className="text-white/25">· {session.iterationCount} 轮</span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1.5 flex-shrink-0">
        <button
          type="button"
          onClick={onNewSession}
          className="flex items-center gap-1 px-2.5 py-1.5 text-[11px] text-white/45 hover:text-white/75 hover:bg-white/5 rounded-lg transition-colors"
          title="换一位伙伴"
        >
          <Users className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">换伙伴</span>
        </button>
        <button
          onClick={onCopyId}
          disabled={!effectiveSessionId}
          className="p-2 text-white/25 hover:text-white/55 hover:bg-white/5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="复制会话 ID"
        >
          <Copy className="w-3.5 h-3.5" />
        </button>

        {isRunning && !isPaused && (
          <button
            type="button"
            disabled={pausePending}
            onClick={onPause}
            className="p-2 text-amber-300/80 hover:bg-amber-500/10 rounded-lg disabled:opacity-40"
            title="暂停"
          >
            <Pause className="w-3.5 h-3.5" />
          </button>
        )}
        {isPaused && (
          <button
            type="button"
            disabled={resumePending}
            onClick={onResume}
            className="p-2 text-emerald-300/80 hover:bg-emerald-500/10 rounded-lg disabled:opacity-40"
            title="继续"
          >
            <Play className="w-3.5 h-3.5" />
          </button>
        )}
        {(isRunning || isPaused) && (
          <button
            type="button"
            disabled={abortPending}
            onClick={onAbort}
            className="p-2 text-rose-300/80 hover:bg-rose-500/10 rounded-lg disabled:opacity-40"
            title="停止"
          >
            <StopCircle className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
    </div>
  )
}
