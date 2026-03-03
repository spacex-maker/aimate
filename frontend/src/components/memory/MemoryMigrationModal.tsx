import { useEffect, useState } from 'react'
import { X, Loader, CheckCircle2, AlertTriangle } from 'lucide-react'
import type { MemoryMigrationEvent } from '../../types/memory'
import { useMemoryMigrationSocket } from '../../hooks/useMemoryMigrationSocket'

interface Props {
  open: boolean
  onClose: () => void
  userId: number | null
}

interface ProgressState {
  status: 'IDLE' | 'RUNNING' | 'DONE' | 'ERROR'
  totalSessions: number
  processedSessions: number
  writtenMemories: number
  currentTask?: string | null
  error?: string | null
}

export function MemoryMigrationModal({ open, onClose, userId }: Props) {
  const [progress, setProgress] = useState<ProgressState>({
    status: 'IDLE',
    totalSessions: 0,
    processedSessions: 0,
    writtenMemories: 0,
  })

  useEffect(() => {
    if (!open) {
      setProgress({
        status: 'IDLE',
        totalSessions: 0,
        processedSessions: 0,
        writtenMemories: 0,
      })
    }
  }, [open])

  useMemoryMigrationSocket(open ? userId ?? null : null, (event: MemoryMigrationEvent) => {
    if (!open) return
    if (event.type === 'START') {
      setProgress({
        status: 'RUNNING',
        totalSessions: 0,
        processedSessions: 0,
        writtenMemories: 0,
      })
    } else if (event.type === 'PROGRESS') {
      setProgress(prev => ({
        ...prev,
        status: 'RUNNING',
        totalSessions: event.totalSessions,
        processedSessions: event.processedSessions,
        writtenMemories: event.writtenMemories,
        currentTask: event.currentTaskDescription ?? prev.currentTask,
      }))
    } else if (event.type === 'DONE') {
      setProgress(prev => ({
        ...prev,
        status: 'DONE',
        totalSessions: event.totalSessions,
        processedSessions: event.totalSessions,
        writtenMemories: event.writtenMemories,
        currentTask: null,
      }))
    } else if (event.type === 'ERROR') {
      setProgress(prev => ({
        ...prev,
        status: 'ERROR',
        error: event.error ?? '同步过程中发生错误',
      }))
    }
  })

  if (!open) return null

  const { status, totalSessions, processedSessions, writtenMemories, currentTask, error } = progress
  const pct = totalSessions > 0 ? Math.round((processedSessions / totalSessions) * 100) : 0

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
            系统会遍历当前用户的所有会话，将用户与助手消息重新写入
            <span className="text-white"> 当前默认向量模型 </span>
            对应的 Milvus Collection。该操作只会<strong className="font-semibold">追加</strong>新向量，不会删除旧数据。
          </p>

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
                  status === 'DONE' ? 'bg-emerald-400' : status === 'ERROR' ? 'bg-red-500' : 'bg-blue-500'
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
        </div>

        <div className="px-5 py-3 border-t border-white/10 flex justify-end gap-2">
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

