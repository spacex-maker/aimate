import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Star, Trash2, Pencil, Key, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { apikeyApi } from '../api/apikey'
import { useAuth } from '../hooks/useAuth'
import type { ApiKeyRequest, ApiKeyResponse } from '../types/apikey'
import { PROVIDERS, KEY_TYPE_LABELS } from '../types/apikey'
import { ApiKeyFormModal } from '../components/apikey/ApiKeyFormModal'

export function ApiKeyPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user!.userId

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ApiKeyResponse | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const { data: keys = [], isLoading, refetch } = useQuery({
    queryKey: ['api-keys', userId],
    queryFn: () => apikeyApi.list(userId),
    enabled: !!userId,
  })

  const createMutation = useMutation({
    mutationFn: (body: ApiKeyRequest) => apikeyApi.create(userId, body),
    onSuccess: () => { toast.success('密钥已保存'); setModalOpen(false); queryClient.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: ApiKeyRequest }) => apikeyApi.update(userId, id, body),
    onSuccess: () => { toast.success('密钥已更新'); setEditing(null); queryClient.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const defaultMutation = useMutation({
    mutationFn: (id: number) => apikeyApi.setDefault(userId, id),
    onSuccess: () => { toast.success('已设为默认'); queryClient.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => apikeyApi.delete(userId, id),
    onMutate: (id) => setDeletingId(id),
    onSuccess: () => { toast.success('已删除'); queryClient.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e: Error) => toast.error(e.message),
    onSettled: () => setDeletingId(null),
  })

  // Group by provider for display
  const grouped = keys.reduce<Record<string, ApiKeyResponse[]>>((acc, k) => {
    (acc[k.provider] ??= []).push(k)
    return acc
  }, {})

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex-shrink-0 px-6 py-5 border-b border-white/10 flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-white">API 密钥管理</h1>
          <p className="text-xs text-white/35 mt-0.5">配置你的第三方 API Key，Agent 将优先使用你的私有密钥</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => refetch()} className="p-2 text-white/30 hover:text-white/70 hover:bg-white/5 rounded-lg transition-colors">
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            onClick={() => { setEditing(null); setModalOpen(true) }}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors"
          >
            <Plus className="w-4 h-4" /> 添加密钥
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-6 py-5">
        {isLoading ? (
          <div className="text-center py-16 text-white/25 text-sm">加载中...</div>
        ) : keys.length === 0 ? (
          <EmptyState onAdd={() => setModalOpen(true)} />
        ) : (
          <div className="space-y-6">
            {Object.entries(grouped).map(([provider, list]) => (
              <ProviderGroup
                key={provider}
                provider={provider}
                keys={list}
                onEdit={(k) => { setEditing(k); setModalOpen(true) }}
                onSetDefault={(id) => defaultMutation.mutate(id)}
                onDelete={(id) => deleteMutation.mutate(id)}
                deletingId={deletingId}
              />
            ))}
          </div>
        )}
      </div>

      {/* Modal */}
      {modalOpen && (
        <ApiKeyFormModal
          initial={editing}
          onClose={() => { setModalOpen(false); setEditing(null) }}
          onSubmit={(body) => {
            if (editing) {
              updateMutation.mutate({ id: editing.id, body })
            } else {
              createMutation.mutate(body)
            }
          }}
          isLoading={createMutation.isPending || updateMutation.isPending}
        />
      )}
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function ProviderGroup({
  provider, keys, onEdit, onSetDefault, onDelete, deletingId,
}: {
  provider: string
  keys: ApiKeyResponse[]
  onEdit: (k: ApiKeyResponse) => void
  onSetDefault: (id: number) => void
  onDelete: (id: number) => void
  deletingId: number | null
}) {
  const info = PROVIDERS.find(p => p.value === provider)
  const label = info?.label ?? provider.charAt(0).toUpperCase() + provider.slice(1)

  return (
    <div className="border border-white/10 rounded-xl overflow-hidden">
      <div className="px-4 py-3 bg-white/[0.03] border-b border-white/[0.06] flex items-center gap-2">
        <div className="w-6 h-6 rounded bg-white/10 flex items-center justify-center">
          <Key className="w-3.5 h-3.5 text-white/50" />
        </div>
        <span className="text-sm font-medium text-white/80">{label}</span>
        <span className="text-xs text-white/30 ml-1">{keys.length} 个密钥</span>
      </div>

      <div className="divide-y divide-white/[0.05]">
        {keys.map(k => (
          <KeyRow
            key={k.id}
            apiKey={k}
            onEdit={() => onEdit(k)}
            onSetDefault={() => onSetDefault(k.id)}
            onDelete={() => onDelete(k.id)}
            isDeleting={deletingId === k.id}
          />
        ))}
      </div>
    </div>
  )
}

function KeyRow({
  apiKey: k, onEdit, onSetDefault, onDelete, isDeleting,
}: {
  apiKey: ApiKeyResponse
  onEdit: () => void
  onSetDefault: () => void
  onDelete: () => void
  isDeleting: boolean
}) {
  return (
    <div className="flex items-center gap-4 px-4 py-3 hover:bg-white/[0.02] group transition-colors">
      <div className="flex-1 min-w-0 space-y-0.5">
        <div className="flex items-center gap-2">
          {k.is_default && (
            <span className="text-[10px] font-mono border border-yellow-500/30 text-yellow-400 bg-yellow-400/10 px-1.5 py-0.5 rounded">
              默认
            </span>
          )}
          <span className="text-xs font-mono text-white/60 bg-black/30 px-2 py-0.5 rounded">
            {k.masked_key}
          </span>
          <span className={clsx(
            'text-[10px] px-1.5 py-0.5 rounded border font-mono',
            k.key_type === 'LLM'       && 'text-blue-400 bg-blue-400/10 border-blue-400/30',
            k.key_type === 'EMBEDDING' && 'text-purple-400 bg-purple-400/10 border-purple-400/30',
            k.key_type === 'VECTOR_DB' && 'text-green-400 bg-green-400/10 border-green-400/30',
            k.key_type === 'OTHER'     && 'text-white/40 bg-white/5 border-white/10',
          )}>
            {KEY_TYPE_LABELS[k.key_type]}
          </span>
        </div>
        <div className="flex items-center gap-3 text-[10px] text-white/25">
          {k.label && <span>{k.label}</span>}
          {k.model && <span>模型: {k.model}</span>}
          {k.base_url && <span className="truncate max-w-[200px]">{k.base_url}</span>}
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {!k.is_default && (
          <button
            onClick={onSetDefault}
            title="设为默认"
            className="p-1.5 text-white/30 hover:text-yellow-400 hover:bg-white/5 rounded transition-colors"
          >
            <Star className="w-3.5 h-3.5" />
          </button>
        )}
        <button
          onClick={onEdit}
          title="编辑"
          className="p-1.5 text-white/30 hover:text-blue-400 hover:bg-white/5 rounded transition-colors"
        >
          <Pencil className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={onDelete}
          disabled={isDeleting}
          title="删除"
          className="p-1.5 text-white/30 hover:text-red-400 hover:bg-white/5 rounded transition-colors disabled:opacity-50"
        >
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
        <Key className="w-7 h-7 text-white/20" />
      </div>
      <p className="text-white/40 text-sm mb-1">还没有配置 API 密钥</p>
      <p className="text-white/20 text-xs mb-6">添加后 Agent 将使用你自己的密钥调用 LLM</p>
      <button
        onClick={onAdd}
        className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors"
      >
        添加第一个密钥
      </button>
    </div>
  )
}
