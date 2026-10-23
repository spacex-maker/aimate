import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { ArrowLeft, Briefcase, Building2, ChevronRight, Coins, UserRound, X } from 'lucide-react'
import {
  multiAgentApi,
  type AgentCareer,
  type AgentCatalogItem,
  type AgentIndustry,
  type RentPeriod,
} from '../api/multiAgent'
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

type Step = 'industry' | 'career' | 'tier'

const OTHER_INDUSTRY: AgentIndustry = {
  id: -1,
  code: 'OTHER',
  name: '其他',
  description: '尚未归入具体行业的职业',
}

export function AgentHirePage() {
  const qc = useQueryClient()
  const { data: industries = [], isLoading: industriesLoading } = useQuery({
    queryKey: ['agent-industries'],
    queryFn: () => multiAgentApi.listIndustries(),
  })
  const { data: careers = [], isLoading: careersLoading } = useQuery({
    queryKey: ['agent-careers'],
    queryFn: () => multiAgentApi.listCareers(),
  })
  const { data: catalog = [], isLoading: catalogLoading } = useQuery({
    queryKey: ['agent-catalog'],
    queryFn: () => multiAgentApi.listCatalog(),
  })

  const [period, setPeriod] = useState<RentPeriod>('MONTH')
  const [step, setStep] = useState<Step>('industry')
  const [industryId, setIndustryId] = useState<number | null>(null)
  const [careerId, setCareerId] = useState<number | null>(null)
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

  const isLoading = industriesLoading || careersLoading || catalogLoading

  const industryOptions = useMemo(() => {
    const knownIds = new Set(industries.map((i) => i.id))
    const hasOrphan = careers.some(
      (c) => c.industryId == null || !knownIds.has(c.industryId)
    )
    return hasOrphan ? [...industries, OTHER_INDUSTRY] : industries
  }, [industries, careers])

  const selectedIndustry = useMemo(
    () => industryOptions.find((i) => i.id === industryId) ?? null,
    [industryOptions, industryId]
  )

  const careersInIndustry = useMemo(() => {
    if (industryId == null) return [] as AgentCareer[]
    if (industryId === OTHER_INDUSTRY.id) {
      const knownIds = new Set(industries.map((i) => i.id))
      return careers.filter((c) => c.industryId == null || !knownIds.has(c.industryId))
    }
    return careers.filter((c) => c.industryId === industryId)
  }, [careers, industries, industryId])

  const selectedCareer = useMemo(
    () => careers.find((c) => c.id === careerId) ?? null,
    [careers, careerId]
  )

  const tiersForCareer = useMemo(() => {
    if (careerId == null) return [] as AgentCatalogItem[]
    return catalog.filter((c) => c.careerId === careerId)
  }, [catalog, careerId])

  const pickIndustry = (id: number) => {
    setIndustryId(id)
    setCareerId(null)
    setStep('career')
  }

  const pickCareer = (id: number) => {
    setCareerId(id)
    setStep('tier')
  }

  const goIndustry = () => {
    setStep('industry')
    setIndustryId(null)
    setCareerId(null)
  }

  const goCareer = () => {
    setStep('career')
    setCareerId(null)
  }

  const periodPoints = (item: AgentCatalogItem) => {
    if (period === 'DAY') return item.priceDayPoints + item.soulPriceDayPoints
    if (period === 'YEAR') return item.priceYearPoints + item.soulPriceYearPoints
    return item.priceMonthPoints + item.soulPriceMonthPoints
  }

  const careerMinPoints = (career: AgentCareer) => {
    const items = catalog.filter((c) => c.careerId === career.id)
    if (!items.length) return null
    return Math.min(...items.map(periodPoints))
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
            <Briefcase className="h-6 w-6 text-cyan-300" />
            Agent 招聘
          </h1>
          <p className="mt-1 text-sm text-white/45">先选行业，再选职业与等级；费用含 Agent 租用与 Soul 席位</p>
        </div>
        <div className="flex rounded-lg bg-white/[0.04] p-0.5">
          {PERIODS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPeriod(p.id)}
              className={clsx(
                'rounded-md px-3 py-1.5 text-xs transition-colors',
                period === p.id ? 'bg-white/[0.1] text-white' : 'text-white/45 hover:text-white/70'
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <nav className="mb-5 flex flex-wrap items-center gap-1.5 text-xs text-white/40">
        <button
          type="button"
          onClick={goIndustry}
          className={clsx(
            'rounded-md px-2 py-1 transition-colors',
            step === 'industry' ? 'bg-white/[0.08] text-white' : 'hover:text-white/70'
          )}
        >
          行业
        </button>
        {selectedIndustry && (
          <>
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-white/25" />
            <button
              type="button"
              onClick={goCareer}
              className={clsx(
                'rounded-md px-2 py-1 transition-colors',
                step === 'career' ? 'bg-white/[0.08] text-white' : 'hover:text-white/70'
              )}
            >
              {selectedIndustry.name}
            </button>
          </>
        )}
        {selectedCareer && (
          <>
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-white/25" />
            <span className="rounded-md bg-white/[0.08] px-2 py-1 text-white">{selectedCareer.name}</span>
          </>
        )}
      </nav>

      {isLoading ? (
        <div className="text-sm text-white/40">加载中…</div>
      ) : step === 'industry' ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {industryOptions.map((ind) => {
            const count =
              ind.id === OTHER_INDUSTRY.id
                ? careers.filter((c) => {
                    const knownIds = new Set(industries.map((i) => i.id))
                    return c.industryId == null || !knownIds.has(c.industryId)
                  }).length
                : careers.filter((c) => c.industryId === ind.id).length
            return (
              <button
                key={ind.id}
                type="button"
                onClick={() => pickIndustry(ind.id)}
                className="group rounded-2xl bg-white/[0.04] p-5 text-left ring-1 ring-inset ring-white/[0.05] transition-colors hover:bg-cyan-400/[0.06] hover:ring-cyan-400/20"
              >
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-cyan-400/10 text-cyan-300 ring-1 ring-inset ring-cyan-400/20">
                    <Building2 className="h-5 w-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-sm font-semibold text-white">{ind.name}</div>
                      <ChevronRight className="h-4 w-4 shrink-0 text-white/25 group-hover:text-cyan-300/80" />
                    </div>
                    {ind.description ? (
                      <p className="mt-1 line-clamp-2 text-[11px] leading-relaxed text-white/40">{ind.description}</p>
                    ) : null}
                    <div className="mt-3 text-[11px] text-white/35">{count} 个职业</div>
                  </div>
                </div>
              </button>
            )
          })}
          {!industryOptions.length && (
            <div className="col-span-full text-sm text-white/40">暂无开放行业，请稍后再试。</div>
          )}
        </div>
      ) : step === 'career' ? (
        <div className="space-y-4">
          <button
            type="button"
            onClick={goIndustry}
            className="inline-flex items-center gap-1.5 text-xs text-white/45 hover:text-white/75"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            返回行业
          </button>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {careersInIndustry.map((career) => {
              const minPts = careerMinPoints(career)
              return (
                <button
                  key={career.id}
                  type="button"
                  onClick={() => pickCareer(career.id)}
                  className="group rounded-2xl bg-white/[0.04] p-5 text-left ring-1 ring-inset ring-white/[0.05] transition-colors hover:bg-cyan-400/[0.06] hover:ring-cyan-400/20"
                >
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white/[0.05] text-white/70 ring-1 ring-inset ring-white/[0.08]">
                      <UserRound className="h-5 w-5" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <div className="text-sm font-semibold text-white">{career.name}</div>
                        <ChevronRight className="h-4 w-4 shrink-0 text-white/25 group-hover:text-cyan-300/80" />
                      </div>
                      {career.executionMode === 'LOCAL_BRIDGE' && (
                        <span className="mt-1.5 inline-flex rounded-md border border-cyan-400/30 bg-cyan-500/10 px-1.5 py-0.5 text-[10px] text-cyan-200/90">
                          本机接入
                        </span>
                      )}
                      {career.description ? (
                        <p className="mt-1 line-clamp-2 text-[11px] leading-relaxed text-white/40">
                          {career.description}
                        </p>
                      ) : null}
                      <div className="mt-3 flex items-center gap-1 text-xs text-amber-200/80">
                        <Coins className="h-3.5 w-3.5" />
                        {minPts != null ? (
                          <>
                            {minPts.toLocaleString()}
                            <span className="text-white/35">积分起</span>
                          </>
                        ) : (
                          <span className="text-white/35">暂无等级</span>
                        )}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}
            {!careersInIndustry.length && (
              <div className="col-span-full text-sm text-white/40">该行业下暂无职业。</div>
            )}
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <button
            type="button"
            onClick={goCareer}
            className="inline-flex items-center gap-1.5 text-xs text-white/45 hover:text-white/75"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            返回职业
          </button>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {tiersForCareer.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelected(item)}
                className="rounded-xl bg-white/[0.04] p-4 text-left ring-1 ring-inset ring-white/[0.04] hover:bg-cyan-400/[0.06] hover:ring-cyan-400/20 transition-colors"
              >
                <div className="text-sm font-semibold text-white">{item.name}</div>
                <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px] text-white/40">
                  <span>{TIER_LABEL[item.tier] || item.tier}</span>
                  {item.executionMode === 'LOCAL_BRIDGE' && (
                    <span className="rounded-md border border-cyan-400/30 bg-cyan-500/10 px-1.5 py-0.5 text-[10px] text-cyan-200/90">
                      本机接入
                    </span>
                  )}
                </div>
                <div className="mt-3 flex items-center gap-1 text-xs text-amber-200/80">
                  <Coins className="h-3.5 w-3.5" />
                  {periodPoints(item).toLocaleString()}
                  <span className="text-white/35">积分</span>
                </div>
                {(item.knowledgeBases?.length ?? 0) > 0 && (
                  <div className="mt-2 text-[11px] text-cyan-200/70">
                    含 {item.knowledgeBases!.length} 个领域知识库
                  </div>
                )}
              </button>
            ))}
            {!tiersForCareer.length && (
              <div className="col-span-full text-sm text-white/40">该职业暂无上架等级。</div>
            )}
          </div>
        </div>
      )}

      {selected &&
        createPortal(
          <div
            className="fixed inset-0 z-[80] flex items-center justify-center p-4 sm:p-6"
            style={{ background: 'radial-gradient(ellipse at center, rgba(8,12,24,0.55), rgba(0,0,0,0.78))' }}
            onClick={() => setSelected(null)}
            role="dialog"
            aria-modal="true"
            aria-labelledby="hire-dialog-title"
          >
            <div
              className="relative w-full max-w-md max-h-[min(90vh,40rem)] flex flex-col overflow-hidden rounded-2xl shadow-[0_32px_80px_-20px_rgba(0,0,0,0.85)] ring-1 ring-inset ring-white/[0.08]"
              style={{
                background:
                  'linear-gradient(165deg, rgba(22,26,36,0.98) 0%, rgba(14,16,22,0.99) 50%, rgba(10,12,18,1) 100%)',
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div
                className="pointer-events-none absolute -top-20 left-1/2 h-40 w-72 -translate-x-1/2 rounded-full opacity-40 blur-3xl"
                style={{ background: 'radial-gradient(circle, rgba(34,211,238,0.28), transparent 70%)' }}
              />
              <div className="relative z-10 flex items-start justify-between gap-3 px-5 pt-5 pb-1">
                <div className="min-w-0 flex-1">
                  <div className="text-[11px] text-white/35">
                    {[selected.industryName, selected.careerName].filter(Boolean).join(' · ')}
                  </div>
                  <h3 id="hire-dialog-title" className="mt-1 text-lg font-semibold text-white leading-snug">
                    {selected.name}
                  </h3>
                  {selected.description ? (
                    <p className="mt-1 text-xs text-white/40 leading-relaxed">{selected.description}</p>
                  ) : null}
                  {(selected.knowledgeBases?.length ?? 0) > 0 && (
                    <div className="mt-2 rounded-lg border border-cyan-400/15 bg-cyan-500/[0.06] px-2.5 py-2">
                      <div className="text-[11px] font-medium text-cyan-100/90">
                        附赠领域知识库 · {selected.knowledgeBases!.length}
                      </div>
                      <ul className="mt-1 space-y-0.5 text-[11px] text-white/45">
                        {selected.knowledgeBases!.slice(0, 6).map((kb) => (
                          <li key={kb.id} className="truncate">
                            {kb.name}
                            <span className="ml-1 font-mono text-white/25">{kb.publicId}</span>
                          </li>
                        ))}
                        {selected.knowledgeBases!.length > 6 && (
                          <li className="text-white/30">…还有 {selected.knowledgeBases!.length - 6} 个</li>
                        )}
                      </ul>
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setSelected(null)}
                  className="shrink-0 rounded-xl border border-white/10 bg-white/[0.03] p-2 text-white/45 transition-colors hover:border-white/20 hover:bg-white/[0.06] hover:text-white"
                  aria-label="关闭"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              <div className="relative z-10 flex-1 overflow-y-auto px-5 pb-5">
                <div className="mt-3 space-y-2 rounded-xl bg-white/[0.04] p-3.5 text-sm">
                  {(quoteQ.data?.lines ?? []).map((l) => (
                    <div key={l.label} className="flex justify-between text-white/65">
                      <span>{l.label}</span>
                      <span className="font-mono tabular-nums">{l.points} 积分</span>
                    </div>
                  ))}
                  <div className="flex justify-between pt-2.5 mt-1 border-t border-white/[0.06] font-medium text-white">
                    <span>合计</span>
                    <span className="font-mono tabular-nums">{quoteQ.data?.totalPoints ?? '—'} 积分</span>
                  </div>
                </div>

                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    className="flex-1 rounded-xl bg-white/[0.05] py-2.5 text-sm text-white/55 hover:bg-white/[0.08] hover:text-white/80 transition-colors"
                    onClick={() => setSelected(null)}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    disabled={hireMut.isPending || !quoteQ.data}
                    className="flex-1 rounded-xl bg-cyan-500/90 py-2.5 text-sm font-medium text-black hover:bg-cyan-400 disabled:opacity-40 transition-colors"
                    onClick={() => hireMut.mutate()}
                  >
                    {hireMut.isPending ? '处理中…' : '确认招聘'}
                  </button>
                </div>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </div>
  )
}
