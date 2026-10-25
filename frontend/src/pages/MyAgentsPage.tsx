import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  ArrowUpRight,
  Bot,
  Brain,
  Camera,
  Code2,
  Copy,
  ExternalLink,
  Globe2,
  Link2,
  MessageCircle,
  Monitor,
  Pin,
  Plus,
  RefreshCw,
  Settings2,
  Sparkles,
  Terminal,
  Trash2,
  X,
} from 'lucide-react'
import {
  multiAgentApi,
  type LocalBridgeConnectBundle,
  type LocalBridgeLinkMode,
  type PromptVersion,
  type RentPeriod,
  type SoulMemoryItem,
} from '../api/multiAgent'
import { AgentAvatar, formatAgentDisplayTitle } from '../components/agent/AgentAvatar'
import { fileToChatImage } from '../lib/chatImage'
import { soulConsoleUrl } from '../lib/host'
import { AppToggle } from '../ui/AppToggle'
import clsx from 'clsx'

type DetailTab = 'soul' | 'devices' | 'settings'

const RENEW_PERIODS: { id: RentPeriod; label: string }[] = [
  { id: 'DAY', label: '包日' },
  { id: 'MONTH', label: '包月' },
  { id: 'YEAR', label: '包年' },
]

const KIND_LABEL: Record<string, string> = {
  PROFILE: '画像',
  PREFERENCE: '偏好',
  FACT: '事实',
  EPISODE: '情节',
  DECISION: '决策',
}

const STATUS_LABEL: Record<string, string> = {
  PENDING_PROFILE: '待完善',
  ACTIVE: '生效中',
  EXPIRED: '已到期',
  DISABLED: '已停用',
}

function kindLabel(kind?: string) {
  if (!kind) return '记忆'
  return KIND_LABEL[kind.toUpperCase()] ?? kind
}

function statusLabel(status?: string | null) {
  if (!status) return '—'
  return STATUS_LABEL[status.toUpperCase()] ?? status
}

/** 展示用本地时间，兼容 ISO / 空格分隔 */
function formatDateTime(raw?: string | null): string {
  if (!raw) return ''
  const d = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}

export function MyAgentsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: agents = [], isLoading } = useQuery({
    queryKey: ['my-agents'],
    queryFn: () => multiAgentApi.listMine(),
    refetchInterval: (q) => {
      const list = q.state.data
      return Array.isArray(list) && list.some((a) => a.localBridge) ? 15_000 : false
    },
  })
  const [activeId, setActiveId] = useState<number | null>(null)
  const [tab, setTab] = useState<DetailTab>('soul')
  const [bridgeBundle, setBridgeBundle] = useState<LocalBridgeConnectBundle | null>(null)
  const [addDeviceOpen, setAddDeviceOpen] = useState(false)
  const [clientHint, setClientHint] = useState<string | null>(null)
  const [deviceLabelDraft, setDeviceLabelDraft] = useState('')
  const [renewOpen, setRenewOpen] = useState(false)
  const [renewPeriod, setRenewPeriod] = useState<RentPeriod>('MONTH')
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
    refetchInterval: (q) => (q.state.data?.localBridge ? 15_000 : false),
  })

  const soulQ = useQuery({
    queryKey: ['agent-soul-memories', activeId],
    queryFn: () => multiAgentApi.soulMemories(activeId!),
    enabled: activeId != null && tab === 'soul',
  })

  useEffect(() => {
    setTab('soul')
    setAddDeviceOpen(false)
    setClientHint(null)
    setBridgeBundle(null)
  }, [activeId])

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

  const renewCatalogId = detailQ.data?.catalogId
  const renewQuoteQ = useQuery({
    queryKey: ['agent-renew-quote', renewCatalogId, renewPeriod],
    queryFn: () => multiAgentApi.quote(renewCatalogId!, renewPeriod),
    enabled: renewOpen && renewCatalogId != null,
  })

  const renewMut = useMutation({
    mutationFn: ({ id, period }: { id: number; period: RentPeriod }) => multiAgentApi.renew(id, period),
    onSuccess: (r) => {
      toast.success(`续费成功，扣 ${r.totalPoints} 积分`)
      setRenewOpen(false)
      qc.invalidateQueries({ queryKey: ['my-agents'] })
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
    },
    onError: (e: Error) => toast.error(e.message || '续费失败'),
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
  useEffect(() => {
    const lim = detailQ.data?.dailyPointsLimit
    setDailyLimitDraft(lim != null ? String(lim) : '')
  }, [detailQ.data?.id, detailQ.data?.dailyPointsLimit])

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

  const registerDeviceMut = useMutation({
    mutationFn: ({
      id,
      linkMode,
      label,
    }: {
      id: number
      linkMode: LocalBridgeLinkMode
      label?: string
    }) => multiAgentApi.registerLocalDevice(id, { linkMode, label }),
    onSuccess: (res) => {
      setAddDeviceOpen(false)
      setDeviceLabelDraft('')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
      if (res.linkMode === 'CLIENT') {
        setClientHint(res.clientHint || '请打开本机 Aimate Connector 并开启对应连接器。')
        toast.success('请按指引用客户端连接')
      } else if (res.bridge) {
        setBridgeBundle(res.bridge)
        toast.success('已登记提示词设备，请复制接入提示词')
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const removeDeviceMut = useMutation({
    mutationFn: ({ id, deviceKey }: { id: number; deviceKey: string }) =>
      multiAgentApi.removeLocalDevice(id, deviceKey),
    onSuccess: () => {
      toast.success('已移除设备')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const disconnectDeviceMut = useMutation({
    mutationFn: ({ id, deviceKey }: { id: number; deviceKey: string }) =>
      multiAgentApi.disconnectLocalDevice(id, deviceKey),
    onSuccess: () => {
      toast.success('已关闭连接')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const allowReconnectMut = useMutation({
    mutationFn: ({ id, deviceKey }: { id: number; deviceKey: string }) =>
      multiAgentApi.allowLocalDeviceReconnect(id, deviceKey),
    onSuccess: () => {
      toast.success('已允许重连，等待设备上线')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const localAssistMut = useMutation({
    mutationFn: (body: { localAssistDm?: boolean; localAssistGroup?: boolean }) =>
      multiAgentApi.updatePersona(activeId!, body),
    onSuccess: () => {
      toast.success('本机协助设置已保存')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const agentToolMut = useMutation({
    mutationFn: (body: {
      memoryEnabled?: boolean
      webSearchEnabled?: boolean
      createToolEnabled?: boolean
      scriptExecEnabled?: boolean
    }) => multiAgentApi.updatePersona(activeId!, body),
    onSuccess: () => {
      toast.success('工具默认已保存，所有单聊已同步')
      qc.invalidateQueries({ queryKey: ['my-agent', activeId] })
      qc.invalidateQueries({ queryKey: ['my-agents'] })
      qc.invalidateQueries({ queryKey: ['tool-settings'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openChatMut = useMutation({
    mutationFn: (id: number) => multiAgentApi.openChat(id),
    onSuccess: (session) => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      qc.invalidateQueries({ queryKey: ['recent-sessions'] })
      navigate(`/session/${session.sessionId}`)
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

  function linkModeLabel(mode?: string) {
    if (mode === 'PROMPT') return '提示词连接'
    return '客户端连接'
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
                  setRenewOpen(false)
                }}
                className={clsx(
                  'w-full rounded-xl px-3 py-3 text-left transition-colors ring-1 ring-inset',
                  activeId === a.id
                    ? 'bg-violet-400/10 ring-violet-400/30'
                    : 'bg-white/[0.03] ring-white/[0.04] hover:bg-white/[0.05] hover:ring-white/[0.08]'
                )}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <AgentAvatar agent={a} size={36} />
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium text-white truncate">
                      {formatAgentDisplayTitle(a)}
                    </div>
                    <div className="mt-0.5 text-[11px] text-white/45 truncate">
                      {[
                        a.genderLabel || null,
                        a.kind === 'SECRETARY' ? null : a.localBridge ? '本机' : null,
                        a.status === 'ACTIVE' ? null : statusLabel(a.status),
                        a.localBridge
                          ? a.localBridgeOnline
                            ? '在线'
                            : '离线'
                          : null,
                      ]
                        .filter(Boolean)
                        .join(' · ') || '—'}
                    </div>
                  </div>
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
                      <h2 className="text-lg font-semibold text-white truncate">
                        {formatAgentDisplayTitle(detailQ.data)}
                        {detailQ.data.genderLabel ? (
                          <span className="ml-2 text-sm font-normal text-white/50">
                            {detailQ.data.genderLabel}
                          </span>
                        ) : null}
                      </h2>
                      <div className="mt-1 text-xs text-white/40 truncate">
                        {[
                          detailQ.data.kind === 'SECRETARY'
                            ? null
                            : detailQ.data.localBridge
                              ? '本机 Agent'
                              : '已招聘',
                          detailQ.data.soulBound ? '已绑 Soul' : null,
                          detailQ.data.expiresAt
                            ? `${formatDateTime(detailQ.data.expiresAt)} 到期`
                            : '长期有效',
                          detailQ.data.status !== 'ACTIVE' ? statusLabel(detailQ.data.status) : null,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <button
                          type="button"
                          disabled={avatarMut.isPending}
                          onClick={() => avatarInputRef.current?.click()}
                          className="rounded-lg bg-white/[0.06] px-2.5 py-1 text-[11px] text-white/60 hover:bg-white/10"
                        >
                          {detailQ.data.avatarUrl ? '换头像' : '设头像'}
                        </button>
                        {detailQ.data.avatarUrl && (
                          <button
                            type="button"
                            disabled={avatarMut.isPending}
                            onClick={() => avatarMut.mutate({ id: detailQ.data!.id, clear: true })}
                            className="rounded-lg bg-white/[0.06] px-2.5 py-1 text-[11px] text-white/45 hover:bg-white/10"
                          >
                            默认头像
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    <button
                      type="button"
                      disabled={
                        openChatMut.isPending ||
                        detailQ.data.status !== 'ACTIVE' ||
                        activeId == null
                      }
                      onClick={() => activeId != null && openChatMut.mutate(activeId)}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-cyan-500/90 px-3 py-2 text-xs font-medium text-black hover:bg-cyan-400 disabled:opacity-40 transition-colors"
                    >
                      <MessageCircle className="h-3.5 w-3.5" />
                      {openChatMut.isPending
                        ? '打开中…'
                        : detailQ.data.genderLabel === '女'
                          ? '联系她'
                          : '联系他'}
                    </button>
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
                </div>

                {detailQ.data.kind === 'HIRED' && detailQ.data.catalogId != null && (
                  <div>
                    <button
                      type="button"
                      onClick={() => {
                        setRenewPeriod('MONTH')
                        setRenewOpen(true)
                      }}
                      className="rounded-xl bg-cyan-500/85 px-3.5 py-2 text-xs font-medium text-black hover:bg-cyan-400 transition-colors"
                    >
                      续费
                    </button>
                  </div>
                )}

                <div className="flex flex-wrap gap-1 border-b border-white/[0.06] pb-1">
                  {(
                    [
                      { id: 'soul' as const, label: '记忆', icon: Brain },
                      ...(detailQ.data.localBridge
                        ? [{ id: 'devices' as const, label: '管理设备', icon: Monitor }]
                        : []),
                      { id: 'settings' as const, label: '设置与偏好', icon: Settings2 },
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
                        暂无记忆条目。群聊中 @ 该 Agent 并完成任务后，服务端会自动提炼耐久事实到 Soul；也可在私聊中沉淀。
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
                                  {formatDateTime(m.updatedAt)}
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

                {tab === 'devices' && detailQ.data.localBridge && (
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-[11px] leading-relaxed text-white/40">
                          同一 Agent 可挂多台设备。连接方式：
                          <span className="text-white/55"> 客户端</span>
                          （Aimate Connector）或
                          <span className="text-white/55"> 提示词</span>
                          （旧版 worker / 脚本）。
                          {(detailQ.data.localBridgeOnlineCount ?? 0) > 0
                            ? ` 当前 ${detailQ.data.localBridgeOnlineCount} 台在线。`
                            : ' 当前无设备在线。'}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => setAddDeviceOpen(true)}
                        className="inline-flex items-center gap-1.5 shrink-0 rounded-lg bg-cyan-500/85 px-3 py-1.5 text-[11px] font-medium text-black"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        添加设备
                      </button>
                    </div>

                    {(detailQ.data.localBridgeDevices?.length ?? 0) === 0 ? (
                      <div className="rounded-xl border border-dashed border-white/10 px-4 py-10 text-center">
                        <Monitor className="mx-auto h-8 w-8 text-white/25" />
                        <p className="mt-3 text-sm text-white/50">还没有登记设备</p>
                        <p className="mt-1 text-xs text-white/35">
                          点右上角「添加设备」，选择客户端或提示词方案。
                        </p>
                      </div>
                    ) : (
                      <div className="grid gap-3 sm:grid-cols-2">
                        {detailQ.data.localBridgeDevices!.map((dev) => (
                          <div
                            key={dev.deviceKey || `${dev.hostname}-${dev.lastSeenAt}`}
                            className={clsx(
                              'rounded-xl px-3.5 py-3 ring-1 ring-inset',
                              dev.online
                                ? 'bg-emerald-500/[0.07] ring-emerald-400/20'
                                : 'bg-white/[0.03] ring-white/[0.06]'
                            )}
                          >
                            <div className="flex items-start justify-between gap-2">
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-1.5 text-[11px] font-medium">
                                  <span
                                    className={clsx(
                                      'h-1.5 w-1.5 rounded-full',
                                      dev.userDisconnected
                                        ? 'bg-amber-400'
                                        : dev.online
                                          ? 'bg-emerald-400 animate-pulse'
                                          : 'bg-white/25'
                                    )}
                                  />
                                  <span
                                    className={
                                      dev.userDisconnected
                                        ? 'text-amber-200/90'
                                        : dev.online
                                          ? 'text-emerald-200/95'
                                          : 'text-white/45'
                                    }
                                  >
                                    {dev.userDisconnected ? '已关闭' : dev.online ? '在线' : '离线'}
                                  </span>
                                  <span className="truncate text-white/80">
                                    {dev.label || dev.hostname || '未命名设备'}
                                  </span>
                                </div>
                                <div className="mt-2 inline-flex items-center gap-1 rounded-md bg-black/25 px-1.5 py-0.5 text-[10px] text-white/55">
                                  {dev.linkMode === 'PROMPT' ? (
                                    <Link2 className="h-3 w-3 text-cyan-300/80" />
                                  ) : (
                                    <Monitor className="h-3 w-3 text-emerald-300/80" />
                                  )}
                                  {linkModeLabel(dev.linkMode)}
                                </div>
                              </div>
                              {dev.deviceKey && (
                                <button
                                  type="button"
                                  title="移除设备"
                                  disabled={removeDeviceMut.isPending}
                                  onClick={() =>
                                    removeDeviceMut.mutate({
                                      id: detailQ.data!.id,
                                      deviceKey: dev.deviceKey!,
                                    })
                                  }
                                  className="shrink-0 rounded-lg p-1.5 text-white/30 hover:bg-white/5 hover:text-rose-300 disabled:opacity-40"
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                </button>
                              )}
                            </div>
                            <div className="mt-2 space-y-0.5 text-[10px] text-white/40">
                              {dev.hostname && dev.label && (
                                <div className="truncate">主机 {dev.hostname}</div>
                              )}
                              {(dev.os || dev.arch) && (
                                <div className="truncate">
                                  {[dev.os, dev.arch].filter(Boolean).join(' · ')}
                                </div>
                              )}
                              {dev.appName && (
                                <div className="truncate" title={dev.appPath}>
                                  {dev.appName}
                                  {dev.connectorVersion ? ` · 连接器 v${dev.connectorVersion}` : ''}
                                </div>
                              )}
                              {dev.userDisconnected ? (
                                <div>已手动关闭，心跳不会再上线</div>
                              ) : dev.lastSeenAt ? (
                                <div>
                                  {dev.online ? '刚刚活跃' : `上次 ${formatDateTime(dev.lastSeenAt)}`}
                                </div>
                              ) : (
                                <div>等待首次上线</div>
                              )}
                              {dev.deviceKey && (
                                <div className="mt-2 flex flex-wrap gap-2">
                                  {dev.userDisconnected ? (
                                    <button
                                      type="button"
                                      disabled={allowReconnectMut.isPending}
                                      onClick={() =>
                                        allowReconnectMut.mutate({
                                          id: detailQ.data!.id,
                                          deviceKey: dev.deviceKey!,
                                        })
                                      }
                                      className="rounded-md bg-emerald-500/20 px-2 py-1 text-[10px] text-emerald-200 hover:bg-emerald-500/30 disabled:opacity-40"
                                    >
                                      允许重连
                                    </button>
                                  ) : (
                                    <button
                                      type="button"
                                      disabled={disconnectDeviceMut.isPending}
                                      onClick={() =>
                                        disconnectDeviceMut.mutate({
                                          id: detailQ.data!.id,
                                          deviceKey: dev.deviceKey!,
                                        })
                                      }
                                      className="rounded-md bg-amber-500/15 px-2 py-1 text-[10px] text-amber-200/90 hover:bg-amber-500/25 disabled:opacity-40"
                                      title="关闭后心跳不会再上线，需点「允许重连」"
                                    >
                                      关闭连接
                                    </button>
                                  )}
                                  {dev.linkMode === 'PROMPT' && (
                                    <button
                                      type="button"
                                      disabled={bridgeMut.isPending}
                                      onClick={() =>
                                        bridgeMut.mutate({
                                          id: detailQ.data!.id,
                                          rotate: false,
                                        })
                                      }
                                      className="text-cyan-300/80 hover:text-cyan-200"
                                    >
                                      复制提示词
                                    </button>
                                  )}
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {tab === 'settings' && (
                  <div className="space-y-5">
                    <section className="overflow-hidden rounded-2xl border border-white/[0.08] bg-[#12151c]/80">
                      <div className="relative border-b border-white/[0.06] px-3.5 pt-3 pb-2.5">
                        <div
                          className="pointer-events-none absolute inset-x-0 top-0 h-14 opacity-50"
                          style={{
                            background:
                              'radial-gradient(ellipse at top, rgba(34,211,238,0.1), transparent 70%)',
                          }}
                        />
                        <div className="relative text-[11px] font-semibold tracking-wide text-white/80 uppercase">
                          系统工具默认
                        </div>
                        <p className="relative mt-0.5 text-[10px] leading-relaxed text-white/35">
                          单聊未改过时跟随此处；聊天框改过仅本会话。此处一改，所有单聊重新跟随。
                        </p>
                      </div>
                      <div className="space-y-1 p-2">
                        {(
                          [
                            {
                              key: 'memoryEnabled' as const,
                              title: '长期记忆',
                              hint: '对话写入 Soul，跨会话召回',
                              icon: Brain,
                            },
                            {
                              key: 'webSearchEnabled' as const,
                              title: '联网搜索',
                              hint: 'Tavily 检索公开网页',
                              icon: Globe2,
                            },
                            {
                              key: 'createToolEnabled' as const,
                              title: '自主编写工具',
                              hint: '沉淀简单工具到工具箱',
                              icon: Code2,
                            },
                            {
                              key: 'scriptExecEnabled' as const,
                              title: '办公室脚本',
                              hint: '装包 / 命令 / 沙箱文件',
                              icon: Terminal,
                            },
                          ] as const
                        ).map(({ key, title, hint, icon: Icon }) => {
                          const checked = detailQ.data[key] !== false
                          return (
                            <div
                              key={key}
                              className={clsx(
                                'flex items-center gap-2.5 rounded-xl px-2.5 py-2 transition-colors',
                                checked ? 'bg-white/[0.04]' : 'hover:bg-white/[0.03]',
                              )}
                            >
                              <div
                                className={clsx(
                                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border',
                                  checked
                                    ? 'border-cyan-400/25 bg-cyan-500/10 text-cyan-300'
                                    : 'border-white/10 bg-white/[0.03] text-white/35',
                                )}
                              >
                                <Icon className="h-3.5 w-3.5" />
                              </div>
                              <div className="min-w-0 flex-1">
                                <div className="text-xs font-medium text-white/90 leading-tight">
                                  {title}
                                </div>
                                <div className="mt-0.5 text-[10px] leading-snug text-white/35">
                                  {hint}
                                </div>
                              </div>
                              <AppToggle
                                checked={checked}
                                disabled={agentToolMut.isPending || activeId == null}
                                ariaLabel={title}
                                onChange={(v) => agentToolMut.mutate({ [key]: v })}
                              />
                            </div>
                          )
                        })}
                      </div>
                    </section>

                    <section className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-3.5 py-3">
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
                    </section>

                    {detailQ.data.localBridge && (
                      <>
                        <section className="overflow-hidden rounded-2xl border border-white/[0.08] bg-[#12151c]/80">
                          <div className="border-b border-white/[0.06] px-3.5 pt-3 pb-2.5">
                            <div className="text-[11px] font-semibold tracking-wide text-white/80 uppercase">
                              本机协助
                            </div>
                            <p className="mt-0.5 text-[10px] leading-relaxed text-white/35">
                              先正常聊天；需控本机时：客户端由 Connector 写入对应本地应用；提示词设备才派发任务。
                            </p>
                          </div>
                          <div className="space-y-1 p-2">
                            {(
                              [
                                {
                                  key: 'localAssistDm' as const,
                                  title: '单聊可用本机',
                                  hint: '私聊里可判断后派发本机任务',
                                },
                                {
                                  key: 'localAssistGroup' as const,
                                  title: '群聊可用本机',
                                  hint: '群里 @ 后可判断是否控本机',
                                },
                              ] as const
                            ).map(({ key, title, hint }) => {
                              const checked = detailQ.data[key] !== false
                              return (
                                <div
                                  key={key}
                                  className={clsx(
                                    'flex items-center gap-2.5 rounded-xl px-2.5 py-2.5 transition-colors',
                                    checked ? 'bg-white/[0.04]' : 'hover:bg-white/[0.03]',
                                  )}
                                >
                                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/[0.03] text-white/40">
                                    <Monitor className="h-3.5 w-3.5" />
                                  </div>
                                  <div className="min-w-0 flex-1">
                                    <div className="text-xs font-medium text-white/90">{title}</div>
                                    <div className="mt-0.5 text-[10px] text-white/35">{hint}</div>
                                  </div>
                                  <AppToggle
                                    checked={checked}
                                    disabled={localAssistMut.isPending}
                                    ariaLabel={title}
                                    onChange={(v) => localAssistMut.mutate({ [key]: v })}
                                  />
                                </div>
                              )
                            })}
                          </div>
                        </section>

                        <div className="rounded-xl bg-cyan-500/[0.06] px-3.5 py-3 ring-1 ring-inset ring-cyan-400/20">
                          <div className="text-[11px] font-medium text-cyan-200/90 flex items-center gap-1.5">
                            <Link2 className="h-3.5 w-3.5" />
                            本地桥接令牌
                          </div>
                          <p className="mt-1 text-[10px] leading-relaxed text-white/40">
                            多设备共用同一 Agent 令牌；添加「提示词」设备时会下发可复制提示词。
                            {detailQ.data.localBridgeConfigured
                              ? ` 已配置（${detailQ.data.localBridgeTokenPrefix || 'lba_'}…）`
                              : ' 尚未生成。'}
                          </p>
                          <button
                            type="button"
                            disabled={bridgeMut.isPending}
                            onClick={() =>
                              bridgeMut.mutate({
                                id: detailQ.data!.id,
                                rotate: true,
                              })
                            }
                            className="mt-2 rounded-lg bg-cyan-500/85 px-3 py-1.5 text-[11px] font-medium text-black disabled:opacity-40"
                          >
                            轮换令牌并查看提示词
                          </button>
                        </div>
                      </>
                    )}

                    <div>
                      <div className="mb-2 flex items-center gap-2 text-sm text-white/70">
                        <Sparkles className="h-4 w-4 text-amber-300/80" />
                        系统提示词版本
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

      {renewOpen &&
        detailQ.data &&
        createPortal(
          <div
            className="fixed inset-0 z-[80] flex items-center justify-center p-4 sm:p-6"
            style={{ background: 'radial-gradient(ellipse at center, rgba(8,12,24,0.55), rgba(0,0,0,0.78))' }}
            onClick={() => !renewMut.isPending && setRenewOpen(false)}
            role="dialog"
            aria-modal="true"
            aria-labelledby="renew-dialog-title"
          >
            <div
              className="relative w-full max-w-md overflow-hidden rounded-2xl shadow-[0_32px_80px_-20px_rgba(0,0,0,0.85)] ring-1 ring-inset ring-white/[0.08]"
              style={{
                background:
                  'linear-gradient(165deg, rgba(22,26,36,0.98) 0%, rgba(14,16,22,0.99) 50%, rgba(10,12,18,1) 100%)',
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start justify-between gap-3 px-5 pt-5 pb-1">
                <div className="min-w-0">
                  <h3 id="renew-dialog-title" className="text-lg font-semibold text-white">
                    续费 · {detailQ.data.name}
                  </h3>
                  <p className="mt-1 text-xs text-white/40">
                    选择周期后确认下单，将从账户余额扣积分（含 Agent 与 Soul 席位）。
                    {detailQ.data.expiresAt
                      ? ` 当前到期 ${formatDateTime(detailQ.data.expiresAt)}`
                      : ''}
                  </p>
                </div>
                <button
                  type="button"
                  disabled={renewMut.isPending}
                  onClick={() => setRenewOpen(false)}
                  className="shrink-0 rounded-xl border border-white/10 bg-white/[0.03] p-2 text-white/45 hover:bg-white/[0.06] hover:text-white disabled:opacity-40"
                  aria-label="关闭"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              <div className="px-5 pb-5">
                <div className="mt-3 flex rounded-lg bg-white/[0.04] p-0.5">
                  {RENEW_PERIODS.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      disabled={renewMut.isPending}
                      onClick={() => setRenewPeriod(p.id)}
                      className={clsx(
                        'flex-1 rounded-md px-3 py-1.5 text-xs transition-colors',
                        renewPeriod === p.id
                          ? 'bg-white/[0.1] text-white'
                          : 'text-white/45 hover:text-white/70',
                      )}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>

                <div className="mt-3 space-y-2 rounded-xl bg-white/[0.04] p-3.5 text-sm">
                  {renewQuoteQ.isLoading ? (
                    <div className="text-xs text-white/40">正在报价…</div>
                  ) : renewQuoteQ.isError ? (
                    <div className="text-xs text-rose-300/80">
                      {(renewQuoteQ.error as Error)?.message || '报价失败'}
                    </div>
                  ) : (
                    <>
                      {(renewQuoteQ.data?.lines ?? []).map((l) => (
                        <div key={l.label} className="flex justify-between text-white/65">
                          <span>{l.label}</span>
                          <span className="font-mono tabular-nums">{l.points} 积分</span>
                        </div>
                      ))}
                      <div className="flex justify-between pt-2.5 mt-1 border-t border-white/[0.06] font-medium text-white">
                        <span>合计</span>
                        <span className="font-mono tabular-nums">
                          {renewQuoteQ.data?.totalPoints ?? '—'} 积分
                        </span>
                      </div>
                    </>
                  )}
                </div>

                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    disabled={renewMut.isPending}
                    className="flex-1 rounded-xl bg-white/[0.05] py-2.5 text-sm text-white/55 hover:bg-white/[0.08] hover:text-white/80 transition-colors disabled:opacity-40"
                    onClick={() => setRenewOpen(false)}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    disabled={renewMut.isPending || !renewQuoteQ.data}
                    className="flex-1 rounded-xl bg-cyan-500/90 py-2.5 text-sm font-medium text-black hover:bg-cyan-400 disabled:opacity-40 transition-colors"
                    onClick={() =>
                      renewMut.mutate({ id: detailQ.data!.id, period: renewPeriod })
                    }
                  >
                    {renewMut.isPending ? '下单中…' : '确认续费'}
                  </button>
                </div>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {addDeviceOpen &&
        detailQ.data &&
        createPortal(
          <div
            className="fixed inset-0 z-[80] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm"
            onClick={() => !registerDeviceMut.isPending && setAddDeviceOpen(false)}
          >
            <div
              className="w-full max-w-md rounded-2xl bg-[#14151a] p-5 ring-1 ring-white/10 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-base font-semibold text-white">添加设备</div>
                  <p className="mt-1 text-[11px] text-white/40">
                    选择连接方案。客户端适合日常；提示词适合脚本 / 旧版 worker。
                  </p>
                </div>
                <button
                  type="button"
                  disabled={registerDeviceMut.isPending}
                  onClick={() => setAddDeviceOpen(false)}
                  className="rounded-lg p-1.5 text-white/40 hover:bg-white/5"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <label className="mt-4 block text-[11px] text-white/45">
                设备备注（可选）
                <input
                  value={deviceLabelDraft}
                  onChange={(e) => setDeviceLabelDraft(e.target.value)}
                  maxLength={64}
                  placeholder="例如：书房 PC"
                  className="mt-1 w-full rounded-lg border border-white/[0.08] bg-white/[0.04] px-2.5 py-2 text-xs text-white outline-none focus:border-cyan-400/35"
                />
              </label>
              <div className="mt-4 grid gap-2">
                <button
                  type="button"
                  disabled={registerDeviceMut.isPending || activeId == null}
                  onClick={() =>
                    registerDeviceMut.mutate({
                      id: activeId!,
                      linkMode: 'CLIENT',
                      label: deviceLabelDraft.trim() || undefined,
                    })
                  }
                  className="rounded-xl bg-emerald-500/15 px-3.5 py-3 text-left ring-1 ring-inset ring-emerald-400/25 hover:bg-emerald-500/20 disabled:opacity-40"
                >
                  <div className="flex items-center gap-2 text-sm font-medium text-emerald-100">
                    <Monitor className="h-4 w-4" />
                    客户端连接
                  </div>
                  <p className="mt-1 text-[10px] leading-relaxed text-white/40">
                    本机运行 Aimate Connector，打开对应连接器开关后自动上线。
                  </p>
                </button>
                <button
                  type="button"
                  disabled={registerDeviceMut.isPending || activeId == null}
                  onClick={() =>
                    registerDeviceMut.mutate({
                      id: activeId!,
                      linkMode: 'PROMPT',
                      label: deviceLabelDraft.trim() || undefined,
                    })
                  }
                  className="rounded-xl bg-cyan-500/10 px-3.5 py-3 text-left ring-1 ring-inset ring-cyan-400/25 hover:bg-cyan-500/15 disabled:opacity-40"
                >
                  <div className="flex items-center gap-2 text-sm font-medium text-cyan-100">
                    <Link2 className="h-4 w-4" />
                    提示词连接
                  </div>
                  <p className="mt-1 text-[10px] leading-relaxed text-white/40">
                    生成可复制提示词，粘贴给 Cursor / Codex / 脚本 worker（旧接入方式）。
                  </p>
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {clientHint &&
        createPortal(
          <div
            className="fixed inset-0 z-[80] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm"
            onClick={() => setClientHint(null)}
          >
            <div
              className="w-full max-w-md rounded-2xl bg-[#14151a] p-5 ring-1 ring-white/10 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="text-base font-semibold text-white flex items-center gap-2">
                  <Monitor className="h-5 w-5 text-emerald-300" />
                  客户端连接指引
                </div>
                <button
                  type="button"
                  onClick={() => setClientHint(null)}
                  className="rounded-lg p-1.5 text-white/40 hover:bg-white/5"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-white/65 whitespace-pre-wrap">{clientHint}</p>
              <button
                type="button"
                onClick={() => setClientHint(null)}
                className="mt-4 w-full rounded-xl bg-cyan-500/85 py-2.5 text-sm font-medium text-black"
              >
                知道了
              </button>
            </div>
          </div>,
          document.body,
        )}

      {bridgeBundle &&
        createPortal(
          <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm">
            <div className="w-full max-w-2xl rounded-2xl bg-[#14151a] p-5 ring-1 ring-white/10 shadow-2xl">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-base font-semibold text-white">
                    提示词接入 · {bridgeBundle.agentName}
                  </div>
                  <p className="mt-1 text-[11px] text-white/40">
                    粘贴给本机 Cursor / Codex。须保持 worker 常驻；完成任务后继续轮询。
                    {bridgeBundle.deviceKey ? ` 设备 ID：${bridgeBundle.deviceKey}` : ''}
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


