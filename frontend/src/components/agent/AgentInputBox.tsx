import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Send, Plus, SlidersHorizontal, Mic } from 'lucide-react'
import { ToolSettingsPanel } from './ToolSettingsPanel'

export interface AgentInputBoxProps {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  placeholder: string
  hintText: string
  isPending: boolean
  pendingLabel: string
  submitTitle: string
  disabled?: boolean
}

/**
 * 启动前（首页）与启动后（会话页）共用的底部输入框，布局完全一致：
 * 固定视口底部、安全区内边距、大圆角输入区 + 底部操作栏 + 提示文案。
 * 在有侧栏的布局中通过 --sidebar-width 与主内容区对齐，避免整体右偏。
 */
export function AgentInputBox({
  value,
  onChange,
  onSubmit,
  placeholder,
  hintText,
  isPending,
  pendingLabel,
  submitTitle,
  disabled = false,
}: AgentInputBoxProps) {
  const canSubmit = value.trim() !== '' && !isPending && !disabled
  const [glow, setGlow] = useState(false)
  const [toolsOpen, setToolsOpen] = useState(false)
  const [toolsAnchor, setToolsAnchor] = useState<{ left: number; bottom: number } | null>(null)
  const toolsButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!toolsOpen || !toolsButtonRef.current) return
    const rect = toolsButtonRef.current.getBoundingClientRect()
    setToolsAnchor({ left: rect.left, bottom: window.innerHeight - rect.top })
  }, [toolsOpen])

  useEffect(() => {
    if (!glow) return
    const t = setTimeout(() => setGlow(false), 1800)
    return () => clearTimeout(t)
  }, [glow])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    setGlow(true)
    onSubmit()
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="fixed bottom-0 left-[var(--sidebar-width,0)] right-0 px-4 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 flex justify-center"
    >
      <div className="max-w-3xl w-full flex flex-col items-center min-w-0">
        <div
          className={`rounded-3xl bg-white/[0.06] backdrop-blur-xl border border-white/10 shadow-xl focus-within:border-white/20 focus-within:ring-1 focus-within:ring-white/10 transition-all overflow-hidden flex flex-col min-h-[120px] w-full ${glow ? 'animate-input-glow border-white/20' : ''}`}
        >
          <textarea
            value={value}
            onChange={e => onChange(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                if (canSubmit) {
                  setGlow(true)
                  onSubmit()
                }
              }
            }}
            rows={1}
            className="flex-1 min-h-[56px] max-h-40 py-4 px-4 bg-transparent text-sm text-white placeholder-white/40 resize-none focus:outline-none"
            placeholder={placeholder}
          />
          <div className="flex-shrink-0 flex items-center justify-between px-3 py-2 border-t border-white/[0.06]">
            <div className="flex items-center gap-1 relative">
              <button
                type="button"
                className="p-2 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
                title="附加文件（敬请期待）"
              >
                <Plus className="w-4 h-4" />
              </button>
              <button
                ref={toolsButtonRef}
                type="button"
                onClick={() => setToolsOpen((o) => !o)}
                className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs transition-colors ${toolsOpen ? 'text-white/90 bg-white/10' : 'text-white/50 hover:text-white/80 hover:bg-white/10'}`}
                title="系统工具开关"
              >
                <SlidersHorizontal className="w-4 h-4" />
                <span>工具</span>
              </button>
              {toolsOpen &&
                toolsAnchor &&
                createPortal(
                  <>
                    <div
                      className="fixed inset-0 z-40"
                      onClick={() => setToolsOpen(false)}
                      role="presentation"
                      aria-hidden
                    />
                    <div
                      className="fixed z-50 w-72"
                      style={{
                        left: toolsAnchor.left,
                        bottom: toolsAnchor.bottom + 8,
                      }}
                    >
                      <ToolSettingsPanel onClose={() => setToolsOpen(false)} />
                    </div>
                  </>,
                  document.body
                )}
            </div>
            <div className="flex items-center gap-0.5">
              <button
                type="button"
                className="p-2 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
                title="语音输入（敬请期待）"
              >
                <Mic className="w-4 h-4" />
              </button>
              <button
                type="submit"
                disabled={!canSubmit}
                className="p-2.5 rounded-xl text-white/80 hover:text-white hover:bg-white/15 disabled:opacity-40 disabled:hover:bg-transparent disabled:cursor-not-allowed transition-colors"
                title={submitTitle}
              >
                {isPending ? (
                  <span className="text-xs px-1">{pendingLabel}</span>
                ) : (
                  <Send className="w-5 h-5" />
                )}
              </button>
            </div>
          </div>
        </div>
        <p className="mt-1.5 text-center text-xs text-white/35">
          {hintText}
        </p>
      </div>
    </form>
  )
}
