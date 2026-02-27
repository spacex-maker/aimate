import { useState, useEffect } from 'react'
import { X, Eye, EyeOff } from 'lucide-react'
import type { EmbeddingModelRequest, EmbeddingModelResponse } from '../../types/embeddingModel'
import { EMBEDDING_PROVIDERS } from '../../types/embeddingModel'

interface Props {
  initial: EmbeddingModelResponse | null
  onClose: () => void
  onSubmit: (body: EmbeddingModelRequest) => void
  isLoading: boolean
}

export function EmbeddingModelFormModal({ initial, onClose, onSubmit, isLoading }: Props) {
  const isEdit = !!initial

  const [provider,   setProvider]   = useState(initial?.provider ?? 'openai')
  const [modelName,  setModelName]  = useState(initial?.model_name ?? '')
  const [name,       setName]       = useState(initial?.name ?? '')
  const [apiKey,     setApiKey]     = useState('')
  const [baseUrl,    setBaseUrl]    = useState(initial?.base_url ?? '')
  const [dimension,  setDimension]  = useState(initial?.dimension ?? 1536)
  const [maxTokens,  setMaxTokens]  = useState(initial?.max_tokens ?? 8192)
  const [isDefault,  setIsDefault]  = useState(initial?.is_default ?? false)
  const [showKey,    setShowKey]    = useState(false)

  const providerInfo = EMBEDDING_PROVIDERS.find(p => p.value === provider)

  // Auto-fill when provider changes
  useEffect(() => {
    if (isEdit) return
    const info = EMBEDDING_PROVIDERS.find(p => p.value === provider)
    if (info) {
      setBaseUrl(info.baseUrl)
      if (info.models.length > 0) {
        setModelName(info.models[0].name)
        setDimension(info.models[0].dim)
      }
    }
  }, [provider, isEdit])

  // Auto-fill dimension when selecting a preset model
  const handleModelSelect = (mn: string) => {
    setModelName(mn)
    const preset = providerInfo?.models.find(m => m.name === mn)
    if (preset) setDimension(preset.dim)
    if (!name || EMBEDDING_PROVIDERS.some(p => p.models.some(m => m.name === name))) {
      setName(mn)
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit({ provider, modelName, name: name || modelName, apiKey: apiKey || undefined,
               baseUrl, dimension, maxTokens, isDefault })
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-md max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10 sticky top-0 bg-[#1a1a1a]">
          <h2 className="text-sm font-semibold text-white">{isEdit ? '编辑嵌入模型' : '添加向量嵌入模型'}</h2>
          <button onClick={onClose} className="text-white/30 hover:text-white"><X className="w-4 h-4" /></button>
        </div>

        <form onSubmit={handleSubmit} autoComplete="off" className="px-6 py-5 space-y-4">
          {/* Provider */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">服务商</label>
            <div className="grid grid-cols-2 gap-2">
              {EMBEDDING_PROVIDERS.map(p => (
                <button key={p.value} type="button"
                  onClick={() => setProvider(p.value)}
                  className={`px-3 py-2 rounded-lg text-xs font-medium text-left transition-colors border ${
                    provider === p.value
                      ? 'bg-purple-600/20 border-purple-500/50 text-purple-300'
                      : 'bg-white/5 border-white/10 text-white/50 hover:bg-white/10'
                  }`}>
                  {p.label}
                </button>
              ))}
            </div>
          </div>

          {/* Preset models */}
          {providerInfo && providerInfo.models.length > 0 && (
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">预设模型</label>
              <div className="flex flex-wrap gap-2">
                {providerInfo.models.map(m => (
                  <button key={m.name} type="button"
                    onClick={() => handleModelSelect(m.name)}
                    className={`text-xs px-2.5 py-1.5 rounded-lg border transition-colors font-mono ${
                      modelName === m.name
                        ? 'bg-purple-600/20 border-purple-500/40 text-purple-300'
                        : 'bg-white/5 border-white/10 text-white/40 hover:bg-white/10'
                    }`}>
                    {m.name} <span className="text-white/25 ml-1">{m.dim}d</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Model name (custom input) */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">模型名称</label>
              <input value={modelName} onChange={e => setModelName(e.target.value)} required
                placeholder="text-embedding-3-small"
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white font-mono placeholder-white/20 focus:outline-none focus:border-purple-500/50" />
            </div>
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">向量维度</label>
              <input type="number" value={dimension} onChange={e => setDimension(Number(e.target.value))} required min={64}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-purple-500/50" />
            </div>
          </div>

          {/* Display name */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">备注名称</label>
            <input value={name} onChange={e => setName(e.target.value)} required placeholder="本地 nomic 模型"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-purple-500/50" />
          </div>

          {/* Base URL */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">
              Base URL
              {provider === 'ollama' && (
                <span className="text-purple-400/60 ml-1">（使用 /v1 路径以兼容 OpenAI 格式）</span>
              )}
            </label>
            <input value={baseUrl} onChange={e => setBaseUrl(e.target.value)} required
              placeholder={provider === 'ollama' ? 'http://175.27.255.29:11434/v1' : 'https://api.openai.com/v1'}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-purple-500/50" />
          </div>

          {/* API Key (optional for Ollama) */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">
              API Key
              {provider === 'ollama' && <span className="text-white/25 ml-1">（Ollama 本地部署可留空）</span>}
              {isEdit && <span className="text-white/25 ml-1">（留空保持原值）</span>}
            </label>
            <div className="relative">
              <input type={showKey ? 'text' : 'password'} value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                autoComplete="new-password"
                placeholder={provider === 'ollama' ? '留空即可' : 'sk-...'}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 pr-10 text-sm text-white placeholder-white/20 font-mono focus:outline-none focus:border-purple-500/50" />
              <button type="button" onClick={() => setShowKey(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60">
                {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Max tokens */}
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">最大输入 Token 数</label>
            <input type="number" value={maxTokens} onChange={e => setMaxTokens(Number(e.target.value))} min={512}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-purple-500/50" />
          </div>

          {/* Collection name preview */}
          {modelName && dimension > 0 && (
            <div className="px-3 py-2 rounded-lg bg-black/30 border border-white/5">
              <p className="text-[10px] text-white/25 mb-0.5">Milvus Collection（自动推导）</p>
              <p className="text-xs font-mono text-purple-300/70">
                {`memories_${modelName.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')}_${dimension}`}
              </p>
            </div>
          )}

          {/* Default toggle */}
          <label className="flex items-center gap-3 cursor-pointer select-none">
            <div onClick={() => setIsDefault(v => !v)}
              className={`w-9 h-5 rounded-full transition-colors relative ${isDefault ? 'bg-purple-600' : 'bg-white/15'}`}>
              <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform ${isDefault ? 'translate-x-4' : 'translate-x-0.5'}`} />
            </div>
            <span className="text-sm text-white/70">设为默认嵌入模型</span>
          </label>

          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose}
              className="flex-1 py-2 rounded-lg border border-white/10 text-sm text-white/60 hover:text-white hover:bg-white/5 transition-colors">
              取消
            </button>
            <button type="submit" disabled={isLoading}
              className="flex-1 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 text-sm text-white font-medium transition-colors disabled:opacity-50">
              {isLoading ? '保存中...' : (isEdit ? '更新' : '保存')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
