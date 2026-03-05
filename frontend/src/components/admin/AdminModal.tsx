import { useState } from 'react'
import type React from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Activity, Box, RefreshCw, Users, SlidersHorizontal } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { adminApi } from '../../api/admin'
import type { SystemModelDto, ToolSettingsDto } from '../../types/agent'
import type { AdminUserListItem, SystemConfigItem, UserContainerStatus } from '../../types/admin'

type AdminTab = 'containers' | 'models' | 'users' | 'systemConfig'

function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

export function AdminModal({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<AdminTab>('containers')

  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-6xl h-[90vh] flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/10 flex-shrink-0">
          <h2 className="text-sm font-semibold text-white">管理</h2>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-white/40 hover:text-white hover:bg-white/10 transition-colors"
            title="关闭"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex flex-1 min-h-0">
          {/* 左侧菜单 */}
          <div className="w-44 flex-shrink-0 border-r border-white/10 py-2">
            <button
              type="button"
              onClick={() => setTab('containers')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                tab === 'containers'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <Activity className="w-4 h-4 flex-shrink-0" />
              容器监控
            </button>
            <button
              type="button"
              onClick={() => setTab('models')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                tab === 'models'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <Box className="w-4 h-4 flex-shrink-0" />
              模型管理
            </button>
            <button
              type="button"
              onClick={() => setTab('users')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                tab === 'users'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <Users className="w-4 h-4 flex-shrink-0" />
              用户管理
            </button>
            <button
              type="button"
              onClick={() => setTab('systemConfig')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                tab === 'systemConfig'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <SlidersHorizontal className="w-4 h-4 flex-shrink-0" />
              系统配置
            </button>
          </div>

          {/* 右侧内容 */}
          <div className="flex-1 overflow-auto p-5 min-w-0 relative">
            {tab === 'containers' && <ContainerMonitorContent />}
            {tab === 'models' && <ModelManageContent />}
            {tab === 'users' && <UserManageContent />}
            {tab === 'systemConfig' && <SystemConfigContent />}
          </div>
        </div>
      </div>
    </div>
  )
}

function ContainerMonitorContent() {
  const { data = [], isLoading, refetch } = useQuery({
    queryKey: ['admin-containers'],
    queryFn: () => adminApi.listContainers(),
  })

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-sm font-medium text-white">容器监控</h3>
          <p className="text-xs text-white/40 mt-0.5">查看所有用户脚本容器的运行状态与资源占用</p>
        </div>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-white/20 text-white/80 hover:text-white hover:bg-white/10 transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          刷新
        </button>
      </div>
      {isLoading ? (
        <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
      ) : data.length === 0 ? (
        <div className="text-center py-10 text-white/30 text-sm">当前没有用户容器。</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/10">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">用户 ID</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">用户名</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">容器名称</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">状态</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">CPU</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">内存</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">最近使用时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {data.map((c: UserContainerStatus) => (
                <tr key={c.containerName} className="hover:bg-white/[0.03]">
                  <td className="py-2 px-3 font-mono text-white/70">{c.userId}</td>
                  <td className="py-2 px-3">{c.username}</td>
                  <td className="py-2 px-3 font-mono text-white/70">{c.containerName}</td>
                  <td className="py-2 px-3">
                    {c.status?.toLowerCase().startsWith('up') || c.status === 'running' ? (
                      <span className="text-emerald-400">运行中</span>
                    ) : (
                      <span className="text-white/40">{c.status || '未知'}</span>
                    )}
                  </td>
                  <td className="py-2 px-3">{c.cpuPercent ?? '—'}</td>
                  <td className="py-2 px-3">{c.memUsage ?? '—'}</td>
                  <td className="py-2 px-3">
                    {c.lastUsedAt
                      ? new Date(c.lastUsedAt).toLocaleString()
                      : <span className="text-white/30">未知</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function ModelManageContent() {
  const queryClient = useQueryClient()
  const { data: models = [], isLoading } = useQuery({
    queryKey: ['admin-system-models'],
    queryFn: () => adminApi.listAllSystemModels(),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      adminApi.updateSystemModelEnabled(id, enabled),
    onSuccess: (_, { enabled }) => {
      toast.success(enabled ? '已开启该模型' : '已关闭该模型')
      queryClient.invalidateQueries({ queryKey: ['admin-system-models'] })
      queryClient.invalidateQueries({ queryKey: ['system-models'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const toggleEnabled = (m: SystemModelDto) => {
    if (m.id == null) return
    updateMutation.mutate({ id: m.id, enabled: !(m.enabled ?? true) })
  }

  return (
    <div className="h-full flex flex-col">
      <div className="mb-4">
        <h3 className="text-sm font-medium text-white">模型管理</h3>
        <p className="text-xs text-white/40 mt-0.5">开启或关闭模型后，将影响所有用户在「模型选择」与「API 密钥」中可见的选项</p>
      </div>
      {isLoading ? (
        <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
      ) : models.length === 0 ? (
        <div className="text-center py-10 text-white/30 text-sm">暂无系统模型，请先执行 seed_system_models.sql</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/10">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">服务商</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">展示名</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">模型 ID</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-24">启用</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {models.map((m: SystemModelDto) => (
                <tr key={m.id} className="hover:bg-white/[0.03]">
                  <td className="py-2 px-3 text-white/70">{formatProvider(m.provider)}</td>
                  <td className="py-2 px-3">{m.displayName}</td>
                  <td className="py-2 px-3 font-mono text-white/60">{m.modelId}</td>
                  <td className="py-2 px-3">
                    <button
                      type="button"
                      onClick={() => toggleEnabled(m)}
                      disabled={updateMutation.isPending}
                      className={clsx(
                        'w-9 h-5 rounded-full transition-colors relative',
                        m.enabled !== false ? 'bg-blue-600' : 'bg-white/15'
                      )}
                    >
                      <div
                        className={clsx(
                          'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform',
                          m.enabled !== false ? 'translate-x-4' : 'translate-x-0.5'
                        )}
                      />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function UserManageContent() {
  const queryClient = useQueryClient()
  const [selectedUser, setSelectedUser] = useState<AdminUserListItem | null>(null)
  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => adminApi.listUsers(),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { status?: string; role?: string } }) =>
      adminApi.updateUser(id, body),
    onSuccess: () => {
      toast.success('已更新')
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const setStatus = (u: AdminUserListItem, status: 'ACTIVE' | 'DISABLED') => {
    updateMutation.mutate({ id: u.id, body: { status } })
  }

  const setRole = (u: AdminUserListItem, role: 'USER' | 'ADMIN') => {
    updateMutation.mutate({ id: u.id, body: { role } })
  }

  const formatTime = (s: string | null) =>
    s ? new Date(s).toLocaleString('zh-CN', { dateStyle: 'short', timeStyle: 'short' }) : '—'

  return (
    <div className="h-full flex flex-col">
      <div className="mb-4">
        <h3 className="text-sm font-medium text-white">用户管理</h3>
        <p className="text-xs text-white/40 mt-0.5">启用/禁用账号、调整用户角色（USER / ADMIN），配置用户可用的系统工具</p>
      </div>
      {isLoading ? (
        <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
      ) : users.length === 0 ? (
        <div className="text-center py-10 text-white/30 text-sm">暂无用户</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/10">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">用户名</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">邮箱</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">昵称</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">状态</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">角色</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">注册时间</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">最近登录</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-24 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {users.map((u: AdminUserListItem) => (
                <tr key={u.id} className="hover:bg-white/[0.03]">
                  <td className="py-2 px-3 font-mono text-white/90">{u.username}</td>
                  <td className="py-2 px-3 text-white/60">{u.email ?? '—'}</td>
                  <td className="py-2 px-3 text-white/70">{u.displayName ?? '—'}</td>
                  <td className="py-2 px-3">
                    <span
                      className={clsx(
                        'px-1.5 py-0.5 rounded text-[11px] font-medium',
                        u.status === 'ACTIVE'
                          ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                          : 'bg-white/10 text-white/50 border border-white/10'
                      )}
                    >
                      {u.status === 'ACTIVE' ? '正常' : '已禁用'}
                    </span>
                  </td>
                  <td className="py-2 px-3">
                    <span
                      className={clsx(
                        'px-1.5 py-0.5 rounded text-[11px] font-medium',
                        u.role === 'ADMIN'
                          ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                          : 'bg-white/10 text-white/60 border border-white/10'
                      )}
                    >
                      {u.role === 'ADMIN' ? '管理员' : '用户'}
                    </span>
                  </td>
                  <td className="py-2 px-3 text-white/50">{formatTime(u.createTime)}</td>
                  <td className="py-2 px-3 text-white/50">{formatTime(u.lastLoginTime)}</td>
                  <td className="py-2 px-3 text-right">
                    <button
                      type="button"
                      onClick={() => setSelectedUser(u)}
                      className="inline-flex items-center justify-center px-2.5 py-1.5 rounded border border-white/25 text-[11px] text-white/80 hover:bg-white/10 transition-colors"
                    >
                      操作
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selectedUser && (
        <UserOperationModal
          user={selectedUser}
          onClose={() => setSelectedUser(null)}
          onChangeStatus={setStatus}
          onChangeRole={setRole}
        />
      )}
    </div>
  )
}

interface UserOperationModalProps {
  user: AdminUserListItem
  onClose: () => void
  onChangeStatus: (u: AdminUserListItem, status: 'ACTIVE' | 'DISABLED') => void
  onChangeRole: (u: AdminUserListItem, role: 'USER' | 'ADMIN') => void
}

function UserOperationModal({
  user,
  onClose,
  onChangeStatus,
  onChangeRole,
}: UserOperationModalProps) {
  const queryClient = useQueryClient()
  const [localUser, setLocalUser] = useState<AdminUserListItem>(user)

  const { data: toolSettings, isLoading: isToolLoading } = useQuery({
    queryKey: ['admin-user-tool-settings', localUser.id],
    queryFn: () => adminApi.getUserToolSettings(localUser.id),
  })

  const updateToolsMutation = useMutation({
    mutationFn: (body: ToolSettingsDto) => adminApi.updateUserToolSettings(localUser.id, body),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin-user-tool-settings', localUser.id], data)
      toast.success('工具设置已保存')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleToggleTool = (key: keyof ToolSettingsDto, value: boolean) => {
    if (!toolSettings) return
    const next: ToolSettingsDto = { ...toolSettings, [key]: value }
    queryClient.setQueryData(['admin-user-tool-settings', localUser.id], next)
    updateToolsMutation.mutate(next)
  }

  const handleStatusChange = (status: 'ACTIVE' | 'DISABLED') => {
    setLocalUser({ ...localUser, status })
    onChangeStatus(localUser, status)
  }

  const handleRoleChange = (role: 'USER' | 'ADMIN') => {
    setLocalUser({ ...localUser, role })
    onChangeRole(localUser, role)
  }

  return (
    <div className="absolute inset-0 bg-black/70 flex items-center justify-center z-10">
      <div className="bg-[#111111] border border-white/10 rounded-xl w-full max-w-lg max-h-[80vh] flex flex-col overflow-hidden shadow-xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <div>
            <h3 className="text-sm font-semibold text-white">用户操作</h3>
            <p className="text-xs text-white/50 mt-0.5">
              {localUser.username}
              {localUser.displayName ? `（${localUser.displayName}）` : ''}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 space-y-4 overflow-auto">
          <section className="space-y-3">
            <div>
              <h4 className="text-xs font-semibold text-white/70">账号状态</h4>
              <p className="mt-0.5 text-[11px] text-white/40">
                控制该账号是否允许登录和使用系统
              </p>
            </div>
            <div className="inline-flex items-stretch rounded-lg border border-white/15 bg-white/[0.03] p-0.5 text-[11px]">
              <button
                type="button"
                onClick={() => handleStatusChange('ACTIVE')}
                className={clsx(
                  'px-3 py-1.5 rounded-md transition-colors min-w-[72px] text-center',
                  localUser.status === 'ACTIVE'
                    ? 'bg-emerald-500/20 text-emerald-200'
                    : 'text-white/55 hover:bg-white/5'
                )}
              >
                正常
              </button>
              <button
                type="button"
                onClick={() => handleStatusChange('DISABLED')}
                className={clsx(
                  'px-3 py-1.5 rounded-md transition-colors min-w-[72px] text-center',
                  localUser.status === 'DISABLED'
                    ? 'bg-red-500/20 text-red-200'
                    : 'text-white/55 hover:bg-white/5'
                )}
              >
                已禁用
              </button>
            </div>
          </section>

          <section className="space-y-3">
            <div>
              <h4 className="text-xs font-semibold text-white/70">用户角色</h4>
              <p className="mt-0.5 text-[11px] text-white/40">
                管理员拥有系统管理和其他用户配置权限
              </p>
            </div>
            <div className="inline-flex items-stretch rounded-lg border border-white/15 bg-white/[0.03] p-0.5 text-[11px]">
              <button
                type="button"
                onClick={() => handleRoleChange('USER')}
                className={clsx(
                  'px-3 py-1.5 rounded-md transition-colors min-w-[72px] text-center',
                  localUser.role === 'USER'
                    ? 'bg-white/15 text-white'
                    : 'text-white/55 hover:bg-white/5'
                )}
              >
                普通用户
              </button>
              <button
                type="button"
                onClick={() => handleRoleChange('ADMIN')}
                className={clsx(
                  'px-3 py-1.5 rounded-md transition-colors min-w-[72px] text-center',
                  localUser.role === 'ADMIN'
                    ? 'bg-amber-500/20 text-amber-200'
                    : 'text-white/55 hover:bg-white/5'
                )}
              >
                管理员
              </button>
            </div>
          </section>

          <section className="space-y-2">
            <h4 className="text-xs font-semibold text-white/70">用户系统工具设置</h4>
            {isToolLoading || !toolSettings ? (
              <p className="text-xs text-white/40 py-1.5">加载工具设置中…</p>
            ) : (
              <div className="space-y-1.5">
                {(
                  [
                    ['memoryEnabled', '长期记忆'],
                    ['webSearchEnabled', '联网搜索'],
                    ['createToolEnabled', 'AI 自主编写工具'],
                    ['scriptExecEnabled', '用户系统脚本执行'],
                  ] as [keyof ToolSettingsDto, string][]
                ).map(([key, label]) => {
                  const checked = toolSettings[key]
                  return (
                    <div
                      key={key}
                      className="flex items-center justify-between gap-3 py-1.5 px-2 rounded-lg hover:bg-white/[0.04] transition-colors"
                    >
                      <span className="text-xs text-white/85">{label}</span>
                      <button
                        type="button"
                        onClick={() => handleToggleTool(key, !checked)}
                        className={clsx(
                          'relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white/40',
                          checked ? 'bg-blue-500' : 'bg-white/20'
                        )}
                      >
                        <span
                          className={clsx(
                            'pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow ring-0 transition-transform mt-0.5 ml-0.5',
                            checked ? 'translate-x-4' : 'translate-x-0'
                          )}
                        />
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

function SystemConfigContent() {
  const queryClient = useQueryClient()
  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['admin-system-configs'],
    queryFn: () => adminApi.listSystemConfigs(),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { configValue?: string | null; description?: string | null } }) =>
      adminApi.updateSystemConfig(id, body),
    onSuccess: () => {
      toast.success('配置已保存')
      queryClient.invalidateQueries({ queryKey: ['admin-system-configs'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleChange =
    (config: SystemConfigItem, field: 'configValue' | 'description') =>
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value
      queryClient.setQueryData<SystemConfigItem[]>(['admin-system-configs'], (prev) =>
        (prev ?? []).map((c) =>
          c.id === config.id
            ? { ...c, [field]: value }
            : c
        )
      )
    }

  const handleBlur = (config: SystemConfigItem) => {
    updateMutation.mutate({
      id: config.id,
      body: { configValue: config.configValue, description: config.description },
    })
  }

  return (
    <div className="h-full flex flex-col">
      <div className="mb-4">
        <h3 className="text-sm font-medium text-white">系统配置</h3>
        <p className="text-xs text-white/40 mt-0.5">
          管理全局配置项，例如第三方 API Key 等。请谨慎修改。
        </p>
      </div>

      {isLoading ? (
        <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
      ) : configs.length === 0 ? (
        <div className="text-center py-10 text-white/30 text-sm">暂无系统配置项。</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/10">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-56">配置键</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">配置值</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-64">说明</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {configs.map((c) => (
                <tr key={c.id} className="align-top hover:bg-white/[0.03]">
                  <td className="py-2.5 px-3 font-mono text-[11px] text-white/70 break-all">
                    {c.configKey}
                  </td>
                  <td className="py-2.5 px-3">
                    <input
                      type="text"
                      value={c.configValue ?? ''}
                      onChange={handleChange(c, 'configValue')}
                      onBlur={() => handleBlur(c)}
                      placeholder="请输入配置值"
                      className="w-full bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white placeholder:text-white/25 focus:outline-none focus:ring-1 focus:ring-blue-500/70 focus:border-blue-500/70"
                    />
                  </td>
                  <td className="py-2.5 px-3">
                    <input
                      type="text"
                      value={c.description ?? ''}
                      onChange={handleChange(c, 'description')}
                      onBlur={() => handleBlur(c)}
                      placeholder="配置说明（可选）"
                      className="w-full bg-black/10 border border-white/10 rounded px-2 py-1.5 text-xs text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-blue-500/50 focus:border-blue-500/50"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
