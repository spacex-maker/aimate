import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Star, Trash2, Pencil, Key, RefreshCw, Activity } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { apikeyApi } from '../api/apikey'
import { useAuth } from '../hooks/useAuth'
import type { ApiKeyRequest, ApiKeyResponse } from '../types/apikey'
import { PROVIDERS, KEY_TYPE_LABELS } from '../types/apikey'
import { ApiKeyFormModal } from '../components/apikey/ApiKeyFormModal'
import { llmLogApi } from '../api/llmLog'
import type { LlmCallLogItem } from '../types/llmLog'

export function ApiKeyPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user?.userId

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ApiKeyResponse | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [tab, setTab] = useState<'keys' | 'logs'>('keys')
  const [logPage, setLogPage] = useState(0)

  const { data: keys = [], isLoading, refetch } = useQuery({
    queryKey: ['api-keys', userId],
    queryFn: () => (userId != null ? apikeyApi.list(userId) : Promise.resolve([])),
    enabled: !!userId,
  })

  const createMutation = useMutation({
    mutationFn: (body: ApiKeyRequest) => apikeyApi.create(userId!, body),
    onSuccess: () => { toast.success('密钥已保存'); setModalOpen(false); if (userId != null) queryClient.invalidateQueries({ queryKey: ['api-keys', userId] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: ApiKeyRequest }) => apikeyApi.update(userId!, id, body),
    onSuccess: () => { toast.success('密钥已更新'); setEditing(null); if (userId != null) queryClient.invalidateQueries({ queryKey: ['api-keys', userId] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const defaultMutation = useMutation({
    mutationFn: (id: number) => apikeyApi.setDefault(userId!, id),
    onSuccess: () => { toast.success('已设为默认'); if (userId != null) queryClient.invalidateQueries({ queryKey: ['api-keys', userId] }) },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => apikeyApi.delete(userId!, id),
    onMutate: (id) => setDeletingId(id),
    onSuccess: () => { toast.success('已删除'); if (userId != null) queryClient.invalidateQueries({ queryKey: ['api-keys', userId] }) },
    onError: (e: Error) => toast.error(e.message),
    onSettled: () => setDeletingId(null),
  })

  if (userId == null) {
    return (
      <div className="h-full flex items-center justify-center text-white/40 text-sm">加载用户信息中…</div>
    )
  }

  // Group by provider for display
  const grouped = keys.reduce<Record<string, ApiKeyResponse[]>>((acc, k) => {
    (acc[k.provider] ??= []).push(k)
    return acc
  }, {})

  const {
    data: logPageData,
    isLoading: logsLoading,
  } = useQuery({
    queryKey: ['llm-logs', userId, logPage],
    queryFn: () => llmLogApi.list(userId!, logPage, 20),
    enabled: !!userId && tab === 'logs',
  })

  return (
    <div className="h-full flex flex-col">
      {/* Header + Tabs */}
      <div className="flex-shrink-0 border-b border-white/10">
        <div className="px-6 pt-5 pb-3 flex items-center justify-between">
          <div>
            <h1 className="text-base font-semibold text-white">API 密钥管理</h1>
            <p className="text-xs text-white/35 mt-0.5">配置第三方 API Key，并查看 LLM 调用消耗</p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => refetch()} className="p-2 text-white/30 hover:text-white/70 hover:bg-white/5 rounded-lg transition-colors">
              <RefreshCw className="w-4 h-4" />
            </button>
            {tab === 'keys' && (
              <button
                onClick={() => { setEditing(null); setModalOpen(true) }}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors"
              >
                <Plus className="w-4 h-4" /> 添加密钥
              </button>
            )}
          </div>
        </div>
        <div className="px-6 flex border-t border-white/10">
          <button
            type="button"
            onClick={() => setTab('keys')}
            className={clsx(
              'px-4 py-2.5 text-xs border-b-2 transition-colors',
              tab === 'keys'
                ? 'border-blue-500 text-blue-400'
                : 'border-transparent text-white/40 hover:text-white/70'
            )}
          >
            密钥列表
          </button>
          <button
            type="button"
            onClick={() => setTab('logs')}
            className={clsx(
              'px-4 py-2.5 text-xs border-b-2 transition-colors flex items-center gap-1.5',
              tab === 'logs'
                ? 'border-blue-500 text-blue-400'
                : 'border-transparent text-white/40 hover:text-white/70'
            )}
          >
            <Activity className="w-3.5 h-3.5" /> 调用日志
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-6 py-5">
        {tab === 'keys' ? (
          isLoading ? (
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
          )
        ) : (
          <LlmLogTable
            logs={logPageData?.items ?? []}
            loading={logsLoading}
            page={logPage}
            size={logPageData?.size ?? 20}
            total={logPageData?.total ?? 0}
            onPageChange={setLogPage}
          />
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
          {k.isDefault && (
            <span className="text-[10px] font-mono border border-yellow-500/30 text-yellow-400 bg-yellow-400/10 px-1.5 py-0.5 rounded">
              默认
            </span>
          )}
          <span className="text-xs font-mono text-white/60 bg-black/30 px-2 py-0.5 rounded">
            {k.maskedKey}
          </span>
          <span className={clsx(
            'text-[10px] px-1.5 py-0.5 rounded border font-mono',
            k.keyType === 'LLM'       && 'text-blue-400 bg-blue-400/10 border-blue-400/30',
            k.keyType === 'EMBEDDING' && 'text-purple-400 bg-purple-400/10 border-purple-400/30',
            k.keyType === 'VECTOR_DB' && 'text-green-400 bg-green-400/10 border-green-400/30',
            k.keyType === 'OTHER'     && 'text-white/40 bg-white/5 border-white/10',
          )}>
            {KEY_TYPE_LABELS[k.keyType]}
          </span>
        </div>
        <div className="flex items-center gap-3 text-[10px] text-white/25">
          {k.label && <span>{k.label}</span>}
          {k.model && <span>模型: {k.model}</span>}
          {k.baseUrl && <span className="truncate max-w-[200px]">{k.baseUrl}</span>}
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {!k.isDefault && (
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

function LlmLogTable({
  logs,
  loading,
  page,
  size,
  total,
  onPageChange,
}: {
  logs: LlmCallLogItem[]
  loading: boolean
  page: number
  size: number
  total: number
  onPageChange: (p: number) => void
}) {
  const totalPages = total > 0 ? Math.ceil(total / size) : 0

  if (loading) {
    return <div className="text-center py-16 text-white/25 text-sm">加载中...</div>
  }

  if (!logs.length) {
    return <div className="text-center py-16 text-white/30 text-sm">暂无调用记录</div>
  }

  return (
    <div className="flex flex-col h-full">
      <div className="overflow-auto rounded-xl border border-white/10 bg-white/[0.02]">
        <table className="min-w-full text-xs text-white/70">
          <thead className="bg-white/[0.04] text-white/50">
            <tr>
              <th className="px-3 py-2 text-left font-medium">时间</th>
              <th className="px-3 py-2 text-left font-medium">提供商 / 模型</th>
              <th className="px-3 py-2 text-left font-medium">用途</th>
              <th className="px-3 py-2 text-right font-medium">Tokens</th>
              <th className="px-3 py-2 text-right font-medium">耗时</th>
              <th className="px-3 py-2 text-left font-medium">HTTP</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/[0.05]">
            {logs.map((l) => (
              <tr key={l.id} className="hover:bg-white/[0.03]">
                <td className="px-3 py-2 whitespace-nowrap text-white/60">
                  {l.createTime ? l.createTime.replace('T', ' ').slice(0, 19) : '-'}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-white/80">{l.provider}</span>
                    <span className="text-white/40 text-[11px] font-mono truncate max-w-[220px]">
                      {l.model}
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-col gap-0.5">
                    <span className="text-[11px] text-white/60">
                      {l.callType || 'OTHER'}{l.toolName ? ` · ${l.toolName}` : ''}
                    </span>
                    {l.sessionId && (
                      <span className="text-[10px] text-white/30 font-mono truncate max-w-[180px]">
                        session: {l.sessionId}
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-3 py-2 text-right font-mono text-[11px]">
                  {l.totalTokens != null ? (
                    <>
                      <span className="text-white/80">{l.totalTokens}</span>
                      <span className="text-white/40"> = </span>
                      <span className="text-white/50">
                        {l.promptTokens ?? 0}/{l.completionTokens ?? 0}
                      </span>
                    </>
                  ) : (
                    '-'
                  )}
                </td>
                <td className="px-3 py-2 text-right text-[11px] text-white/60">
                  {l.latencyMs != null ? `${l.latencyMs} ms` : '-'}
                </td>
                <td className="px-3 py-2 text-left text-[11px]">
                  <span
                    className={clsx(
                      'px-1.5 py-0.5 rounded font-mono',
                      l.success
                        ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30'
                        : 'bg-red-500/10 text-red-300 border border-red-500/30'
                    )}
                  >
                    {l.httpStatus ?? '-'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-[11px] text-white/40">
          <div>
            第 <span className="text-white/70">{page + 1}</span> / {totalPages} 页， 共{' '}
            <span className="text-white/70">{total}</span> 条
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onPageChange(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-2 py-1 rounded border border-white/15 text-white/60 hover:text-white hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              上一页
            </button>
            <button
              type="button"
              onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="px-2 py-1 rounded border border-white/15 text-white/60 hover:text-white hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              下一页
            </button>
          </div>
        </div>
      )}
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
