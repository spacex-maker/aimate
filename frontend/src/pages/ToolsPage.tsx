import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Wrench, Pencil, Trash2, Shield } from 'lucide-react'
import toast from 'react-hot-toast'
import { toolsApi } from '../api/tools'
import { useAuth } from '../hooks/useAuth'
import type { UserToolDto, UpdateUserToolRequest } from '../types/tools'

export function ToolsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user?.userId

  const [editing, setEditing] = useState<UserToolDto | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const { data: tools = [], isLoading } = useQuery({
    queryKey: ['user-tools', userId],
    queryFn: () => (userId != null ? toolsApi.list(userId) : Promise.resolve([])),
    enabled: !!userId,
  })

  const updateMutation = useMutation({
    mutationFn: ({ toolId, body }: { toolId: number; body: UpdateUserToolRequest }) =>
      toolsApi.update(userId!, toolId, body),
    onSuccess: () => {
      toast.success('已更新')
      setEditing(null)
      if (userId != null) queryClient.invalidateQueries({ queryKey: ['user-tools', userId] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (toolId: number) => toolsApi.delete(userId!, toolId),
    onMutate: (id) => setDeletingId(id),
    onSuccess: () => {
      toast.success('已删除')
      if (userId != null) queryClient.invalidateQueries({ queryKey: ['user-tools', userId] })
    },
    onError: (e: Error) => toast.error(e.message),
    onSettled: () => setDeletingId(null),
  })

  if (userId == null) {
    return (
      <div className="h-full flex items-center justify-center text-white/40 text-sm">加载用户信息中…</div>
    )
  }

  const systemTools = tools.filter((t) => t.isSystem)
  const userTools = tools.filter((t) => !t.isSystem)

  return (
    <div className="h-full flex flex-col">
      <div className="flex-shrink-0 px-6 py-4 border-b border-white/10">
        <h1 className="text-base font-semibold text-white">我的工具</h1>
        <p className="text-xs text-white/40 mt-0.5">
          系统工具仅展示不可操作；您创建的工具可编辑、启用/停用或删除
        </p>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4">
        {isLoading ? (
          <div className="text-center py-10 text-white/40 text-sm">加载中…</div>
        ) : tools.length === 0 ? (
          <div className="text-center py-10 text-white/30 text-sm">暂无工具（对话中由 Agent 创建的脚本工具会出现在此处）</div>
        ) : (
          <div className="space-y-6">
            {systemTools.length > 0 && (
              <section>
                <h2 className="text-xs font-medium text-white/50 uppercase tracking-wider mb-2 flex items-center gap-2">
                  <Shield className="w-3.5 h-3.5" /> 系统工具（只读）
                </h2>
                <ul className="space-y-2">
                  {systemTools.map((t) => (
                    <ToolRow key={t.id} tool={t} isSystem onEdit={() => {}} onDelete={() => {}} />
                  ))}
                </ul>
              </section>
            )}
            {userTools.length > 0 && (
              <section>
                <h2 className="text-xs font-medium text-white/50 uppercase tracking-wider mb-2">我的工具</h2>
                <ul className="space-y-2">
                  {userTools.map((t) => (
                    <ToolRow
                      key={t.id}
                      tool={t}
                      isSystem={false}
                      onEdit={() => setEditing(t)}
                      onDelete={() => deleteMutation.mutate(t.id)}
                      isDeleting={deletingId === t.id}
                    />
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </div>

      {editing && (
        <EditToolModal
          tool={editing}
          onClose={() => setEditing(null)}
          onSave={(body) => {
            updateMutation.mutate({ toolId: editing.id, body })
          }}
          isSaving={updateMutation.isPending}
        />
      )}
    </div>
  )
}

function ToolRow({
  tool,
  isSystem,
  onEdit,
  onDelete,
  isDeleting,
}: {
  tool: UserToolDto
  isSystem: boolean
  onEdit: () => void
  onDelete: () => void
  isDeleting?: boolean
}) {
  return (
    <li className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-3 flex items-center justify-between gap-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm text-white/90">{tool.toolName}</span>
          {isSystem && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/50">系统</span>
          )}
          {!tool.isActive && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-400/80">已停用</span>
          )}
        </div>
        <p className="text-xs text-white/50 mt-0.5 line-clamp-2">{tool.toolDescription}</p>
        <p className="text-[11px] text-white/30 mt-1">{tool.toolType}</p>
      </div>
      {!isSystem && (
        <div className="flex items-center gap-1 flex-shrink-0">
          <button
            type="button"
            onClick={onEdit}
            className="p-2 rounded-lg text-white/50 hover:text-white hover:bg-white/10 transition-colors"
            title="编辑"
          >
            <Pencil className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={onDelete}
            disabled={isDeleting}
            className="p-2 rounded-lg text-white/50 hover:text-red-400 hover:bg-white/10 transition-colors disabled:opacity-50"
            title="删除"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      )}
    </li>
  )
}

function EditToolModal({
  tool,
  onClose,
  onSave,
  isSaving,
}: {
  tool: UserToolDto
  onClose: () => void
  onSave: (body: UpdateUserToolRequest) => void
  isSaving: boolean
}) {
  const [description, setDescription] = useState(tool.toolDescription)
  const [isActive, setIsActive] = useState(tool.isActive)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={onClose}>
      <div
        className="bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl max-w-lg w-full mx-4 p-5 text-left"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-sm font-semibold text-white/90 mb-3">编辑工具：{tool.toolName}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs text-white/50 block mb-1">描述</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/30 focus:outline-none focus:border-blue-500/60"
            />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="edit-active"
              checked={isActive}
              onChange={(e) => setIsActive(e.target.checked)}
              className="rounded border-white/20"
            />
            <label htmlFor="edit-active" className="text-xs text-white/70">启用</label>
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 text-xs rounded-lg border border-white/20 text-white/70 hover:bg-white/10"
          >
            取消
          </button>
          <button
            type="button"
            onClick={() => onSave({ toolDescription: description, isActive })}
            disabled={isSaving}
            className="px-3 py-1.5 text-xs rounded-lg bg-blue-600 text-white hover:bg-blue-500 disabled:opacity-50"
          >
            {isSaving ? '保存中…' : '保存'}
          </button>
        </div>
      </div>
    </div>
  )
}
