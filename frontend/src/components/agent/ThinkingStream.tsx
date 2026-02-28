import { useEffect, useRef } from 'react'
import { RotateCw } from 'lucide-react'
import { ToolCallCard } from './ToolCallCard'
import type { StreamBlock } from '../../types/agent'

interface Props {
  /** 用户首条消息（Gemini 风格下作为对话列表第一条展示） */
  userMessage: string | null
  blocks: StreamBlock[]
  isRunning: boolean
  /** 会话已终止时展示重试按钮 */
  canRetry?: boolean
  /** 点击重试：用同一任务发起新会话（由父组件跳转） */
  onRetry?: (task: string) => void
}

export function ThinkingStream({ userMessage, blocks, isRunning, canRetry, onRetry }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [blocks, userMessage])

  const isEmpty = !userMessage && blocks.length === 0

  if (isEmpty) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-white/25 text-sm">输入问题后，Agent 将在此展示思考与回答</p>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
        {/* 用户消息：Gemini 风格气泡，靠右；终止后可重试 */}
        {userMessage && (
          <div className="flex justify-end gap-2 items-end">
            {canRetry && onRetry && (
              <button
                type="button"
                onClick={() => onRetry(userMessage)}
                className="flex-shrink-0 flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-white/50 hover:text-white hover:bg-white/10 transition-colors"
                title="用相同问题重新发起会话"
              >
                <RotateCw className="w-3.5 h-3.5" /> 重试
              </button>
            )}
            <div className="max-w-[85%] rounded-2xl rounded-tr-md bg-white/10 px-4 py-3 text-sm text-white/95 leading-relaxed">
              {userMessage}
            </div>
          </div>
        )}

        {/* 助手回复区域：合并为一块，内部按 block 排列 */}
        {(blocks.length > 0) && (
          <div className="space-y-4">
            {blocks.map((block) => {
              if (block.kind === 'iteration') {
                return (
                  <div key={block.id} className="flex items-center gap-3 py-2">
                    <div className="h-px flex-1 bg-white/5" />
                    <span className="text-[11px] text-white/25 uppercase tracking-wider">
                      第 {block.number} 轮
                    </span>
                    <div className="h-px flex-1 bg-white/5" />
                  </div>
                )
              }

              if (block.kind === 'thinking') {
                return (
                  <div key={block.id} className="animate-fade-in">
                    <div className="text-white/55 text-sm leading-relaxed whitespace-pre-wrap break-words">
                      {block.content}
                      {!block.complete && isRunning && (
                        <span className="inline-block w-2 h-4 bg-blue-400/80 ml-0.5 animate-pulse align-middle rounded-sm" />
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
                  <div key={block.id} className="animate-fade-in pt-1">
                    <div className="text-white/90 text-[15px] leading-relaxed whitespace-pre-wrap break-words">
                      {block.content}
                    </div>
                  </div>
                )
              }

              if (block.kind === 'error') {
                return (
                  <div
                    key={block.id}
                    className="rounded-xl bg-red-500/10 border border-red-500/20 px-4 py-3 animate-fade-in"
                  >
                    <p className="text-xs text-red-400/90 mb-1">错误</p>
                    <p className="text-sm text-red-300/80">{block.message}</p>
                  </div>
                )
              }

              return null
            })}
          </div>
        )}

        <div ref={bottomRef} />
      </div>
    </div>
  )
}
