import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { ArrowUpRight, Bot, Brain, Camera, Copy, ExternalLink, Link2, Pin, RefreshCw, Sparkles, X } from 'lucide-react'
import {
  multiAgentApi,
  type LocalBridgeConnectBundle,
  type PromptVersion,
  type RentPeriod,
  type SoulMemoryItem,
} from '../api/multiAgent'
import { AgentAvatar } from '../components/agent/AgentAvatar'
import { fileToChatImage } from '../lib/chatImage'
import { soulConsoleUrl } from '../lib/host'
import clsx from 'clsx'

type DetailTab = 'prompt' | 'soul'

const KIND_LABEL: Record<string, string> = {
  PROFILE: '画像',
  PREFERENCE: '偏好',
  FACT: '事实',
  EPISODE: '情节',
  DECISION: '决策',
}

function kindLabel(kind?: string) {
  if (!kind) return '记忆'
  return KIND_LABEL[kind.toUpperCase()] ?? kind
}

export function MyAgentsPage() {
  const qc = useQueryClient()
  const { data: agents = [], isLoading } = useQuery({
    queryKey: ['my-agents'],
    queryFn: () => multiAgentApi.listMine(),
  })
  const [activeId, setActiveId] = useState<number | null>(null)
  const [tab, setTab] = useState<DetailTab>('soul')
  const [bridgeBundle, setBridgeBundle] = useState<LocalBridgeConnectBundle | null>(null)
  const avatarInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (activeId == null && agents.length > 0) {
      setActiveId(agents[0].id)
    }
  }, [agents, activeId])

  const detailQ = useQuery({
    queryKey: ['my-agent', activeId],
    queryFn: () => multiAgentApi.detail(activeId!),
    enabled: activeId != null,
  })

  const soulQ = useQuery({
    queryKey: ['agent-soul-memories', activeId],
    queryFn: () => multiAgentApi.soulMemories(activeId!),
    enabled: activeId != null && tab === 'soul',
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

  const avatarMut = useMutation({
    mutationFn: async ({ id, file, clear }: { id: number; file?: File; clear?: boolean }) => {
      if (clear) return multiAgentApi.clearAvatar(id)
      if (!file) throw new Error('请选择图片')
      const dataUrl = await fileToChatImage(file)
      return multiAgentApi.uploadAvatar(id, dataUrl)
    },
    onSuccess: (_r, vars) => {
      toast.success(vars.clear ? '已恢复默认头像' : '头像已更新')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const [dailyLimitDraft, setDailyLimitDraft] = useState('')
  const [nickDraft, setNickDraft] = useState('')
  useEffect(() => {
    const lim = detailQ.data?.dailyPointsLimit
    setDailyLimitDraft(lim != null ? String(lim) : '')
  }, [detailQ.data?.id, detailQ.data?.dailyPointsLimit])
  useEffect(() => {
    setNickDraft(detailQ.data?.name ?? '')
  }, [detailQ.data?.id, detailQ.data?.name])

  const dailyLimitMut = useMutation({
    mutationFn: (limit: number | null) =>
      multiAgentApi.updatePersona(activeId!, { dailyPointsLimit: limit }),
    onSuccess: () => {
      toast.success('每日积分上限已更新')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const nickMut = useMutation({
    mutationFn: (name: string) => multiAgentApi.updatePersona(activeId!, { name }),
    onSuccess: () => {
      toast.success('昵称已更新')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const bridgeMut = useMutation({
    mutationFn: ({ id, rotate }: { id: number; rotate: boolean }) =>
      multiAgentApi.localBridgeConnect(id, rotate),
    onSuccess: (bundle) => {
      setBridgeBundle(bundle)
      if (bundle.tokenRotated) toast.success('已生成接入令牌与提示词')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const copyPrompt = async () => {
    if (!bridgeBundle?.prompt) return
    try {
      await navigator.clipboard.writeText(bridgeBundle.prompt)
      toast.success('提示词已复制')
    } catch {
      toast.error('复制失败，请手动全选复制')
    }
  }

  const activeVersion: PromptVersion | undefined = detailQ.data?.promptVersions?.find((v) => v.active)
  const consoleUrl = soulConsoleUrl(detailQ.data?.soulAgentId ?? soulQ.data?.soulAgentId)

  let atoms: { id: string; text: string; excluded?: boolean }[] = []
  try {
    atoms = activeVersion?.atomsJson ? JSON.parse(activeVersion.atomsJson) : []
  } catch {
    atoms = []
  }

  const memoryItems: SoulMemoryItem[] = soulQ.data?.items ?? []

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="text-2xl font-semibold text-white flex items-center gap-2">
        <Bot className="h-6 w-6 text-violet-300" />
        我的 Agent
      </h1>
      <p className="mt-1 text-sm text-white/45">
        每个 Agent 绑定一颗独立 Soul。可在此预览记忆，或跳转 Soul 平台查看完整档案。
      </p>

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
                onClick={() => {
                  setActiveId(a.id)
                  setTab('soul')
                }}
                className={clsx(
                  'w-full rounded-xl px-3 py-3 text-left transition-colors ring-1 ring-inset',
                  activeId === a.id
                    ? 'bg-violet-400/10 ring-violet-400/30'
                    : 'bg-white/[0.03] ring-white/[0.04] hover:bg-white/[0.05] hover:ring-white/[0.08]'
                )}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <AgentAvatar agent={a} size={28} />
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-white truncate">{a.name}</div>
                      {a.originalName && a.originalName !== a.name && (
                        <div className="mt-0.5 text-[10px] text-white/30 truncate">{a.originalName}</div>
                      )}
                    </div>
                  </div>
                  <span
                    className={clsx(
                      'shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-medium',
                      a.soulBound
                        ? 'bg-emerald-500/15 text-emerald-300/90 border border-emerald-400/25'
                        : 'bg-white/5 text-white/35 border border-white/10'
                    )}
                    title={a.soulBound ? '已绑定 Soul' : '尚未绑定 Soul'}
                  >
                    {a.soulBound ? 'Soul' : '未绑'}
                  </span>
                </div>
                <div className="mt-1 flex flex-wrap gap-2 text-[10px] text-white/40">
                  <span>{a.kind === 'SECRETARY' ? '秘书' : '已招聘'}</span>
                  <span>{a.status}</span>
                  <span>v{a.currentPromptVersion}</span>
                </div>
              </button>
            ))}
          </div>

          <div className="rounded-2xl bg-white/[0.03] p-5 min-h-[320px] ring-1 ring-inset ring-white/[0.04]">
            {!activeId ? (
              <div className="text-sm text-white/35">选择左侧 Agent 查看详情</div>
            ) : detailQ.isLoading ? (
              <div className="text-sm text-white/40">加载中…</div>
            ) : detailQ.data ? (
              <div className="space-y-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-start gap-3 min-w-0">
                    <div className="relative group/avatar shrink-0">
                      <AgentAvatar agent={detailQ.data} size={56} className="ring-1 ring-white/10" />
                      <button
                        type="button"
                        disabled={avatarMut.isPending}
                        onClick={() => avatarInputRef.current?.click()}
                        className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover/avatar:opacity-100 transition-opacity"
                        title="更换头像"
                      >
                        <Camera className="h-4 w-4 text-white" />
                      </button>
                      <input
                        ref={avatarInputRef}
                        type="file"
                        accept="image/jpeg,image/png,image/webp,image/gif"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0]
                          e.target.value = ''
                          if (!file || !detailQ.data) return
                          avatarMut.mutate({ id: detailQ.data.id, file })
                        }}
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <input
                          value={nickDraft}
                          onChange={(e) => setNickDraft(e.target.value)}
                          maxLength={128}
                          aria-label="Agent 昵称"
                          className="min-w-0 flex-1 max-w-xs rounded-lg bg-white/[0.05] px-2.5 py-1.5 text-lg font-semibold text-white outline-none ring-1 ring-inset ring-white/[0.06] focus:ring-cyan-400/35"
                        />
                        <button
                          type="button"
                          disabled={
                            nickMut.isPending ||
                            !nickDraft.trim() ||
                            nickDraft.trim() === (detailQ.data.name || '')
                          }
                          onClick={() => nickMut.mutate(nickDraft.trim())}
                          className="rounded-lg bg-cyan-500/80 px-2.5 py-1.5 text-[11px] font-medium text-black disabled:opacity-40"
                        >
                          保存昵称
                        </button>
                        {detailQ.data.soulBound ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[11px] text-emerald-300/90">
                            <Brain className="h-3 w-3" />
                            Soul #{detailQ.data.soulAgentId}
                          </span>
                        ) : (
                          <span className="rounded-full bg-white/5 px-2 py-0.5 text-[11px] text-white/40">
                            未绑定 Soul
                          </span>
                        )}
                      </div>
                      {detailQ.data.originalName && (
                        <div className="mt-1 text-[11px] text-white/30">
                          原名 {detailQ.data.originalName}
                        </div>
                      )}
                      <div className="mt-1 text-xs text-white/40">
                        {detailQ.data.expiresAt ? `到期 ${detailQ.data.expiresAt}` : '长期有效'}
                        {' · '}状态 {detailQ.data.status}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <button
                          type="button"
                          disabled={avatarMut.isPending}
                          onClick={() => avatarInputRef.current?.click()}
                          className="rounded-lg bg-white/[0.06] px-2.5 py-1 text-[11px] text-white/60 hover:bg-white/10"
                        >
                          {detailQ.data.avatarUrl ? '更换头像' : '设置头像'}
                        </button>
                        {detailQ.data.avatarUrl && (
                          <button
                            type="button"
                            disabled={avatarMut.isPending}
                            onClick={() => avatarMut.mutate({ id: detailQ.data!.id, clear: true })}
                            className="rounded-lg bg-white/[0.06] px-2.5 py-1 text-[11px] text-white/45 hover:bg-white/10"
                          >
                            恢复默认
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                  {consoleUrl && (
                    <a
                      href={consoleUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 rounded-xl border border-violet-400/35 bg-violet-500/10 px-3 py-2 text-xs font-medium text-violet-200 hover:bg-violet-500/20 transition-colors"
                    >
                      在 Soul 打开
                      <ExternalLink className="h-3.5 w-3.5" />
                    </a>
                  )}
                </div>

                {detailQ.data.kind === 'HIRED' && (
                  <div className="flex gap-2">
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

                {detailQ.data.localBridge && (
                  <div className="rounded-xl bg-cyan-500/[0.06] px-3.5 py-3 ring-1 ring-inset ring-cyan-400/20">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-[11px] font-medium text-cyan-200/90 flex items-center gap-1.5">
                          <Link2 className="h-3.5 w-3.5" />
                          接入本地 AI
                        </div>
                        <p className="mt-1 text-[10px] leading-relaxed text-white/40">
                          复制提示词给本机 Cursor / Codex 等；群聊中需 @ 该 Agent 才会派发任务。
                          {detailQ.data.localBridgeConfigured
                            ? ` 已配置令牌（${detailQ.data.localBridgeTokenPrefix || 'lba_'}…）`
                            : ' 尚未生成令牌。'}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={bridgeMut.isPending || activeId == null}
                        onClick={() =>
                          bridgeMut.mutate({
                            id: detailQ.data!.id,
                            rotate: !detailQ.data!.localBridgeConfigured,
                          })
                        }
                        className="shrink-0 rounded-lg bg-cyan-500/85 px-3 py-1.5 text-[11px] font-medium text-black disabled:opacity-40"
                      >
                        {detailQ.data.localBridgeConfigured ? '查看接入提示词' : '生成接入提示词'}
                      </button>
                    </div>
                  </div>
                )}

                <div className="rounded-xl bg-white/[0.03] px-3.5 py-3 ring-1 ring-inset ring-white/[0.05]">
                  <div className="text-[11px] font-medium text-white/55">每日积分上限</div>
                  <p className="mt-1 text-[10px] leading-relaxed text-white/30">
                    限制该 Agent 对话/搜索/闲时学习的日消耗；留空表示不限制。
                  </p>
                  <div className="mt-2 flex items-center gap-2">
                    <input
                      value={dailyLimitDraft}
                      onChange={(e) => setDailyLimitDraft(e.target.value.replace(/\D/g, ''))}
                      placeholder="不限制"
                      className="w-36 rounded-lg border border-white/[0.06] bg-white/[0.04] px-2.5 py-1.5 text-xs text-white outline-none focus:border-cyan-400/35"
                    />
                    <button
                      type="button"
                      disabled={dailyLimitMut.isPending || activeId == null}
                      onClick={() => {
                        const v = dailyLimitDraft.trim()
                        dailyLimitMut.mutate(v ? Number(v) : null)
                      }}
                      className="rounded-lg bg-cyan-500/80 px-2.5 py-1.5 text-[11px] font-medium text-black disabled:opacity-40"
                    >
                      保存
                    </button>
                  </div>
                </div>

                <div className="flex gap-1">
                  {(
                    [
                      { id: 'soul' as const, label: 'Soul 记忆', icon: Brain },
                      { id: 'prompt' as const, label: '提示词', icon: Sparkles },
                    ] as const
                  ).map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => setTab(t.id)}
                      className={clsx(
                        'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs transition-colors',
                        tab === t.id
                          ? 'bg-violet-500/15 text-violet-200'
                          : 'text-white/40 hover:text-white/70 hover:bg-white/[0.04]'
                      )}
                    >
                      <t.icon className="h-3.5 w-3.5" />
                      {t.label}
                    </button>
                  ))}
                </div>

                {tab === 'soul' && (
                  <div className="space-y-4">
                    <div className="flex items-start justify-between gap-3">
                      <p className="text-[11px] leading-relaxed text-white/35">
                        对话写入的长期记忆归属本 Agent 的独立灵魂，与其他 Agent 互不串台。
                      </p>
                      <button
                        type="button"
                        onClick={() => soulQ.refetch()}
                        disabled={soulQ.isFetching}
                        className="shrink-0 p-1.5 rounded-lg text-white/30 hover:text-white/65 hover:bg-white/[0.04] disabled:opacity-40"
                        title="刷新"
                      >
                        <RefreshCw className={clsx('h-3.5 w-3.5', soulQ.isFetching && 'animate-spin')} />
                      </button>
                    </div>

                    {!detailQ.data.soulBound && !soulQ.data?.soulBound ? (
                      <div className="rounded-xl bg-white/[0.03] px-4 py-8 text-center">
                        <Brain className="mx-auto h-8 w-8 text-white/25" />
                        <p className="mt-3 text-sm text-white/50">该 Agent 尚未绑定 Soul</p>
                        <p className="mt-1 text-xs text-white/30">
                          秘书需完善画像后自动建魂；招聘 Agent 会在付款时一并开通席位。
                        </p>
                      </div>
                    ) : soulQ.isLoading ? (
                      <div className="py-10 text-center text-sm text-white/35">正在读取 Soul 记忆…</div>
                    ) : soulQ.data && !soulQ.data.ok ? (
                      <div className="rounded-xl bg-amber-500/[0.06] px-4 py-4 text-xs text-amber-200/80">
                        {soulQ.data.error || '暂时无法读取 Soul 记忆'}
                        {consoleUrl && (
                          <a
                            href={consoleUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="mt-2 inline-flex items-center gap-1 text-violet-300 hover:text-violet-200"
                          >
                            改去 Soul 平台查看 <ArrowUpRight className="h-3.5 w-3.5" />
                          </a>
                        )}
                      </div>
                    ) : memoryItems.length === 0 ? (
                      <div className="rounded-xl bg-white/[0.03] px-4 py-8 text-center text-sm text-white/35">
                        暂无记忆条目。与该 Agent 对话后，重要信息会沉淀到这里。
                        {consoleUrl && (
                          <div className="mt-3">
                            <a
                              href={consoleUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1 text-xs text-violet-300/90 hover:text-violet-200"
                            >
                              打开 Soul 档案 <ArrowUpRight className="h-3.5 w-3.5" />
                            </a>
                          </div>
                        )}
                      </div>
                    ) : (
                      <ul className="space-y-2 max-h-[28rem] overflow-y-auto pr-1">
                        {memoryItems.map((m, i) => (
                          <li
                            key={m.id ?? `${m.kind}-${i}`}
                            className="rounded-xl bg-white/[0.03] px-3.5 py-3"
                          >
                            <div className="flex items-center gap-2 text-[10px] text-white/40">
                              <span className="rounded bg-white/8 px-1.5 py-0.5 text-white/55">
                                {kindLabel(m.kind)}
                              </span>
                              {m.pinned && (
                                <span className="inline-flex items-center gap-0.5 text-amber-300/80">
                                  <Pin className="h-3 w-3" /> CORE
                                </span>
                              )}
                              {typeof m.importance === 'number' && (
                                <span>重要度 {(m.importance * 100).toFixed(0)}%</span>
                              )}
                              {m.updatedAt && (
                                <span className="ml-auto truncate max-w-[40%]">
                                  {new Date(m.updatedAt).toLocaleString()}
                                </span>
                              )}
                            </div>
                            <p className="mt-1.5 text-sm leading-relaxed text-white/75 whitespace-pre-wrap">
                              {m.content}
                            </p>
                          </li>
                        ))}
                      </ul>
                    )}

                    {consoleUrl && memoryItems.length > 0 && (
                      <div className="pt-1 flex justify-end">
                        <a
                          href={consoleUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1.5 text-xs text-violet-300/90 hover:text-violet-200"
                        >
                          在 Soul 查看完整档案与心智图
                          <ArrowUpRight className="h-3.5 w-3.5" />
                        </a>
                      </div>
                    )}
                  </div>
                )}

                {tab === 'prompt' && (
                  <div className="space-y-5">
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
                              v.active
                                ? 'border-amber-400/30 bg-amber-400/5 text-white/80'
                                : 'border-white/8 text-white/45'
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
                )}
              </div>
            ) : null}
          </div>
        </div>
      )}

      {bridgeBundle &&
        createPortal(
          <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm">
            <div className="w-full max-w-2xl rounded-2xl bg-[#14151a] p-5 ring-1 ring-white/10 shadow-2xl">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-base font-semibold text-white">接入本地 AI · {bridgeBundle.agentName}</div>
                  <p className="mt-1 text-[11px] text-white/40">
                    把下面提示词粘贴给本机 Cursor / Codex。令牌仅在生成/轮换时完整展示，请自行妥善保存。
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setBridgeBundle(null)}
                  className="rounded-lg p-1.5 text-white/40 hover:bg-white/5 hover:text-white/70"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              {bridgeBundle.hint && (
                <div className="mt-3 rounded-lg border border-amber-400/25 bg-amber-500/10 px-3 py-2 text-[11px] text-amber-100/85">
                  {bridgeBundle.hint}
                </div>
              )}
              <pre className="mt-3 max-h-[50vh] overflow-auto whitespace-pre-wrap rounded-xl border border-white/8 bg-black/40 p-3 text-[11px] leading-relaxed text-white/70">
                {bridgeBundle.prompt}
              </pre>
              <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
                <button
                  type="button"
                  disabled={bridgeMut.isPending || activeId == null}
                  onClick={() => bridgeMut.mutate({ id: activeId!, rotate: true })}
                  className="rounded-lg border border-white/12 px-3 py-1.5 text-[11px] text-white/60 hover:bg-white/5 disabled:opacity-40"
                >
                  重新生成令牌
                </button>
                <button
                  type="button"
                  onClick={() => void copyPrompt()}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-cyan-500/85 px-3 py-1.5 text-[11px] font-medium text-black"
                >
                  <Copy className="h-3.5 w-3.5" />
                  复制提示词
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </div>
  )
}


