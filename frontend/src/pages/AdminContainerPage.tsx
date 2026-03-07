import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../api/admin'
import { ComponentStatusCard } from '../components/admin/ComponentStatusCard'
import { useAuth } from '../hooks/useAuth'

export function AdminContainerPage() {
  const { user } = useAuth()
  const { data = [], isLoading, refetch } = useQuery({
    queryKey: ['admin-containers'],
    queryFn: () => adminApi.listContainers(),
  })
  const { data: componentStatus, isLoading: statusLoading } = useQuery({
    queryKey: ['admin-component-status'],
    queryFn: () => adminApi.getComponentStatus(),
  })

  if (!user || user.role !== 'ADMIN') {
    return <div className="p-6 text-sm text-red-400">无权限访问（仅管理员可查看容器监控）。</div>
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center justify-between px-6 py-4 border-b border-white/10">
        <div>
          <h1 className="text-base font-semibold text-white">容器监控</h1>
          <p className="text-xs text-white/40 mt-0.5">查看所有用户脚本容器的运行状态与资源占用</p>
        </div>
        <button
          onClick={() => refetch()}
          className="px-3 py-1.5 text-xs rounded-lg border border-white/20 text-white/80 hover:text-white hover:bg-white/10 transition-colors"
        >
          刷新
        </button>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4 space-y-4">
        {!statusLoading && componentStatus && (
          <ComponentStatusCard status={componentStatus} />
        )}

      <div>
        {isLoading ? (
          <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
        ) : data.length === 0 ? (
          <div className="text-center py-10 text-white/30 text-sm">当前没有用户容器。</div>
        ) : (
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left">
                <th className="pb-2 pr-3 font-medium text-white/40">用户 ID</th>
                <th className="pb-2 pr-3 font-medium text-white/40">用户名</th>
                <th className="pb-2 pr-3 font-medium text-white/40">容器名称</th>
                <th className="pb-2 pr-3 font-medium text-white/40">状态</th>
                <th className="pb-2 pr-3 font-medium text-white/40">CPU</th>
                <th className="pb-2 pr-3 font-medium text-white/40">内存</th>
                <th className="pb-2 pr-3 font-medium text-white/40">最近使用时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {data.map((c) => (
                <tr key={c.containerName} className="hover:bg-white/[0.03]">
                  <td className="py-2 pr-3 font-mono text-white/70">{c.userId}</td>
                  <td className="py-2 pr-3">{c.username}</td>
                  <td className="py-2 pr-3 font-mono text-white/70">{c.containerName}</td>
                  <td className="py-2 pr-3">
                    {c.status?.toLowerCase().startsWith('up') || c.status === 'running' ? (
                      <span className="text-emerald-400">运行中</span>
                    ) : (
                      <span className="text-white/40">{c.status || '未知'}</span>
                    )}
                  </td>
                  <td className="py-2 pr-3">{c.cpuPercent ?? '—'}</td>
                  <td className="py-2 pr-3">{c.memUsage ?? '—'}</td>
                  <td className="py-2 pr-3">
                    {c.lastUsedAt
                      ? new Date(c.lastUsedAt).toLocaleString()
                      : <span className="text-white/30">未知</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      </div>
    </div>
  )
}
