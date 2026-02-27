import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Star, Trash2, Pencil, Cpu, RefreshCw, Info } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { embeddingModelApi } from '../api/embeddingModel'
import { useAuth } from '../hooks/useAuth'
import type { EmbeddingModelRequest, EmbeddingModelResponse } from '../types/embeddingModel'
import { EMBEDDING_PROVIDERS } from '../types/embeddingModel'
import { EmbeddingModelFormModal } from '../components/embedding/EmbeddingModelFormModal'

export function EmbeddingModelPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user!.userId

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EmbeddingModelResponse | null>(null)

  const { data: models = [], isLoading, refetch } = useQuery({
    queryKey: ['embedding-models', userId],
    queryFn: () => embeddingModelApi.list(userId),
    enabled: !!userId,
  })

  const createMutation = useMutation({
    mutationFn: (body: EmbeddingModelRequest) => embeddingModelApi.create(userId, body),
    onSuccess: () => { toast.success('向量模型已保存'); setModalOpen(false); queryClient.invalidateQueries({ queryKey: ['embedding-models'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: EmbeddingModelRequest }) =>
      embeddingModelApi.update(userId, id, body),
    onSuccess: () => { toast.success('已更新'); setEditing(null); setModalOpen(false); queryClient.invalidateQueries({ queryKey: ['embedding-models'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const defaultMutation = useMutation({
    mutationFn: (id: number) => embeddingModelApi.setDefault(userId, id),
    onSuccess: () => { toast.success('已设为默认'); queryClient.invalidateQueries({ queryKey: ['embedding-models'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => embeddingModelApi.delete(userId, id),
    onSuccess: () => { toast.success('已删除'); queryClient.invalidateQueries({ queryKey: ['embedding-models'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex-shrink-0 px-6 py-5 border-b border-white/10 flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-white">向量嵌入模型</h1>
          <p className="text-xs text-white/35 mt-0.5">配置用于长期记忆的向量模型，不同维度自动使用独立 Milvus Collection</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => refetch()} className="p-2 text-white/30 hover:text-white/70 hover:bg-white/5 rounded-lg transition-colors">
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            onClick={() => { setEditing(null); setModalOpen(true) }}
            className="flex items-center gap-2 px-4 py-2 bg-purple-600 hover:bg-purple-500 rounded-lg text-sm text-white font-medium transition-colors"
          >
            <Plus className="w-4 h-4" /> 添加模型
          </button>
        </div>
      </div>

      {/* Info banner */}
      <div className="flex-shrink-0 mx-6 mt-4 flex items-start gap-2.5 px-4 py-3 rounded-xl bg-purple-500/5 border border-purple-500/20">
        <Info className="w-4 h-4 text-purple-400 flex-shrink-0 mt-0.5" />
        <div className="text-xs text-purple-300/70 leading-relaxed">
          <strong className="text-purple-300">不同模型使用独立 Collection：</strong>
          切换模型后，之前用其他模型存储的记忆将不会出现在搜索结果中（向量空间不兼容）。
          同一模型可被多个用户共享同一 Collection。
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {isLoading ? (
          <div className="text-center py-16 text-white/25 text-sm">加载中...</div>
        ) : models.length === 0 ? (
          <EmptyState onAdd={() => setModalOpen(true)} />
        ) : (
          <div className="space-y-3 mt-2">
            {models.map(m => (
              <ModelCard
                key={m.id}
                model={m}
                onEdit={() => { setEditing(m); setModalOpen(true) }}
                onSetDefault={() => defaultMutation.mutate(m.id)}
                onDelete={() => deleteMutation.mutate(m.id)}
              />
            ))}
          </div>
        )}
      </div>

      {modalOpen && (
        <EmbeddingModelFormModal
          initial={editing}
          onClose={() => { setModalOpen(false); setEditing(null) }}
          onSubmit={(body) => {
            if (editing) updateMutation.mutate({ id: editing.id, body })
            else createMutation.mutate(body)
          }}
          isLoading={createMutation.isPending || updateMutation.isPending}
        />
      )}
    </div>
  )
}

// ── Card ──────────────────────────────────────────────────────────────────────

function ModelCard({ model: m, onEdit, onSetDefault, onDelete }: {
  model: EmbeddingModelResponse
  onEdit: () => void
  onSetDefault: () => void
  onDelete: () => void
}) {
  const providerInfo = EMBEDDING_PROVIDERS.find(p => p.value === m.provider)
  const providerLabel = providerInfo?.label ?? m.provider

  return (
    <div className={clsx(
      'flex items-center gap-4 px-4 py-3.5 rounded-xl border transition-colors group',
      m.is_default
        ? 'border-purple-500/30 bg-purple-500/5'
        : 'border-white/10 bg-white/[0.02] hover:bg-white/[0.04]'
    )}>
      <div className="w-9 h-9 rounded-lg bg-purple-500/15 border border-purple-500/20 flex items-center justify-center flex-shrink-0">
        <Cpu className="w-4 h-4 text-purple-400" />
      </div>

      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium text-white">{m.name}</span>
          {m.is_default && (
            <span className="text-[10px] font-mono border border-yellow-500/30 text-yellow-400 bg-yellow-400/10 px-1.5 py-0.5 rounded">
              默认
            </span>
          )}
          <span className="text-[10px] text-white/40 bg-white/5 border border-white/10 px-1.5 py-0.5 rounded font-mono">
            {providerLabel}
          </span>
        </div>
        <div className="flex items-center gap-3 flex-wrap text-[11px] text-white/35">
          <span className="font-mono text-white/50">{m.model_name}</span>
          <span className="text-purple-400/60">dim={m.dimension}</span>
          <span className="truncate max-w-[200px]">{m.base_url}</span>
        </div>
        <div className="text-[10px] text-white/20 font-mono">
          collection: {m.collection_name}
        </div>
      </div>

      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
        {!m.is_default && (
          <button onClick={onSetDefault} title="设为默认"
            className="p-1.5 text-white/30 hover:text-yellow-400 hover:bg-white/5 rounded transition-colors">
            <Star className="w-3.5 h-3.5" />
          </button>
        )}
        <button onClick={onEdit} title="编辑"
          className="p-1.5 text-white/30 hover:text-purple-400 hover:bg-white/5 rounded transition-colors">
          <Pencil className="w-3.5 h-3.5" />
        </button>
        <button onClick={onDelete} title="删除"
          className="p-1.5 text-white/30 hover:text-red-400 hover:bg-white/5 rounded transition-colors">
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  )
}

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="text-center py-20">
      <div className="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mx-auto mb-4">
        <Cpu className="w-7 h-7 text-white/20" />
      </div>
      <p className="text-white/40 text-sm mb-1">还没有配置向量嵌入模型</p>
      <p className="text-white/20 text-xs mb-6">Agent 将使用系统默认模型，添加后可使用本地 Ollama 或自定义服务</p>
      <button onClick={onAdd}
        className="px-5 py-2.5 bg-purple-600 hover:bg-purple-500 rounded-lg text-sm text-white font-medium transition-colors">
        添加第一个模型
      </button>
    </div>
  )
}
