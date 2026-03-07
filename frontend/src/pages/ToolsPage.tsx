import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Wrench, Pencil, Trash2, Shield, Plus } from 'lucide-react'
import toast from 'react-hot-toast'
import { toolsApi } from '../api/tools'
import { useAuth } from '../hooks/useAuth'
import type { CreateUserToolRequest, UserToolDto, UpdateUserToolRequest } from '../types/tools'

export function ToolsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user?.userId

  const [editing, setEditing] = useState<UserToolDto | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<UserToolDto | null>(null)
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

  const createMutation = useMutation({
    mutationFn: (body: CreateUserToolRequest) => toolsApi.create(userId!, body),
    onSuccess: () => {
      toast.success('已创建')
      setShowCreate(false)
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
      <div className="flex-shrink-0 px-6 py-4 border-b border-white/10 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-base font-semibold text-white">我的工具</h1>
          <p className="text-xs text-white/40 mt-0.5">
            系统工具仅展示不可操作；您创建的工具可编辑、启用/停用或删除
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors"
        >
          <Plus className="w-4 h-4" />
          新增工具
        </button>
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
                      onDelete={() => setConfirmDelete(t)}
                      isDeleting={deletingId === t.id}
                    />
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </div>

      {showCreate && (
        <CreateToolModal
          onClose={() => setShowCreate(false)}
          onCreate={(body) => createMutation.mutate(body)}
          isCreating={createMutation.isPending}
        />
      )}

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

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setConfirmDelete(null)}>
          <div
            className="bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl max-w-sm w-full mx-4 p-5 text-left"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-sm font-semibold text-white/90 mb-2">确认删除</h3>
            <p className="text-xs text-white/60 mb-4">
              确定要删除工具 <span className="font-mono text-white/80">{confirmDelete.toolName}</span> 吗？此操作不可恢复。
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmDelete(null)}
                className="px-3 py-1.5 text-xs rounded-lg border border-white/20 text-white/70 hover:bg-white/10"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => {
                  deleteMutation.mutate(confirmDelete.id)
                  setConfirmDelete(null)
                }}
                disabled={deletingId === confirmDelete.id}
                className="px-3 py-1.5 text-xs rounded-lg bg-red-600 text-white hover:bg-red-500 disabled:opacity-50"
              >
                {deletingId === confirmDelete.id ? '删除中…' : '确定删除'}
              </button>
            </div>
          </div>
        </div>
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

const INPUT_SCHEMA_PLACEHOLDER = `{"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}`

const RESERVED_NAMES = [
  'recall_memory', 'store_memory', 'tavily_search', 'create_tool',
  'install_container_package', 'run_container_cmd', 'write_container_file',
]

function CreateToolModal({
  onClose,
  onCreate,
  isCreating,
}: {
  onClose: () => void
  onCreate: (body: CreateUserToolRequest) => void
  isCreating: boolean
}) {
  const [toolName, setToolName] = useState('')
  const [toolDescription, setToolDescription] = useState('')
  const [toolType, setToolType] = useState<CreateUserToolRequest['toolType']>('PYTHON_SCRIPT')
  const [inputSchema, setInputSchema] = useState('')
  const [scriptContent, setScriptContent] = useState('')
  const [entryPoint, setEntryPoint] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [schemaError, setSchemaError] = useState<string | null>(null)

  const validateName = (name: string) => {
    const t = name.trim()
    if (!t) return '工具名不能为空'
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(t)) return '须以字母开头，仅含字母、数字、下划线（如 get_weather）'
    if (RESERVED_NAMES.includes(t)) return '不能与内置工具重名'
    return null
  }

  const validateSchema = (json: string) => {
    const t = json.trim()
    if (!t) return '参数结构不能为空'
    try {
      const o = JSON.parse(t)
      if (o === null || Array.isArray(o)) return '须为 JSON 对象'
      return null
    } catch {
      return '须为合法 JSON'
    }
  }

  const handleSubmit = () => {
    const nErr = validateName(toolName)
    const sErr = validateSchema(inputSchema)
    setNameError(nErr)
    setSchemaError(sErr)
    if (nErr || sErr) return
    const name = toolName.trim()
    const desc = toolDescription.trim()
    if (!desc) {
      toast.error('请填写工具描述')
      return
    }
    const schema = inputSchema.trim()
    const script = scriptContent.trim() || undefined
    const entry = entryPoint.trim() || undefined
    onCreate({
      toolName: name,
      toolDescription: desc,
      inputSchema: schema,
      toolType,
      scriptContent: script,
      entryPoint: entry,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto" onClick={onClose}>
      <div
        className="bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl max-w-lg w-full my-4 p-5 text-left"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-sm font-semibold text-white/90 mb-1">新增工具</h3>
        <p className="text-xs text-white/45 mb-4">与 AI 生成工具规范一致：工具名、描述、参数结构（JSON Schema）、脚本类型及可选脚本内容</p>
        <div className="space-y-3">
          <div>
            <label className="text-xs text-white/50 block mb-1">工具名 <span className="text-white/30">（唯一标识，字母开头，仅字母/数字/下划线）</span></label>
            <input
              type="text"
              value={toolName}
              onChange={(e) => { setToolName(e.target.value); setNameError(null) }}
              onBlur={() => setNameError(validateName(toolName))}
              placeholder="例如 get_weather、calc_sum"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/30 focus:outline-none focus:border-blue-500/60 font-mono"
            />
            {nameError && <p className="text-xs text-red-400 mt-0.5">{nameError}</p>}
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">工具描述 <span className="text-white/30">（何时调用此工具，供模型理解）</span></label>
            <textarea
              value={toolDescription}
              onChange={(e) => setToolDescription(e.target.value)}
              rows={2}
              placeholder="例如：根据城市名查询当前天气"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/30 focus:outline-none focus:border-blue-500/60"
            />
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">工具类型</label>
            <select
              value={toolType}
              onChange={(e) => setToolType(e.target.value as CreateUserToolRequest['toolType'])}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500/60"
            >
              <option value="PYTHON_SCRIPT">PYTHON_SCRIPT（Python 脚本）</option>
              <option value="NODE_SCRIPT">NODE_SCRIPT（Node.js 脚本）</option>
              <option value="SHELL_CMD">SHELL_CMD（Shell 命令）</option>
            </select>
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">参数结构 input_schema <span className="text-white/30">（JSON 对象，定义工具入参）</span></label>
            <textarea
              value={inputSchema}
              onChange={(e) => { setInputSchema(e.target.value); setSchemaError(null) }}
              onBlur={() => setSchemaError(validateSchema(inputSchema))}
              rows={4}
              placeholder={INPUT_SCHEMA_PLACEHOLDER}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/40 focus:outline-none focus:border-blue-500/60 font-mono text-xs"
            />
            {schemaError && <p className="text-xs text-red-400 mt-0.5">{schemaError}</p>}
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">脚本内容 <span className="text-white/30">（可选，可后续在对话中由 AI 补充）</span></label>
            <textarea
              value={scriptContent}
              onChange={(e) => setScriptContent(e.target.value)}
              rows={4}
              placeholder={toolType === 'PYTHON_SCRIPT' ? '# 例如：print("hello")' : toolType === 'NODE_SCRIPT' ? '// 例如：console.log("hello")' : '# 例如：echo hello'}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/40 focus:outline-none focus:border-blue-500/60 font-mono text-xs"
            />
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">入口文件名 entry_point <span className="text-white/30">（可选，默认 工具名+.py/.js/.sh）</span></label>
            <input
              type="text"
              value={entryPoint}
              onChange={(e) => setEntryPoint(e.target.value)}
              placeholder={`例如 ${toolName || 'tool_name'}.py`}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/30 focus:outline-none focus:border-blue-500/60 font-mono"
            />
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
            onClick={handleSubmit}
            disabled={isCreating}
            className="px-3 py-1.5 text-xs rounded-lg bg-blue-600 text-white hover:bg-blue-500 disabled:opacity-50"
          >
            {isCreating ? '创建中…' : '创建'}
          </button>
        </div>
      </div>
    </div>
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
