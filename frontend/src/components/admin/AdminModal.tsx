import { useEffect, useLayoutEffect, useRef, useState, useMemo } from 'react'
import type React from 'react'
import { createPortal } from 'react-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Activity, Box, RefreshCw, Users, SlidersHorizontal, Receipt } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { adminApi } from '../../api/admin'
import { ComponentStatusCard } from './ComponentStatusCard'
import type { HostResourceStatusDto, SystemModelDto, ToolSettingsDto } from '../../types/agent'
import type { AdminUserListItem, BillingUsageDetail, SystemConfigItem, UserContainerStatus } from '../../types/admin'
import { useHostStatusSocket } from '../../hooks/useHostStatusSocket'

type AdminTab = 'containers' | 'models' | 'users' | 'billing' | 'systemConfig'

function formatBytes(v?: number | null): string {
  if (v == null || v <= 0) return '未知'
  const gb = v / (1024 * 1024 * 1024)
  if (gb >= 1) return `${gb.toFixed(1)} GB`
  const mb = v / (1024 * 1024)
  return `${mb.toFixed(1)} MB`
}

function HostMetricLine({
  label,
  series,
  colorClass,
}: {
  label: string
  series: number[]
  colorClass: string
}) {
  const safeSeries = series.filter((v) => Number.isFinite(v))
  if (!safeSeries.length) {
    return (
      <div className="flex items-center justify-between text-[11px] text-white/45">
        <span>{label}</span>
        <span className="text-white/30">暂无数据</span>
      </div>
    )
  }
  const max = 100
  const min = 0
  const w = 140
  const h = 28
  const stepX = safeSeries.length <= 1 ? 0 : w / (safeSeries.length - 1)
  const path = safeSeries
    .map((v, idx) => {
      const clamped = Math.min(max, Math.max(min, v))
      const x = idx * stepX
      const y = h - ((clamped - min) / (max - min || 1)) * h
      return `${idx === 0 ? 'M' : 'L'} ${x} ${y}`
    })
    .join(' ')

  const latest = safeSeries[safeSeries.length - 1]

  return (
    <div className="flex items-center justify-between gap-3 text-[11px] text-white/75">
      <span className="text-white/60">{label}</span>
      <div className="flex items-center gap-2">
        <svg width={w} height={h} className="overflow-visible">
          <path d={path} className={colorClass} strokeWidth={1.5} fill="none" />
        </svg>
        <span className="font-mono text-white/80">{latest.toFixed(1)}%</span>
      </div>
    </div>
  )
}

function formatProvider(provider: string): string {
  const s = (provider || '').toLowerCase()
  if (s === 'xai') return 'xAI'
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : provider
}

export function AdminModal({ onClose }: { onClose: () => void }) {
  // 默认进入模型管理；容器监控为遗留能力，产线已关闭每用户 Docker
  const [tab, setTab] = useState<AdminTab>('models')

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
              onClick={() => setTab('containers')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                tab === 'containers'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <Activity className="w-4 h-4 flex-shrink-0" />
              主机状态
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
              onClick={() => setTab('billing')}
              className={clsx(
                'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors pl-8',
                tab === 'billing'
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:text-white/90 hover:bg-white/5'
              )}
            >
              <Receipt className="w-4 h-4 flex-shrink-0" />
              计费管理
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
            {tab === 'billing' && <BillingManageContent />}
            {tab === 'systemConfig' && <SystemConfigContent />}
          </div>
        </div>
      </div>
    </div>
  )
}

export function ContainerMonitorContent() {
  const { data = [], isLoading, isFetching, refetch } = useQuery({
    queryKey: ['admin-containers'],
    queryFn: () => adminApi.listContainers(),
  })
  const { data: hostStatus } = useQuery({
    queryKey: ['admin-host-status'],
    queryFn: () => adminApi.getHostStatus(),
  })
  const { data: componentStatus } = useQuery({
    queryKey: ['admin-component-status'],
    queryFn: () => adminApi.getComponentStatus(),
  })

  const [liveHostStatus, setLiveHostStatus] = useState<HostResourceStatusDto | null>(null)
  useHostStatusSocket(true, (status) => {
    setLiveHostStatus(status)
  })

  const effectiveHostStatus = liveHostStatus ?? hostStatus

  const memAvailablePercent = effectiveHostStatus?.hostAvailableMemoryPercent ?? null
  const memUsedPercent =
    memAvailablePercent != null ? Math.min(100, Math.max(0, 100 - memAvailablePercent)) : null
  const cpuLoadPercent = effectiveHostStatus?.systemCpuLoadPercent ?? null
  const diskUsedPercent = effectiveHostStatus?.rootFsUsedPercent ?? null

  const [rangeHours, setRangeHours] = useState<1 | 24 | 168>(24)
  const { data: metrics = [], isLoading: isMetricsLoading } = useQuery({
    queryKey: ['admin-host-metrics', rangeHours],
    queryFn: async () => {
      const nowMs = Date.now()
      const fromMs = nowMs - rangeHours * 60 * 60 * 1000
      return adminApi.listHostMetrics({ from: fromMs, to: nowMs })
    },
    refetchOnWindowFocus: false,
  })

  const cpuSeries = (metrics || []).map((m) => m.cpuLoadPercent ?? 0)
  const memSeries = (metrics || []).map((m) =>
    m.memAvailablePercent != null ? Math.max(0, 100 - m.memAvailablePercent) : 0
  )
  const diskSeries = (metrics || []).map((m) => m.rootFsUsedPercent ?? 0)

  return (
    <div className="h-full flex flex-col">
      <div className="mb-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-medium text-white">容器监控</h3>
            <p className="text-xs text-white/40 mt-0.5">查看所有用户脚本容器的运行状态与资源占用</p>
          </div>
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className={clsx(
              'flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-white/20 text-white/80 hover:text-white hover:bg-white/10 transition-colors',
              isFetching && 'opacity-60 cursor-not-allowed hover:bg-transparent'
            )}
          >
            <RefreshCw
              className={clsx(
                'w-3.5 h-3.5',
                isFetching && 'animate-spin'
              )}
            />
            {isFetching ? '刷新中…' : '刷新'}
          </button>
        </div>

        {componentStatus && <ComponentStatusCard status={componentStatus} />}

        {effectiveHostStatus && (
          <div className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-1.5 text-[11px]">
            <div className="text-white/75">
              <span className="text-white/40 mr-1.5">宿主机 CPU</span>
              <span className="font-mono">
                {effectiveHostStatus.cpuCores ?? '未知'} 核
                {cpuLoadPercent != null ? ` / 负载 ${cpuLoadPercent.toFixed(1)}%` : ''}
              </span>
              {cpuLoadPercent != null && (
                <div className="mt-1 w-40 h-1.5 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className={clsx(
                      'h-full rounded-full transition-all',
                      cpuLoadPercent >= 85
                        ? 'bg-red-500'
                        : cpuLoadPercent >= 65
                          ? 'bg-amber-400'
                          : 'bg-emerald-400'
                    )}
                    style={{ width: `${Math.min(100, Math.max(0, cpuLoadPercent))}%` }}
                  />
                </div>
              )}
            </div>
            <div className="text-white/75">
              <span className="text-white/40 mr-1.5">宿主机内存</span>
              <span className="font-mono">
                {formatBytes(effectiveHostStatus.hostAvailableMemoryBytes)} / {formatBytes(effectiveHostStatus.hostTotalMemoryBytes)}
                {effectiveHostStatus.hostAvailableMemoryPercent != null ? `（可用 ${effectiveHostStatus.hostAvailableMemoryPercent}%）` : ''}
              </span>
              {memUsedPercent != null && (
                <div className="mt-1 w-40 h-1.5 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className={clsx(
                      'h-full rounded-full transition-all',
                      memUsedPercent >= 85
                        ? 'bg-red-500'
                        : memUsedPercent >= 65
                          ? 'bg-amber-400'
                          : 'bg-emerald-400'
                    )}
                    style={{ width: `${memUsedPercent}%` }}
                  />
                </div>
              )}
            </div>
            <div className="text-white/75">
              <span className="text-white/40 mr-1.5">根分区使用</span>
              <span className="font-mono">
                {formatBytes(effectiveHostStatus.rootFsTotalBytes)} /{' '}
                {effectiveHostStatus.rootFsUsedPercent != null ? `${effectiveHostStatus.rootFsUsedPercent}%` : '未知'}
              </span>
              {diskUsedPercent != null && (
                <div className="mt-1 w-40 h-1.5 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className={clsx(
                      'h-full rounded-full transition-all',
                      diskUsedPercent >= 90
                        ? 'bg-red-500'
                        : diskUsedPercent >= 75
                          ? 'bg-amber-400'
                          : 'bg-emerald-400'
                    )}
                    style={{ width: `${Math.min(100, Math.max(0, diskUsedPercent))}%` }}
                  />
                </div>
              )}
            </div>
            <div className="text-white/75">
              <span className="text-white/40 mr-1.5">容器资源限制</span>
              <span className="font-mono">
                {(effectiveHostStatus.dockerMemoryLimit || 'mem不限') +
                  ' / ' +
                  (effectiveHostStatus.dockerCpuLimit && effectiveHostStatus.dockerCpuLimit > 0
                    ? `${effectiveHostStatus.dockerCpuLimit}核`
                    : 'CPU不限')}
              </span>
            </div>
          </div>
        )}

        <div className="rounded-lg border border-white/10 bg-black/30 px-4 py-3 space-y-2">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-white">系统负载时间轴</p>
              <p className="text-[10px] text-white/40">查看最近一段时间 CPU / 内存 / 磁盘的变化趋势</p>
            </div>
            <div className="inline-flex items-center rounded-full bg-white/5 border border-white/10 p-0.5 text-[10px]">
              {[1, 24, 168].map((h) => (
                <button
                  key={h}
                  type="button"
                  onClick={() => setRangeHours(h as 1 | 24 | 168)}
                  className={clsx(
                    'px-2.5 py-0.5 rounded-full transition-colors',
                    rangeHours === h
                      ? 'bg-white/90 text-black'
                      : 'text-white/60 hover:bg-white/10'
                  )}
                >
                  {h === 1 ? '1小时' : h === 24 ? '24小时' : '7天'}
                </button>
              ))}
            </div>
          </div>

          {isMetricsLoading ? (
            <p className="text-[11px] text-white/40 py-1.5">加载历史数据中…</p>
          ) : !metrics || metrics.length === 0 ? (
            <p className="text-[11px] text-white/35 py-1.5">最近暂无负载记录，请稍后再试。</p>
          ) : (
            <div className="space-y-1.5">
              <HostMetricLine label="CPU 负载" series={cpuSeries} colorClass="stroke-emerald-400" />
              <HostMetricLine label="内存使用" series={memSeries} colorClass="stroke-sky-400" />
              <HostMetricLine label="磁盘使用" series={diskSeries} colorClass="stroke-amber-400" />
            </div>
          )}
        </div>
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

function KeyPicker({
  value,
  options,
  disabled,
  onChange,
}: {
  value?: string | null
  options: { value: string; label: string }[]
  disabled?: boolean
  onChange: (next: string | null) => void
}) {
  const [open, setOpen] = useState(false)
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    if (!open || !buttonRef.current) {
      setMenuPos(null)
      return
    }
    const rect = buttonRef.current.getBoundingClientRect()
    const width = Math.max(rect.width, 160)
    const menuHeight = 208
    const spaceBelow = window.innerHeight - rect.bottom - 8
    const openUp = spaceBelow < Math.min(menuHeight, 120) && rect.top > spaceBelow
    const top = openUp
      ? Math.max(8, rect.top - Math.min(menuHeight, rect.top - 8) - 4)
      : rect.bottom + 4
    let left = rect.left
    if (left + width > window.innerWidth - 8) left = Math.max(8, window.innerWidth - width - 8)
    setMenuPos({ top, left, width })
  }, [open])

  useEffect(() => {
    if (!open) return
    const onDoc = (event: MouseEvent) => {
      const target = event.target as Node
      if (buttonRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setOpen(false)
    }
    const onScroll = () => setOpen(false)
    document.addEventListener('mousedown', onDoc)
    window.addEventListener('scroll', onScroll, true)
    window.addEventListener('resize', onScroll)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      window.removeEventListener('scroll', onScroll, true)
      window.removeEventListener('resize', onScroll)
    }
  }, [open])

  const current = options.find((option) => option.value === value)
  const label = current?.label ?? (value ? value : '未指定')
  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className="w-full text-left bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white/80 hover:border-white/30 disabled:opacity-40"
      >
        {label}
      </button>
      {open &&
        menuPos &&
        createPortal(
          <div
            ref={menuRef}
            className="fixed z-[80] max-h-52 overflow-auto rounded border border-white/15 bg-[#161616] shadow-xl"
            style={{ top: menuPos.top, left: menuPos.left, width: menuPos.width }}
          >
            <button
              type="button"
              onClick={() => {
                onChange(null)
                setOpen(false)
              }}
              className="block w-full text-left px-2 py-1.5 text-xs text-white/50 hover:bg-white/10"
            >
              未指定
            </button>
            {options.length === 0 ? (
              <div className="px-2 py-1.5 text-[11px] text-white/35">请先在系统配置添加 DeepSeek Key</div>
            ) : (
              options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    onChange(option.value)
                    setOpen(false)
                  }}
                  className={clsx(
                    'block w-full text-left px-2 py-1.5 text-xs hover:bg-white/10',
                    option.value === value ? 'text-blue-300' : 'text-white/80'
                  )}
                >
                  {option.label}
                </button>
              ))
            )}
          </div>,
          document.body
        )}
    </>
  )
}

export function ModelManageContent() {
  const queryClient = useQueryClient()
  const { data: models = [], isLoading } = useQuery({
    queryKey: ['admin-system-models'],
    queryFn: () => adminApi.listAllSystemModels(),
  })
  const { data: configs = [] } = useQuery({
    queryKey: ['admin-system-configs'],
    queryFn: () => adminApi.listSystemConfigs(),
  })
  const keyOptions = configs
    .filter((c) => c.configKey.startsWith('DEEPSEEK_API_KEY'))
    .map((c) => ({
      value: c.configKey,
      label: c.description?.trim() || c.configKey,
    }))

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof adminApi.updateSystemModel>[1] }) =>
      adminApi.updateSystemModel(id, body),
    onSuccess: () => {
      toast.success('已更新模型')
      queryClient.invalidateQueries({ queryKey: ['admin-system-models'] })
      queryClient.invalidateQueries({ queryKey: ['system-models'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const toggleEnabled = (m: SystemModelDto) => {
    if (m.id == null) return
    updateMutation.mutate({ id: m.id, body: { enabled: !(m.enabled ?? true) } })
  }

  const moveUp = (index: number) => {
    if (index <= 0) return
    const current = models[index]
    const prev = models[index - 1]
    if (!current || !prev) return
    updateMutation.mutate({ id: current.id, body: { sortOrder: (prev.sortOrder ?? 0) - 1 } })
  }

  const moveDown = (index: number) => {
    if (index >= models.length - 1) return
    const current = models[index]
    const next = models[index + 1]
    if (!current || !next) return
    updateMutation.mutate({ id: current.id, body: { sortOrder: (next.sortOrder ?? 0) + 1 } })
  }

  return (
    <div className="h-full flex flex-col">
      <div className="mb-4">
        <h3 className="text-sm font-medium text-white">模型管理</h3>
        <p className="text-xs text-white/40 mt-0.5">开启/关闭、API Key、提示，以及用户扣费积分倍率与系统成本单价（CNY/百万 token）。</p>
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
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-10">排序</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">服务商</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">展示名</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">模型 ID</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 min-w-[8rem]">提示</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-28">积分/1K</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-36">成本 CNY/1M</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-44">API Key</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-24">启用</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {models.map((m: SystemModelDto, index: number) => (
                <tr key={m.id} className="hover:bg-white/[0.03]">
                  <td className="py-2 px-2">
                    <div className="flex flex-col items-center gap-0.5">
                      <button
                        type="button"
                        onClick={() => moveUp(index)}
                        disabled={index === 0 || updateMutation.isPending}
                        className="text-white/40 hover:text-white/80 disabled:opacity-30 cursor-pointer"
                        title="提高优先级"
                      >
                        ▲
                      </button>
                      <button
                        type="button"
                        onClick={() => moveDown(index)}
                        disabled={index === models.length - 1 || updateMutation.isPending}
                        className="text-white/40 hover:text-white/80 disabled:opacity-30 cursor-pointer"
                        title="降低优先级"
                      >
                        ▼
                      </button>
                    </div>
                  </td>
                  <td className="py-2 px-3 text-white/70">{formatProvider(m.provider)}</td>
                  <td className="py-2 px-3">{m.displayName}</td>
                  <td className="py-2 px-3 font-mono text-white/60">{m.modelId}</td>
                  <td className="py-2 px-3">
                    <input
                      type="text"
                      defaultValue={m.tip ?? ''}
                      key={`tip-${m.id}-${m.tip ?? ''}`}
                      disabled={updateMutation.isPending}
                      placeholder="如：非高峰半价"
                      onBlur={(e) => {
                        if (m.id == null) return
                        const next = e.target.value.trim()
                        if (next === (m.tip ?? '').trim()) return
                        updateMutation.mutate({ id: m.id, body: { tip: next } })
                      }}
                      className="w-full min-w-[8rem] bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white placeholder:text-white/25 focus:outline-none focus:ring-1 focus:ring-blue-500/70"
                    />
                  </td>
                  <td className="py-2 px-2">
                    <div className="flex gap-1 items-center">
                      <input
                        type="number"
                        min={0}
                        defaultValue={m.pointsPer1kPromptTokens ?? 1}
                        key={`pp-${m.id}-${m.pointsPer1kPromptTokens}`}
                        title="prompt"
                        disabled={updateMutation.isPending}
                        onBlur={(e) => {
                          const v = Number(e.target.value)
                          if (!Number.isFinite(v) || v === (m.pointsPer1kPromptTokens ?? 1)) return
                          updateMutation.mutate({ id: m.id, body: { pointsPer1kPromptTokens: v } })
                        }}
                        className="w-12 bg-black/20 border border-white/15 rounded px-1 py-1 text-[11px] text-white"
                      />
                      <span className="text-white/25">/</span>
                      <input
                        type="number"
                        min={0}
                        defaultValue={m.pointsPer1kCompletionTokens ?? 3}
                        key={`pc-${m.id}-${m.pointsPer1kCompletionTokens}`}
                        title="completion"
                        disabled={updateMutation.isPending}
                        onBlur={(e) => {
                          const v = Number(e.target.value)
                          if (!Number.isFinite(v) || v === (m.pointsPer1kCompletionTokens ?? 3)) return
                          updateMutation.mutate({ id: m.id, body: { pointsPer1kCompletionTokens: v } })
                        }}
                        className="w-12 bg-black/20 border border-white/15 rounded px-1 py-1 text-[11px] text-white"
                      />
                    </div>
                  </td>
                  <td className="py-2 px-2">
                    <div className="grid grid-cols-2 gap-1">
                      {(
                        [
                          ['costInputCnyPer1mPeak', m.costInputCnyPer1mPeak, 'in高峰'],
                          ['costInputCnyPer1mOffpeak', m.costInputCnyPer1mOffpeak, 'in闲'],
                          ['costOutputCnyPer1mPeak', m.costOutputCnyPer1mPeak, 'out高峰'],
                          ['costOutputCnyPer1mOffpeak', m.costOutputCnyPer1mOffpeak, 'out闲'],
                        ] as const
                      ).map(([key, val, label]) => (
                        <input
                          key={`${key}-${m.id}-${val ?? ''}`}
                          type="number"
                          step="0.1"
                          min={0}
                          defaultValue={val ?? ''}
                          title={label}
                          placeholder={label}
                          disabled={updateMutation.isPending}
                          onBlur={(e) => {
                            const raw = e.target.value.trim()
                            const v = raw === '' ? null : Number(raw)
                            if (raw !== '' && !Number.isFinite(v as number)) return
                            if (v === (val ?? null)) return
                            updateMutation.mutate({ id: m.id, body: { [key]: v } })
                          }}
                          className="w-full bg-black/20 border border-white/15 rounded px-1 py-0.5 text-[10px] text-white placeholder:text-white/25"
                        />
                      ))}
                    </div>
                  </td>
                  <td className="py-2 px-3">
                    <KeyPicker
                      value={m.apiKeyConfigKey}
                      options={keyOptions}
                      disabled={updateMutation.isPending}
                      onChange={(next) => {
                        if (m.id == null) return
                        updateMutation.mutate({ id: m.id, body: { apiKeyConfigKey: next ?? '' } })
                      }}
                    />
                  </td>
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

export function UserManageContent() {
  const queryClient = useQueryClient()
  const [selectedUser, setSelectedUser] = useState<AdminUserListItem | null>(null)
  const [ledgerUser, setLedgerUser] = useState<AdminUserListItem | null>(null)
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
        <p className="text-xs text-white/40 mt-0.5">启用/禁用、角色、积分余额与流水</p>
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
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">积分</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">状态</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">角色</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">注册时间</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">最近登录</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-32 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {users.map((u: AdminUserListItem) => (
                <tr key={u.id} className="hover:bg-white/[0.03]">
                  <td className="py-2 px-3 font-mono text-white/90">{u.username}</td>
                  <td className="py-2 px-3 text-white/60">{u.email ?? '—'}</td>
                  <td className="py-2 px-3 text-white/70">{u.displayName ?? '—'}</td>
                  <td className="py-2 px-3 font-mono text-white/80">{(u.pointsBalance ?? 0).toLocaleString()}</td>
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
                  <td className="py-2 px-3 text-right space-x-1">
                    <button
                      type="button"
                      onClick={() => setLedgerUser(u)}
                      className="inline-flex items-center justify-center px-2 py-1.5 rounded border border-white/25 text-[11px] text-white/80 hover:bg-white/10"
                    >
                      流水
                    </button>
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
      {ledgerUser && (
        <UserLedgerModal
          user={ledgerUser}
          onClose={() => {
            setLedgerUser(null)
            queryClient.invalidateQueries({ queryKey: ['admin-users'] })
          }}
        />
      )}
    </div>
  )
}

function UserLedgerModal({ user, onClose }: { user: AdminUserListItem; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [delta, setDelta] = useState('')
  const [remark, setRemark] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['admin-user-ledger', user.id, page],
    queryFn: () => adminApi.getUserLedger(user.id, { page, size: 20 }),
  })
  const adjustMutation = useMutation({
    mutationFn: () => adminApi.adjustUserPoints(user.id, { delta: Number(delta), remark: remark || undefined }),
    onSuccess: () => {
      toast.success('已调整积分')
      setDelta('')
      setRemark('')
      queryClient.invalidateQueries({ queryKey: ['admin-user-ledger', user.id] })
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
      <div className="w-full max-w-md max-h-[80vh] flex flex-col rounded-xl border border-white/15 bg-[#12141a]" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <div>
            <h3 className="text-sm text-white font-medium">{user.username} 积分流水</h3>
            <p className="text-[11px] text-white/40">余额 {(data?.pointsBalance ?? user.pointsBalance ?? 0).toLocaleString()}</p>
          </div>
          <button type="button" onClick={onClose} className="text-white/40 hover:text-white"><X className="w-4 h-4" /></button>
        </div>
        <div className="px-4 py-3 border-b border-white/10 flex gap-2 items-center">
          <input
            type="number"
            value={delta}
            onChange={(e) => setDelta(e.target.value)}
            placeholder="调整量（可负）"
            className="flex-1 bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white"
          />
          <input
            type="text"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            placeholder="备注"
            className="flex-1 bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white"
          />
          <button
            type="button"
            disabled={adjustMutation.isPending || !delta || Number(delta) === 0}
            onClick={() => adjustMutation.mutate()}
            className="px-2.5 py-1.5 rounded bg-blue-600 text-xs text-white disabled:opacity-40"
          >
            调整
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {isLoading ? (
            <div className="text-xs text-white/40 text-center py-6">加载中…</div>
          ) : !(data?.items?.length) ? (
            <div className="text-xs text-white/30 text-center py-6">暂无流水</div>
          ) : (
            data!.items.map((row) => (
              <div key={row.id} className="rounded border border-white/10 px-2.5 py-2 text-[11px]">
                <div className="flex justify-between text-white/80">
                  <span>{row.remark || row.reason}</span>
                  <span className={clsx('font-mono', row.delta >= 0 ? 'text-emerald-300' : 'text-rose-300')}>
                    {row.delta > 0 ? '+' : ''}{row.delta}
                  </span>
                </div>
                <div className="mt-1 text-white/35 flex justify-between">
                  <span>{row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '—'}</span>
                  <span>余额 {row.balanceAfter.toLocaleString()}</span>
                </div>
              </div>
            ))
          )}
        </div>
        {(data?.totalPages ?? 0) > 1 && (
          <div className="px-3 py-2 border-t border-white/10 flex justify-center gap-2">
            <button type="button" disabled={page <= 0} onClick={() => setPage(page - 1)} className="text-[11px] text-white/50 disabled:opacity-30">上一页</button>
            <span className="text-[11px] text-white/35">{page + 1}/{data?.totalPages}</span>
            <button type="button" disabled={(page + 1) >= (data?.totalPages ?? 0)} onClick={() => setPage(page + 1)} className="text-[11px] text-white/50 disabled:opacity-30">下一页</button>
          </div>
        )}
      </div>
    </div>
  )
}

export function BillingManageContent() {
  const [subTab, setSubTab] = useState<'usage' | 'packages' | 'orders'>('usage')
  const [rangeDays, setRangeDays] = useState<7 | 30 | 90>(30)
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null)
  const queryClient = useQueryClient()

  const fromIso = useMemo(() => {
    const d = new Date()
    d.setDate(d.getDate() - rangeDays)
    return d.toISOString()
  }, [rangeDays])

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['admin-billing-usage', rangeDays],
    queryFn: () => adminApi.getBillingUsage({ from: fromIso }),
    enabled: subTab === 'usage',
  })

  const { data: details = [], isLoading: detailsLoading } = useQuery({
    queryKey: ['admin-billing-detail', selectedUserId, rangeDays],
    queryFn: () =>
      selectedUserId == null
        ? Promise.resolve([] as BillingUsageDetail[])
        : adminApi.getBillingUsageDetail(selectedUserId, { from: fromIso, limit: 100 }),
    enabled: subTab === 'usage' && selectedUserId != null,
  })

  const { data: packages = [], isLoading: packagesLoading } = useQuery({
    queryKey: ['admin-point-packages'],
    queryFn: () => adminApi.listPointPackages(),
    enabled: subTab === 'packages',
  })

  const [orderPage, setOrderPage] = useState(0)
  const [orderStatus, setOrderStatus] = useState('')
  const { data: orderPageData, isLoading: ordersLoading } = useQuery({
    queryKey: ['admin-payment-orders', orderPage, orderStatus],
    queryFn: () => adminApi.listPaymentOrders({ page: orderPage, size: 20, status: orderStatus || undefined }),
    enabled: subTab === 'orders',
  })

  const [packForm, setPackForm] = useState({ name: '', priceYuan: '6', points: '600', bonusPoints: '0', description: '' })
  const createPackMutation = useMutation({
    mutationFn: () =>
      adminApi.createPointPackage({
        name: packForm.name.trim(),
        priceYuan: Number(packForm.priceYuan),
        points: Number(packForm.points),
        bonusPoints: Number(packForm.bonusPoints) || 0,
        description: packForm.description || undefined,
        enabled: true,
        sortOrder: 100,
      }),
    onSuccess: () => {
      toast.success('套餐已创建')
      setPackForm({ name: '', priceYuan: '6', points: '600', bonusPoints: '0', description: '' })
      queryClient.invalidateQueries({ queryKey: ['admin-point-packages'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const togglePackMutation = useMutation({
    mutationFn: (p: { id: number; name: string; priceYuan: number; points: number; bonusPoints: number; enabled: boolean; sortOrder?: number; description?: string | null }) =>
      adminApi.updatePointPackage(p.id, {
        name: p.name,
        priceYuan: Number(p.priceYuan),
        points: p.points,
        bonusPoints: p.bonusPoints,
        enabled: !p.enabled,
        sortOrder: p.sortOrder ?? 100,
        description: p.description ?? undefined,
      }),
    onSuccess: () => {
      toast.success('已更新套餐')
      queryClient.invalidateQueries({ queryKey: ['admin-point-packages'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="h-full flex flex-col">
      <div className="mb-3 flex items-center gap-2">
        {([
          ['usage', '成本核算'],
          ['packages', '充值套餐'],
          ['orders', '支付订单'],
        ] as const).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setSubTab(id)}
            className={clsx(
              'px-2.5 py-1 rounded text-[11px] border',
              subTab === id ? 'border-white/30 bg-white/10 text-white' : 'border-white/10 text-white/50',
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {subTab === 'usage' && (
        <>
          <div className="mb-4 flex items-start justify-between gap-3">
            <div>
              <h3 className="text-sm font-medium text-white">成本核算</h3>
              <p className="text-xs text-white/40 mt-0.5">Token 合计 + 估算系统成本 CNY（含桌面代理）</p>
            </div>
            <div className="flex items-center gap-2">
              {([7, 30, 90] as const).map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => setRangeDays(d)}
                  className={clsx(
                    'px-2 py-1 rounded text-[11px] border',
                    rangeDays === d ? 'border-white/30 bg-white/10 text-white' : 'border-white/10 text-white/50',
                  )}
                >
                  {d} 天
                </button>
              ))}
              <button type="button" onClick={() => refetch()} className="p-1.5 rounded border border-white/15 text-white/60" title="刷新">
                <RefreshCw className={clsx('w-3.5 h-3.5', isFetching && 'animate-spin')} />
              </button>
            </div>
          </div>

          <div className="mb-4 grid grid-cols-2 gap-3">
            <div className="rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2">
              <div className="text-[11px] text-white/40">总 Token</div>
              <div className="text-lg font-mono text-white/90">{data?.grandTotalTokens?.toLocaleString() ?? '—'}</div>
            </div>
            <div className="rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2">
              <div className="text-[11px] text-white/40">估算成本 (CNY)</div>
              <div className="text-lg font-mono text-white/90">
                {data ? `¥${data.grandTotalCostUsd.toFixed(4)}` : '—'}
              </div>
            </div>
          </div>

          {isLoading ? (
            <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
          ) : !data?.summaries?.length ? (
            <div className="text-center py-10 text-white/30 text-sm">所选时间范围内暂无用量</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-white/10 mb-4">
              <table className="w-full text-xs text-white/80">
                <thead>
                  <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">用户</th>
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">调用次数</th>
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">Prompt</th>
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">Completion</th>
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">Total</th>
                    <th className="pb-2 pt-2 px-3 font-medium text-white/40">成本 CNY</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/10">
                  {data.summaries.map((row) => (
                    <tr
                      key={row.userId}
                      className={clsx('hover:bg-white/[0.03] cursor-pointer', selectedUserId === row.userId && 'bg-white/[0.06]')}
                      onClick={() => setSelectedUserId(row.userId)}
                    >
                      <td className="py-2 px-3 font-mono">{row.username}</td>
                      <td className="py-2 px-3">{row.callCount}</td>
                      <td className="py-2 px-3">{row.promptTokens.toLocaleString()}</td>
                      <td className="py-2 px-3">{row.completionTokens.toLocaleString()}</td>
                      <td className="py-2 px-3">{row.totalTokens.toLocaleString()}</td>
                      <td className="py-2 px-3">¥{row.estimatedCostUsd.toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {selectedUserId != null && (
            <div className="flex-1 min-h-0 overflow-auto rounded-lg border border-white/10">
              <div className="px-3 py-2 border-b border-white/10 text-xs text-white/50 flex justify-between">
                <span>用户 #{selectedUserId} 明细</span>
                <button type="button" className="text-white/40 hover:text-white" onClick={() => setSelectedUserId(null)}>关闭</button>
              </div>
              {detailsLoading ? (
                <div className="p-4 text-xs text-white/40">加载明细…</div>
              ) : (
                <table className="w-full text-[11px] text-white/70">
                  <thead>
                    <tr className="text-left text-white/35 border-b border-white/10">
                      <th className="px-3 py-1.5">时间</th>
                      <th className="px-3 py-1.5">Model</th>
                      <th className="px-3 py-1.5">Tokens</th>
                      <th className="px-3 py-1.5">成本</th>
                      <th className="px-3 py-1.5">积分</th>
                      <th className="px-3 py-1.5">状态</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {details.map((d) => (
                      <tr key={d.id}>
                        <td className="px-3 py-1.5 whitespace-nowrap">{d.createTime ?? '—'}</td>
                        <td className="px-3 py-1.5">{d.model}</td>
                        <td className="px-3 py-1.5">{d.totalTokens ?? 0}</td>
                        <td className="px-3 py-1.5">{d.estimatedCostCny != null ? `¥${d.estimatedCostCny.toFixed(4)}` : '—'}</td>
                        <td className="px-3 py-1.5">{d.pointsCharged ?? '—'}</td>
                        <td className="px-3 py-1.5">{d.success ? 'OK' : 'ERR'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </>
      )}

      {subTab === 'packages' && (
        <div className="space-y-4">
          <div className="rounded-lg border border-white/10 p-3 space-y-2">
            <div className="text-xs text-white/50">新建套餐</div>
            <div className="grid grid-cols-2 gap-2">
              <input className="bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white" placeholder="名称" value={packForm.name} onChange={(e) => setPackForm({ ...packForm, name: e.target.value })} />
              <input className="bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white" placeholder="价格元" value={packForm.priceYuan} onChange={(e) => setPackForm({ ...packForm, priceYuan: e.target.value })} />
              <input className="bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white" placeholder="积分" value={packForm.points} onChange={(e) => setPackForm({ ...packForm, points: e.target.value })} />
              <input className="bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white" placeholder="赠送" value={packForm.bonusPoints} onChange={(e) => setPackForm({ ...packForm, bonusPoints: e.target.value })} />
            </div>
            <button type="button" disabled={createPackMutation.isPending || !packForm.name.trim()} onClick={() => createPackMutation.mutate()} className="px-3 py-1.5 rounded bg-blue-600 text-xs text-white disabled:opacity-40">创建套餐</button>
          </div>
          {packagesLoading ? (
            <div className="text-center py-8 text-xs text-white/40">加载中…</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-white/10">
              <table className="w-full text-xs text-white/80">
                <thead>
                  <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                    <th className="px-3 py-2 text-white/40">名称</th>
                    <th className="px-3 py-2 text-white/40">价格</th>
                    <th className="px-3 py-2 text-white/40">积分</th>
                    <th className="px-3 py-2 text-white/40">赠送</th>
                    <th className="px-3 py-2 text-white/40">启用</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/10">
                  {packages.map((p) => (
                    <tr key={p.id}>
                      <td className="px-3 py-2">{p.name}</td>
                      <td className="px-3 py-2">¥{Number(p.priceYuan).toFixed(2)}</td>
                      <td className="px-3 py-2">{p.points}</td>
                      <td className="px-3 py-2">{p.bonusPoints}</td>
                      <td className="px-3 py-2">
                        <button type="button" onClick={() => togglePackMutation.mutate(p as typeof p & { enabled: boolean })} className="text-[11px] text-blue-300">
                          {p.enabled === false ? '已下架' : '已上架'}（点此切换）
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {subTab === 'orders' && (
        <div className="space-y-3">
          <div className="flex gap-2">
            {['', 'PENDING', 'SUCCESS', 'CLOSED'].map((s) => (
              <button
                key={s || 'all'}
                type="button"
                onClick={() => { setOrderStatus(s); setOrderPage(0) }}
                className={clsx('px-2 py-1 rounded text-[11px] border', orderStatus === s ? 'border-white/30 bg-white/10 text-white' : 'border-white/10 text-white/50')}
              >
                {s || '全部'}
              </button>
            ))}
          </div>
          {ordersLoading ? (
            <div className="text-center py-8 text-xs text-white/40">加载中…</div>
          ) : !(orderPageData?.items?.length) ? (
            <div className="text-center py-8 text-xs text-white/30">暂无订单</div>
          ) : (
            <>
              <div className="overflow-x-auto rounded-lg border border-white/10">
                <table className="w-full text-xs text-white/80">
                  <thead>
                    <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                      <th className="px-3 py-2 text-white/40">订单号</th>
                      <th className="px-3 py-2 text-white/40">用户</th>
                      <th className="px-3 py-2 text-white/40">金额</th>
                      <th className="px-3 py-2 text-white/40">状态</th>
                      <th className="px-3 py-2 text-white/40">时间</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/10">
                    {orderPageData.items.map((o) => (
                      <tr key={o.id}>
                        <td className="px-3 py-2 font-mono">{o.orderNo}</td>
                        <td className="px-3 py-2">{o.userId ?? '—'}</td>
                        <td className="px-3 py-2">¥{Number(o.amountYuan).toFixed(2)}</td>
                        <td className="px-3 py-2">{o.status}</td>
                        <td className="px-3 py-2 text-white/50">{o.createTime ? new Date(o.createTime).toLocaleString('zh-CN') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {(orderPageData.totalPages ?? 0) > 1 && (
                <div className="flex justify-center gap-2">
                  <button type="button" disabled={orderPage <= 0} onClick={() => setOrderPage(orderPage - 1)} className="text-[11px] text-white/50 disabled:opacity-30">上一页</button>
                  <span className="text-[11px] text-white/35">{orderPage + 1}/{orderPageData.totalPages}</span>
                  <button type="button" disabled={orderPage + 1 >= orderPageData.totalPages} onClick={() => setOrderPage(orderPage + 1)} className="text-[11px] text-white/50 disabled:opacity-30">下一页</button>
                </div>
              )}
            </>
          )}
        </div>
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

const DEEPSEEK_KEY = /^DEEPSEEK_API_KEY(_[A-Za-z0-9]+)?$/

export function SystemConfigContent() {
  const queryClient = useQueryClient()
  const [keyLabel, setKeyLabel] = useState('')
  const [keyValue, setKeyValue] = useState('')
  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['admin-system-configs'],
    queryFn: () => adminApi.listSystemConfigs(),
  })
  const deepseekKeys = configs.filter((c) => DEEPSEEK_KEY.test(c.configKey))
  const otherConfigs = configs.filter((c) => !DEEPSEEK_KEY.test(c.configKey) && c.configKey !== 'RAG_MEMORY_ENABLED')

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { configValue?: string | null; description?: string | null } }) =>
      adminApi.updateSystemConfig(id, body),
    onSuccess: () => {
      toast.success('配置已保存')
      queryClient.invalidateQueries({ queryKey: ['admin-system-configs'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const createMutation = useMutation({
    mutationFn: () => adminApi.createDeepseekKey({ configValue: keyValue.trim(), description: keyLabel.trim() || undefined }),
    onSuccess: () => {
      toast.success('已添加 DeepSeek API Key')
      setKeyLabel('')
      setKeyValue('')
      queryClient.invalidateQueries({ queryKey: ['admin-system-configs'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminApi.deleteSystemConfig(id),
    onSuccess: () => {
      toast.success('已删除')
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
          可添加多条 DeepSeek API Key。模型管理里为每个模型选择其中一条后，对话就会用对应的模型。
        </p>
      </div>

      <div className="mb-4 rounded-lg border border-white/10 p-3 space-y-3">
        <div className="text-xs text-white/70">DeepSeek API Key</div>
        <div className="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            value={keyLabel}
            onChange={(e) => setKeyLabel(e.target.value)}
            placeholder="名称，如主 Key"
            className="sm:w-40 bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white placeholder:text-white/25 focus:outline-none focus:ring-1 focus:ring-blue-500/70"
          />
          <input
            type="password"
            value={keyValue}
            onChange={(e) => setKeyValue(e.target.value)}
            placeholder="sk-..."
            autoComplete="off"
            className="flex-1 bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white placeholder:text-white/25 focus:outline-none focus:ring-1 focus:ring-blue-500/70"
          />
          <button
            type="button"
            disabled={!keyValue.trim() || createMutation.isPending}
            onClick={() => createMutation.mutate()}
            className="px-3 py-1.5 rounded bg-blue-600 text-xs text-white disabled:opacity-40"
          >
            添加
          </button>
        </div>
        {deepseekKeys.length === 0 ? (
          <div className="text-[11px] text-white/35">还没有 DeepSeek Key。</div>
        ) : (
          <div className="space-y-2">
            {deepseekKeys.map((c) => (
              <div key={c.id} className="grid grid-cols-1 sm:grid-cols-[8rem_1fr_auto] gap-2 items-center">
                <input
                  type="text"
                  value={c.description ?? ''}
                  onChange={handleChange(c, 'description')}
                  onBlur={() => handleBlur(c)}
                  placeholder="名称"
                  className="bg-black/10 border border-white/10 rounded px-2 py-1.5 text-xs text-white"
                />
                <input
                  type="password"
                  value={c.configValue ?? ''}
                  onChange={handleChange(c, 'configValue')}
                  onBlur={() => handleBlur(c)}
                  autoComplete="off"
                  className="bg-black/20 border border-white/15 rounded px-2 py-1.5 text-xs text-white"
                />
                <button
                  type="button"
                  onClick={() => deleteMutation.mutate(c.id)}
                  className="text-[11px] text-white/45 hover:text-red-300"
                >
                  删除
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {isLoading ? (
        <div className="text-center py-10 text-white/40 text-sm">加载中...</div>
      ) : otherConfigs.length === 0 ? null : (
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
              {otherConfigs.map((c) => (
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
