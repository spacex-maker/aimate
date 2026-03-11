import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { Send, Plus, SlidersHorizontal, Mic, ChevronDown } from 'lucide-react'
import { ToolSettingsPanel } from './ToolSettingsPanel'
import { agentApi } from '../../api/agent'
import type { AvailableModelsDto, SystemModelDto, UserModelDto } from '../../types/agent'

/** provider 转展示用公司名（如 openai → OpenAI，xai → xAI） */
function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

export interface AgentInputBoxProps {
  value: string
  onChange: (value: string) => void
  /**
   * 提交时回调，携带当前模型选择信息（若有）。
   * HomePage 不关心模型时可以忽略参数。
   */
  onSubmit: (options?: { modelSource?: 'SYSTEM' | 'USER_KEY'; systemModelId?: number | null; userApiKeyId?: number | null }) => void
  placeholder: string
  hintText: string
  isPending: boolean
  pendingLabel: string
  submitTitle: string
  disabled?: boolean
  /** 允许外层自定义根节点额外样式（例如控制台页左侧预留会话列表宽度） */
  className?: string
  /** 当前选中的系统模型 id；不传则仅展示选择器，由内部维护状态 */
  selectedSystemModelId?: number | null
  /** 用户切换模型时回调；不传则仅内部状态 */
  onSystemModelChange?: (model: SystemModelDto | null) => void
  /** 模型选择变化时的回调（包含 source / id 信息），用于页面级逻辑（如重试时携带当前模型） */
  onModelChange?: (selection: { source: 'SYSTEM' | 'USER_KEY'; systemModelId?: number | null; userApiKeyId?: number | null }) => void
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
  className,
  selectedSystemModelId: controlledModelId,
  onSystemModelChange,
  onModelChange,
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
  const [selectedSource, setSelectedSource] = useState<'SYSTEM' | 'USER_KEY'>('SYSTEM')
  const [selectedUserModel, setSelectedUserModel] = useState<UserModelDto | null>(null)

  const { data: availableModels, isLoading: modelsLoading } = useQuery<AvailableModelsDto>({
    queryKey: ['available-models'],
    queryFn: () => agentApi.getAvailableModels(),
  })
  const systemModels: SystemModelDto[] = availableModels?.systemModels ?? []
  const userModels: UserModelDto[] = availableModels?.userModels ?? []
  const userDefaultModel = availableModels?.defaultModel

  const [initializedFromDefault, setInitializedFromDefault] = useState(false)
  const selectedModelId = controlledModelId ?? internalModelId
  const selectedModel =
    selectedSource === 'SYSTEM' && selectedModelId != null
      ? systemModels.find((m) => m.id === selectedModelId) ?? null
      : null
  const displayModelLabel = modelsLoading
    ? '模型加载中…'
    : selectedSource === 'USER_KEY' && selectedUserModel
      ? `${formatProvider(selectedUserModel.provider)} · ${selectedUserModel.label || selectedUserModel.model || '我的模型'}`
      : selectedModel
        ? `${formatProvider(selectedModel.provider)} · ${selectedModel.displayName}`
        : '模型'

  // 首次进入时，优先使用后端记录的用户首选模型；仅初始化一次，不再覆盖用户手动选择
  useEffect(() => {
    if (controlledModelId != null) return
    if (initializedFromDefault) return
    if ((!systemModels || systemModels.length === 0) && userModels.length === 0) return

    // 用户首选为用户自有模型
    if (userDefaultModel?.source === 'USER_KEY' && userDefaultModel.userApiKeyId != null) {
      const u = userModels.find(x => x.id === userDefaultModel.userApiKeyId)
      if (u) {
        setSelectedSource('USER_KEY')
        setSelectedUserModel(u)
        setInternalModelId(null)
        onModelChange?.({ source: 'USER_KEY', userApiKeyId: u.id ?? null })
        setInitializedFromDefault(true)
        return
      }
    }

    // 用户首选为系统模型
    const sysDefaultId =
      userDefaultModel?.source === 'SYSTEM' && userDefaultModel.systemModelId != null
        ? userDefaultModel.systemModelId
        : null

    if (sysDefaultId != null && systemModels.some(m => m.id === sysDefaultId)) {
      setSelectedSource('SYSTEM')
      setSelectedUserModel(null)
      setInternalModelId(sysDefaultId)
      onModelChange?.({ source: 'SYSTEM', systemModelId: sysDefaultId })
      setInitializedFromDefault(true)
      return
    }

    // 没有用户记录或记录已失效时，使用排序权重最高的系统模型（列表第一条）
    if (systemModels.length > 0) {
      setSelectedSource('SYSTEM')
      setSelectedUserModel(null)
      setInternalModelId(systemModels[0].id)
      onModelChange?.({ source: 'SYSTEM', systemModelId: systemModels[0].id ?? null })
      setInitializedFromDefault(true)
    } else if (userModels.length > 0) {
      // 仅有用户模型时，默认选第一条用户模型
      setSelectedSource('USER_KEY')
      setSelectedUserModel(userModels[0])
      setInternalModelId(null)
      onModelChange?.({ source: 'USER_KEY', userApiKeyId: userModels[0].id ?? null })
      setInitializedFromDefault(true)
    }
  }, [controlledModelId, initializedFromDefault, systemModels, userModels, userDefaultModel])

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
    else {
      setSelectedSource('SYSTEM')
      setSelectedUserModel(null)
      setInternalModelId(m.id)
    }
    // 记住用户最近一次选择的系统模型（首选模型）
    if (m.id != null) {
      agentApi.setUserDefaultModel({ source: 'SYSTEM', systemModelId: m.id }).catch(() => {
        // 记忆失败不影响前端切换，不做打扰性提示
      })
    }
    onModelChange?.({ source: 'SYSTEM', systemModelId: m.id ?? null })
    setModelOpen(false)
  }

  const handleSelectUserModel = (u: UserModelDto) => {
    setSelectedSource('USER_KEY')
    setSelectedUserModel(u)
    setInternalModelId(null)
    if (u.id != null) {
      agentApi.setUserDefaultModel({ source: 'USER_KEY', userApiKeyId: u.id }).catch(() => {
        // 忽略记忆失败
      })
    }
    onModelChange?.({ source: 'USER_KEY', userApiKeyId: u.id ?? null })
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
    const payload = {
      modelSource: selectedSource,
      systemModelId: selectedSource === 'SYSTEM' ? selectedModelId ?? null : null,
      userApiKeyId: selectedSource === 'USER_KEY' && selectedUserModel ? selectedUserModel.id ?? null : null,
    }
    onSubmit(payload)
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={`fixed bottom-0 left-[var(--sidebar-width,0)] right-0 px-4 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 flex justify-center ${className ?? ''}`}
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
                  const payload = {
                    modelSource: selectedSource,
                    systemModelId: selectedSource === 'SYSTEM' ? selectedModelId ?? null : null,
                    userApiKeyId: selectedSource === 'USER_KEY' && selectedUserModel ? selectedUserModel.id ?? null : null,
                  }
                  onSubmit(payload)
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
                  onClick={() => !modelsLoading && setModelOpen((o) => !o)}
                  disabled={modelsLoading}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs transition-colors max-w-[180px] truncate ${
                    modelsLoading
                      ? 'text-white/40 bg-white/5 cursor-wait'
                      : modelOpen
                        ? 'text-white/90 bg-white/10'
                        : 'text-white/50 hover:text-white/80 hover:bg-white/10'
                  }`}
                  title={modelsLoading ? '模型列表加载中…' : selectedModel ? `${formatProvider(selectedModel.provider)} · ${selectedModel.displayName}` : '选择推理模型'}
                >
                  <span className="truncate">{displayModelLabel}</span>
                  <ChevronDown className="w-3.5 h-3.5 flex-shrink-0" />
                </button>
                {!modelsLoading && modelOpen &&
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
                          {systemModels.length === 0 && userModels.length === 0 ? (
                            <p className="text-xs text-white/50 py-3 px-2">暂无可用模型</p>
                          ) : (
                            <>
                              {userModels.length > 0 && (
                                <div className="mb-1">
                                  <div className="px-2 py-1 text-[11px] text-white/40 uppercase tracking-wide">
                                    我的模型
                                  </div>
                                  {userModels.map((u) => (
                                    <button
                                      key={`user-${u.id}`}
                                      type="button"
                                      onClick={() => handleSelectUserModel(u)}
                                      className={`w-full text-left px-3 py-1.5 text-xs rounded-lg transition-colors ${
                                        selectedSource === 'USER_KEY' && selectedUserModel?.id === u.id
                                          ? 'bg-white/15 text-white/95'
                                          : 'text-white/80 hover:bg-white/10'
                                      }`}
                                      title={u.model || u.label || ''}
                                    >
                                      <span className="flex items-center gap-2 flex-wrap">
                                        <span className="text-white/55 font-medium shrink-0">
                                          {formatProvider(u.provider)}
                                        </span>
                                        <span className="truncate">
                                          {u.label || u.model || '(未命名模型)'}
                                        </span>
                                      </span>
                                    </button>
                                  ))}
                                </div>
                              )}
                              {systemModels.length > 0 && (
                                <div>
                                  {userModels.length > 0 && (
                                    <div className="px-2 py-1 text-[11px] text-white/40 uppercase tracking-wide">
                                      系统模型
                                    </div>
                                  )}
                                  {systemModels.map((m) => (
                                    <button
                                      key={m.id}
                                      type="button"
                                      onClick={() => handleSelectModel(m)}
                                      className={`w-full text-left px-3 py-2 rounded-lg text-xs transition-colors ${
                                        selectedSource === 'SYSTEM' && m.id === selectedModelId
                                          ? 'bg-white/15 text-white/95'
                                          : 'text-white/80 hover:bg-white/10'
                                      }`}
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
                                  ))}
                                </div>
                              )}
                            </>
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
