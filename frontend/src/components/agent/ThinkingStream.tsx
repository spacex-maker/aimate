import { Fragment, useEffect, useRef, useState } from 'react'
import { RotateCw, Square, History, Brain, ChevronDown, ChevronUp } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { agentApi } from '../../api/agent'
import { ToolCallCard } from './ToolCallCard'
import { MarkdownContent } from './MarkdownContent'
import type { AssistantVersionDto, ChatMessageDto, StreamBlock } from '../../types/agent'

/** 思考+工具调用区域固定高度，单区按事件顺序滚动（思考与工具调用穿插显示） */
const BLOCKS_AREA_HEIGHT = 'min(70vh, 520px)'

function BlocksArea({ blocks, isRunning }: { blocks: StreamBlock[]; isRunning: boolean }) {
  const lastBlock = blocks[blocks.length - 1]
  const streamingThinkingInProgress = isRunning && lastBlock?.kind === 'thinking' && !lastBlock.complete

  return (
    <div className="w-full min-w-0 flex flex-col rounded-xl border border-white/10 bg-white/[0.06] overflow-hidden" style={{ height: BLOCKS_AREA_HEIGHT }}>
      <div className="px-3 py-2 flex items-center gap-2 border-b border-white/5 bg-white/[0.03] shrink-0">
        <Brain className="w-4 h-4 shrink-0 text-white/50" />
        <span className="text-xs font-medium text-white/60">思考与工具调用</span>
        {streamingThinkingInProgress && <span className="text-white/40 text-xs">（输出中…）</span>}
      </div>
      {/* 按事件顺序：思考内容、第 N 轮、工具调用、思考、… 在同一流中显示 */}
      <div className="flex-1 min-h-0 overflow-y-auto px-4 py-3 space-y-4">
        {blocks.length === 0 ? (
          <p className="text-white/40 text-sm">暂无内容</p>
        ) : (
          blocks.map((block) => {
            if (block.kind === 'thinking') {
              return (
                <div key={block.id} className="text-sm text-white/70 leading-relaxed whitespace-pre-wrap break-words">
                  {block.content}
                  {streamingThinkingInProgress && block === lastBlock && (
                    <span className="inline-block w-2 h-4 bg-blue-400/80 ml-0.5 animate-pulse align-middle rounded-sm" />
                  )}
                </div>
              )
            }
            if (block.kind === 'iteration') {
              return (
                <div key={block.id} className="flex items-center gap-3 py-2">
                  <div className="h-px flex-1 bg-white/5" />
                  <span className="text-[11px] text-white/25 uppercase tracking-wider">第 {block.number} 轮</span>
                  <div className="h-px flex-1 bg-white/5" />
                </div>
              )
            }
            if (block.kind === 'toolCall') {
              return (
                <div key={block.id} className="animate-fade-in">
                  <ToolCallCard call={block.call} result={block.result} streamingOutput={block.streamingOutput} />
                </div>
              )
            }
            if (block.kind === 'finalAnswer') {
              return (
                <div key={block.id} className="animate-fade-in pt-1 text-white/90 text-[15px] leading-relaxed">
                  <MarkdownContent content={block.content} className="break-words" />
                </div>
              )
            }
            if (block.kind === 'error') {
              return (
                <div key={block.id} className="rounded-xl bg-red-500/10 border border-red-500/20 px-4 py-3 animate-fade-in">
                  <p className="text-xs text-red-400/90 mb-1">错误</p>
                  <p className="text-sm text-red-300/80">{block.message}</p>
                </div>
              )
            }
            return null
          })
        )}
      </div>
    </div>
  )
}

function VersionsButton({ sessionId, messageId, open, onToggle }: { sessionId: string; messageId: number; open: boolean; onToggle: () => void }) {
  const panelRef = useRef<HTMLDivElement>(null)
  const { data: versions, isLoading } = useQuery({
    queryKey: ['message-versions', sessionId, messageId],
    queryFn: () => agentApi.getMessageVersions(sessionId, messageId),
    enabled: open && !!sessionId && !!messageId,
  })
  const [selectedVersion, setSelectedVersion] = useState<AssistantVersionDto | null>(null)
  useEffect(() => { if (!open) setSelectedVersion(null) }, [open])

  // 打开后保持打开，仅点击面板外时关闭
  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        onToggle()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open, onToggle])

  return (
    <div ref={panelRef} className={`relative transition-opacity ${open ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`}>
      <button
        type="button"
        onClick={() => { if (!open) onToggle() }}
        className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs text-white/50 hover:text-white hover:bg-white/10"
        title="查看历史版本"
      >
        <History className="w-3.5 h-3.5" /> 历史版本
      </button>
      {open && (
        <div className="absolute left-0 top-full mt-1 z-10 min-w-[280px] max-w-[90vw] rounded-lg bg-gray-900 border border-white/10 shadow-xl overflow-hidden flex flex-col max-h-[70vh]">
          {isLoading ? (
            <div className="p-3 text-white/50 text-sm">加载中…</div>
          ) : versions && versions.length > 0 ? (
            <>
              <div className="p-2 border-b border-white/10 text-xs text-white/50 shrink-0">共 {versions.length} 个版本（上下文以最新为准）</div>
              <div className="overflow-auto shrink-0 max-h-[200px]">
                {versions.map((v) => (
                  <button
                    key={v.version}
                    type="button"
                    onClick={() => setSelectedVersion(prev => (prev?.version === v.version ? null : v))}
                    className={`w-full text-left px-3 py-2 text-sm border-b border-white/5 last:border-0 hover:bg-white/5 ${selectedVersion?.version === v.version ? 'bg-white/10' : ''}`}
                  >
                    <span className="text-white/70">v{v.version}</span>
                    {v.createTime && <span className="ml-2 text-white/40 text-xs">{v.createTime.slice(0, 19).replace('T', ' ')}</span>}
                  </button>
                ))}
              </div>
              {selectedVersion && (
                <div className="border-t border-white/10 flex-1 min-h-0 flex flex-col">
                  <div className="p-2 text-xs text-white/50 shrink-0">v{selectedVersion.version} 内容</div>
                  <div className="p-3 text-sm text-white/80 whitespace-pre-wrap break-words overflow-auto flex-1">
                    {selectedVersion.content || '(无)'}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="p-3 text-white/50 text-sm">暂无历史版本</div>
          )}
        </div>
      )}
    </div>
  )
}

interface Props {
  /** 用户首条消息（无历史时展示；有历史时由 historyMessages 展示） */
  userMessage: string | null
  /** 从服务端加载的历史消息，点击会话时展示 */
  historyMessages?: ChatMessageDto[] | null
  blocks: StreamBlock[]
  isRunning: boolean
  /** 重试时：被重试的那条用户消息 id，流式内容插到该条下方或下一条 assistant 位置 */
  retryTargetUserMessageId?: number | null
  canRetry?: boolean
  /** 重试该条用户消息（用此前上下文重新生成下一条 assistant） */
  onRetry?: (userMessageId: number) => void
  isRetrying?: boolean
  /** 消息级中断：传入 assistant 消息 id */
  onInterrupt?: (assistantMessageId: number) => void
  /** 会话 id，用于拉取 assistant 历史版本 */
  sessionId?: string
}

export function ThinkingStream({ userMessage, historyMessages, blocks, isRunning, retryTargetUserMessageId, canRetry, onRetry, onInterrupt, sessionId }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)
  const [versionsOpenForId, setVersionsOpenForId] = useState<number | null>(null)
  const [expandedThinkingIndex, setExpandedThinkingIndex] = useState<number | null>(null)

  const hasHistory = historyMessages && historyMessages.length > 0
  const isEmpty = !userMessage && blocks.length === 0 && !hasHistory
  const retryIndex = hasHistory && retryTargetUserMessageId != null
    ? historyMessages!.findIndex(m => m.role === 'user' && m.id === retryTargetUserMessageId)
    : -1
  const nextSlotIsAssistant = retryIndex >= 0 && historyMessages![retryIndex + 1]?.role === 'assistant'
  const showBlocksInline = retryTargetUserMessageId != null && retryIndex >= 0 && blocks.length > 0
  const showBlocksAtBottom = blocks.length > 0 && !showBlocksInline

  useEffect(() => {
    if (!showBlocksAtBottom) return
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [blocks, userMessage, historyMessages, showBlocksAtBottom])

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
        {/* 历史消息：按条展示；重试时流式内容插到被重试用户消息下方或下一条 assistant 位置 */}
        {hasHistory && (
          <>
            {historyMessages!.map((msg, i) => {
              if (showBlocksInline && nextSlotIsAssistant && i === retryIndex + 1) {
                const assistantMsg = msg
                return (
                  <div key={`retry-reply-${i}`} className="group flex justify-start">
                    <div className="flex flex-col items-start gap-1.5 max-w-[85%] w-full min-w-0">
                      <div className="rounded-2xl rounded-tl-md px-4 py-3 text-sm leading-relaxed bg-white/5 text-white/80 w-full min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <span className="text-amber-400/90 text-xs">再次回答中…</span>
                          {assistantMsg.id != null && onInterrupt && (
                            <button
                              type="button"
                              onClick={() => onInterrupt(assistantMsg.id!)}
                              className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1 px-1.5 py-0.5 text-[11px] text-amber-400/90 hover:text-amber-300 border border-amber-400/30 rounded"
                              title="中断该条回复"
                            >
                              <Square className="w-3 h-3" /> 中断
                            </button>
                          )}
                        </div>
                        <BlocksArea blocks={blocks} isRunning={isRunning} />
                      </div>
                    </div>
                  </div>
                )
              }
              const messageRow = (
                <div
                  key={i}
                  className={`group ${msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'}`}
                >
                  <div className={msg.role === 'user' ? 'flex flex-col items-end gap-1.5 max-w-[85%]' : 'flex flex-col items-start gap-1.5 max-w-[85%]'}>
                  {/* AI 回复消息卡上方：思考（展开后含思考文字 + 工具调用，工具显示在思考内容下方） */}
                  {msg.role === 'assistant' && (msg.thinkingContent || (msg.toolCalls && msg.toolCalls.length > 0)) && (
                    <div className="w-full min-w-0 mb-2">
                      <button
                        type="button"
                        onClick={() => setExpandedThinkingIndex(prev => (prev === i ? null : i))}
                        className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium text-white/60 hover:text-white/90 hover:bg-white/10 border border-white/10 hover:border-white/20 transition-colors shrink-0"
                        title={expandedThinkingIndex === i ? '收起' : '点击查阅思考过程与工具调用'}
                      >
                        <Brain className="w-4 h-4 shrink-0 text-white/50" />
                        <span>思考</span>
                        {msg.toolCalls && msg.toolCalls.length > 0 && (
                          <span className="text-white/40">（含 {msg.toolCalls.length} 次工具调用）</span>
                        )}
                        {expandedThinkingIndex === i ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                      </button>
                      {/* 展开后：先思考文字，再工具调用列表，均不超出消息区域 */}
                      {expandedThinkingIndex === i && (
                        <div className="mt-2 w-full min-w-0 rounded-xl border border-white/10 bg-white/[0.06] overflow-hidden">
                          <div className="px-4 py-3 text-sm text-white/70 leading-relaxed max-h-[28rem] overflow-auto space-y-4">
                            {msg.thinkingContent && (
                              <div className="whitespace-pre-wrap break-words">
                                <p className="text-[11px] text-white/40 uppercase tracking-wider mb-2">思考过程</p>
                                {msg.thinkingContent}
                              </div>
                            )}
                            {msg.toolCalls && msg.toolCalls.length > 0 && (
                              <div className="space-y-2 overflow-x-auto overflow-y-visible">
                                <p className="text-[11px] text-white/40 uppercase tracking-wider mb-1.5">调用的工具</p>
                                {msg.toolCalls.map((tc, idx) => (
                                  <ToolCallCard
                                    key={idx}
                                    call={{ id: '', type: 'function', function: { name: tc.name, arguments: tc.arguments } }}
                                    result={tc.result ?? null}
                                  />
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                  <div
                    className={`rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                      msg.role === 'user'
                        ? 'rounded-tr-md bg-white/10 text-white/95'
                        : 'rounded-tl-md bg-white/5 text-white/80'
                    }`}
                  >
                    {msg.role === 'assistant' && msg.messageStatus === 'ANSWERING' && (
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-amber-400/90 text-xs">回答中…</span>
                        {msg.id != null && onInterrupt && (
                          <button
                            type="button"
                            onClick={() => onInterrupt(msg.id!)}
                            className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1 px-1.5 py-0.5 text-[11px] text-amber-400/90 hover:text-amber-300 border border-amber-400/30 rounded"
                            title="中断该条回复"
                          >
                            <Square className="w-3 h-3" /> 中断
                          </button>
                        )}
                      </div>
                    )}
                    {msg.role === 'assistant' && msg.messageStatus === 'INTERRUPTED' && (
                      <span className="text-white/45 text-xs block mb-1">已中断</span>
                    )}
                    {msg.role === 'assistant' ? (
                      <MarkdownContent content={msg.content || (msg.messageStatus === 'ANSWERING' ? '' : '(无内容)')} className="break-words" />
                    ) : (
                      <span className="whitespace-pre-wrap break-words">{msg.content || ''}</span>
                  )}
                  {msg.createTime && (
                    <div className="mt-1 text-[10px] text-white/30 text-right">
                      {msg.createTime.replace('T', ' ').slice(0, 19)}
                    </div>
                  )}
                  </div>
                  {msg.role === 'user' && canRetry && onRetry && msg.content && msg.id != null && (
                    <button
                      type="button"
                      onClick={() => onRetry(msg.id!)}
                      className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs text-white/50 hover:text-white hover:bg-white/10"
                      title="用此前上下文重新生成该条回复"
                    >
                      <RotateCw className="w-3.5 h-3.5" /> 重试
                    </button>
                  )}
                  {msg.role === 'assistant' && (msg.messageStatus === 'DONE' || msg.messageStatus === 'INTERRUPTED') && msg.id != null && sessionId && (
                    <VersionsButton
                      sessionId={sessionId}
                      messageId={msg.id}
                      open={versionsOpenForId === msg.id}
                      onToggle={() => setVersionsOpenForId(prev => (prev === msg.id ? null : msg.id!))}
                    />
                  )}
                </div>
              </div>
            )
              if (showBlocksInline && !nextSlotIsAssistant && i === retryIndex) {
                return (
                  <Fragment key={i}>
                    {messageRow}
                    <div className="flex justify-start">
                      <div className="flex flex-col items-start gap-1.5 max-w-[85%] w-full min-w-0">
                        <div className="rounded-2xl rounded-tl-md px-4 py-3 text-sm leading-relaxed bg-white/5 text-white/80 w-full min-w-0">
                          {isRunning && (
                            <div className="flex items-center gap-2 mb-2">
                              <span className="text-amber-400/90 text-xs">再次回答中…</span>
                            </div>
                          )}
                          <BlocksArea blocks={blocks} isRunning={isRunning} />
                        </div>
                      </div>
                    </div>
                  </Fragment>
                )
              }
              return messageRow
            })}
          </>
        )}
        {/* 当前轮用户首条消息（无历史时展示；有历史时由上面展示） */}
        {userMessage && !hasHistory && (
          <div className="group flex justify-end gap-2 items-end">
            {/* 首条无 id 时不显示重试；重试需传 userMessageId */}
            <div className="max-w-[85%] rounded-2xl rounded-tr-md bg-white/10 px-4 py-3 text-sm text-white/95 leading-relaxed">
              {userMessage}
            </div>
          </div>
        )}

        {/* 非重试时：助手回复区域在列表最底部；重试时已在上面插到对应位置 */}
        {showBlocksAtBottom && (
          <div className="flex justify-start">
            <div className="flex flex-col items-start gap-1.5 max-w-[85%] w-full min-w-0">
              <BlocksArea blocks={blocks} isRunning={isRunning} />
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>
    </div>
  )
}
