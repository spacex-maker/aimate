import { useState, useEffect, useMemo, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { Send, ImagePlus, SlidersHorizontal, Mic, ChevronDown, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { ToolSettingsPanel } from './ToolSettingsPanel'
import { ContextUsageRing } from './ContextUsageRing'
import { agentApi } from '../../api/agent'
import { estimateTokens } from '../../lib/estimateTokens'
import { fileToChatImage } from '../../lib/chatImage'
import type { AvailableModelsDto, SystemModelDto, UserModelDto } from '../../types/agent'
import {
  MentionPicker,
  detectMentionAt,
  filterMentions,
  type MentionCandidate,
} from '../chat/MentionPicker'

export type { MentionCandidate }

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
  onSubmit: (options?: { modelSource?: 'SYSTEM' | 'USER_KEY'; systemModelId?: number | null; userApiKeyId?: number | null; images?: string[] }) => void
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
  /** 底部提示行右侧的自定义插槽（例如执行计划小面板） */
  bottomRightSlot?: ReactNode
  /** 有会话时拉取服务端上下文占用；首页无会话则仅估算当前输入 */
  sessionId?: string | null
  /**
   * plain：群聊等场景复用同一外壳，隐藏模型/工具/上下文环（仍可传图）。
   * agent：默认，会话页完整能力。
   */
  variant?: 'agent' | 'plain'
  /** 群聊：输入 @ 后可快捷选择的成员 / Agent */
  mentionCandidates?: MentionCandidate[]
  /** 输入区顶部插槽（如引用条） */
  topSlot?: ReactNode
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
  bottomRightSlot,
  sessionId = null,
  variant = 'agent',
  mentionCandidates = [],
  topSlot,
}: AgentInputBoxProps) {
  const plain = variant === 'plain'
  const [images, setImages] = useState<string[]>([])
  const imageInputRef = useRef<HTMLInputElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const [mention, setMention] = useState<{ start: number; query: string } | null>(null)
  const [mentionIndex, setMentionIndex] = useState(0)
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
    enabled: !plain,
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
  const visionOn = plain || (selectedSource === 'SYSTEM' && !!selectedModel?.supportsVision)
  const canSubmit = (value.trim() !== '' || images.length > 0) && !isPending && !disabled

  const mentionEnabled = mentionCandidates.length > 0
  const mentionItems = useMemo(() => {
    if (!mention) return []
    return filterMentions(mentionCandidates, mention.query).slice(0, 12)
  }, [mention, mentionCandidates])

  useEffect(() => {
    setMentionIndex(0)
  }, [mention?.start, mention?.query, mentionItems.length])

  const syncMentionFromCaret = (text: string, caret: number) => {
    if (!mentionEnabled) {
      setMention(null)
      return
    }
    setMention(detectMentionAt(text, caret))
  }

  const applyMention = (item: MentionCandidate) => {
    if (!mention) return
    const ta = textareaRef.current
    const caret = ta?.selectionStart ?? value.length
    const before = value.slice(0, mention.start)
    const after = value.slice(caret)
    const insert = `@${item.label} `
    const next = before + insert + after
    onChange(next)
    setMention(null)
    requestAnimationFrame(() => {
      const el = textareaRef.current
      if (!el) return
      const pos = before.length + insert.length
      el.focus()
      el.setSelectionRange(pos, pos)
    })
  }

  /** 占用率分母：以当前选中大模型声明的上下文窗口为准 */
  const modelLimit =
    selectedSource === 'SYSTEM' && selectedModel?.contextWindowTokens && selectedModel.contextWindowTokens > 0
      ? selectedModel.contextWindowTokens
      : 128_000

  const { data: contextUsage } = useQuery({
    queryKey: ['context-usage', sessionId, selectedSource === 'SYSTEM' ? selectedModelId : null],
    queryFn: () =>
      agentApi.getContextUsage(
        sessionId!,
        selectedSource === 'SYSTEM' ? selectedModelId ?? null : null
      ),
    enabled: !!sessionId,
    refetchInterval: sessionId ? 15_000 : false,
    staleTime: 5_000,
  })

  // 系统模型：始终用模型配置的窗口；自有密钥：用接口回退默认
  const contextLimit =
    selectedSource === 'SYSTEM' && modelLimit > 0
      ? modelLimit
      : contextUsage?.limitTokens && contextUsage.limitTokens > 0
        ? contextUsage.limitTokens
        : modelLimit
  const draftTokens = useMemo(() => estimateTokens(value), [value])
  const systemTokens = contextUsage?.systemTokens ?? 0
  const conversationTokens = contextUsage?.conversationTokens ?? 0
  const messageTokens = contextUsage?.messageTokens ?? systemTokens + conversationTokens
  const toolsTokens = contextUsage?.toolsTokens ?? (sessionId ? 1800 : 0)
  const usedTokens = messageTokens + toolsTokens + draftTokens
  const usagePercent = Math.min(100, Math.round((usedTokens * 1000) / Math.max(1, contextLimit)) / 10)

  // 按用户期望：按钮上仅显示模型名称，不再附带 provider，名称过长时允许区域适当变宽
  const displayModelLabel = modelsLoading
    ? '模型加载中…'
    : selectedSource === 'USER_KEY' && selectedUserModel
      ? selectedUserModel.label || selectedUserModel.model || '我的模型'
      : selectedModel
        ? selectedModel.displayName
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

  const addImageFiles = async (files: FileList | File[]) => {
    const list = Array.from(files).filter((f) => f.type.startsWith('image/'))
    if (list.length === 0) return
    const room = 4 - images.length
    if (room <= 0) {
      toast.error('最多 4 张图片')
      return
    }
    const next: string[] = []
    for (const file of list.slice(0, room)) {
      try {
        next.push(await fileToChatImage(file))
      } catch {
        toast.error('图片处理失败')
      }
    }
    if (next.length) setImages((prev) => [...prev, ...next].slice(0, 4))
  }

  const submitPayload = () => {
    const payload = {
      modelSource: selectedSource,
      systemModelId: selectedSource === 'SYSTEM' ? selectedModelId ?? null : null,
      userApiKeyId: selectedSource === 'USER_KEY' && selectedUserModel ? selectedUserModel.id ?? null : null,
      images: images.length ? images : undefined,
    }
    setImages([])
    setMention(null)
    onSubmit(payload)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    setGlow(true)
    submitPayload()
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={`fixed bottom-0 left-[var(--sidebar-width,0)] right-0 px-4 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 flex justify-center ${className ?? ''}`}
    >
      <div className="max-w-3xl w-full flex flex-col items-center min-w-0">
        <div
          className={`relative rounded-3xl bg-white/[0.06] backdrop-blur-xl border border-white/10 shadow-xl focus-within:border-white/20 focus-within:ring-1 focus-within:ring-white/10 transition-all overflow-visible flex flex-col min-h-[128px] w-full ${glow ? 'animate-input-glow border-white/20' : ''}`}
        >
          {mention && (
            <MentionPicker
              items={mentionItems}
              activeIndex={Math.min(mentionIndex, Math.max(0, mentionItems.length - 1))}
              onHover={setMentionIndex}
              onSelect={applyMention}
              query={mention.query}
            />
          )}
          <div className="rounded-3xl overflow-hidden flex flex-col flex-1 min-h-[128px]">
          {topSlot}
          {images.length > 0 && (
            <div className="flex gap-2 px-4 pt-2.5">
              {images.map((src, i) => (
                <div key={i} className="relative">
                  <img src={src} alt="" className="h-14 w-14 rounded-lg object-cover border border-white/10" />
                  <button
                    type="button"
                    onClick={() => setImages((prev) => prev.filter((_, idx) => idx !== i))}
                    className="absolute -right-1 -top-1 rounded-full bg-black/80 p-0.5 text-white/80"
                    aria-label="移除图片"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              ))}
            </div>
          )}
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => {
              const next = e.target.value
              onChange(next)
              syncMentionFromCaret(next, e.target.selectionStart ?? next.length)
            }}
            onClick={(e) => {
              const el = e.currentTarget
              syncMentionFromCaret(el.value, el.selectionStart ?? el.value.length)
            }}
            onKeyUp={(e) => {
              if (['ArrowUp', 'ArrowDown', 'Enter', 'Tab', 'Escape'].includes(e.key)) return
              const el = e.currentTarget
              syncMentionFromCaret(el.value, el.selectionStart ?? el.value.length)
            }}
            onKeyDown={(e) => {
              if (mention) {
                if (e.key === 'Escape') {
                  e.preventDefault()
                  setMention(null)
                  return
                }
                if (e.key === 'ArrowDown') {
                  e.preventDefault()
                  if (mentionItems.length === 0) return
                  setMentionIndex((i) => (i + 1) % mentionItems.length)
                  return
                }
                if (e.key === 'ArrowUp') {
                  e.preventDefault()
                  if (mentionItems.length === 0) return
                  setMentionIndex((i) => (i - 1 + mentionItems.length) % mentionItems.length)
                  return
                }
                if (e.key === 'Enter' || e.key === 'Tab') {
                  if (mentionItems.length > 0) {
                    e.preventDefault()
                    applyMention(mentionItems[Math.min(mentionIndex, mentionItems.length - 1)])
                    return
                  }
                }
              }
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                if (canSubmit) {
                  setGlow(true)
                  submitPayload()
                }
              }
            }}
            rows={2}
            className="flex-1 min-h-[72px] max-h-44 py-3.5 px-4 bg-transparent text-sm text-white placeholder-white/40 resize-none focus:outline-none leading-relaxed"
            placeholder={placeholder}
          />
          <div className="flex-shrink-0 flex items-center justify-between px-2.5 py-1 border-t border-white/[0.06]">
            <div className="flex items-center gap-0.5 relative">
              <input
                ref={imageInputRef}
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp"
                multiple
                className="hidden"
                onChange={(e) => {
                  if (e.target.files) void addImageFiles(e.target.files)
                  e.target.value = ''
                }}
              />
              <button
                type="button"
                disabled={!visionOn}
                onClick={() => imageInputRef.current?.click()}
                className="p-1.5 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors disabled:opacity-30 disabled:hover:bg-transparent disabled:cursor-not-allowed"
                title={
                  plain
                    ? '上传图片'
                    : visionOn
                      ? '上传图片，DeepSeek Flash 会看图回答'
                      : '当前模型不支持图片，请选择 DeepSeek V4.1 Flash'
                }
              >
                <ImagePlus className="w-4 h-4" />
              </button>
              {!plain && (
              <button
                ref={toolsButtonRef}
                type="button"
                onClick={() => setToolsOpen((o) => !o)}
                className={`flex items-center gap-1.5 px-2 py-1 rounded-lg text-xs transition-colors ${toolsOpen ? 'text-white/90 bg-white/10' : 'text-white/50 hover:text-white/80 hover:bg-white/10'}`}
                title="系统工具开关"
              >
                <SlidersHorizontal className="w-3.5 h-3.5" />
                <span>工具</span>
              </button>
              )}
              {!plain && toolsOpen &&
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
                      className="fixed z-50"
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
              {!plain && (
              <div className="relative flex items-center">
                <button
                  ref={modelButtonRef}
                  type="button"
                  onClick={() => !modelsLoading && setModelOpen((o) => !o)}
                  disabled={modelsLoading}
                  className={`flex items-center gap-1.5 px-2 py-1 rounded-lg text-xs transition-colors ${
                    modelsLoading
                      ? 'text-white/40 bg-white/5 cursor-wait'
                      : modelOpen
                        ? 'text-white/90 bg-white/10'
                        : 'text-white/50 hover:text-white/80 hover:bg-white/10'
                  }`}
                  title={
                    modelsLoading
                      ? '模型列表加载中…'
                      : [displayModelLabel, selectedModel?.tip].filter(Boolean).join(' · ')
                  }
                >
                  <span className="truncate max-w-[7rem] sm:max-w-[10rem]">{displayModelLabel}</span>
                  {selectedModel?.tip ? (
                    <span className="hidden sm:inline-block max-w-[9rem] truncate rounded px-1 py-0.5 text-[10px] leading-none bg-emerald-500/15 text-emerald-300/90 border border-emerald-500/20">
                      {selectedModel.tip}
                    </span>
                  ) : null}
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
                        className="fixed z-50 w-80 max-h-72 overflow-y-auto rounded-xl bg-[#1a1a1a] border border-white/10 shadow-xl"
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
                                      <div className="flex flex-col gap-0.5">
                                        <span className="text-white/90">
                                          {u.label || u.model || '(未命名模型)'}
                                        </span>
                                        <span className="text-[11px] text-white/45">
                                          {formatProvider(u.provider)}
                                        </span>
                                      </div>
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
                                      title={
                                        [m.tip, m.description].filter(Boolean).join(' · ')
                                        || `${formatProvider(m.provider)} ${m.displayName}`
                                      }
                                    >
                                      <div className="flex flex-col gap-0.5">
                                        <div className="flex items-center gap-1.5 min-w-0">
                                          <span className="font-medium text-white/90 truncate">{m.displayName}</span>
                                          {m.supportsVision ? (
                                            <span className="shrink-0 rounded px-1.5 py-0.5 text-[10px] leading-none bg-sky-500/15 text-sky-300/90 border border-sky-500/25">
                                              视觉
                                            </span>
                                          ) : null}
                                          {m.tip ? (
                                            <span className="shrink-0 max-w-[10rem] truncate rounded px-1.5 py-0.5 text-[10px] leading-none bg-emerald-500/15 text-emerald-300/90 border border-emerald-500/25">
                                              {m.tip}
                                            </span>
                                          ) : null}
                                        </div>
                                        <span className="text-[11px] text-white/45">
                                          {formatProvider(m.provider)}
                                        </span>
                                      </div>
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
              )}
              {!plain && (
              <ContextUsageRing
                percent={usagePercent}
                usedTokens={usedTokens}
                limitTokens={contextLimit}
                systemTokens={systemTokens}
                conversationTokens={conversationTokens}
                messageTokens={messageTokens}
                toolsTokens={toolsTokens}
                draftTokens={draftTokens}
                compressRecommended={
                  !!contextUsage?.compressRecommended || usagePercent >= 72
                }
                sessionId={sessionId}
                systemModelId={selectedSource === 'SYSTEM' ? selectedModelId : null}
              />
              )}
              {!plain && (
              <button
                type="button"
                className="p-1.5 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
                title="语音输入（敬请期待）"
              >
                <Mic className="w-4 h-4" />
              </button>
              )}
              <button
                type="submit"
                disabled={!canSubmit}
                className="p-2 rounded-xl text-white/80 hover:text-white hover:bg-white/15 disabled:opacity-40 disabled:hover:bg-transparent disabled:cursor-not-allowed transition-colors"
                title={submitTitle}
              >
                {isPending ? (
                  <span className="text-xs px-1">{pendingLabel}</span>
                ) : (
                  <Send className="w-4 h-4" />
                )}
              </button>
            </div>
          </div>
          </div>
        </div>
        <div className="mt-1.5 w-full flex items-center justify-between gap-2">
          <p className="text-xs text-white/35">
            {hintText}
          </p>
          {bottomRightSlot && (
            <div className="flex-shrink-0">
              {bottomRightSlot}
            </div>
          )}
        </div>
      </div>
    </form>
  )
}
