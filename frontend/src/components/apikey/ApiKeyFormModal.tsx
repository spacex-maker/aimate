import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { X, Eye, EyeOff, ChevronDown } from 'lucide-react'
import type { ApiKeyRequest, ApiKeyResponse, KeyType } from '../../types/apikey'
import { PROVIDERS, KEY_TYPE_LABELS, LLM_DEFAULT_MODELS } from '../../types/apikey'
import { agentApi } from '../../api/agent'
import type { SystemModelDto } from '../../types/agent'

function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

interface Props {
  initial: ApiKeyResponse | null
  onClose: () => void
  onSubmit: (body: ApiKeyRequest) => void
  isLoading: boolean
}

const KEY_TYPES: KeyType[] = ['LLM', 'EMBEDDING', 'VECTOR_DB', 'OTHER']

/** 暗色风格下拉，避免原生 select 在暗色界面下弹出白底 */
function DarkSelect<T extends string>({
  value,
  options,
  onChange,
  placeholder = '请选择',
  label,
  buttonRef,
  open,
  onOpenChange,
}: {
  value: T
  options: { value: T; label: string }[]
  onChange: (v: T) => void
  placeholder?: string
  label?: string
  buttonRef: React.RefObject<HTMLButtonElement | null>
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const selected = options.find(o => o.value === value)
  const display = selected ? selected.label : placeholder

  return (
    <div className="relative">
      {label != null && (
        <label className="text-xs text-white/50 mb-1.5 block">{label}</label>
      )}
      <button
        ref={buttonRef as React.Ref<HTMLButtonElement>}
        type="button"
        onClick={() => onOpenChange(!open)}
        className="w-full flex items-center justify-between gap-2 bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-left text-white placeholder-white/40 focus:outline-none focus:border-blue-500/50 hover:border-white/20 transition-colors"
      >
        <span className={selected ? 'text-white' : 'text-white/50'}>{display}</span>
        <ChevronDown className={`w-4 h-4 flex-shrink-0 text-white/50 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open &&
        buttonRef.current &&
        createPortal(
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => onOpenChange(false)}
              role="presentation"
              aria-hidden
            />
            <div
              className="fixed z-50 py-1 min-w-[var(--button-width)] max-h-56 overflow-y-auto rounded-lg bg-[#1a1a1a] border border-white/10 shadow-xl"
              style={{
                left: buttonRef.current.getBoundingClientRect().left,
                top: buttonRef.current.getBoundingClientRect().bottom + 4,
                width: Math.max(buttonRef.current.offsetWidth, 160),
              }}
              onClick={(e) => e.stopPropagation()}
            >
              {options.length === 0 ? (
                <div className="px-3 py-2 text-xs text-white/50">暂无选项</div>
              ) : (
                options.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => {
                      onChange(opt.value)
                      onOpenChange(false)
                    }}
                    className={`w-full text-left px-3 py-2 text-sm transition-colors ${opt.value === value ? 'bg-white/15 text-white' : 'text-white/80 hover:bg-white/10'}`}
                  >
                    {opt.label}
                  </button>
                ))
              )}
            </div>
          </>,
          document.body
        )}
    </div>
  )
}

export function ApiKeyFormModal({ initial, onClose, onSubmit, isLoading }: Props) {
  const isEdit = !!initial

  const [provider, setProvider]   = useState(initial?.provider ?? 'openai')
  const [keyType, setKeyType]     = useState<KeyType>(initial?.keyType ?? 'LLM')
  const [label, setLabel]         = useState(initial?.label ?? '')
  const [keyValue, setKeyValue]   = useState('')          // never pre-fill for security
  const [baseUrl, setBaseUrl]     = useState(initial?.baseUrl ?? '')
  const [model, setModel]         = useState(initial?.model ?? '')
  const [isDefault, setIsDefault] = useState(initial?.isDefault ?? false)
  const [showKey, setShowKey]     = useState(false)
  const [providerOpen, setProviderOpen] = useState(false)
  const [keyTypeOpen, setKeyTypeOpen]   = useState(false)
  const [modelOpen, setModelOpen]       = useState(false)
  const providerButtonRef = useRef<HTMLButtonElement>(null)
  const keyTypeButtonRef  = useRef<HTMLButtonElement>(null)
  const modelButtonRef    = useRef<HTMLButtonElement>(null)

  const { data: systemModels = [] } = useQuery({
    queryKey: ['system-models'],
    queryFn: () => agentApi.getSystemModels(),
  })

  // 从「已开启的系统模型」推导：服务商列表（去重、保序）、按服务商分组的模型列表
  const { providerOptions, modelsByProvider, defaultBaseUrlByProvider } = useMemo(() => {
    const seen = new Set<string>()
    const providers: { value: string; label: string }[] = []
    const byProvider: Record<string, SystemModelDto[]> = {}
    const baseByProvider: Record<string, string> = {}
    for (const m of systemModels) {
      if (!seen.has(m.provider)) {
        seen.add(m.provider)
        providers.push({ value: m.provider, label: formatProvider(m.provider) })
      }
      (byProvider[m.provider] ??= []).push(m)
      if (m.baseUrl && !baseByProvider[m.provider]) baseByProvider[m.provider] = m.baseUrl
    }
    return {
      // 仅允许选择系统当前开启的 provider；若为空，则前端表单将引导用户联系管理员开启模型
      providerOptions: providers,
      modelsByProvider: byProvider,
      defaultBaseUrlByProvider: baseByProvider,
    }
  }, [systemModels])

  // 编辑时若当前 provider 不在系统模型列表中，补一条以便下拉能选中
  const providerOptionsWithInitial = useMemo(() => {
    if (!initial?.provider) return providerOptions
    if (providerOptions.some(p => p.value === initial.provider)) return providerOptions
    return [{ value: initial.provider, label: formatProvider(initial.provider) }, ...providerOptions]
  }, [providerOptions, initial?.provider])

  const modelsForCurrentProvider = modelsByProvider[provider] ?? []
  const firstModelId = modelsForCurrentProvider[0]?.modelId

  // Auto-fill base URL：优先用系统模型里该服务商的 baseUrl，否则用 PROVIDERS 的
  useEffect(() => {
    if (isEdit && baseUrl) return
    const url = defaultBaseUrlByProvider[provider] ?? PROVIDERS.find(p => p.value === provider)?.baseUrl
    if (url) setBaseUrl(url)
  }, [provider, isEdit, defaultBaseUrlByProvider])

  // 当服务商/用途变化时，为 LLM 自动预填/修正默认模型（优先用系统模型列表）
  useEffect(() => {
    if (keyType !== 'LLM') return
    const def = firstModelId ?? LLM_DEFAULT_MODELS[provider]
    if (!def) return
    if (!isEdit && !model) {
      setModel(def)
      return
    }
    const oldDefault = modelsByProvider[initial?.provider ?? '']?.[0]?.modelId ?? LLM_DEFAULT_MODELS[initial?.provider ?? '']
    if (isEdit && initial && provider !== initial.provider && model === oldDefault) {
      setModel(def)
    }
  }, [provider, keyType, isEdit, firstModelId, model, initial, modelsByProvider])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!isEdit && !keyValue.trim()) return
    onSubmit({
      provider,
      keyType,
      label:     label || undefined,
      keyValue:  keyValue || '***unchanged***', // backend ignores this sentinel when editing
      baseUrl:   baseUrl || undefined,
      model:     model || undefined,
      isDefault,
    })
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-md">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10">
          <h2 className="text-sm font-semibold text-white">{isEdit ? '编辑密钥' : '添加 API 密钥'}</h2>
          <button onClick={onClose} className="text-white/30 hover:text-white"><X className="w-4 h-4" /></button>
        </div>

        <form onSubmit={handleSubmit} autoComplete="off" className="px-6 py-5 space-y-4">
          {/* 第一行：API 密钥 */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">
              API Key {isEdit && <span className="text-white/25">（留空则保持原值不变）</span>}
            </label>
            <div className="relative">
              <input
                type={showKey ? 'text' : 'password'}
                value={keyValue}
                onChange={e => setKeyValue(e.target.value)}
                placeholder={isEdit ? '留空保持原值' : 'sk-...'}
                autoComplete="new-password"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 pr-10 text-sm text-white placeholder-white/20 font-mono focus:outline-none focus:border-blue-500/50"
              />
              <button type="button" onClick={() => setShowKey(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60">
                {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* 第二行：服务商（左）| 模型名称（右） */}
          <div className="grid grid-cols-2 gap-3">
            <DarkSelect
              label="服务商"
              value={provider}
              options={providerOptionsWithInitial}
              onChange={(next) => {
                setProvider(next)
                if (keyType === 'LLM') {
                  const nextModels = modelsByProvider[next] ?? []
                  const nextFirst = nextModels[0]?.modelId
                  if (!nextModels.some(m => m.modelId === model)) setModel(nextFirst ?? '')
                }
              }}
              buttonRef={providerButtonRef}
              open={providerOpen}
              onOpenChange={setProviderOpen}
            />
            {keyType === 'LLM' ? (
              <DarkSelect
                label="模型名称"
                value={model}
                options={
                  modelsForCurrentProvider.length > 0
                    ? modelsForCurrentProvider.map(m => ({ value: m.modelId, label: m.displayName }))
                    : []
                }
                onChange={setModel}
                placeholder={modelsForCurrentProvider.length > 0 ? '请选择' : '暂无可用模型，请联系管理员开启'}
                buttonRef={modelButtonRef}
                open={modelOpen}
                onOpenChange={setModelOpen}
              />
            ) : (
              <div>
                <label className="text-xs text-white/50 mb-1.5 block">模型名称（可选）</label>
                <input
                  value={model}
                  onChange={e => setModel(e.target.value)}
                  placeholder={firstModelId ?? LLM_DEFAULT_MODELS[provider] ?? 'gpt-4o'}
                  className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
                />
              </div>
            )}
          </div>

          {/* 第三行：用途 | 备注标签 */}
          <div className="grid grid-cols-2 gap-3">
            <DarkSelect
              label="用途"
              value={keyType}
              options={KEY_TYPES.map(t => ({ value: t, label: KEY_TYPE_LABELS[t] }))}
              onChange={(v) => setKeyType(v)}
              buttonRef={keyTypeButtonRef}
              open={keyTypeOpen}
              onOpenChange={setKeyTypeOpen}
            />
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">备注标签（可选）</label>
              <input
                value={label}
                onChange={e => setLabel(e.target.value)}
                placeholder="个人账号"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
              />
            </div>
          </div>

          {/* Base URL */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">Base URL（可选，留空使用默认地址）</label>
            <input
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              placeholder="https://api.openai.com/v1"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
            />
          </div>

          {/* Default toggle */}
          <label className="flex items-center gap-3 cursor-pointer select-none">
            <div
              onClick={() => setIsDefault(v => !v)}
              className={`w-9 h-5 rounded-full transition-colors relative ${isDefault ? 'bg-blue-600' : 'bg-white/15'}`}
            >
              <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform ${isDefault ? 'translate-x-4' : 'translate-x-0.5'}`} />
            </div>
            <span className="text-sm text-white/70">设为该服务商的默认密钥</span>
          </label>

          {/* Buttons */}
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose}
              className="flex-1 py-2 rounded-lg border border-white/10 text-sm text-white/60 hover:text-white hover:bg-white/5 transition-colors">
              取消
            </button>
            <button type="submit" disabled={isLoading || (!isEdit && !keyValue.trim())}
              className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-sm text-white font-medium transition-colors disabled:opacity-50">
              {isLoading ? '保存中...' : (isEdit ? '更新密钥' : '保存密钥')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
