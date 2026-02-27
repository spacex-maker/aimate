import { useEffect, useRef } from 'react'
import { ToolCallCard } from './ToolCallCard'
import type { StreamBlock } from '../../types/agent'

interface Props {
  blocks: StreamBlock[]
  isRunning: boolean
}

export function ThinkingStream({ blocks, isRunning }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  // Auto-scroll to bottom as new content arrives
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [blocks])

  if (blocks.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-white/20 text-sm">等待 Agent 开始思考...</p>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4 font-mono text-sm">
      {blocks.map((block) => {
        if (block.kind === 'iteration') {
          return (
            <div key={block.id} className="flex items-center gap-3 py-1 animate-fade-in">
              <div className="h-px flex-1 bg-white/10" />
              <span className="text-xs text-white/30 font-mono">
                ─── 第 {block.number} 轮思考 ───
              </span>
              <div className="h-px flex-1 bg-white/10" />
            </div>
          )
        }

        if (block.kind === 'thinking') {
          return (
            <div key={block.id} className="animate-fade-in">
              <div className="text-white/70 leading-relaxed whitespace-pre-wrap break-words">
                {block.content}
                {!block.complete && isRunning && (
                  <span className="inline-block w-2 h-4 bg-blue-400 ml-0.5 animate-blink align-middle" />
                )}
              </div>
            </div>
          )
        }

        if (block.kind === 'toolCall') {
          return (
            <div key={block.id} className="animate-fade-in">
              <ToolCallCard call={block.call} result={block.result} />
            </div>
          )
        }

        if (block.kind === 'finalAnswer') {
          return (
            <div
              key={block.id}
              className="border border-blue-500/40 bg-blue-500/5 rounded-xl p-5 animate-fade-in"
            >
              <div className="text-xs text-blue-400 font-mono mb-2 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-400 inline-block" />
                最终答案
              </div>
              <div className="text-white/90 leading-relaxed whitespace-pre-wrap break-words">
                {block.content}
              </div>
            </div>
          )
        }

        if (block.kind === 'error') {
          return (
            <div
              key={block.id}
              className="border border-red-500/40 bg-red-500/5 rounded-xl p-4 animate-fade-in"
            >
              <div className="text-xs text-red-400 font-mono mb-1">错误</div>
              <div className="text-red-300 text-xs">{block.message}</div>
            </div>
          )
        }

        return null
      })}
      <div ref={bottomRef} />
    </div>
  )
}
