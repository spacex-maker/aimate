import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { formatTokenCount } from '../../lib/estimateTokens'

export interface ContextUsageRingProps {
  /** 0–100+；超过 100 仍按满环展示 */
  percent: number
  usedTokens: number
  limitTokens: number
  messageTokens?: number
  toolsTokens?: number
  draftTokens?: number
  className?: string
}

function ringColor(percent: number): string {
  if (percent >= 90) return '#f87171' // red-400
  if (percent >= 70) return '#fbbf24' // amber-400
  return '#34d399' // emerald-400
}

/**
 * Cursor 风格上下文占用圆环：描边进度圈 + hover/点击明细。
 */
export function ContextUsageRing({
  percent,
  usedTokens,
  limitTokens,
  messageTokens = 0,
  toolsTokens = 0,
  draftTokens = 0,
  className,
}: ContextUsageRingProps) {
  const [open, setOpen] = useState(false)
  const [anchor, setAnchor] = useState<{ left: number; bottom: number } | null>(null)

  const size = 18
  const stroke = 2.25
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const clamped = Math.min(100, Math.max(0, percent))
  const dash = (clamped / 100) * c
  const color = ringColor(percent)
  const label = `${percent >= 10 ? Math.round(percent) : percent.toFixed(1)}%`

  const title = useMemo(
    () => `上下文 ${label} · ${formatTokenCount(usedTokens)} / ${formatTokenCount(limitTokens)}`,
    [label, usedTokens, limitTokens]
  )

  return (
    <div className={`relative flex items-center ${className ?? ''}`}>
      <button
        type="button"
        className="p-1.5 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
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
              className="fixed z-50 w-56 -translate-x-1/2 rounded-xl bg-[#1a1a1a] border border-white/10 shadow-xl px-3 py-2.5"
              style={{ left: anchor.left, bottom: anchor.bottom + 8 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-baseline justify-between gap-2 mb-2">
                <span className="text-xs font-medium text-white/90">上下文占用</span>
                <span className="text-xs font-mono tabular-nums" style={{ color }}>
                  {label}
                </span>
              </div>
              <div className="h-1.5 rounded-full bg-white/10 overflow-hidden mb-2.5">
                <div
                  className="h-full rounded-full transition-all duration-300"
                  style={{ width: `${clamped}%`, background: color }}
                />
              </div>
              <ul className="space-y-1 text-[11px] text-white/55">
                <li className="flex justify-between gap-2">
                  <span>对话 / 系统</span>
                  <span className="font-mono text-white/75">{formatTokenCount(messageTokens)}</span>
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
                <li className="flex justify-between gap-2 pt-1 border-t border-white/10 text-white/70">
                  <span>合计 / 上限</span>
                  <span className="font-mono">
                    {formatTokenCount(usedTokens)} / {formatTokenCount(limitTokens)}
                  </span>
                </li>
              </ul>
            </div>
          </>,
          document.body
        )}
    </div>
  )
}
