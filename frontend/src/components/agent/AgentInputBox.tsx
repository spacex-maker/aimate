import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { Send, Plus, SlidersHorizontal, Mic, ChevronDown } from 'lucide-react'
import { ToolSettingsPanel } from './ToolSettingsPanel'
import { agentApi } from '../../api/agent'
import type { SystemModelDto } from '../../types/agent'

/** provider 转展示用公司名（如 openai → OpenAI，xai → xAI） */
function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

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
  /** 当前选中的系统模型 id；不传则仅展示选择器，由内部维护状态 */
  selectedSystemModelId?: number | null
  /** 用户切换模型时回调；不传则仅内部状态 */
  onSystemModelChange?: (model: SystemModelDto | null) => void
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
  selectedSystemModelId: controlledModelId,
  onSystemModelChange,
}: AgentInputBoxProps) {
  const canSubmit = value.trim() !== '' && !isPending && !disabled
  const [glow, setGlow] = useState(false)
  const [toolsOpen, setToolsOpen] = useState(false)
  const [toolsAnchor, setToolsAnchor] = useState<{ left: number; bottom: number } | null>(null)
  const toolsButtonRef = useRef<HTMLButtonElement>(null)
  const [modelOpen, setModelOpen] = useState(false)
  const [modelAnchor, setModelAnchor] = useState<{ left: number; bottom: number } | null>(null)
  const modelButtonRef = useRef<HTMLButtonElement>(null)
  const [internalModelId, setInternalModelId] = useState<number | null>(null)

  const { data: systemModels = [] } = useQuery({
    queryKey: ['system-models'],
    queryFn: () => agentApi.getSystemModels(),
  })
  const selectedModelId = controlledModelId ?? internalModelId
  const selectedModel = selectedModelId != null ? systemModels.find((m) => m.id === selectedModelId) ?? null : null
  const displayModelLabel = selectedModel
    ? `${formatProvider(selectedModel.provider)} · ${selectedModel.displayName}`
    : '模型'

  useEffect(() => {
    if (!toolsOpen || !toolsButtonRef.current) return
    const rect = toolsButtonRef.current.getBoundingClientRect()
    setToolsAnchor({ left: rect.left, bottom: window.innerHeight - rect.top })
  }, [toolsOpen])

  useEffect(() => {
    if (!modelOpen || !modelButtonRef.current) return
    const rect = modelButtonRef.current.getBoundingClientRect()
    setModelAnchor({ left: rect.left, bottom: window.innerHeight - rect.top })
  }, [modelOpen])

  const handleSelectModel = (m: SystemModelDto) => {
    if (onSystemModelChange) onSystemModelChange(m)
    else setInternalModelId(m.id)
    setModelOpen(false)
  }

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
              <div className="relative flex items-center">
                <button
                  ref={modelButtonRef}
                  type="button"
                  onClick={() => setModelOpen((o) => !o)}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs transition-colors max-w-[180px] truncate ${modelOpen ? 'text-white/90 bg-white/10' : 'text-white/50 hover:text-white/80 hover:bg-white/10'}`}
                  title={selectedModel ? `${formatProvider(selectedModel.provider)} · ${selectedModel.displayName}` : '选择推理模型'}
                >
                  <span className="truncate">{displayModelLabel}</span>
                  <ChevronDown className="w-3.5 h-3.5 flex-shrink-0" />
                </button>
                {modelOpen &&
                  modelAnchor &&
                  createPortal(
                    <>
                      <div
                        className="fixed inset-0 z-40"
                        onClick={() => setModelOpen(false)}
                        role="presentation"
                        aria-hidden
                      />
                      <div
                        className="fixed z-50 w-64 max-h-72 overflow-y-auto rounded-xl bg-[#1a1a1a] border border-white/10 shadow-xl"
                        style={{
                          left: modelAnchor.left,
                          bottom: modelAnchor.bottom + 8,
                        }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="px-3 py-2 border-b border-white/10">
                          <h3 className="text-xs font-semibold text-white/80">选择模型</h3>
                        </div>
                        <div className="p-1.5 space-y-0.5">
                          {systemModels.length === 0 ? (
                            <p className="text-xs text-white/50 py-3 px-2">暂无可用模型</p>
                          ) : (
                            systemModels.map((m) => (
                              <button
                                key={m.id}
                                type="button"
                                onClick={() => handleSelectModel(m)}
                                className={`w-full text-left px-3 py-2 rounded-lg text-xs transition-colors ${m.id === selectedModelId ? 'bg-white/15 text-white/95' : 'text-white/80 hover:bg-white/10'}`}
                                title={m.description ?? `${formatProvider(m.provider)} ${m.displayName}`}
                              >
                                <span className="flex items-center gap-2 flex-wrap">
                                  <span className="text-white/55 font-medium shrink-0">
                                    {formatProvider(m.provider)}
                                  </span>
                                  <span className="font-medium truncate">{m.displayName}</span>
                                </span>
                                {m.description && (
                                  <span className="block text-white/50 mt-0.5 truncate">
                                    {m.description}
                                  </span>
                                )}
                              </button>
                            ))
                          )}
                        </div>
                      </div>
                    </>,
                    document.body
                  )}
              </div>
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
