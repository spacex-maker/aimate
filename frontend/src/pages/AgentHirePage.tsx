import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Briefcase, Coins } from 'lucide-react'
import { multiAgentApi, type AgentCatalogItem, type RentPeriod } from '../api/multiAgent'
import clsx from 'clsx'

const PERIODS: { id: RentPeriod; label: string }[] = [
  { id: 'DAY', label: '包日' },
  { id: 'MONTH', label: '包月' },
  { id: 'YEAR', label: '包年' },
]

const TIER_LABEL: Record<string, string> = {
  JUNIOR: '初级',
  MID: '中级',
  SENIOR: '高级',
  EXPERT: '专家',
}

export function AgentHirePage() {
  const qc = useQueryClient()
  const { data: catalog = [], isLoading } = useQuery({
    queryKey: ['agent-catalog'],
    queryFn: () => multiAgentApi.listCatalog(),
  })
  const [period, setPeriod] = useState<RentPeriod>('MONTH')
  const [selected, setSelected] = useState<AgentCatalogItem | null>(null)

  const quoteQ = useQuery({
    queryKey: ['agent-quote', selected?.id, period],
    queryFn: () => multiAgentApi.quote(selected!.id, period),
    enabled: !!selected,
  })

  const hireMut = useMutation({
    mutationFn: () => multiAgentApi.hire(selected!.id, period),
    onSuccess: (r) => {
      toast.success(`招聘成功，扣 ${r.totalPoints} 积分`)
      qc.invalidateQueries({ queryKey: ['my-agents'] })
      setSelected(null)
    },
    onError: (e: Error) => toast.error(e.message || '招聘失败'),
  })

  const grouped = useMemo(() => {
    const m = new Map<string, AgentCatalogItem[]>()
    for (const c of catalog) {
      const key = c.careerName || String(c.careerId)
      if (!m.has(key)) m.set(key, [])
      m.get(key)!.push(c)
    }
    return [...m.entries()]
  }, [catalog])

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
            <Briefcase className="h-6 w-6 text-cyan-300" />
            Agent 招聘
          </h1>
          <p className="mt-1 text-sm text-white/45">官方职业 × 等级；费用含 Agent 租用与 Soul 席位</p>
        </div>
        <div className="flex rounded-lg border border-white/10 p-0.5">
          {PERIODS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPeriod(p.id)}
              className={clsx(
                'rounded-md px-3 py-1.5 text-xs transition-colors',
                period === p.id ? 'bg-white/10 text-white' : 'text-white/45 hover:text-white/70'
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-white/40">加载中…</div>
      ) : (
        <div className="space-y-8">
          {grouped.map(([career, items]) => (
            <section key={career}>
              <h2 className="mb-3 text-sm font-medium text-white/70">{career}</h2>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setSelected(item)}
                    className="rounded-xl border border-white/10 bg-white/[0.03] p-4 text-left hover:border-cyan-400/30 hover:bg-cyan-400/[0.05] transition-colors"
                  >
                    <div className="text-sm font-semibold text-white">{item.name}</div>
                    <div className="mt-1 text-[11px] text-white/40">{TIER_LABEL[item.tier] || item.tier}</div>
                    <div className="mt-3 flex items-center gap-1 text-xs text-amber-200/80">
                      <Coins className="h-3.5 w-3.5" />
                      {period === 'DAY' && item.priceDayPoints + item.soulPriceDayPoints}
                      {period === 'MONTH' && item.priceMonthPoints + item.soulPriceMonthPoints}
                      {period === 'YEAR' && item.priceYearPoints + item.soulPriceYearPoints}
                      <span className="text-white/35">积分起</span>
                    </div>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setSelected(null)}>
          <div
            className="w-full max-w-md rounded-2xl border border-white/12 bg-[#12141a] p-5 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold text-white">{selected.name}</h3>
            <p className="mt-1 text-xs text-white/40">{selected.description}</p>
            <div className="mt-4 space-y-2 rounded-xl border border-white/8 bg-white/[0.03] p-3 text-sm">
              {(quoteQ.data?.lines ?? []).map((l) => (
                <div key={l.label} className="flex justify-between text-white/70">
                  <span>{l.label}</span>
                  <span className="font-mono tabular-nums">{l.points} 积分</span>
                </div>
              ))}
              <div className="flex justify-between border-t border-white/10 pt-2 font-medium text-white">
                <span>合计</span>
                <span className="font-mono tabular-nums">{quoteQ.data?.totalPoints ?? '—'} 积分</span>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <button
                type="button"
                className="flex-1 rounded-lg border border-white/12 py-2 text-sm text-white/60 hover:bg-white/5"
                onClick={() => setSelected(null)}
              >
                取消
              </button>
              <button
                type="button"
                disabled={hireMut.isPending || !quoteQ.data}
                className="flex-1 rounded-lg bg-cyan-500/90 py-2 text-sm font-medium text-black hover:bg-cyan-400 disabled:opacity-40"
                onClick={() => hireMut.mutate()}
              >
                {hireMut.isPending ? '处理中…' : '确认招聘'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
