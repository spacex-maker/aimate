import { useState } from 'react'
import { ChevronDown, ChevronRight, Wrench, CheckCircle, Loader } from 'lucide-react'
import type { ToolCall } from '../../types/agent'

interface Props {
  call: ToolCall
  result: string | null
}

export function ToolCallCard({ call, result }: Props) {
  const [open, setOpen] = useState(false)

  let args: unknown
  try { args = JSON.parse(call.function.arguments) } catch { args = call.function.arguments }

  const isPending = result === null

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
