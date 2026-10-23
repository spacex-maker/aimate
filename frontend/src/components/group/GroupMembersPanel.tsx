import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, MoreHorizontal, UserPlus } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import {
  groupsApi,
  type AgentReplyMode,
  type GroupDetail,
  type LocalBridgeTaskStatus,
} from '../../api/groups'
import { AppSelect } from '../../ui/AppSelect'
import { useAuth } from '../../hooks/useAuth'

const REPLY_MODE_OPTIONS = [
  { value: 'SILENT', label: '静默（仅已读）' },
  { value: 'MENTION_ONLY', label: '仅 @ / 回复' },
  { value: 'ALWAYS', label: '总是参与' },
  { value: 'SMART', label: '智能相关' },
]

const TASK_STATUS_TABS: { value: '' | LocalBridgeTaskStatus; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '未领取' },
  { value: 'CLAIMED', label: '已领取' },
  { value: 'DONE', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
]

function roleLabel(role: string) {
  if (role === 'OWNER') return '群主'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

function MemberAvatar({ name, url, agent }: { name: string; url?: string | null; agent?: boolean }) {
  return (
    <div
      className={clsx(
        'flex h-10 w-10 flex-shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium',
        agent ? 'bg-cyan-500/15 text-cyan-100' : 'bg-white/[0.08] text-white/80',
      )}
    >
      {url ? (
        <img src={url} alt="" className="h-full w-full object-cover" />
      ) : (
        (name || '?').slice(0, 1)
      )}
    </div>
  )
}

type MoreMenuItem = {
  key: string
  label: string
  danger?: boolean
  onClick?: () => void
  /** Nested choices (e.g. reply habit modes); picking one closes the menu. */
  choices?: { value: string; label: string; active?: boolean; onPick: () => void }[]
}

function MoreMenu({ items }: { items: MoreMenuItem[] }) {
  const [open, setOpen] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) {
      setExpanded(null)
      return
    }
    const onDoc = (e: MouseEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [open])
  if (!items.length) return null
  return (
    <div ref={root} className="relative">
      <button
        type="button"
        className="rounded-lg p-1.5 text-white/35 hover:bg-white/10 hover:text-white/70"
        aria-label="更多操作"
        onClick={() => setOpen((v) => !v)}
      >
        <MoreHorizontal className="h-4 w-4" />
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-1 min-w-[10.5rem] overflow-hidden rounded-lg bg-[#1c1c1c] py-1 shadow-xl ring-1 ring-inset ring-white/[0.06]">
          {items.map((it) =>
            it.choices?.length ? (
              <div key={it.key}>
                <button
                  type="button"
                  className="flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left text-xs text-white/75 hover:bg-white/[0.06]"
                  onClick={() => setExpanded((k) => (k === it.key ? null : it.key))}
                >
                  <span>{it.label}</span>
                  <ChevronDown
                    className={clsx(
                      'h-3 w-3 text-white/35 transition',
                      expanded === it.key && 'rotate-180',
                    )}
                  />
                </button>
                {expanded === it.key && (
                  <div className="bg-black/30 py-0.5">
                    {it.choices.map((c) => (
                      <button
                        key={c.value}
                        type="button"
                        className={clsx(
                          'block w-full px-3 py-1.5 pl-4 text-left text-[11px] hover:bg-white/8',
                          c.active ? 'text-cyan-200/90' : 'text-white/55',
                        )}
                        onClick={() => {
                          setOpen(false)
                          c.onPick()
                        }}
                      >
                        {c.active ? '✓ ' : ''}
                        {c.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <button
                key={it.key}
                type="button"
                className={clsx(
                  'block w-full px-3 py-1.5 text-left text-xs hover:bg-white/8',
                  it.danger ? 'text-red-300/85' : 'text-white/75',
                )}
                onClick={() => {
                  setOpen(false)
                  it.onClick?.()
                }}
              >
                {it.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  )
}

function AgentTaskPool({
  publicId,
  userAgentId,
  agentName,
  canView,
}: {
  publicId: string
  userAgentId: number
  agentName: string
  canView: boolean
}) {
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState<'' | LocalBridgeTaskStatus>('')
  const [page, setPage] = useState(0)
  const q = useQuery({
    queryKey: ['group-agent-tasks', publicId, userAgentId, status, page],
    queryFn: () =>
      groupsApi.agentTasks(publicId, userAgentId, {
        status: status || undefined,
        page,
        size: 10,
      }),
    enabled: open && canView,
  })

  if (!canView) {
    return <p className="text-[11px] text-white/30">任务池仅主人或群管理可见</p>
  }

  const counts = q.data?.counts
  return (
    <div className="rounded-lg bg-black/25">
      <button
        type="button"
        className="flex w-full items-center justify-between gap-2 px-2.5 py-2 text-left"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="text-[11px] text-white/55">
          任务池 · {agentName}
          {counts && (
            <span className="ml-1.5 text-white/35">
              未领 {counts.PENDING ?? 0} · 已领 {counts.CLAIMED ?? 0} · 完成 {counts.DONE ?? 0}
              {(counts.FAILED ?? 0) > 0 && (
                <span className="text-red-300/70"> · 失败 {counts.FAILED}</span>
              )}
            </span>
          )}
        </span>
        <ChevronDown className={clsx('h-3.5 w-3.5 text-white/35 transition', open && 'rotate-180')} />
      </button>
      {open && (
        <div className="px-2 pb-2 pt-0.5">
          <div className="mb-2 flex flex-wrap gap-1">
            {TASK_STATUS_TABS.map((t) => (
              <button
                key={t.value || 'all'}
                type="button"
                className={clsx(
                  'rounded-md px-2 py-0.5 text-[10px]',
                  status === t.value
                    ? 'bg-white/12 text-white'
                    : 'bg-white/[0.04] text-white/40 hover:text-white/65',
                )}
                onClick={() => {
                  setStatus(t.value)
                  setPage(0)
                }}
              >
                {t.label}
                {t.value && counts?.[t.value] != null ? ` ${counts[t.value]}` : ''}
              </button>
            ))}
          </div>
          {q.isLoading ? (
            <p className="py-3 text-center text-[11px] text-white/35">加载中…</p>
          ) : !(q.data?.items.length) ? (
            <p className="py-3 text-center text-[11px] text-white/30">暂无任务</p>
          ) : (
            <ul className="max-h-48 space-y-1.5 overflow-auto">
              {q.data!.items.map((t) => (
                <li
                  key={t.id}
                  className="rounded-md bg-white/[0.04] px-2 py-1.5 text-[11px]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className={clsx(
                        'rounded px-1.5 py-0.5 text-[10px]',
                        t.status === 'FAILED' && 'bg-red-500/15 text-red-200/90',
                        t.status === 'DONE' && 'bg-emerald-500/15 text-emerald-200/80',
                        t.status === 'PENDING' && 'bg-amber-500/15 text-amber-100/80',
                        t.status === 'CLAIMED' && 'bg-sky-500/15 text-sky-100/80',
                        t.status === 'CANCELLED' && 'bg-white/8 text-white/45',
                      )}
                    >
                      {t.statusLabel}
                    </span>
                    <span className="font-mono text-[10px] text-white/25">{t.publicId}</span>
                  </div>
                  {t.instruction && (
                    <p className="mt-1 line-clamp-2 text-white/55">{t.instruction}</p>
                  )}
                  {t.status === 'FAILED' && t.errorMessage && (
                    <p className="mt-1 text-red-300/75">失败原因：{t.errorMessage}</p>
                  )}
                  {t.status === 'DONE' && t.resultPreview && (
                    <p className="mt-1 line-clamp-2 text-white/40">结果：{t.resultPreview}</p>
                  )}
                </li>
              ))}
            </ul>
          )}
          {(q.data?.totalPages ?? 0) > 1 && (
            <div className="mt-2 flex justify-between text-[10px] text-white/35">
              <button
                type="button"
                disabled={page <= 0}
                className="disabled:opacity-30"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                上一页
              </button>
              <span>
                {page + 1}/{q.data?.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= (q.data?.totalPages ?? 1)}
                className="disabled:opacity-30"
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export function GroupMembersPanel({
  publicId,
  detail,
  onChanged,
  onInvite,
}: {
  publicId: string
  detail: GroupDetail
  onChanged: () => void
  onInvite: () => void
}) {
  const { user } = useAuth()
  const [selectedAgents, setSelectedAgents] = useState<number[]>([])
  const [batchMode, setBatchMode] = useState<AgentReplyMode>('MENTION_ONLY')
  const [batchOpen, setBatchOpen] = useState(false)
  const canManage = detail.myRole === 'OWNER' || detail.myRole === 'ADMIN'
  const canInvite = detail.canInvite !== false

  const membersSorted = useMemo(() => {
    const rank = (r: string) => (r === 'OWNER' ? 0 : r === 'ADMIN' ? 1 : 2)
    return [...detail.members].sort((a, b) => {
      const d = rank(a.role) - rank(b.role)
      if (d !== 0) return d
      if (a.memberType !== b.memberType) return a.memberType === 'USER' ? -1 : 1
      return (a.name || '').localeCompare(b.name || '', 'zh')
    })
  }, [detail.members])

  const agents = detail.members.filter((m) => m.memberType === 'AGENT')

  const setReply = async (memberId: number, mode: AgentReplyMode) => {
    try {
      await groupsApi.setReplyMode(publicId, { agentMemberId: memberId, mode })
      onChanged()
    } catch (e) {
      toast.error((e as Error).message)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-start justify-between gap-2">
        <p className="text-[11px] leading-relaxed text-white/40">
          人类与 Agent 同等管理。Agent 显示归属用户；本地桥 Agent 可查看本群任务池（未领/已领/完成/失败/取消）。
        </p>
        {canInvite ? (
          <button
            type="button"
            className="inline-flex flex-shrink-0 items-center gap-1 rounded-lg bg-white/[0.06] px-2.5 py-1.5 text-[11px] text-white/70 hover:bg-white/10"
            onClick={onInvite}
          >
            <UserPlus className="h-3.5 w-3.5" />
            邀请
          </button>
        ) : (
          <span className="flex-shrink-0 text-[10px] text-white/30">邀请已关闭</span>
        )}
      </div>

      {canManage && (
        <label className="flex items-center justify-between gap-3 rounded-xl bg-white/[0.04] px-3 py-2 text-xs text-white/65">
          <span>
            允许普通成员邀请
            <span className="mt-0.5 block text-[10px] text-white/35">
              关闭后仅群主/管理员可邀请用户或 Agent，扫码入群也会暂停
            </span>
          </span>
          <input
            type="checkbox"
            className="accent-blue-500"
            checked={detail.membersInviteEnabled !== false}
            onChange={async (e) => {
              try {
                await groupsApi.patch(publicId, { membersInviteEnabled: e.target.checked })
                toast.success(e.target.checked ? '已开启成员邀请' : '已关闭成员邀请')
                onChanged()
              } catch (err) {
                toast.error((err as Error).message)
              }
            }}
          />
        </label>
      )}

      <ul className="space-y-2">
        {membersSorted.map((m) => {
          const isAgent = m.memberType === 'AGENT'
          const displayName = m.name || m.username || (isAgent ? 'Agent' : '成员')
          const isSelf = !isAgent && m.userId === user?.userId
          const ownsAgent = isAgent && m.ownerUserId != null && m.ownerUserId === user?.userId
          const canPromote = detail.myRole === 'OWNER' && m.role !== 'OWNER'
          const canRemove =
            m.role !== 'OWNER' &&
            (canManage || ownsAgent) &&
            !(m.role === 'ADMIN' && detail.myRole !== 'OWNER')
          const canEditReply = isAgent && (canManage || ownsAgent)
          const canViewTasks = isAgent && !!m.localBridge && (canManage || ownsAgent)
          const menuItems: MoreMenuItem[] = []
          if (canEditReply) {
            const cur = m.agentReplyMode || 'MENTION_ONLY'
            menuItems.push({
              key: 'reply',
              label: '回复习惯',
              choices: REPLY_MODE_OPTIONS.map((opt) => ({
                value: opt.value,
                label: opt.label,
                active: cur === opt.value,
                onPick: () => setReply(m.id, opt.value as AgentReplyMode),
              })),
            })
          }
          if (canPromote) {
            menuItems.push({
              key: 'role',
              label: m.role === 'ADMIN' ? '取消管理员' : '设为管理员',
              onClick: async () => {
                try {
                  if (m.role === 'ADMIN') await groupsApi.demoteAdmin(publicId, m.id)
                  else await groupsApi.promoteAdmin(publicId, m.id)
                  toast.success(m.role === 'ADMIN' ? '已取消管理员' : '已设为管理员')
                  onChanged()
                } catch (e) {
                  toast.error((e as Error).message)
                }
              },
            })
          }
          if (canRemove) {
            menuItems.push({
              key: 'remove',
              label: '移出群聊',
              danger: true,
              onClick: async () => {
                const who = isAgent ? `Agent「${displayName}」` : `「${displayName}」`
                if (!confirm(`确定将 ${who} 移出群聊？`)) return
                try {
                  await groupsApi.removeMember(publicId, m.id)
                  toast.success('已移出')
                  onChanged()
                } catch (e) {
                  toast.error((e as Error).message)
                }
              },
            })
          }

          return (
            <li
              key={m.id}
              className="rounded-xl bg-white/[0.04] p-3"
            >
              <div className="flex items-start gap-3">
                <MemberAvatar name={displayName} url={m.avatarUrl} agent={isAgent} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="truncate text-sm font-medium text-white/90">{displayName}</span>
                        {isSelf && <span className="text-[10px] text-white/30">我</span>}
                        <span className="rounded bg-white/[0.07] px-1.5 py-0.5 text-[10px] text-white/45">
                          {isAgent ? 'Agent' : '人类'}
                        </span>
                        <span className="rounded bg-white/[0.07] px-1.5 py-0.5 text-[10px] text-white/45">
                          {roleLabel(m.role)}
                        </span>
                        {isAgent && m.localBridge && (
                          <span className="rounded bg-cyan-500/12 px-1.5 py-0.5 text-[10px] text-cyan-200/85">
                            本机桥
                          </span>
                        )}
                      </div>
                      <p className="mt-0.5 truncate text-[11px] text-white/35">
                        {isAgent ? (
                          <>
                            归属：{m.ownerName || `用户#${m.ownerUserId ?? '?'}`}
                            {m.agentKind ? ` · ${m.agentKind}` : ''}
                          </>
                        ) : (
                          <>
                            {m.username ? `@${m.username}` : ''}
                            {m.nickname ? ` · 群昵称 ${m.nickname}` : ''}
                          </>
                        )}
                      </p>
                        {isAgent && (
                        <p className="mt-1 text-[10px] text-white/30">
                          回复：
                          {REPLY_MODE_OPTIONS.find((o) => o.value === (m.agentReplyMode || 'MENTION_ONLY'))
                            ?.label ?? m.agentReplyMode}
                          {m.localBridge && m.taskCounts && (
                            <>
                              {' '}
                              · 任务 未领 {m.taskCounts.PENDING ?? 0} / 已领 {m.taskCounts.CLAIMED ?? 0} /
                              完成 {m.taskCounts.DONE ?? 0}
                              {(m.taskCounts.FAILED ?? 0) > 0 && (
                                <span className="text-red-300/65"> / 失败 {m.taskCounts.FAILED}</span>
                              )}
                            </>
                          )}
                        </p>
                      )}
                    </div>
                    <div className="flex flex-shrink-0 items-center gap-1">
                      {canEditReply && batchOpen && (
                        <input
                          type="checkbox"
                          className="accent-blue-500"
                          checked={selectedAgents.includes(m.id)}
                          onChange={(e) => {
                            setSelectedAgents((prev) =>
                              e.target.checked ? [...prev, m.id] : prev.filter((x) => x !== m.id),
                            )
                          }}
                          title="批量选择"
                        />
                      )}
                      <MoreMenu items={menuItems} />
                    </div>
                  </div>

                  {isAgent && m.localBridge && m.userAgentId != null && (
                    <div className="mt-2">
                      <AgentTaskPool
                        publicId={publicId}
                        userAgentId={m.userAgentId}
                        agentName={displayName}
                        canView={canViewTasks}
                      />
                    </div>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      {agents.length > 0 && (canManage || agents.some((a) => a.ownerUserId === user?.userId)) && (
        <div className="rounded-xl bg-white/[0.04]">
          <button
            type="button"
            className="flex w-full items-center justify-between px-3 py-2 text-left text-xs text-white/55"
            onClick={() => setBatchOpen((v) => !v)}
          >
            <span>批量设置 Agent 回复习惯</span>
            <ChevronDown className={clsx('h-3.5 w-3.5 transition', batchOpen && 'rotate-180')} />
          </button>
          {batchOpen && (
            <div className="flex gap-2 px-3 pb-2.5 pt-0.5">
              <AppSelect
                className="flex-1"
                value={batchMode}
                options={REPLY_MODE_OPTIONS}
                onChange={(v) => setBatchMode(v as AgentReplyMode)}
              />
              <button
                type="button"
                disabled={selectedAgents.length === 0}
                className="rounded-lg bg-blue-600/80 px-3 py-2 text-xs hover:bg-blue-600 disabled:opacity-40"
                onClick={async () => {
                  try {
                    await groupsApi.setReplyMode(publicId, {
                      agentMemberIds: selectedAgents,
                      mode: batchMode,
                    })
                    toast.success('已批量更新')
                    setSelectedAgents([])
                    onChanged()
                  } catch (e) {
                    toast.error((e as Error).message)
                  }
                }}
              >
                应用 ({selectedAgents.length})
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
