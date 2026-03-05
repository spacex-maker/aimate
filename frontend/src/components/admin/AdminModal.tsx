import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Activity, Box, RefreshCw, Users } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { adminApi } from '../../api/admin'
import type { SystemModelDto } from '../../types/agent'
import type { AdminUserListItem, UserContainerStatus } from '../../types/admin'

type AdminTab = 'containers' | 'models' | 'users'

function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

export function AdminModal({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<AdminTab>('containers')

  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-4xl max-h-[85vh] flex flex-col overflow-hidden">
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
          </div>

          {/* 右侧内容 */}
          <div className="flex-1 overflow-auto p-5 min-w-0">
            {tab === 'containers' && <ContainerMonitorContent />}
            {tab === 'models' && <ModelManageContent />}
            {tab === 'users' && <UserManageContent />}
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
        <p className="text-xs text-white/40 mt-0.5">启用/禁用账号、调整用户角色（USER / ADMIN）</p>
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
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-32">操作</th>
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
                  <td className="py-2 px-3">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {u.status === 'ACTIVE' ? (
                        <button
                          type="button"
                          onClick={() => setStatus(u, 'DISABLED')}
                          disabled={updateMutation.isPending}
                          className="px-2 py-1 rounded border border-red-500/40 text-red-400/90 hover:bg-red-500/10 text-[11px] transition-colors disabled:opacity-50"
                        >
                          禁用
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setStatus(u, 'ACTIVE')}
                          disabled={updateMutation.isPending}
                          className="px-2 py-1 rounded border border-emerald-500/40 text-emerald-400/90 hover:bg-emerald-500/10 text-[11px] transition-colors disabled:opacity-50"
                        >
                          启用
                        </button>
                      )}
                      {u.role === 'ADMIN' ? (
                        <button
                          type="button"
                          onClick={() => setRole(u, 'USER')}
                          disabled={updateMutation.isPending}
                          className="px-2 py-1 rounded border border-white/20 text-white/70 hover:bg-white/10 text-[11px] transition-colors disabled:opacity-50"
                        >
                          设为用户
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setRole(u, 'ADMIN')}
                          disabled={updateMutation.isPending}
                          className="px-2 py-1 rounded border border-amber-500/40 text-amber-400/90 hover:bg-amber-500/10 text-[11px] transition-colors disabled:opacity-50"
                        >
                          设为管理员
                        </button>
                      )}
                    </div>
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
