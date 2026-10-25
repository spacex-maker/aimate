import { useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bot, Check, Search, UserPlus, Users, X } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { groupsApi, type GroupMember, type UserSearchItem } from '../../api/groups'
import { multiAgentApi, type UserAgentBrief } from '../../api/multiAgent'
import { useAuth } from '../../hooks/useAuth'

type Tab = 'people' | 'agents'

export function GroupInviteDrawer({
  publicId,
  members,
  suggestedSecretaryId,
  onClose,
  onChanged,
}: {
  publicId: string
  members: GroupMember[]
  suggestedSecretaryId?: number | null
  onClose: () => void
  onChanged: () => void
}) {
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('people')
  const [q, setQ] = useState('')
  const [pickedUsers, setPickedUsers] = useState<UserSearchItem[]>([])
  const [pickedAgents, setPickedAgents] = useState<UserAgentBrief[]>([])
  const [includeSecretary, setIncludeSecretary] = useState(!!suggestedSecretaryId)
  const [submitting, setSubmitting] = useState(false)

  const inGroupUserIds = useMemo(
    () => new Set(members.filter((m) => m.memberType === 'USER' && m.userId != null).map((m) => m.userId!)),
    [members],
  )
  const inGroupAgentIds = useMemo(
    () =>
      new Set(
        members.filter((m) => m.memberType === 'AGENT' && m.userAgentId != null).map((m) => m.userAgentId!),
      ),
    [members],
  )

  const { data: search, isFetching: searching } = useQuery({
    queryKey: ['user-search', q],
    queryFn: () => groupsApi.searchUsers(q, 0, 20),
    enabled: q.trim().length >= 1,
  })

  const { data: agents = [], isLoading: agentsLoading } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId,
  })

  const activeAgents = useMemo(
    () => agents.filter((a) => a.status === 'ACTIVE'),
    [agents],
  )
  const secretary = useMemo(
    () => activeAgents.find((a) => a.kind === 'SECRETARY' && a.id === suggestedSecretaryId),
    [activeAgents, suggestedSecretaryId],
  )

  const pickedUserIds = useMemo(() => new Set(pickedUsers.map((u) => u.id)), [pickedUsers])
  const pickedAgentIds = useMemo(() => new Set(pickedAgents.map((a) => a.id)), [pickedAgents])

  const totalCount =
    pickedUsers.length +
    pickedAgents.length +
    (includeSecretary && secretary && !inGroupAgentIds.has(secretary.id) && !pickedAgentIds.has(secretary.id)
      ? 1
      : 0)

  const toggleUser = (u: UserSearchItem) => {
    if (inGroupUserIds.has(u.id)) return
    setPickedUsers((prev) =>
      prev.some((x) => x.id === u.id) ? prev.filter((x) => x.id !== u.id) : [...prev, u],
    )
  }

  const toggleAgent = (a: UserAgentBrief) => {
    if (inGroupAgentIds.has(a.id)) return
    setPickedAgents((prev) =>
      prev.some((x) => x.id === a.id) ? prev.filter((x) => x.id !== a.id) : [...prev, a],
    )
    if (a.kind === 'SECRETARY' && a.id === suggestedSecretaryId) {
      setIncludeSecretary(false)
    }
  }

  const submit = async () => {
    if (totalCount === 0 || submitting) return
    setSubmitting(true)
    try {
      const agentIds = [...pickedAgentIds]
      let incSec = includeSecretary && !!secretary && !inGroupAgentIds.has(secretary.id)
      if (secretary && agentIds.includes(secretary.id)) {
        incSec = false
      }
      await groupsApi.addMembers(publicId, {
        userIds: [...pickedUserIds],
        agentIds,
        includeSecretary: incSec,
      })
      toast.success(totalCount === 1 ? '已加入群聊' : `已邀请 ${totalCount} 位`)
      onChanged()
      onClose()
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <button type="button" className="absolute inset-0 bg-black/55 backdrop-blur-[2px]" aria-label="关闭" onClick={onClose} />
      <aside className="relative flex h-full w-full max-w-md flex-col border-l border-white/[0.07] bg-[#121214] shadow-[0_0_80px_-20px_rgba(0,0,0,0.9)]">
        <header className="flex-shrink-0 border-b border-white/[0.06] px-5 pb-4 pt-5">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-cyan-500/15 text-cyan-200/90">
                  <UserPlus className="h-4 w-4" />
                </span>
                <h2 className="text-[15px] font-semibold tracking-tight text-white/95">邀请进群</h2>
              </div>
              <p className="mt-1.5 pl-10 text-[11px] leading-relaxed text-white/40">
                搜索 AIMATEX 用户，或从你的 Agent 里挑选同事入群。
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg p-1.5 text-white/35 hover:bg-white/[0.06] hover:text-white/70"
              aria-label="关闭"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="mt-4 flex gap-1 rounded-xl bg-white/[0.04] p-1">
            {(
              [
                { key: 'people' as const, label: '成员', icon: Users },
                { key: 'agents' as const, label: 'Agent', icon: Bot },
              ] as const
            ).map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setTab(t.key)}
                className={clsx(
                  'flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2 text-xs font-medium transition',
                  tab === t.key
                    ? 'bg-white/[0.1] text-white shadow-sm'
                    : 'text-white/40 hover:text-white/65',
                )}
              >
                <t.icon className="h-3.5 w-3.5" />
                {t.label}
              </button>
            ))}
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          {totalCount > 0 && (
            <div className="mb-4 flex flex-wrap gap-1.5">
              {pickedUsers.map((u) => (
                <SelectedChip
                  key={`u-${u.id}`}
                  label={u.displayName || u.username}
                  onRemove={() => setPickedUsers((prev) => prev.filter((x) => x.id !== u.id))}
                />
              ))}
              {pickedAgents.map((a) => (
                <SelectedChip
                  key={`a-${a.id}`}
                  label={a.name}
                  accent="cyan"
                  onRemove={() => setPickedAgents((prev) => prev.filter((x) => x.id !== a.id))}
                />
              ))}
              {includeSecretary &&
                secretary &&
                !pickedAgentIds.has(secretary.id) &&
                !inGroupAgentIds.has(secretary.id) && (
                  <SelectedChip
                    label={`${secretary.name}（建议）`}
                    accent="cyan"
                    onRemove={() => setIncludeSecretary(false)}
                  />
                )}
            </div>
          )}

          {tab === 'people' ? (
            <section>
              <div className="relative mb-3">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-white/30" />
                <input
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="搜索用户名、昵称或邮箱"
                  className="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] py-2.5 pl-9 pr-3 text-sm text-white/90 outline-none placeholder:text-white/25 focus:border-cyan-500/35 focus:bg-white/[0.06]"
                />
              </div>

              {q.trim().length < 1 ? (
                <EmptyHint text="输入关键词开始搜索用户" />
              ) : searching ? (
                <EmptyHint text="搜索中…" />
              ) : (search?.items ?? []).length === 0 ? (
                <EmptyHint text="没有匹配的用户" />
              ) : (
                <ul className="space-y-1.5">
                  {(search?.items ?? []).map((u) => {
                    const inGroup = inGroupUserIds.has(u.id)
                    const selected = pickedUserIds.has(u.id)
                    return (
                      <li key={u.id}>
                        <PickRow
                          selected={selected}
                          disabled={inGroup}
                          onClick={() => toggleUser(u)}
                          avatar={
                            <AvatarBubble
                              url={u.avatarUrl}
                              label={u.displayName || u.username}
                              tone="user"
                            />
                          }
                          title={u.displayName || u.username}
                          subtitle={u.email || u.username}
                          badge={inGroup ? '已在群里' : undefined}
                        />
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          ) : (
            <section className="space-y-4">
              {secretary && !inGroupAgentIds.has(secretary.id) && (
                <button
                  type="button"
                  onClick={() => {
                    if (pickedAgentIds.has(secretary.id)) {
                      toggleAgent(secretary)
                      return
                    }
                    setIncludeSecretary((v) => !v)
                  }}
                  className={clsx(
                    'flex w-full items-start gap-3 rounded-2xl border px-3.5 py-3 text-left transition',
                    includeSecretary || pickedAgentIds.has(secretary.id)
                      ? 'border-cyan-400/30 bg-cyan-500/[0.08]'
                      : 'border-white/[0.07] bg-white/[0.03] hover:bg-white/[0.05]',
                  )}
                >
                  <AvatarBubble url={secretary.avatarUrl} label={secretary.name} tone="agent" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-sm font-medium text-white/90">{secretary.name}</span>
                      <span className="rounded-md bg-cyan-500/15 px-1.5 py-0.5 text-[10px] text-cyan-200/85">
                        建议
                      </span>
                    </div>
                    <p className="mt-0.5 text-[11px] leading-relaxed text-white/40">
                      默认秘书：记上下文与群内事件，建议一并拉入。
                    </p>
                  </div>
                  <SelectMark on={includeSecretary || pickedAgentIds.has(secretary.id)} />
                </button>
              )}

              <div>
                <h3 className="mb-2 text-[11px] font-medium uppercase tracking-wide text-white/35">
                  我的 Agent
                </h3>
                {agentsLoading ? (
                  <EmptyHint text="加载中…" />
                ) : activeAgents.length === 0 ? (
                  <EmptyHint text="还没有可用的 Agent" />
                ) : (
                  <ul className="space-y-1.5">
                    {activeAgents.map((a) => {
                      const inGroup = inGroupAgentIds.has(a.id)
                      const selected =
                        pickedAgentIds.has(a.id) ||
                        (includeSecretary && a.id === suggestedSecretaryId && !inGroup)
                      return (
                        <li key={a.id}>
                          <PickRow
                            selected={selected && !inGroup}
                            disabled={inGroup}
                            onClick={() => toggleAgent(a)}
                            avatar={<AvatarBubble url={a.avatarUrl} label={a.name} tone="agent" />}
                            title={a.name}
                            subtitle={
                              a.kind === 'SECRETARY'
                                ? '秘书'
                                : a.localBridge
                                  ? '本机桥'
                                  : '专业同事'
                            }
                            badge={inGroup ? '已在群里' : a.kind === 'SECRETARY' ? '秘书' : undefined}
                          />
                        </li>
                      )
                    })}
                  </ul>
                )}
              </div>
            </section>
          )}
        </div>

        <footer className="flex-shrink-0 border-t border-white/[0.06] bg-[#121214]/95 px-5 py-3.5 backdrop-blur-md">
          <div className="mb-2.5 flex items-center justify-between text-[11px] text-white/40">
            <span>{totalCount > 0 ? `已选 ${totalCount} 位` : '尚未选择'}</span>
            {totalCount > 0 && (
              <button
                type="button"
                className="text-white/45 hover:text-white/70"
                onClick={() => {
                  setPickedUsers([])
                  setPickedAgents([])
                  setIncludeSecretary(false)
                }}
              >
                清空
              </button>
            )}
          </div>
          <button
            type="button"
            disabled={totalCount === 0 || submitting}
            onClick={() => void submit()}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-cyan-400/90 py-2.5 text-sm font-semibold text-black/85 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-35"
          >
            <UserPlus className="h-4 w-4" />
            {submitting ? '邀请中…' : totalCount > 0 ? `确认邀请（${totalCount}）` : '确认邀请'}
          </button>
        </footer>
      </aside>
    </div>
  )
}

function SelectedChip({
  label,
  onRemove,
  accent,
}: {
  label: string
  onRemove: () => void
  accent?: 'cyan'
}) {
  return (
    <span
      className={clsx(
        'inline-flex max-w-full items-center gap-1 rounded-full py-1 pl-2.5 pr-1 text-[11px]',
        accent === 'cyan'
          ? 'bg-cyan-500/15 text-cyan-100/90'
          : 'bg-white/[0.08] text-white/75',
      )}
    >
      <span className="truncate">{label}</span>
      <button
        type="button"
        onClick={onRemove}
        className="rounded-full p-0.5 hover:bg-black/25"
        aria-label={`移除 ${label}`}
      >
        <X className="h-3 w-3 opacity-70" />
      </button>
    </span>
  )
}

function EmptyHint({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-white/[0.08] px-4 py-10 text-center text-[12px] text-white/30">
      {text}
    </div>
  )
}

function AvatarBubble({
  url,
  label,
  tone,
}: {
  url?: string | null
  label: string
  tone: 'user' | 'agent'
}) {
  return (
    <div
      className={clsx(
        'flex h-10 w-10 flex-shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium',
        tone === 'agent' ? 'bg-cyan-500/15 text-cyan-100' : 'bg-white/[0.08] text-white/80',
      )}
    >
      {url ? (
        <img src={url} alt="" className="h-full w-full object-cover" />
      ) : (
        (label || '?').slice(0, 1)
      )}
    </div>
  )
}

function SelectMark({ on }: { on: boolean }) {
  return (
    <span
      className={clsx(
        'mt-1 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border transition',
        on
          ? 'border-cyan-300/80 bg-cyan-400 text-black'
          : 'border-white/20 bg-transparent text-transparent',
      )}
    >
      <Check className="h-3 w-3" strokeWidth={3} />
    </span>
  )
}

function PickRow({
  avatar,
  title,
  subtitle,
  badge,
  selected,
  disabled,
  onClick,
}: {
  avatar: ReactNode
  title: string
  subtitle?: string
  badge?: string
  selected: boolean
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={clsx(
        'flex w-full items-center gap-3 rounded-2xl border px-3 py-2.5 text-left transition',
        disabled
          ? 'cursor-default border-white/[0.04] bg-white/[0.02] opacity-55'
          : selected
            ? 'border-cyan-400/30 bg-cyan-500/[0.08]'
            : 'border-white/[0.06] bg-white/[0.03] hover:border-white/[0.1] hover:bg-white/[0.05]',
      )}
    >
      {avatar}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-medium text-white/90">{title}</span>
          {badge ? (
            <span className="flex-shrink-0 rounded-md bg-white/[0.07] px-1.5 py-0.5 text-[10px] text-white/45">
              {badge}
            </span>
          ) : null}
        </div>
        {subtitle ? <p className="mt-0.5 truncate text-[11px] text-white/35">{subtitle}</p> : null}
      </div>
      {!disabled && <SelectMark on={selected} />}
    </button>
  )
}
