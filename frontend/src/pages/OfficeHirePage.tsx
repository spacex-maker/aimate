import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Building2, Check, Coins, Cpu, HardDrive, MemoryStick } from 'lucide-react'
import clsx from 'clsx'
import { officeApi, type OfficePlan, type RentPeriod } from '../api/multiAgent'

const PERIODS: { id: RentPeriod; label: string; hint: string }[] = [
  { id: 'DAY', label: '包日', hint: '按天计费，灵活试用' },
  { id: 'MONTH', label: '包月', hint: '按月续期，常用' },
  { id: 'YEAR', label: '包年', hint: '年付更省' },
]

const STATUS_LABEL: Record<string, string> = {
  NONE: '未租用',
  PROVISIONING: '开通中',
  ACTIVE: '运行中',
  STOPPING: '释放中',
  STOPPED: '已释放',
  EXPIRED: '已到期',
  FAILED: '开通失败',
}

function unitPrice(plan: OfficePlan, period: RentPeriod) {
  if (period === 'DAY') return plan.priceDayPoints
  if (period === 'YEAR') return plan.priceYearPoints
  return plan.priceMonthPoints
}

function periodUnitLabel(period: RentPeriod) {
  if (period === 'DAY') return '积分 / 天'
  if (period === 'YEAR') return '积分 / 年'
  return '积分 / 月'
}

export function OfficeHirePage() {
  const qc = useQueryClient()
  const { data: plans = [], isLoading } = useQuery({
    queryKey: ['office-plans'],
    queryFn: () => officeApi.listPlans(),
  })
  const { data: mine } = useQuery({
    queryKey: ['office-me'],
    queryFn: () => officeApi.me(),
    refetchInterval: (q) => (q.state.data?.status === 'PROVISIONING' ? 3000 : 15000),
  })

  const sorted = useMemo(
    () => [...plans].sort((a, b) => a.areaSqm - b.areaSqm),
    [plans],
  )

  /** 购买流：先配置 → 再时长 → 才报价（对齐云 CVM 购买页） */
  const [planId, setPlanId] = useState<number | null>(null)
  const [period, setPeriod] = useState<RentPeriod | null>(null)
  const [renewPeriod, setRenewPeriod] = useState<RentPeriod>('MONTH')

  const selected = useMemo(
    () => (planId == null ? null : sorted.find((p) => p.id === planId) ?? null),
    [planId, sorted],
  )

  // 换配置时清空时长，避免「未选时长却显示旧价」
  useEffect(() => {
    setPeriod(null)
  }, [planId])

  // 默认选中第一档配置（仅首次有数据时）
  useEffect(() => {
    if (planId == null && sorted.length > 0) setPlanId(sorted[0].id)
  }, [sorted, planId])

  const canQuote = !!selected && !!period
  const quoteQ = useQuery({
    queryKey: ['office-quote', selected?.id, period],
    queryFn: () => officeApi.quote(selected!.id, period!),
    enabled: canQuote,
  })

  const renewQuoteQ = useQuery({
    queryKey: ['office-renew-quote', mine?.planId, renewPeriod],
    queryFn: () => officeApi.quote(mine!.planId!, renewPeriod),
    enabled: !!mine?.planId && (mine.status === 'ACTIVE' || mine.status === 'PROVISIONING'),
  })

  const rentMut = useMutation({
    mutationFn: () => officeApi.rent(selected!.id, period!),
    onSuccess: () => {
      toast.success('已提交租用，正在开通办公室…')
      setPeriod(null)
      qc.invalidateQueries({ queryKey: ['office-me'] })
    },
    onError: (e: Error) => toast.error(e.message || '租用失败'),
  })

  const renewMut = useMutation({
    mutationFn: () => officeApi.renew(renewPeriod),
    onSuccess: () => {
      toast.success('续费成功')
      qc.invalidateQueries({ queryKey: ['office-me'] })
    },
    onError: (e: Error) => toast.error(e.message || '续费失败'),
  })

  const releaseMut = useMutation({
    mutationFn: () => officeApi.release(),
    onSuccess: () => {
      toast.success('办公室已释放')
      qc.invalidateQueries({ queryKey: ['office-me'] })
    },
    onError: (e: Error) => toast.error(e.message || '释放失败'),
  })

  const hasOffice = mine && mine.status !== 'NONE' && mine.status !== 'STOPPED'
  const canBuyNew = !hasOffice || mine?.status === 'FAILED'
  const canRenew = mine && (mine.status === 'ACTIVE' || mine.status === 'PROVISIONING')

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 pb-28 lg:pb-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
          <Building2 className="h-6 w-6 text-cyan-300" />
          办公室租用
        </h1>
        <p className="mt-1 text-sm text-white/45">
          一人一间私网办公环境（CVM）。先选配置，再选租用时长，右侧确认费用后开通——流程对齐云主机购买页。
        </p>
      </div>

      {mine && mine.status !== 'NONE' && (
        <div className="mb-6 rounded-2xl bg-white/[0.04] p-4 ring-1 ring-inset ring-white/[0.06]">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div className="text-sm font-medium text-white">
                当前办公室 · {mine.planName || '—'} · {STATUS_LABEL[mine.status] || mine.status}
                {mine.mock ? <span className="ml-2 text-[10px] text-amber-200/70">模拟开通</span> : null}
              </div>
              <div className="mt-1 text-xs text-white/40">
                {mine.areaSqm ? `${mine.areaSqm}㎡` : ''}
                {mine.expiresAt ? ` · 到期 ${new Date(mine.expiresAt).toLocaleString('zh-CN')}` : ''}
              </div>
              {mine.provisionError ? (
                <div className="mt-1 text-xs text-rose-300/80">{mine.provisionError}</div>
              ) : null}
            </div>
            {hasOffice && (
              <button
                type="button"
                disabled={releaseMut.isPending}
                onClick={() => {
                  if (window.confirm('释放后办公室重活工具将不可用，确认？')) releaseMut.mutate()
                }}
                className="rounded-xl border border-rose-400/25 px-3 py-2 text-xs text-rose-200/80 hover:bg-rose-400/10"
              >
                释放办公室
              </button>
            )}
          </div>

          {canRenew && (
            <div className="mt-4 border-t border-white/[0.06] pt-4">
              <div className="text-xs font-medium text-white/55 mb-2">续费时长</div>
              <div className="flex flex-wrap gap-2 mb-3">
                {PERIODS.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setRenewPeriod(p.id)}
                    className={clsx(
                      'rounded-lg px-3 py-2 text-xs ring-1 ring-inset transition-colors',
                      renewPeriod === p.id
                        ? 'bg-cyan-500/15 text-cyan-100 ring-cyan-400/35'
                        : 'bg-white/[0.03] text-white/50 ring-white/[0.08] hover:text-white/70',
                    )}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="text-sm text-white/70">
                  续费合计{' '}
                  <span className="font-mono text-amber-200/90">
                    {renewQuoteQ.data?.totalPoints?.toLocaleString() ?? (renewQuoteQ.isFetching ? '…' : '—')}
                  </span>{' '}
                  <span className="text-white/35 text-xs">积分</span>
                </div>
                <button
                  type="button"
                  disabled={renewMut.isPending || !renewQuoteQ.data}
                  onClick={() => renewMut.mutate()}
                  className="rounded-xl bg-white/[0.08] px-4 py-2 text-xs text-white/80 hover:bg-white/[0.12] disabled:opacity-40"
                >
                  {renewMut.isPending ? '续费中…' : '确认续费'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {canBuyNew ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
          <div className="space-y-8 min-w-0">
            {/* Step 1 — 配置 */}
            <section>
              <div className="mb-3 flex items-baseline gap-2">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-cyan-500/20 text-[11px] font-semibold text-cyan-200">
                  1
                </span>
                <h2 className="text-sm font-semibold text-white">选择办公室配置</h2>
                <span className="text-[11px] text-white/35">对应云主机实例规格</span>
              </div>

              {isLoading ? (
                <div className="text-sm text-white/40">加载配置中…</div>
              ) : (
                <div className="overflow-x-auto rounded-2xl ring-1 ring-inset ring-white/[0.08]">
                  <table className="w-full min-w-[640px] text-left text-sm">
                    <thead>
                      <tr className="border-b border-white/[0.06] bg-white/[0.03] text-[11px] uppercase tracking-wide text-white/40">
                        <th className="px-3 py-2.5 font-medium w-10" />
                        <th className="px-3 py-2.5 font-medium">规格</th>
                        <th className="px-3 py-2.5 font-medium">面积</th>
                        <th className="px-3 py-2.5 font-medium">算力</th>
                        <th className="px-3 py-2.5 font-medium">内存</th>
                        <th className="px-3 py-2.5 font-medium">磁盘</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sorted.map((plan) => {
                        const active = planId === plan.id
                        return (
                          <tr
                            key={plan.id}
                            onClick={() => setPlanId(plan.id)}
                            className={clsx(
                              'cursor-pointer border-b border-white/[0.04] transition-colors last:border-0',
                              active ? 'bg-cyan-500/[0.08]' : 'hover:bg-white/[0.03]',
                            )}
                          >
                            <td className="px-3 py-3">
                              <span
                                className={clsx(
                                  'flex h-4 w-4 items-center justify-center rounded-full ring-1',
                                  active
                                    ? 'bg-cyan-500 ring-cyan-400 text-black'
                                    : 'ring-white/25',
                                )}
                              >
                                {active ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}
                              </span>
                            </td>
                            <td className="px-3 py-3">
                              <div className="font-medium text-white">{plan.name}</div>
                              {plan.description ? (
                                <div className="mt-0.5 text-[11px] text-white/35 line-clamp-1">
                                  {plan.description}
                                </div>
                              ) : null}
                            </td>
                            <td className="px-3 py-3 text-white/75">{plan.areaSqm}㎡</td>
                            <td className="px-3 py-3 text-white/60">
                              <span className="inline-flex items-center gap-1">
                                <Cpu className="h-3 w-3 text-white/35" />
                                {plan.cpuDesc || '—'}
                              </span>
                            </td>
                            <td className="px-3 py-3 text-white/60">
                              <span className="inline-flex items-center gap-1">
                                <MemoryStick className="h-3 w-3 text-white/35" />
                                {plan.memDesc || '—'}
                              </span>
                            </td>
                            <td className="px-3 py-3 text-white/60">
                              <span className="inline-flex items-center gap-1">
                                <HardDrive className="h-3 w-3 text-white/35" />
                                {plan.diskGb != null ? `${plan.diskGb}G` : '—'}
                              </span>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            {/* Step 2 — 时长（配置选定后） */}
            <section className={clsx(!selected && 'opacity-45 pointer-events-none')}>
              <div className="mb-3 flex items-baseline gap-2">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-cyan-500/20 text-[11px] font-semibold text-cyan-200">
                  2
                </span>
                <h2 className="text-sm font-semibold text-white">选择租用时长</h2>
                <span className="text-[11px] text-white/35">对应计费周期</span>
              </div>
              {!selected ? (
                <p className="text-xs text-white/40">请先选择上方办公室配置</p>
              ) : (
                <div className="grid gap-3 sm:grid-cols-3">
                  {PERIODS.map((p) => {
                    const active = period === p.id
                    const unit = unitPrice(selected, p.id)
                    return (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => setPeriod(p.id)}
                        className={clsx(
                          'rounded-2xl p-4 text-left ring-1 ring-inset transition-colors',
                          active
                            ? 'bg-cyan-500/[0.12] ring-cyan-400/40'
                            : 'bg-white/[0.03] ring-white/[0.08] hover:bg-white/[0.05]',
                        )}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-semibold text-white">{p.label}</span>
                          {active ? (
                            <span className="flex h-4 w-4 items-center justify-center rounded-full bg-cyan-500 text-black">
                              <Check className="h-2.5 w-2.5" strokeWidth={3} />
                            </span>
                          ) : null}
                        </div>
                        <p className="mt-1 text-[11px] text-white/40">{p.hint}</p>
                        <p className="mt-3 text-xs text-white/50">
                          单价{' '}
                          <span className="font-mono text-amber-200/85">{unit.toLocaleString()}</span>{' '}
                          <span className="text-white/30">{periodUnitLabel(p.id)}</span>
                        </p>
                      </button>
                    )
                  })}
                </div>
              )}
            </section>
          </div>

          {/* Step 3 — 右侧费用确认（CVM 购买页配置清单） */}
          <aside className="lg:sticky lg:top-20">
            <div className="rounded-2xl bg-white/[0.04] p-5 ring-1 ring-inset ring-white/[0.08]">
              <h2 className="text-sm font-semibold text-white mb-1">配置清单</h2>
              <p className="text-[11px] text-white/35 mb-4">确认规格与时长后显示应付积分</p>

              <dl className="space-y-2.5 text-xs">
                <div className="flex justify-between gap-3">
                  <dt className="text-white/40">办公室</dt>
                  <dd className="text-right text-white/80">
                    {selected ? `${selected.name} · ${selected.areaSqm}㎡` : '未选择'}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-white/40">算力 / 内存</dt>
                  <dd className="text-right text-white/70">
                    {selected ? `${selected.cpuDesc || '—'} / ${selected.memDesc || '—'}` : '—'}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-white/40">磁盘</dt>
                  <dd className="text-right text-white/70">
                    {selected?.diskGb != null ? `${selected.diskGb}G` : '—'}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-white/40">租用时长</dt>
                  <dd className="text-right text-white/80">
                    {period ? PERIODS.find((p) => p.id === period)?.label : '请先选择时长'}
                  </dd>
                </div>
              </dl>

              <div className="mt-4 border-t border-white/[0.06] pt-4">
                {!canQuote ? (
                  <p className="text-xs text-white/40">
                    {!selected ? '① 选择配置' : '② 选择包日 / 包月 / 包年'}后显示价格
                  </p>
                ) : quoteQ.isFetching && !quoteQ.data ? (
                  <p className="text-xs text-white/40">正在核算费用…</p>
                ) : (
                  <div className="space-y-2 text-xs">
                    {(quoteQ.data?.lines ?? []).map((l) => (
                      <div key={l.label} className="flex justify-between text-white/60">
                        <span>{l.label}</span>
                        <span className="font-mono">{l.points.toLocaleString()} 积分</span>
                      </div>
                    ))}
                    <div className="flex items-end justify-between gap-2 border-t border-white/[0.06] pt-3">
                      <span className="text-white/45">应付总额</span>
                      <div className="text-right">
                        <div className="flex items-center justify-end gap-1 text-amber-200">
                          <Coins className="h-4 w-4" />
                          <span className="font-mono text-xl font-semibold tabular-nums">
                            {(quoteQ.data?.totalPoints ?? 0).toLocaleString()}
                          </span>
                        </div>
                        <div className="text-[10px] text-white/30">积分</div>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              <button
                type="button"
                disabled={!canQuote || !quoteQ.data || rentMut.isPending}
                onClick={() => rentMut.mutate()}
                className="mt-5 w-full rounded-xl bg-cyan-500 py-3 text-sm font-semibold text-black hover:bg-cyan-400 disabled:opacity-40 disabled:hover:bg-cyan-500"
              >
                {rentMut.isPending ? '开通中…' : '确认租用并开通'}
              </button>
              <p className="mt-2 text-[10px] leading-relaxed text-white/30">
                开通后 Agent 可在办公室环境执行装包、命令等重活；简单 API 工具无需办公室。
              </p>
            </div>
          </aside>
        </div>
      ) : (
        <div className="rounded-2xl bg-white/[0.03] px-4 py-8 text-center text-sm text-white/40 ring-1 ring-inset ring-white/[0.06]">
          当前已有办公室在租。如需换配置，请先释放后再按上方流程重新选配开通。
        </div>
      )}
    </div>
  )
}
