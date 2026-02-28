import { useState, useEffect } from 'react'
import { X, Eye, EyeOff } from 'lucide-react'
import type { ApiKeyRequest, ApiKeyResponse, KeyType } from '../../types/apikey'
import { PROVIDERS, KEY_TYPE_LABELS, LLM_DEFAULT_MODELS } from '../../types/apikey'

interface Props {
  initial: ApiKeyResponse | null
  onClose: () => void
  onSubmit: (body: ApiKeyRequest) => void
  isLoading: boolean
}

const KEY_TYPES: KeyType[] = ['LLM', 'EMBEDDING', 'VECTOR_DB', 'OTHER']

export function ApiKeyFormModal({ initial, onClose, onSubmit, isLoading }: Props) {
  const isEdit = !!initial

  const [provider, setProvider]   = useState(initial?.provider ?? 'openai')
  const [keyType, setKeyType]     = useState<KeyType>(initial?.key_type ?? 'LLM')
  const [label, setLabel]         = useState(initial?.label ?? '')
  const [keyValue, setKeyValue]   = useState('')          // never pre-fill for security
  const [baseUrl, setBaseUrl]     = useState(initial?.base_url ?? '')
  const [model, setModel]         = useState(initial?.model ?? '')
  const [isDefault, setIsDefault] = useState(initial?.is_default ?? false)
  const [showKey, setShowKey]     = useState(false)

  // Auto-fill base URL when selecting a known provider
  useEffect(() => {
    const info = PROVIDERS.find(p => p.value === provider)
    if (info && !isEdit) setBaseUrl(info.baseUrl)
  }, [provider, isEdit])

  // 当服务商 / 用途变化时，为 LLM 自动预填/修正默认模型
  useEffect(() => {
    if (keyType !== 'LLM') return
    const def = LLM_DEFAULT_MODELS[provider]
    if (!def) return

    // 新增密钥：没填过 model 时直接预填默认模型
    if (!isEdit && !model) {
      setModel(def)
      return
    }

    // 编辑时：如果之前是别的服务商的默认模型（例如 openai 的 gpt-4o），切到新服务商时同步成新默认
    if (isEdit && initial) {
      const prevDef = LLM_DEFAULT_MODELS[initial.provider]
      if (prevDef && model === prevDef && def !== prevDef) {
        setModel(def)
      }
    }
  }, [provider, keyType, isEdit, model, initial])

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
          {/* Provider */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">服务商</label>
              <select
                value={provider}
                onChange={e => setProvider(e.target.value)}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500/50"
              >
                {PROVIDERS.map(p => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">用途</label>
              <select
                value={keyType}
                onChange={e => setKeyType(e.target.value as KeyType)}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500/50"
              >
                {KEY_TYPES.map(t => (
                  <option key={t} value={t}>{KEY_TYPE_LABELS[t]}</option>
                ))}
              </select>
            </div>
          </div>

          {/* API Key */}
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

          {/* Model + Label */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">默认模型（可选）</label>
              <input
                value={model}
                onChange={e => setModel(e.target.value)}
                placeholder={LLM_DEFAULT_MODELS[provider] ?? 'gpt-4o'}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
              />
            </div>
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
