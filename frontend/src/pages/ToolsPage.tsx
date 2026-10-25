import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2, Shield, Plus, Bot, Building2, Lock } from 'lucide-react'
import toast from 'react-hot-toast'
import { toolsApi } from '../api/tools'
import { officeApi } from '../api/multiAgent'
import { useAuth } from '../hooks/useAuth'
import { AppSelect } from '../ui/AppSelect'
import type { CreateUserToolRequest, UserToolDto, UpdateUserToolRequest } from '../types/tools'

type ToolsTab = 'official' | 'agent' | 'office'

/** 官方工具库（与后端 OFFICIAL_TOOL_ORDER 一致；含 create_tool 元工具） */
const OFFICIAL_TOOL_ORDER = [
  'recall_memory',
  'store_memory',
  'find_tools',
  'tavily_search',
  'http_request',
  'create_tool',
] as const

const OFFICIAL_TOOL_NAMES = new Set<string>(OFFICIAL_TOOL_ORDER)

const OFFICIAL_TOOL_META: Record<string, { title: string }> = {
  recall_memory: { title: '回忆记忆' },
  store_memory: { title: '写入记忆' },
  find_tools: { title: '查找工具' },
  tavily_search: { title: '联网搜索' },
  http_request: { title: 'HTTP 请求' },
  create_tool: { title: '创建工具' },
}

/** 办公室重活（不在官方库展示） */
const OFFICE_TOOL_NAMES = new Set([
  'install_container_package',
  'run_container_cmd',
  'write_container_file',
])

/** 需租用办公室的重活（装 JDK、任意 shell、沙箱写文件）；简单 API 工具不在此列 */
const OFFICE_TOOL_CATALOG: { name: string; title: string; description: string }[] = [
  {
    name: 'install_container_package',
    title: '安装软件包',
    description: '在办公室 CVM 安装运行时/系统包（如 JDK、python3、nodejs）。简单查天气类工具不需要。',
  },
  {
    name: 'run_container_cmd',
    title: '执行命令',
    description: '在办公室执行任意 shell（部署后端、环境配置等）。平台侧简单脚本请用 Agent 工具箱。',
  },
  {
    name: 'write_container_file',
    title: '写入文件',
    description: '向办公室沙箱写入文件（长脚本、配置）。Agent 工具箱脚本由平台保存，不必走此工具。',
  },
]

export function ToolsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const userId = user?.userId
  const [tab, setTab] = useState<ToolsTab>('official')

  const [editing, setEditing] = useState<UserToolDto | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<UserToolDto | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const { data: tools = [], isLoading } = useQuery({
    queryKey: ['user-tools', userId],
    queryFn: () => (userId != null ? toolsApi.list(userId) : Promise.resolve([])),
    enabled: !!userId,
  })

  const { data: office } = useQuery({
    queryKey: ['office-me'],
    queryFn: () => officeApi.me(),
    enabled: !!userId,
    staleTime: 30_000,
  })

  const hasOffice = !!(office && (office.status === 'ACTIVE' || office.ready))

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

  /** 官方库：仅四件套且按名去重（防御历史重复行） */
  const officialTools = useMemo(() => {
    const byName = new Map<string, UserToolDto>()
    for (const t of tools) {
      if (!t.isSystem || !OFFICIAL_TOOL_NAMES.has(t.toolName) || OFFICE_TOOL_NAMES.has(t.toolName)) continue
      const prev = byName.get(t.toolName)
      if (!prev || t.id < prev.id) byName.set(t.toolName, t)
    }
    return OFFICIAL_TOOL_ORDER.map((n) => byName.get(n)).filter((t): t is UserToolDto => !!t)
  }, [tools])
  const agentTools = useMemo(() => tools.filter((t) => !t.isSystem), [tools])

  if (userId == null) {
    return (
      <div className="h-full flex items-center justify-center text-white/40 text-sm">加载用户信息中…</div>
    )
  }

  const tabs: { id: ToolsTab; label: string; icon: typeof Shield }[] = [
    { id: 'official', label: '官方工具库', icon: Shield },
    { id: 'agent', label: 'Agent 工具箱', icon: Bot },
    { id: 'office', label: '办公室工具', icon: Building2 },
  ]

  return (
    <div className="h-full flex flex-col">
      <div className="flex-shrink-0 px-6 py-4 border-b border-white/10 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-base font-semibold text-white">工具</h1>
          <p className="text-xs text-white/40 mt-0.5">
            官方工具库平台内置；Agent 可沉淀简单工具由平台执行；装 JDK 等重活才需办公室
          </p>
        </div>
        {tab === 'agent' && (
          <button
            type="button"
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors"
          >
            <Plus className="w-4 h-4" />
            新增工具
          </button>
        )}
      </div>

      <div className="flex-shrink-0 px-6 pt-3 flex gap-1 border-b border-white/10">
        {tabs.map((t) => {
          const Icon = t.icon
          const active = tab === t.id
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-1.5 px-3 py-2 text-sm rounded-t-lg border-b-2 transition-colors ${
                active
                  ? 'border-blue-500 text-white'
                  : 'border-transparent text-white/45 hover:text-white/75'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {t.label}
              {t.id === 'office' && !hasOffice && <Lock className="w-3 h-3 text-white/35" />}
            </button>
          )
        })}
      </div>

      <div className="flex-1 overflow-auto px-6 py-4">
        {isLoading ? (
          <div className="text-center py-10 text-white/40 text-sm">加载中…</div>
        ) : tab === 'official' ? (
          <section>
            <p className="text-xs text-white/40 mb-3">
              系统级内置能力（无用户归属），所有用户可用（受个人工具开关影响）。只读，不可编辑删除。
            </p>
            {officialTools.length === 0 ? (
              <div className="text-center py-10 text-white/30 text-sm">暂无官方工具</div>
            ) : (
              <ul className="space-y-2">
                {officialTools.map((t) => (
                  <ToolRow
                    key={t.toolName}
                    tool={t}
                    badge="官方"
                    title={OFFICIAL_TOOL_META[t.toolName]?.title}
                    readOnly
                  />
                ))}
              </ul>
            )}
          </section>
        ) : tab === 'agent' ? (
          <section>
            <p className="text-xs text-white/40 mb-3">
              对话中 Agent 用 create_tool 沉淀的简单工具（如查天气 API）会出现在这里；由平台保存并执行，无需办公室。
              装 JDK、任意 shell 等请到「办公室工具」。
            </p>
            {agentTools.length === 0 ? (
              <div className="text-center py-10 text-white/30 text-sm space-y-2">
                <p>暂无 Agent 工具</p>
                <p className="text-white/25 text-xs">在对话中让 Agent 编写并沉淀工具，或手动新增</p>
              </div>
            ) : (
              <ul className="space-y-2">
                {agentTools.map((t) => (
                  <ToolRow
                    key={t.id}
                    tool={t}
                    badge="Agent"
                    onEdit={() => setEditing(t)}
                    onDelete={() => setConfirmDelete(t)}
                    isDeleting={deletingId === t.id}
                  />
                ))}
              </ul>
            )}
          </section>
        ) : (
          <section>
            {!hasOffice ? (
              <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 px-4 py-4 mb-4">
                <div className="flex items-start gap-3">
                  <Lock className="w-4 h-4 text-amber-400/90 mt-0.5 shrink-0" />
                  <div className="min-w-0">
                    <p className="text-sm text-white/85">尚未开通办公室</p>
                    <p className="text-xs text-white/45 mt-1">
                      办公室面向「需要私有 Linux 环境」的重活：装 JDK、apt、任意命令、沙箱写文件。查天气等简单工具请用 Agent 工具箱，平台即可执行。
                    </p>
                    <Link
                      to="/offices"
                      className="inline-flex items-center gap-1.5 mt-3 text-xs text-sky-400 hover:text-sky-300"
                    >
                      <Building2 className="w-3.5 h-3.5" />
                      前往办公室租用
                    </Link>
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-xs text-white/40 mb-3">
                办公室已开通
                {office?.planName ? `（${office.planName}）` : ''}
                — 下列工具已对 Agent 开放，在对话中按需调用。
              </p>
            )}
            <ul className="space-y-2">
              {OFFICE_TOOL_CATALOG.map((t) => (
                <li
                  key={t.name}
                  className={`rounded-lg border px-4 py-3 ${
                    hasOffice
                      ? 'border-white/10 bg-white/[0.03]'
                      : 'border-white/8 bg-white/[0.02] opacity-70'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm text-white/90">{t.name}</span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-300/80">
                      办公室
                    </span>
                    {!hasOffice && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/40">未解锁</span>
                    )}
                  </div>
                  <p className="text-xs text-white/70 mt-1">{t.title}</p>
                  <p className="text-xs text-white/45 mt-0.5">{t.description}</p>
                </li>
              ))}
            </ul>
          </section>
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
  badge,
  title,
  readOnly,
  onEdit,
  onDelete,
  isDeleting,
}: {
  tool: UserToolDto
  badge?: string
  title?: string
  readOnly?: boolean
  onEdit?: () => void
  onDelete?: () => void
  isDeleting?: boolean
}) {
  return (
    <li className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-3 flex items-center justify-between gap-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm text-white/90">{tool.toolName}</span>
          {badge && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/50">{badge}</span>
          )}
          {!tool.isActive && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-400/80">已停用</span>
          )}
        </div>
        {title && <p className="text-xs text-white/70 mt-1">{title}</p>}
        <p className="text-xs text-white/50 mt-0.5 line-clamp-2">{tool.toolDescription}</p>
        <p className="text-[11px] text-white/30 mt-1">{tool.toolType}</p>
      </div>
      {!readOnly && onEdit && onDelete && (
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
  'recall_memory', 'store_memory', 'find_tools', 'tavily_search', 'http_request', 'create_tool',
  'install_container_package', 'run_container_cmd', 'write_container_file',
]

const TOOL_TYPE_OPTIONS = [
  { value: 'PYTHON_SCRIPT', label: 'PYTHON_SCRIPT（Python 脚本）' },
  { value: 'NODE_SCRIPT', label: 'NODE_SCRIPT（Node.js 脚本）' },
  { value: 'SHELL_CMD', label: 'SHELL_CMD（Shell 命令）' },
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
    onCreate({
      toolName: name,
      toolDescription: desc,
      inputSchema: inputSchema.trim(),
      toolType,
      scriptContent: scriptContent.trim() || undefined,
      entryPoint: entryPoint.trim() || undefined,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto" onClick={onClose}>
      <div
        className="bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl max-w-lg w-full my-4 p-5 text-left"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-sm font-semibold text-white/90 mb-1">新增 Agent 工具</h3>
        <p className="text-xs text-white/45 mb-4">平台保存并执行（无需办公室）；适合 API 封装等简单脚本，与对话中 create_tool 一致</p>
        <div className="space-y-3">
          <div>
            <label className="text-xs text-white/50 block mb-1">工具名</label>
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
            <label className="text-xs text-white/50 block mb-1">工具描述</label>
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
            <AppSelect
              value={toolType}
              onChange={(v) => setToolType(v as CreateUserToolRequest['toolType'])}
              options={TOOL_TYPE_OPTIONS}
              ariaLabel="工具类型"
            />
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">参数结构 input_schema</label>
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
            <label className="text-xs text-white/50 block mb-1">脚本内容（可选）</label>
            <textarea
              value={scriptContent}
              onChange={(e) => setScriptContent(e.target.value)}
              rows={4}
              placeholder={toolType === 'PYTHON_SCRIPT' ? '# 例如：print("hello")' : toolType === 'NODE_SCRIPT' ? '// 例如：console.log("hello")' : '# 例如：echo hello'}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/40 focus:outline-none focus:border-blue-500/60 font-mono text-xs"
            />
          </div>
          <div>
            <label className="text-xs text-white/50 block mb-1">入口文件名 entry_point（可选）</label>
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
