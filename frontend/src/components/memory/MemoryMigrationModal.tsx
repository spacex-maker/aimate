import { useEffect, useRef } from 'react'
import { X, Loader, CheckCircle2, AlertTriangle } from 'lucide-react'

export interface MigrationProgressState {
  status: 'IDLE' | 'RUNNING' | 'DONE' | 'ERROR' | 'CANCELLED'
  totalSessions: number
  processedSessions: number
  writtenMemories: number
  currentTask?: string | null
  error?: string | null
  stepLog: string[]
}

export const INITIAL_MIGRATION_PROGRESS: MigrationProgressState = {
  status: 'IDLE',
  totalSessions: 0,
  processedSessions: 0,
  writtenMemories: 0,
  stepLog: [],
}

interface Props {
  open: boolean
  onClose: () => void
  progress: MigrationProgressState
  onCancel?: () => void
  isCancelling?: boolean
}

export function MemoryMigrationModal({ open, onClose, progress, onCancel, isCancelling }: Props) {
  if (!open) return null

  const { status, totalSessions, processedSessions, writtenMemories, currentTask, error, stepLog } = progress
  const pct = totalSessions > 0 ? Math.round((processedSessions / totalSessions) * 100) : 0
  const stepLogEndRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    stepLogEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [stepLog.length])

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#111111] border border-white/10 rounded-2xl w-full max-w-lg shadow-2xl">
        <div className="flex items-center justify-between px-5 py-3 border-b border-white/10">
          <h2 className="text-sm font-semibold text-white">同步对话到记忆</h2>
          <button
            onClick={onClose}
            className="text-white/40 hover:text-white/80 rounded-lg p-1 hover:bg-white/10 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="px-5 py-4 space-y-4">
          <p className="text-xs text-white/60 leading-relaxed">
            系统会遍历当前用户的所有会话，将用户与助手消息
            <span className="text-white"> 向量化 </span>
            后写入当前默认向量模型对应的集合。该操作只会<strong className="font-semibold">追加</strong>新向量，不会删除旧数据。
          </p>

          {stepLog.length > 0 && (
            <div className="rounded-lg bg-black/30 border border-white/10 overflow-hidden">
              <div className="px-3 py-2 border-b border-white/10 text-[11px] text-white/50 font-medium">
                步骤详情
              </div>
              <div className="px-3 py-2 max-h-40 overflow-y-auto font-mono text-[11px] text-white/70 space-y-1">
                {stepLog.map((line, i) => (
                  <div key={i} className="flex items-start gap-2">
                    <span className="text-white/40 shrink-0">{i + 1}.</span>
                    <span>{line}</span>
                  </div>
                ))}
                <div ref={stepLogEndRef} />
              </div>
            </div>
          )}

          <div>
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs text-white/50">同步进度</span>
              <span className="text-[11px] text-white/40">
                {processedSessions}/{totalSessions || '…'} 个会话
              </span>
            </div>
            <div className="w-full h-2 rounded-full bg-white/5 overflow-hidden">
              <div
                className={`h-full rounded-full ${
                  status === 'DONE'
                    ? 'bg-emerald-400'
                    : status === 'ERROR'
                      ? 'bg-red-500'
                      : status === 'CANCELLED'
                        ? 'bg-amber-500'
                        : 'bg-blue-500'
                }`}
                style={{ width: `${pct}%` }}
              />
            </div>
            <div className="mt-1 text-[11px] text-white/40">
              已写入记忆：<span className="text-white/70">{writtenMemories}</span> 条
            </div>
          </div>

          {currentTask && status === 'RUNNING' && (
            <div className="px-3 py-2 rounded-lg bg-white/[0.03] border border-white/10">
              <div className="text-[11px] text-white/40 mb-1">当前会话</div>
              <div className="text-xs text-white/75 truncate" title={currentTask}>
                {currentTask}
              </div>
            </div>
          )}

          {status === 'ERROR' && error && (
            <div className="flex items-start gap-2 rounded-lg bg-red-500/10 border border-red-500/40 px-3 py-2">
              <AlertTriangle className="w-4 h-4 text-red-400 mt-0.5" />
              <p className="text-xs text-red-200/90 break-words">{error}</p>
            </div>
          )}

          {status === 'DONE' && (
            <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/40 px-3 py-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              <p className="text-xs text-emerald-100/90">
                同步完成，共写入 <span className="font-semibold">{writtenMemories}</span> 条长期记忆。
              </p>
            </div>
          )}

          {status === 'CANCELLED' && (
            <div className="flex items-center gap-2 rounded-lg bg-amber-500/10 border border-amber-500/40 px-3 py-2">
              <AlertTriangle className="w-4 h-4 text-amber-400" />
              <p className="text-xs text-amber-100/90">
                已中断同步，已写入 <span className="font-semibold">{writtenMemories}</span> 条记忆。
              </p>
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-white/10 flex justify-end gap-2">
          {status === 'RUNNING' && onCancel && (
            <button
              type="button"
              onClick={onCancel}
              disabled={isCancelling}
              className="px-3 py-1.5 rounded-lg border border-amber-500/40 text-xs text-amber-400 hover:bg-amber-500/10 transition-colors disabled:opacity-50"
            >
              {isCancelling ? '请求中…' : '中断同步'}
            </button>
          )}
          <button
            onClick={onClose}
            className="px-3 py-1.5 rounded-lg border border-white/15 text-xs text-white/70 hover:text-white hover:bg-white/10 transition-colors"
          >
            关闭
          </button>
          {status === 'RUNNING' && (
            <div className="flex items-center gap-1 text-xs text-white/50">
              <Loader className="w-3.5 h-3.5 animate-spin" />
              正在同步中，请稍候…
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

