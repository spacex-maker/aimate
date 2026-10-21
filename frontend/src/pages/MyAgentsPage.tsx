import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Bot, Sparkles } from 'lucide-react'
import { multiAgentApi, type PromptVersion, type RentPeriod } from '../api/multiAgent'
import clsx from 'clsx'

export function MyAgentsPage() {
  const qc = useQueryClient()
  const { data: agents = [], isLoading } = useQuery({
    queryKey: ['my-agents'],
    queryFn: () => multiAgentApi.listMine(),
  })
  const [activeId, setActiveId] = useState<number | null>(null)
  const detailQ = useQuery({
    queryKey: ['my-agent', activeId],
    queryFn: () => multiAgentApi.detail(activeId!),
    enabled: activeId != null,
  })

  const excludeMut = useMutation({
    mutationFn: ({ id, atomIds }: { id: number; atomIds: string[] }) =>
      multiAgentApi.excludeAtoms(id, atomIds),
    onSuccess: () => {
      toast.success('已剔除并生成新版本')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const renewMut = useMutation({
    mutationFn: ({ id, period }: { id: number; period: RentPeriod }) => multiAgentApi.renew(id, period),
    onSuccess: (r) => {
      toast.success(`续费成功，扣 ${r.totalPoints} 积分`)
      qc.invalidateQueries({ queryKey: ['my-agents'] })
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const activeVersion: PromptVersion | undefined = detailQ.data?.promptVersions?.find((v) => v.active)

  let atoms: { id: string; text: string; excluded?: boolean }[] = []
  try {
    atoms = activeVersion?.atomsJson ? JSON.parse(activeVersion.atomsJson) : []
  } catch {
    atoms = []
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
        <Bot className="h-6 w-6 text-violet-300" />
        我的 Agent
      </h1>
      <p className="mt-1 text-sm text-white/45">查看租约、提示词版本，并可剔除原子更新做微调</p>

      {isLoading ? (
        <div className="mt-8 text-sm text-white/40">加载中…</div>
      ) : agents.length === 0 ? (
        <div className="mt-10 rounded-2xl border border-dashed border-white/10 p-10 text-center text-sm text-white/40">
          暂无 Agent。请先完善个人画像生成秘书，或前往招聘。
        </div>
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-[280px_1fr]">
          <div className="space-y-2">
            {agents.map((a) => (
              <button
                key={a.id}
                type="button"
                onClick={() => setActiveId(a.id)}
                className={clsx(
                  'w-full rounded-xl border px-3 py-3 text-left transition-colors',
                  activeId === a.id
                    ? 'border-violet-400/40 bg-violet-400/10'
                    : 'border-white/10 bg-white/[0.02] hover:border-white/20'
                )}
              >
                <div className="text-sm font-medium text-white">{a.name}</div>
                <div className="mt-1 flex flex-wrap gap-2 text-[10px] text-white/40">
                  <span>{a.kind === 'SECRETARY' ? '秘书' : '已招聘'}</span>
                  <span>{a.status}</span>
                  <span>v{a.currentPromptVersion}</span>
                </div>
              </button>
            ))}
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-5 min-h-[320px]">
            {!activeId ? (
              <div className="text-sm text-white/35">选择左侧 Agent 查看详情</div>
            ) : detailQ.isLoading ? (
              <div className="text-sm text-white/40">加载中…</div>
            ) : detailQ.data ? (
              <div className="space-y-5">
                <div>
                  <div className="text-lg font-semibold text-white">{detailQ.data.name}</div>
                  <div className="mt-1 text-xs text-white/40">
                    {detailQ.data.expiresAt ? `到期 ${detailQ.data.expiresAt}` : '长期有效'}
                    {' · '}状态 {detailQ.data.status}
                  </div>
                  {detailQ.data.kind === 'HIRED' && (
                    <div className="mt-3 flex gap-2">
                      {(['DAY', 'MONTH', 'YEAR'] as RentPeriod[]).map((p) => (
                        <button
                          key={p}
                          type="button"
                          disabled={renewMut.isPending}
                          onClick={() => renewMut.mutate({ id: detailQ.data!.id, period: p })}
                          className="rounded-lg border border-white/12 px-2.5 py-1 text-[11px] text-white/60 hover:bg-white/5"
                        >
                          续费{p === 'DAY' ? '日' : p === 'MONTH' ? '月' : '年'}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                <div>
                  <div className="mb-2 flex items-center gap-2 text-sm text-white/70">
                    <Sparkles className="h-4 w-4 text-amber-300/80" />
                    提示词版本
                  </div>
                  <div className="max-h-40 space-y-1 overflow-y-auto">
                    {(detailQ.data.promptVersions ?? []).map((v) => (
                      <div
                        key={v.id}
                        className={clsx(
                          'rounded-lg border px-3 py-2 text-xs',
                          v.active ? 'border-amber-400/30 bg-amber-400/5 text-white/80' : 'border-white/8 text-white/45'
                        )}
                      >
                        <div className="font-medium">
                          v{v.versionNo}
                          {v.active ? ' · 当前' : ''}
                        </div>
                        <div className="mt-0.5">{v.changeSummary || '—'}</div>
                      </div>
                    ))}
                  </div>
                </div>

                {atoms.length > 0 && (
                  <div>
                    <div className="mb-2 text-sm text-white/70">原子更新（可剔除）</div>
                    <ul className="space-y-2">
                      {atoms.map((a) => (
                        <li
                          key={a.id}
                          className={clsx(
                            'flex items-start justify-between gap-3 rounded-lg border border-white/8 px-3 py-2 text-xs',
                            a.excluded ? 'opacity-40 line-through' : 'text-white/70'
                          )}
                        >
                          <span>{a.text}</span>
                          {!a.excluded && (
                            <button
                              type="button"
                              className="shrink-0 text-rose-300/80 hover:text-rose-200"
                              onClick={() =>
                                excludeMut.mutate({ id: detailQ.data!.id, atomIds: [a.id] })
                              }
                            >
                              剔除
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {activeVersion?.promptText && (
                  <div>
                    <div className="mb-2 text-sm text-white/70">当前系统提示词</div>
                    <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded-xl border border-white/8 bg-black/30 p-3 text-[11px] leading-relaxed text-white/55">
                      {activeVersion.promptText}
                    </pre>
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  )
}
