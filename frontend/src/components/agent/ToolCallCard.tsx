import { useState } from 'react'
import { ChevronDown, ChevronRight, Wrench, CheckCircle, Loader } from 'lucide-react'
import type { ToolCall } from '../../types/agent'

interface Props {
  call: ToolCall
  result: string | null
  /** 执行中时实时输出的日志片段（如 run_container_cmd 的 stdout） */
  streamingOutput?: string
  /** 执行耗时（毫秒），完成后展示在标题旁 */
  durationMs?: number | null
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const sec = ms / 1000
  if (sec < 60) return `${sec.toFixed(1)}s`
  const min = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${min}m${s}s`
}

export function ToolCallCard({ call, result, streamingOutput, durationMs }: Props) {
  const [open, setOpen] = useState(false)

  let args: unknown
  try { args = JSON.parse(call.function.arguments) } catch { args = call.function.arguments }

  const isPending = result === null
  const hasLiveOutput = isPending && (streamingOutput != null && streamingOutput.length > 0)

  return (
    <div className="rounded-xl bg-white/[0.04] border border-white/[0.08] overflow-hidden animate-fade-in">
      <button
        onClick={() => setOpen(v => !v)}
        className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-white/[0.04] transition-colors text-left"
      >
        <Wrench className="w-3.5 h-3.5 text-white/40 flex-shrink-0" />
        <span className="text-sm text-white/70 truncate flex-1 min-w-0">
          {call.function.name}
        </span>
        {durationMs != null && durationMs >= 0 && !isPending && (
          <span className="text-[11px] text-white/40 tabular-nums flex-shrink-0" title={`耗时 ${durationMs}ms`}>
            {formatDuration(durationMs)}
          </span>
        )}
        {isPending ? (
          <Loader className="w-3.5 h-3.5 text-white/40 animate-spin flex-shrink-0" />
        ) : (
          <CheckCircle className="w-3.5 h-3.5 text-emerald-400/80 flex-shrink-0" />
        )}
        <span className="text-white/25 flex-shrink-0">
          {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </span>
      </button>

      {open && (
        <div className="px-4 pb-4 pt-0 space-y-3 border-t border-white/[0.06] mt-0 pt-3">
          <div>
            <p className="text-[10px] text-white/30 uppercase tracking-wider mb-1">参数</p>
            <pre className="text-xs text-white/60 font-mono rounded-lg bg-black/20 p-3 overflow-x-auto whitespace-pre-wrap break-all">
              {JSON.stringify(args, null, 2)}
            </pre>
          </div>
          {isPending && (
            <div>
              <p className="text-[10px] text-white/30 uppercase tracking-wider mb-1 flex items-center gap-2">
                {hasLiveOutput ? '执行中（实时输出）' : '运行中…'}
                <span className="inline-block w-2 h-2 rounded-full bg-amber-400/80 animate-pulse" />
              </p>
              {hasLiveOutput && (
                <pre className="text-xs text-white/70 font-mono rounded-lg bg-black/20 p-3 overflow-x-auto whitespace-pre-wrap break-all max-h-48 overflow-y-auto">
                  {streamingOutput}
                </pre>
              )}
            </div>
          )}
          {result !== null && (
            <div>
              <p className="text-[10px] text-white/30 uppercase tracking-wider mb-1">结果</p>
              <pre className="text-xs text-white/70 font-mono rounded-lg bg-black/20 p-3 overflow-x-auto whitespace-pre-wrap break-all max-h-40">
                {result}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
