import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Building2, Coins, MapPin } from 'lucide-react'
import clsx from 'clsx'
import { officeApi, type OfficePlan, type RentPeriod } from '../api/multiAgent'

const PERIODS: { id: RentPeriod; label: string }[] = [
  { id: 'DAY', label: '包日' },
  { id: 'MONTH', label: '包月' },
  { id: 'YEAR', label: '包年' },
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
  const [period, setPeriod] = useState<RentPeriod>('MONTH')
  const [selected, setSelected] = useState<OfficePlan | null>(null)

  const quoteQ = useQuery({
    queryKey: ['office-quote', selected?.id, period],
    queryFn: () => officeApi.quote(selected!.id, period),
    enabled: !!selected,
  })

  const rentMut = useMutation({
    mutationFn: () => officeApi.rent(selected!.id, period),
    onSuccess: () => {
      toast.success('已提交租用，正在开通办公室…')
      setSelected(null)
      qc.invalidateQueries({ queryKey: ['office-me'] })
    },
    onError: (e: Error) => toast.error(e.message || '租用失败'),
  })

  const renewMut = useMutation({
    mutationFn: () => officeApi.renew(period),
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

  const periodPoints = (p: OfficePlan) => {
    if (period === 'DAY') return p.priceDayPoints
    if (period === 'YEAR') return p.priceYearPoints
    return p.priceMonthPoints
  }

  const sorted = useMemo(
    () => [...plans].sort((a, b) => a.areaSqm - b.areaSqm),
    [plans],
  )

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
            <Building2 className="h-6 w-6 text-cyan-300" />
            办公室租用
          </h1>
          <p className="mt-1 text-sm text-white/45">
            一人一间私网 CVM。未租用仅能用平台基础工具；租用后 Agent 可在办公室执行命令、部署服务。
          </p>
        </div>
        <div className="flex rounded-lg bg-white/[0.04] p-0.5">
          {PERIODS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPeriod(p.id)}
              className={clsx(
                'rounded-md px-3 py-1.5 text-xs transition-colors',
                period === p.id ? 'bg-white/[0.1] text-white' : 'text-white/45 hover:text-white/70',
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {mine && mine.status !== 'NONE' && (
        <div className="mb-6 rounded-2xl bg-white/[0.04] p-4 ring-1 ring-inset ring-white/[0.06]">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-sm font-medium text-white">
                {mine.planName || '办公室'} · {STATUS_LABEL[mine.status] || mine.status}
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
            <div className="flex gap-2">
              {(mine.status === 'ACTIVE' || mine.status === 'PROVISIONING') && (
                <button
                  type="button"
                  disabled={renewMut.isPending}
                  onClick={() => renewMut.mutate()}
                  className="rounded-xl bg-white/[0.06] px-3 py-2 text-xs text-white/70 hover:bg-white/[0.1]"
                >
                  按当前周期续费
                </button>
              )}
              {hasOffice && (
                <button
                  type="button"
                  disabled={releaseMut.isPending}
                  onClick={() => {
                    if (window.confirm('释放后宽边界工具将不可用，确认？')) releaseMut.mutate()
                  }}
                  className="rounded-xl border border-rose-400/25 px-3 py-2 text-xs text-rose-200/80 hover:bg-rose-400/10"
                >
                  释放办公室
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-sm text-white/40">加载中…</div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {sorted.map((plan) => (
            <button
              key={plan.id}
              type="button"
              disabled={!!hasOffice && mine?.status !== 'FAILED'}
              onClick={() => setSelected(plan)}
              className="rounded-2xl bg-white/[0.04] p-5 text-left ring-1 ring-inset ring-white/[0.05] transition-colors hover:bg-cyan-400/[0.06] hover:ring-cyan-400/20 disabled:opacity-40"
            >
              <div className="flex items-center gap-2 text-cyan-300/90">
                <MapPin className="h-4 w-4" />
                <span className="text-lg font-semibold text-white">{plan.areaSqm}㎡</span>
              </div>
              <div className="mt-2 text-sm font-medium text-white">{plan.name}</div>
              <p className="mt-1 text-[11px] leading-relaxed text-white/40">{plan.description}</p>
              <div className="mt-3 text-[11px] text-white/35">
                {plan.cpuDesc} · {plan.memDesc} · 盘 {plan.diskGb}G
              </div>
              <div className="mt-3 flex items-center gap-1 text-xs text-amber-200/80">
                <Coins className="h-3.5 w-3.5" />
                {periodPoints(plan).toLocaleString()}
                <span className="text-white/35">积分</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {selected && (
        <div
          className="fixed inset-0 z-[80] flex items-center justify-center p-4"
          style={{ background: 'rgba(0,0,0,0.72)' }}
          onClick={() => setSelected(null)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-[#12141c] p-5 ring-1 ring-inset ring-white/10"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold text-white">
              {selected.name} · {selected.areaSqm}㎡
            </h3>
            <p className="mt-1 text-xs text-white/40">{selected.description}</p>
            <div className="mt-4 space-y-2 rounded-xl bg-white/[0.04] p-3.5 text-sm">
              {(quoteQ.data?.lines ?? []).map((l) => (
                <div key={l.label} className="flex justify-between text-white/65">
                  <span>{l.label}</span>
                  <span className="font-mono">{l.points} 积分</span>
                </div>
              ))}
              <div className="flex justify-between border-t border-white/[0.06] pt-2 font-medium text-white">
                <span>合计</span>
                <span className="font-mono">{quoteQ.data?.totalPoints ?? '—'} 积分</span>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <button
                type="button"
                className="flex-1 rounded-xl bg-white/[0.05] py-2.5 text-sm text-white/55"
                onClick={() => setSelected(null)}
              >
                取消
              </button>
              <button
                type="button"
                disabled={rentMut.isPending || !quoteQ.data}
                className="flex-1 rounded-xl bg-cyan-500/90 py-2.5 text-sm font-medium text-black disabled:opacity-40"
                onClick={() => rentMut.mutate()}
              >
                {rentMut.isPending ? '处理中…' : '确认租用'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
