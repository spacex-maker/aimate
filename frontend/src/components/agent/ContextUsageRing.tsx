import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Minimize2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { formatTokenCount } from '../../lib/estimateTokens'
import { agentApi } from '../../api/agent'

export interface ContextUsageRingProps {
  percent: number
  usedTokens: number
  limitTokens: number
  systemTokens?: number
  conversationTokens?: number
  messageTokens?: number
  toolsTokens?: number
  draftTokens?: number
  /** 服务端建议压缩（≥72% 或消息量偏多） */
  compressRecommended?: boolean
  /** 有会话时可手动压缩 */
  sessionId?: string | null
  systemModelId?: number | null
  className?: string
}

function ringColor(percent: number, recommend?: boolean): string {
  if (percent >= 90) return '#f87171'
  if (percent >= 72 || recommend) return '#fbbf24'
  if (percent >= 70) return '#fbbf24'
  return '#34d399'
}

/**
 * Cursor 风格上下文占用圆环：进度圈 + 分项明细 + 手动压缩。
 */
export function ContextUsageRing({
  percent,
  usedTokens,
  limitTokens,
  systemTokens = 0,
  conversationTokens = 0,
  messageTokens = 0,
  toolsTokens = 0,
  draftTokens = 0,
  compressRecommended = false,
  sessionId = null,
  systemModelId = null,
  className,
}: ContextUsageRingProps) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [anchor, setAnchor] = useState<{ left: number; bottom: number } | null>(null)

  const size = 18
  const stroke = 2.25
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const clamped = Math.min(100, Math.max(0, percent))
  const suggest = compressRecommended || percent >= 72
  const visiblePct = usedTokens > 0 ? Math.max(clamped, 1.5) : 0
  const dash = (Math.min(100, visiblePct) / 100) * c
  const color = ringColor(percent, suggest)
  const label = `${percent >= 10 ? Math.round(percent) : percent.toFixed(1)}%`
  const sys = systemTokens
  const conv = conversationTokens > 0 ? conversationTokens : Math.max(0, messageTokens - systemTokens)

  const compressMut = useMutation({
    mutationFn: () => agentApi.compressContext(sessionId!, systemModelId),
    onSuccess: (r) => {
      if (r.ok) {
        toast.success(
          `已压缩 ${r.compressedMessages} 条 · ${formatTokenCount(r.usageBefore.usedTokens)} → ${formatTokenCount(r.usageAfter.usedTokens)}`
        )
      } else {
        toast(r.message || '暂无需压缩', { icon: 'ℹ️' })
      }
      qc.invalidateQueries({ queryKey: ['context-usage', sessionId] })
    },
    onError: (e: Error) => toast.error(e.message || '压缩失败'),
  })

  const title = useMemo(
    () => `上下文 ${label} · ${formatTokenCount(usedTokens)} / ${formatTokenCount(limitTokens)}`,
    [label, usedTokens, limitTokens]
  )

  return (
    <div className={`relative flex items-center gap-1 ${className ?? ''}`}>
      <button
        type="button"
        className="flex items-center gap-1.5 rounded-lg px-1.5 py-1 text-white/50 hover:text-white/80 hover:bg-white/[0.06] transition-colors"
        title={title}
        aria-label={title}
        onClick={(e) => {
          const rect = e.currentTarget.getBoundingClientRect()
          setAnchor({ left: rect.left + rect.width / 2, bottom: window.innerHeight - rect.top })
          setOpen((o) => !o)
        }}
      >
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="block -rotate-90" aria-hidden>
          <circle
            cx={size / 2}
            cy={size / 2}
            r={r}
            fill="none"
            stroke="rgba(255,255,255,0.12)"
            strokeWidth={stroke}
          />
          <circle
            cx={size / 2}
            cy={size / 2}
            r={r}
            fill="none"
            stroke={color}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${dash} ${c - dash}`}
            className="transition-[stroke-dasharray] duration-300 ease-out"
          />
        </svg>
        <span className="hidden sm:inline text-[10px] font-mono tabular-nums text-white/40">
          {formatTokenCount(usedTokens)}
        </span>
      </button>

      {open &&
        anchor &&
        createPortal(
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => setOpen(false)}
              role="presentation"
              aria-hidden
            />
            <div
              className="fixed z-50 w-64 -translate-x-1/2 rounded-2xl bg-[#141820]/96 backdrop-blur-xl shadow-[0_24px_64px_-16px_rgba(0,0,0,0.8)] px-3.5 py-3 ring-1 ring-inset ring-white/[0.06]"
              style={{ left: anchor.left, bottom: anchor.bottom + 8 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-baseline justify-between gap-2 mb-2">
                <span className="text-xs font-medium text-white/90">上下文窗口</span>
                <span className="text-xs font-mono tabular-nums" style={{ color }}>
                  {label}
                </span>
              </div>
              <div className="h-1.5 rounded-full bg-white/[0.08] overflow-hidden mb-2.5">
                <div
                  className="h-full rounded-full transition-all duration-300"
                  style={{ width: `${clamped}%`, background: color }}
                />
              </div>
              {suggest && (
                <div className="mb-2 rounded-lg border border-amber-400/30 bg-amber-400/10 px-2 py-1.5 text-[10px] text-amber-100/90 leading-relaxed">
                  建议压缩：占用已较高。将更早轮次收成结构化状态，近期原文与钉住消息保留。
                </div>
              )}
              <p className="mb-2 text-[10px] text-white/35 leading-relaxed">
                按当前模型上限估算。压缩为结构化会话状态 + 近期原文；聊天记录不删。
              </p>
              <ul className="space-y-1 text-[11px] text-white/55">
                {sys > 0 && (
                  <li className="flex justify-between gap-2">
                    <span>系统提示</span>
                    <span className="font-mono text-white/75">{formatTokenCount(sys)}</span>
                  </li>
                )}
                <li className="flex justify-between gap-2">
                  <span>对话 / 摘要</span>
                  <span className="font-mono text-white/75">{formatTokenCount(conv)}</span>
                </li>
                {toolsTokens > 0 && (
                  <li className="flex justify-between gap-2">
                    <span>工具定义</span>
                    <span className="font-mono text-white/75">{formatTokenCount(toolsTokens)}</span>
                  </li>
                )}
                {draftTokens > 0 && (
                  <li className="flex justify-between gap-2">
                    <span>当前输入</span>
                    <span className="font-mono text-white/75">{formatTokenCount(draftTokens)}</span>
                  </li>
                )}
                <li className="flex justify-between gap-2 pt-1.5 mt-0.5 border-t border-white/[0.06] text-white/75">
                  <span>合计 / 上限</span>
                  <span className="font-mono">
                    {formatTokenCount(usedTokens)} / {formatTokenCount(limitTokens)}
                  </span>
                </li>
              </ul>

              {sessionId && (
                <button
                  type="button"
                  disabled={compressMut.isPending}
                  onClick={() => compressMut.mutate()}
                  className={
                    suggest
                      ? 'mt-3 flex w-full items-center justify-center gap-1.5 rounded-xl bg-amber-500/20 py-2 text-xs font-medium text-amber-100 hover:bg-amber-500/30 disabled:opacity-40 transition-colors ring-1 ring-inset ring-amber-400/25'
                      : 'mt-3 flex w-full items-center justify-center gap-1.5 rounded-xl bg-white/[0.06] py-2 text-xs font-medium text-white/75 hover:bg-white/[0.1] hover:text-white disabled:opacity-40 transition-colors'
                  }
                >
                  <Minimize2 className="h-3.5 w-3.5" />
                  {compressMut.isPending ? '正在压缩…' : suggest ? '建议压缩上下文' : '压缩上下文'}
                </button>
              )}
            </div>
          </>,
          document.body
        )}
    </div>
  )
}
