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
    <div className="border border-white/10 rounded-lg overflow-hidden animate-fade-in">
      {/* Header */}
      <button
        onClick={() => setOpen(v => !v)}
        className="w-full flex items-center gap-3 px-4 py-3 bg-white/5 hover:bg-white/8 transition-colors text-left"
      >
        <div className="flex items-center gap-2 flex-1 min-w-0">
          <Wrench className="w-4 h-4 text-purple-400 flex-shrink-0" />
          <span className="text-sm font-mono text-purple-300 font-medium truncate">
            {call.function.name}
          </span>
          {isPending ? (
            <Loader className="w-3 h-3 text-white/40 animate-spin ml-auto flex-shrink-0" />
          ) : (
            <CheckCircle className="w-3 h-3 text-green-400 ml-auto flex-shrink-0" />
          )}
        </div>
        <span className="text-white/30">
          {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </span>
      </button>

      {/* Body */}
      {open && (
        <div className="px-4 py-3 space-y-3 bg-black/20">
          <div>
            <div className="text-[10px] font-mono text-white/30 uppercase mb-1">Parameters</div>
            <pre className="text-xs text-white/70 font-mono bg-black/30 rounded p-2 overflow-x-auto whitespace-pre-wrap break-all">
              {JSON.stringify(args, null, 2)}
            </pre>
          </div>

          {result !== null && (
            <div>
              <div className="text-[10px] font-mono text-white/30 uppercase mb-1">Result</div>
              <pre className="text-xs text-green-300/80 font-mono bg-black/30 rounded p-2 overflow-x-auto whitespace-pre-wrap break-all max-h-48">
                {result}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
