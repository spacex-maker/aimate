import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
// qrcode 动态 import 用于群二维码
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  CalendarDays,
  Copy,
  Image as ImageIcon,
  Link2,
  Paperclip,
  Quote,
  Settings,
  UserPlus,
  Volume2,
  FileText,
  Video,
  X,
  Brain,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import {
  groupsApi,
  type GroupMessage,
  type GroupMessageReplyTo,
  type MediaKind,
} from '../api/groups'
import { multiAgentApi } from '../api/multiAgent'
import { useAuth } from '../hooks/useAuth'
import { useGroupSocket, type GroupWsEvent } from '../hooks/useGroupSocket'
import { AgentInputBox, type MentionCandidate } from '../components/agent/AgentInputBox'
import { AgentAvatar, AgentBubbleHeader } from '../components/agent/AgentAvatar'
import { GroupAgentContextRing } from '../components/group/GroupAgentContextRing'
import { GroupMembersPanel } from '../components/group/GroupMembersPanel'

const MEDIA_TABS: { kind: MediaKind; label: string; icon: typeof ImageIcon }[] = [
  { kind: 'IMAGE', label: '图片', icon: ImageIcon },
  { kind: 'VIDEO', label: '视频', icon: Video },
  { kind: 'FILE', label: '文件', icon: FileText },
  { kind: 'LINK', label: '链接', icon: Link2 },
  { kind: 'AUDIO', label: '音频', icon: Volume2 },
]

function formatMessageTime(raw?: string | null): string {
  if (!raw) return ''
  const d = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return `${hh}:${mm}`
  return `${d.getMonth() + 1}/${d.getDate()} ${hh}:${mm}`
}

export function GroupChatPage() {
  const { publicId = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [messages, setMessages] = useState<GroupMessage[]>([])
  const [draft, setDraft] = useState('')
  const [quoting, setQuoting] = useState<GroupMessage | null>(null)
  const [highlightId, setHighlightId] = useState<number | null>(null)
  const [panel, setPanel] = useState<'none' | 'settings' | 'invite' | 'media' | 'calendar'>('none')
  const [agentStatus, setAgentStatus] = useState<Record<number, string>>({})
  /** live 思考过程（.partial / CDP 统一一条） */
  const [liveThinking, setLiveThinking] = useState<
    Record<number, { content?: string; taskId?: string }>
  >({})
  const bottomRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const jumpToMessage = useCallback((id: number) => {
    const el = document.getElementById(`gm-${id}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      setHighlightId(id)
      if (highlightTimer.current) clearTimeout(highlightTimer.current)
      highlightTimer.current = setTimeout(() => setHighlightId(null), 1600)
    } else {
      toast('原消息不在当前加载范围', { icon: '↑' })
    }
  }, [])

  const { data: detail, refetch: refetchDetail } = useQuery({
    queryKey: ['group', publicId],
    queryFn: () => groupsApi.detail(publicId),
    enabled: !!publicId,
  })

  const loadMessages = useCallback(async () => {
    if (!publicId) return
    const list = await groupsApi.messages(publicId, { limit: 80 })
    setMessages(list)
  }, [publicId])

  useEffect(() => {
    loadMessages().catch((e: Error) => toast.error(e.message))
  }, [loadMessages])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length, liveThinking])

  const onWs = useCallback(
    (ev: GroupWsEvent) => {
      if (ev.type === 'MESSAGE') {
        const msg = ev.payload as GroupMessage
        setMessages((prev) => {
          const idx = prev.findIndex((m) => m.id === msg.id)
          if (idx >= 0) {
            const next = [...prev]
            next[idx] = { ...prev[idx], ...msg }
            return next
          }
          return [...prev, msg]
        })
      } else if (ev.type === 'READ') {
        const p = ev.payload as {
          messageId?: number
          upToMessageId?: number
          reader?: NonNullable<GroupMessage['reads']>[number]
        }
        if (p.reader) {
          setMessages((prev) =>
            prev.map((m) => {
              const hit =
                (p.messageId != null && m.id === p.messageId) ||
                (p.upToMessageId != null && m.id <= p.upToMessageId)
              if (!hit) return m
              const reads = m.reads ?? []
              const idx = reads.findIndex((r) => r.memberId === p.reader!.memberId)
              if (idx >= 0) {
                const next = [...reads]
                next[idx] = { ...next[idx], ...p.reader! }
                return { ...m, reads: next }
              }
              return { ...m, reads: [...reads, p.reader!] }
            }),
          )
        }
      } else if (ev.type === 'MEMBER') {
        refetchDetail()
      } else if (ev.type === 'AGENT_STATUS') {
        const p = ev.payload as { memberId: number; status: string }
        setAgentStatus((s) => ({ ...s, [p.memberId]: p.status }))
      } else if (ev.type === 'AGENT_THINKING') {
        const p = ev.payload as {
          memberId?: number
          content?: string
          taskId?: string
          messageId?: number
          done?: boolean
        }
        const memberId = p.memberId
        if (memberId == null) return
        if (p.done) {
          setLiveThinking((prev) => {
            const next = { ...prev }
            delete next[memberId]
            return next
          })
          if (p.messageId != null && p.content) {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === p.messageId ? { ...m, thinking: p.content } : m,
              ),
            )
          }
          return
        }
        if (p.content != null) {
          setLiveThinking((prev) => ({
            ...prev,
            [memberId]: { content: p.content || '', taskId: p.taskId },
          }))
        }
      }
    },
    [refetchDetail],
  )

  useGroupSocket(publicId || null, onWs, () => {
    loadMessages()
    refetchDetail()
  })

  const sendMut = useMutation({
    mutationFn: (opts?: { images?: string[] }) => {
      const content = draft.trim()
      const images = opts?.images ?? []
      const attachments = images.map((url) => ({ kind: 'IMAGE', url }))
      return groupsApi.send(publicId, {
        content: content || undefined,
        replyToId: quoting?.id,
        attachments: attachments.length ? attachments : undefined,
      })
    },
    onSuccess: (msg) => {
      setDraft('')
      setQuoting(null)
      setMessages((prev) => (prev.some((m) => m.id === msg.id) ? prev : [...prev, msg]))
      groupsApi.read(publicId, msg.id).catch(() => {})
    },
    onError: (e: Error) => {
      console.error('[groupSend]', e)
      toast.error(e.message)
    },
  })

  useEffect(() => {
    if (!publicId || messages.length === 0) return
    const last = messages[messages.length - 1]
    groupsApi.read(publicId, last.id).catch(() => {})
  }, [publicId, messages.length])

  const thinkingAgents = useMemo(() => {
    return Object.entries(agentStatus)
      .filter(([, s]) => s === 'thinking')
      .map(([id]) => Number(id))
  }, [agentStatus])

  const mentionCandidates = useMemo<MentionCandidate[]>(() => {
    if (!detail?.members) return []
    // Agent 优先，便于点名触发回复；再列其他用户（不含自己）
    const agents = detail.members
      .filter((m) => m.memberType === 'AGENT' && m.name)
      .map((m) => {
        const career = (m.careerName || '').trim()
        const kindHint = m.agentKind === 'SECRETARY' ? '秘书' : null
        const sub =
          career ||
          (m.displayTitle && m.displayTitle !== m.name ? m.displayTitle : null) ||
          kindHint ||
          'Agent'
        return {
          id: `agent-${m.id}`,
          label: m.name!,
          kind: 'AGENT' as const,
          sublabel: sub,
          gender: m.gender,
          genderLabel: m.genderLabel,
          avatarUrl: m.avatarUrl,
        }
      })
    const users = detail.members
      .filter((m) => m.memberType === 'USER' && m.userId !== user?.userId && m.name)
      .map((m) => ({
        id: `user-${m.id}`,
        label: m.name!,
        kind: 'USER' as const,
        sublabel: m.username || (m.role === 'OWNER' ? '群主' : m.role === 'ADMIN' ? '管理员' : '成员'),
        avatarUrl: m.avatarUrl,
      }))
    return [...agents, ...users]
  }, [detail?.members, user?.userId])

  if (!detail) {
    return <div className="h-full flex items-center justify-center text-white/40 text-sm">加载群组…</div>
  }

  return (
    <div className="h-full flex flex-col min-h-0">
      {/* 与 SessionHeader 同一套顶栏节奏 */}
      <header className="flex-shrink-0 border-b border-white/10 px-6 py-4 flex items-center gap-4">
        <button
          type="button"
          onClick={() => navigate('/app')}
          className="text-white/30 hover:text-white/70 transition-colors"
          title="返回控制台"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div className="min-w-0 flex-1">
          <div className="text-sm text-white/70 truncate">
            {detail.displayTitle || detail.myRemark || detail.title}
          </div>
          <div className="text-xs text-white/30 mt-0.5 truncate">
            {detail.members?.length ?? 0} 成员
            {thinkingAgents.length > 0 && (
              <span className="text-amber-300/80"> · Agent 思考中…</span>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          <button type="button" title="日历" className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors" onClick={() => setPanel('calendar')}>
            <CalendarDays className="w-4 h-4" />
          </button>
          <button type="button" title="媒体" className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors" onClick={() => setPanel('media')}>
            <ImageIcon className="w-4 h-4" />
          </button>
          {(detail.canInvite !== false) && (
          <button type="button" title="邀请成员 / 我的 Agent" className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors" onClick={() => setPanel('invite')}>
            <UserPlus className="w-4 h-4" />
          </button>
          )}
          <button type="button" title="设置" className="p-2 text-white/30 hover:text-white/60 hover:bg-white/5 rounded-lg transition-colors" onClick={() => setPanel('settings')}>
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* 与 ThinkingStream 同一列宽 / 底部留白（给固定输入框） */}
      <div ref={listRef} className="flex-1 min-h-0 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-4 pt-6 pb-40 space-y-6 min-w-0">
          {messages.length === 0 ? (
            <div className="flex items-center justify-center py-16">
              <p className="text-white/25 text-sm">发送第一条消息开始群聊</p>
            </div>
          ) : (
            messages.map((m) => (
              <MessageBubble
                key={m.id}
                msg={m}
                publicId={publicId}
                myUserId={user?.userId}
                highlighted={highlightId === m.id}
                onQuote={() => setQuoting(m)}
                onJumpTo={jumpToMessage}
              />
            ))
          )}
          {Object.entries(liveThinking).map(([memberId, th]) => {
            const mem = detail.members?.find((x) => x.id === Number(memberId))
            return (
              <LiveThinkingPanel
                key={`think-${memberId}`}
                title={mem?.displayTitle || mem?.name || 'Agent'}
                content={th.content}
              />
            )
          })}
          <div ref={bottomRef} />
        </div>
      </div>

      <AgentInputBox
        variant="plain"
        value={draft}
        onChange={setDraft}
        onSubmit={(opts) => {
          if ((!draft.trim() && !(opts?.images && opts.images.length > 0)) || sendMut.isPending) return
          sendMut.mutate({ images: opts?.images })
        }}
        mentionCandidates={mentionCandidates}
        placeholder="输入 @ 可点名成员或 Agent… · Enter 发送 · Shift+Enter 换行"
        hintText="群消息对所有成员可见；右键气泡可复制/引用；@Agent 可触发其回复"
        isPending={sendMut.isPending}
        pendingLabel="发送中"
        submitTitle="发送"
        disabled={!publicId}
        topSlot={
          quoting ? (
            <QuoteComposerBar
              msg={quoting}
              onClear={() => setQuoting(null)}
              onJump={() => jumpToMessage(quoting.id)}
            />
          ) : null
        }
      />

      {panel === 'settings' && (
        <GroupSettingsDrawer
          publicId={publicId}
          detail={detail}
          onClose={() => setPanel('none')}
          onInvite={() => setPanel('invite')}
          onChanged={() => {
            refetchDetail()
            qc.invalidateQueries({ queryKey: ['groups'] })
            qc.invalidateQueries({ queryKey: ['conversations'] })
          }}
        />
      )}
      {panel === 'invite' && (
        <InviteDrawer
          publicId={publicId}
          suggestedSecretaryId={detail.suggestedSecretaryAgentId}
          onClose={() => setPanel('none')}
          onChanged={() => refetchDetail()}
        />
      )}
      {panel === 'media' && (
        <MediaDrawer publicId={publicId} onClose={() => setPanel('none')} />
      )}
      {panel === 'calendar' && (
        <CalendarDrawer
          publicId={publicId}
          onClose={() => setPanel('none')}
          onPickDay={async (day) => {
            const list = await groupsApi.messages(publicId, { on: day })
            setMessages(list)
            setPanel('none')
          }}
        />
      )}
    </div>
  )
}

function QuoteBlock({
  reply,
  onJump,
  compact,
}: {
  reply: GroupMessageReplyTo
  onJump?: (id: number) => void
  compact?: boolean
}) {
  const deleted = !!reply.deleted
  return (
    <button
      type="button"
      disabled={deleted || !onJump}
      onClick={() => !deleted && onJump?.(reply.id)}
      className={clsx(
        'flex w-full items-start gap-2 rounded-lg bg-black/25 px-2.5 py-1.5 text-left transition',
        !deleted && onJump && 'hover:bg-black/35 cursor-pointer',
        compact ? 'mb-0' : 'mb-2',
      )}
    >
      <div className="mt-0.5 w-0.5 self-stretch rounded-full bg-cyan-400/50 flex-shrink-0" />
      {reply.imageUrl && !deleted && (
        <img src={reply.imageUrl} alt="" className="h-9 w-9 rounded object-cover flex-shrink-0 opacity-90" />
      )}
      <div className="min-w-0 flex-1">
        <div className="text-[10px] text-cyan-200/70 truncate">{deleted ? '引用' : reply.senderName || '消息'}</div>
        <div className={clsx('text-[11px] line-clamp-2', deleted ? 'text-white/30 italic' : 'text-white/45')}>
          {reply.contentPreview || (deleted ? '原消息已删除' : '…')}
        </div>
      </div>
    </button>
  )
}

function QuoteComposerBar({
  msg,
  onClear,
  onJump,
}: {
  msg: GroupMessage
  onClear: () => void
  onJump: () => void
}) {
  const preview =
    (msg.content || '').trim() ||
    (msg.attachments?.some((a) => a.kind === 'IMAGE') ? '[图片]' : '…')
  return (
    <div className="flex items-start gap-2 border-b border-white/[0.06] px-3 pt-2.5 pb-2">
      <button type="button" onClick={onJump} className="min-w-0 flex-1 flex items-start gap-2 text-left">
        <div className="mt-0.5 w-0.5 self-stretch rounded-full bg-cyan-400/55 flex-shrink-0" />
        {msg.senderAvatarUrl ? (
          <img src={msg.senderAvatarUrl} alt="" className="h-6 w-6 rounded-full object-cover flex-shrink-0" />
        ) : (
          <div className="h-6 w-6 rounded-full bg-white/10 flex items-center justify-center text-[10px] text-white/50 flex-shrink-0">
            {(msg.senderName || '?')[0]}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="text-[10px] text-cyan-200/75 truncate">引用 {msg.senderName || '消息'}</div>
          <div className="text-[11px] text-white/45 line-clamp-2">{preview}</div>
        </div>
      </button>
      <button
        type="button"
        onClick={onClear}
        className="rounded-md p-1 text-white/35 hover:bg-white/10 hover:text-white/70"
        aria-label="取消引用"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  )
}


/** 列表底部：统一「思考过程」实时区 */
function LiveThinkingPanel({ title, content }: { title: string; content?: string }) {
  const scrollRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [content])
  if (!content) return null
  return (
    <div className="w-full min-w-0 rounded-xl border border-amber-400/20 bg-amber-500/[0.06] overflow-hidden animate-fade-in">
      <div className="px-3 py-2 flex items-center gap-2 border-b border-white/5 bg-white/[0.03]">
        <Brain className="w-4 h-4 shrink-0 text-amber-200/70" />
        <span className="text-xs font-medium text-amber-100/80 truncate">{title} · 思考中</span>
        <span className="inline-block w-1.5 h-3 bg-amber-300/70 ml-auto animate-pulse rounded-sm" />
      </div>
      <div
        ref={scrollRef}
        className="max-h-48 overflow-y-auto px-3 py-2 text-[13px] text-white/65 leading-relaxed whitespace-pre-wrap break-words"
      >
        {content}
      </div>
    </div>
  )
}

/** Settled bubble: Cursor thinking only (never duplicate final reply) */
function MessageThinkingBlock({ label, text }: { label: string; text: string }) {
  const [open, setOpen] = useState(false)
  const preview = text.length > 120 ? `${text.slice(0, 120)}…` : text
  return (
    <div className="w-full min-w-0 mb-1 rounded-xl border border-white/10 bg-white/[0.04] overflow-hidden">
      <button
        type="button"
        className="w-full px-3 py-1.5 flex items-center gap-2 text-left hover:bg-white/[0.04] transition-colors"
        onClick={() => setOpen((v) => !v)}
      >
        <Brain className="w-3.5 h-3.5 shrink-0 text-white/45" />
        <span className="text-[11px] font-medium text-white/50">{label}</span>
        <span className="text-[11px] text-white/30 truncate flex-1">{open ? '' : preview}</span>
        {open ? (
          <ChevronUp className="w-3.5 h-3.5 text-white/30 shrink-0" />
        ) : (
          <ChevronDown className="w-3.5 h-3.5 text-white/30 shrink-0" />
        )}
      </button>
      {open && (
        <div className="px-3 pb-2.5 text-[12px] text-white/55 leading-relaxed whitespace-pre-wrap break-words max-h-56 overflow-y-auto border-t border-white/5 pt-2">
          {text}
        </div>
      )}
    </div>
  )
}


function MessageBubble({
  msg,
  publicId,
  myUserId,
  highlighted,
  onQuote,
  onJumpTo,
}: {
  msg: GroupMessage
  publicId: string
  myUserId?: number
  highlighted?: boolean
  onQuote: () => void
  onJumpTo: (id: number) => void
}) {
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null)
  const longPress = useRef<ReturnType<typeof setTimeout> | null>(null)

  if (msg.senderType === 'SYSTEM') {
    return (
      <div className="flex items-center gap-3 py-1">
        <div className="h-px flex-1 bg-white/5" />
        <span className="text-[11px] text-white/25 text-center max-w-[80%]">{msg.content}</span>
        <div className="h-px flex-1 bg-white/5" />
      </div>
    )
  }
  const mine = msg.senderType === 'USER' && msg.senderUserId === myUserId
  const isAgent = msg.senderType === 'AGENT'

  const copyText = async () => {
    const text = (msg.content || '').trim()
    if (!text) {
      toast.error('无可复制文本')
      return
    }
    try {
      await navigator.clipboard.writeText(text)
      toast.success('已复制')
    } catch {
      toast.error('复制失败')
    }
  }

  const openMenu = (x: number, y: number) => {
    setMenu({ x: Math.min(x, window.innerWidth - 140), y: Math.min(y, window.innerHeight - 100) })
  }

  return (
    <div
      id={`gm-${msg.id}`}
      className={clsx(
        'group min-w-0 rounded-xl transition-colors duration-500',
        mine ? 'flex justify-end' : 'flex justify-start',
        highlighted && 'bg-cyan-500/10 ring-1 ring-cyan-400/25',
      )}
    >
      <div
        className={clsx(
          'min-w-0 w-full flex flex-col gap-1.5 max-w-[85%]',
          mine ? 'items-end' : 'items-start',
        )}
      >
        {!mine && (
          isAgent ? (
            <AgentBubbleHeader
              agent={{
                id: msg.senderAgentId,
                name: msg.senderName,
                originalName: msg.senderOriginalName,
                careerName: msg.senderCareerName,
                avatarUrl: msg.senderAvatarUrl,
                externalAgentKey: msg.senderName,
              }}
              timeLabel={formatMessageTime(msg.createTime) || null}
              avatar={
                msg.senderAgentId != null && publicId ? (
                  <GroupAgentContextRing
                    publicId={publicId}
                    userAgentId={msg.senderAgentId}
                    size={40}
                  >
                    <AgentAvatar
                      agent={{
                        id: msg.senderAgentId,
                        name: msg.senderName,
                        originalName: msg.senderOriginalName,
                        careerName: msg.senderCareerName,
                        avatarUrl: msg.senderAvatarUrl,
                        externalAgentKey: msg.senderName,
                      }}
                      size={32}
                      className="h-full w-full"
                    />
                  </GroupAgentContextRing>
                ) : undefined
              }
            />
          ) : (
            <div className="flex items-center gap-2 px-0.5 mb-1">
              <div className="w-[22px] h-[22px] rounded-full bg-white/10 overflow-hidden flex-shrink-0">
                {msg.senderAvatarUrl ? (
                  <img src={msg.senderAvatarUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <span className="text-[10px] flex items-center justify-center h-full text-white/50">
                    {(msg.senderName || '?')[0]}
                  </span>
                )}
              </div>
              <span className="text-xs font-medium text-white/65 truncate max-w-[14rem]">
                {msg.senderName || '用户'}
              </span>
            </div>
          )
        )}
        {!mine && isAgent && msg.thinking ? (
          <MessageThinkingBlock label="思考过程" text={msg.thinking} />
        ) : null}
        <div
          className={clsx(
            'relative min-w-0 max-w-full rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap break-words',
            mine
              ? 'rounded-tr-md bg-white/10 text-white/95'
              : 'rounded-tl-md bg-white/5 text-white/80',
          )}
          onContextMenu={(e) => {
            e.preventDefault()
            openMenu(e.clientX, e.clientY)
          }}
          onTouchStart={(e) => {
            const t = e.touches[0]
            longPress.current = setTimeout(() => openMenu(t.clientX, t.clientY), 480)
          }}
          onTouchEnd={() => {
            if (longPress.current) clearTimeout(longPress.current)
          }}
          onTouchMove={() => {
            if (longPress.current) clearTimeout(longPress.current)
          }}
        >
          {msg.replyTo && <QuoteBlock reply={msg.replyTo} onJump={onJumpTo} />}
          {msg.content}
          {msg.attachments && msg.attachments.length > 0 && (
            <div className="mt-2 space-y-1">
              {msg.attachments.map((a) =>
                a.kind === 'IMAGE' ? (
                  <img
                    key={a.id}
                    src={a.url}
                    alt=""
                    className="max-h-48 max-w-full rounded-lg object-contain"
                  />
                ) : (
                  <a
                    key={a.id}
                    href={a.url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-xs underline opacity-80"
                  >
                    <Paperclip className="w-3 h-3" />
                    {a.title || a.url}
                  </a>
                ),
              )}
            </div>
          )}
          {/* 桌面悬停小工具条 */}
          <div
            className={clsx(
              'absolute -top-3 opacity-0 group-hover:opacity-100 transition-opacity',
              mine ? 'left-0' : 'right-0',
            )}
          >
            <div className="flex overflow-hidden rounded-lg bg-[#1c1c1c] shadow-lg ring-1 ring-inset ring-white/[0.06]">
              <button
                type="button"
                className="p-1.5 text-white/50 hover:bg-white/10 hover:text-white/80"
                title="复制"
                onClick={() => void copyText()}
              >
                <Copy className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                className="p-1.5 text-white/50 hover:bg-white/10 hover:text-white/80"
                title="引用"
                onClick={onQuote}
              >
                <Quote className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </div>
        {menu && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setMenu(null)} onContextMenu={(e) => { e.preventDefault(); setMenu(null) }} />
            <div
              className="fixed z-50 min-w-[7.5rem] overflow-hidden rounded-lg bg-[#1c1c1c] py-1 shadow-xl ring-1 ring-inset ring-white/[0.06]"
              style={{ left: menu.x, top: menu.y }}
            >
              <button
                type="button"
                className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs text-white/75 hover:bg-white/[0.06]"
                onClick={() => {
                  setMenu(null)
                  void copyText()
                }}
              >
                <Copy className="h-3.5 w-3.5" /> 复制
              </button>
              <button
                type="button"
                className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs text-white/75 hover:bg-white/[0.06]"
                onClick={() => {
                  setMenu(null)
                  onQuote()
                }}
              >
                <Quote className="h-3.5 w-3.5" /> 引用
              </button>
            </div>
          </>
        )}
        {(() => {
          const readers = (msg.reads ?? []).filter((r) => {
            if (msg.senderType === 'USER' && r.userId != null && r.userId === msg.senderUserId) return false
            if (msg.senderType === 'AGENT' && r.userAgentId != null && r.userAgentId === msg.senderAgentId) return false
            return true
          })
          if (readers.length === 0) return null
          return (
          <div className="flex items-center gap-0.5 px-0.5">
            {readers.slice(0, 8).map((r) => (
              <div
                key={r.memberId}
                title={readReceiptTooltip(r)}
                className="group/read relative w-4 h-4 -ml-0.5 first:ml-0 cursor-default"
              >
                <div className="w-4 h-4 rounded-full border border-[#0d0d0d] bg-white/20 overflow-hidden">
                  {r.avatarUrl ? (
                    <img src={r.avatarUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-[8px] flex items-center justify-center h-full text-white/70">
                      {(r.name || '?')[0]}
                    </span>
                  )}
                </div>
                <span className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-1.5 -translate-x-1/2 whitespace-nowrap rounded-md bg-black/90 px-2 py-1 text-[10px] text-white/90 opacity-0 shadow-lg transition-opacity group-hover/read:opacity-100">
                  {readReceiptTooltip(r)}
                </span>
              </div>
            ))}
            {readers.length > 8 && (
              <span className="text-[10px] text-white/35 ml-1">+{readers.length - 8}</span>
            )}
          </div>
          )
        })()}
      </div>
    </div>
  )
}

function GroupSettingsDrawer({
  publicId,
  detail,
  onClose,
  onChanged,
  onInvite,
}: {
  publicId: string
  detail: NonNullable<Awaited<ReturnType<typeof groupsApi.detail>>>
  onClose: () => void
  onChanged: () => void
  onInvite: () => void
}) {
  const [title, setTitle] = useState(detail.title)
  const [remark, setRemark] = useState(detail.myRemark || '')
  const [nickname, setNickname] = useState(detail.myNickname || '')
  const [announcement, setAnnouncement] = useState(detail.announcement || '')
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)
  const [invitePath, setInvitePath] = useState('')
  const [settingsTab, setSettingsTab] = useState<'profile' | 'members'>('profile')
  const canManage = detail.myRole === 'OWNER' || detail.myRole === 'ADMIN'

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const info = await groupsApi.invite(publicId)
        if (cancelled) return
        setInvitePath(info.invitePath)
        const full = `${window.location.origin}${info.invitePath}`
        const QR = await import('qrcode')
        const url = await QR.toDataURL(full, { width: 200, margin: 1, color: { dark: '#111111', light: '#ffffff' } })
        if (!cancelled) setQrDataUrl(url)
      } catch {
        /* ignore */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [publicId])

  return (
    <Drawer title="群设置" onClose={onClose}>
      <div className="flex gap-1 mb-4 p-0.5 rounded-lg bg-white/[0.04]">
        {(
          [
            { key: 'profile' as const, label: '资料' },
            { key: 'members' as const, label: `成员管理 (${detail.members.length})` },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            className={clsx(
              'flex-1 rounded-md py-1.5 text-xs transition',
              settingsTab === t.key ? 'bg-white/12 text-white' : 'text-white/45 hover:text-white/70',
            )}
            onClick={() => setSettingsTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {settingsTab === 'profile' && (
        <>
      <label className="block text-xs text-white/50 mb-1">群聊名称{canManage ? '' : '（仅管理员可改）'}</label>
      <div className="flex gap-2 mb-3">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          disabled={!canManage}
          className="flex-1 rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm disabled:opacity-50"
        />
        {canManage && (
          <button
            type="button"
            className="rounded-lg bg-white/10 px-3 text-sm hover:bg-white/15"
            onClick={async () => {
              try {
                await groupsApi.patch(publicId, { title: title.trim() })
                toast.success('群名已更新')
                onChanged()
              } catch (e) {
                toast.error((e as Error).message)
              }
            }}
          >
            保存
          </button>
        )}
      </div>

      <label className="block text-xs text-white/50 mb-1">群备注（仅自己可见，会话列表优先显示）</label>
      <div className="flex gap-2 mb-3">
        <input
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="例如：项目周会"
          className="flex-1 rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm"
        />
        <button
          type="button"
          className="rounded-lg bg-white/10 px-3 text-sm hover:bg-white/15"
          onClick={async () => {
            try {
              await groupsApi.patch(publicId, { remark })
              toast.success('备注已保存')
              onChanged()
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          保存
        </button>
      </div>

      <label className="block text-xs text-white/50 mb-1">我在本群的昵称</label>
      <div className="flex gap-2 mb-3">
        <input
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="不填则用账号名"
          className="flex-1 rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm"
        />
        <button
          type="button"
          className="rounded-lg bg-white/10 px-3 text-sm hover:bg-white/15"
          onClick={async () => {
            try {
              await groupsApi.patch(publicId, { nickname })
              toast.success('昵称已保存')
              onChanged()
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          保存
        </button>
      </div>

      <label className="block text-xs text-white/50 mb-1">群公告{canManage ? '' : '（仅管理员可改）'}</label>
      <textarea
        value={announcement}
        onChange={(e) => setAnnouncement(e.target.value)}
        disabled={!canManage}
        rows={3}
        className="w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm mb-2 disabled:opacity-50"
        placeholder="填写群公告…"
      />
      {canManage && (
        <button
          type="button"
          className="mb-4 rounded-lg bg-white/10 px-3 py-1.5 text-sm hover:bg-white/15"
          onClick={async () => {
            try {
              await groupsApi.patch(publicId, { announcement })
              toast.success('公告已更新')
              onChanged()
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          保存公告
        </button>
      )}

      <h3 className="text-xs font-medium text-white/50 mb-2 mt-2">群二维码</h3>
      <div className="rounded-xl border border-white/10 bg-white/[0.03] p-3 mb-4 flex flex-col items-center gap-2">
        {qrDataUrl ? (
          <img src={qrDataUrl} alt="群二维码" className="w-40 h-40 rounded-lg bg-white" />
        ) : (
          <div className="w-40 h-40 rounded-lg bg-white/5 flex items-center justify-center text-xs text-white/35">
            生成中…
          </div>
        )}
        <p className="text-[11px] text-white/40 text-center break-all px-2">
          {invitePath ? `${window.location.origin}${invitePath}` : ''}
        </p>
        {canManage && (
          <button
            type="button"
            className="text-xs text-blue-300/80 hover:text-blue-200"
            onClick={async () => {
              try {
                const info = await groupsApi.refreshInvite(publicId)
                setInvitePath(info.invitePath)
                const full = `${window.location.origin}${info.invitePath}`
                const QR = await import('qrcode')
                setQrDataUrl(await QR.toDataURL(full, { width: 200, margin: 1 }))
                toast.success('已刷新邀请码')
              } catch (e) {
                toast.error((e as Error).message)
              }
            }}
          >
            重置二维码
          </button>
        )}
      </div>

      <div className="flex gap-2 mb-5">
        <button
          type="button"
          className="flex-1 rounded-lg border border-white/15 py-2 text-xs hover:bg-white/5"
          onClick={async () => {
            try {
              await groupsApi.pin(publicId, !detail.pinned)
              toast.success(detail.pinned ? '已取消置顶' : '已置顶')
              onChanged()
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          {detail.pinned ? '取消置顶' : '置顶会话'}
        </button>
        <button
          type="button"
          className="flex-1 rounded-lg border border-white/15 py-2 text-xs hover:bg-white/5"
          onClick={async () => {
            try {
              await groupsApi.archive(publicId, true)
              toast.success('已归档，会话区不再显示')
              onChanged()
              onClose()
              window.location.href = '/app'
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          归档
        </button>
      </div>

      {detail.myRole === 'OWNER' && (
        <button
          type="button"
          className="w-full mt-2 rounded-lg border border-red-500/40 text-red-300/90 py-2 text-sm hover:bg-red-500/10"
          onClick={async () => {
            if (!confirm('确定解散该群？')) return
            try {
              await groupsApi.dissolve(publicId)
              toast.success('群已解散')
              onChanged()
              onClose()
              window.location.href = '/app'
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          解散群组
        </button>
      )}
      {detail.myRole !== 'OWNER' && (
        <button
          type="button"
          className="w-full mt-2 rounded-lg border border-white/15 py-2 text-sm text-white/60 hover:bg-white/5"
          onClick={async () => {
            if (!detail.myMemberId) return
            try {
              await groupsApi.removeMember(publicId, detail.myMemberId)
              toast.success('已退群')
              window.location.href = '/app'
            } catch (e) {
              toast.error((e as Error).message)
            }
          }}
        >
          退出群组
        </button>
      )}
        </>
      )}

      {settingsTab === 'members' && (
        <GroupMembersPanel
          publicId={publicId}
          detail={detail}
          onChanged={onChanged}
          onInvite={() => {
            onClose()
            onInvite()
          }}
        />
      )}
    </Drawer>
  )
}

function InviteDrawer({
  publicId,
  suggestedSecretaryId,
  onClose,
  onChanged,
}: {
  publicId: string
  suggestedSecretaryId?: number | null
  onClose: () => void
  onChanged: () => void
}) {
  const { user } = useAuth()
  const [q, setQ] = useState('')
  const [pickedUsers, setPickedUsers] = useState<number[]>([])
  const [pickedAgents, setPickedAgents] = useState<number[]>([])
  const [includeSecretary, setIncludeSecretary] = useState(!!suggestedSecretaryId)

  const { data: search } = useQuery({
    queryKey: ['user-search', q],
    queryFn: () => groupsApi.searchUsers(q, 0, 20),
    enabled: q.trim().length >= 1,
  })

  const { data: agents = [] } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId,
  })

  return (
    <Drawer title="邀请成员 / Agent" onClose={onClose}>
      <p className="mb-3 text-[11px] text-white/40">
        可邀请其他用户，或把自己名下的 Agent 拉进本群。
      </p>
      <label className="block text-xs text-white/50 mb-1">搜索 AIMATEX 用户</label>
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="用户名 / 邮箱 / 昵称"
        className="w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm mb-2"
      />
      <ul className="max-h-32 overflow-auto space-y-1 mb-4">
        {(search?.items ?? []).map((u) => (
          <li key={u.id}>
            <label className="flex items-center gap-2 text-sm cursor-pointer py-1">
              <input
                type="checkbox"
                checked={pickedUsers.includes(u.id)}
                onChange={(e) =>
                  setPickedUsers((prev) =>
                    e.target.checked ? [...prev, u.id] : prev.filter((x) => x !== u.id),
                  )
                }
              />
              <span className="truncate">{u.displayName || u.username}</span>
              <span className="text-white/30 text-xs truncate">{u.email}</span>
            </label>
          </li>
        ))}
      </ul>

      <h3 className="text-xs text-white/50 mb-2">我的 Agent</h3>
      <ul className="max-h-40 overflow-auto space-y-1 mb-3">
        {agents
          .filter((a) => a.status === 'ACTIVE')
          .map((a) => (
            <li key={a.id}>
              <label className="flex items-center gap-2 text-sm cursor-pointer py-1">
                <input
                  type="checkbox"
                  checked={pickedAgents.includes(a.id)}
                  onChange={(e) =>
                    setPickedAgents((prev) =>
                      e.target.checked ? [...prev, a.id] : prev.filter((x) => x !== a.id),
                    )
                  }
                />
                <span>
                  {a.name}
                  {a.kind === 'SECRETARY' && <span className="text-white/35 text-xs ml-1">秘书</span>}
                </span>
              </label>
            </li>
          ))}
      </ul>

      <label className="flex items-center gap-2 text-sm text-white/70 mb-4 cursor-pointer">
        <input
          type="checkbox"
          checked={includeSecretary}
          onChange={(e) => setIncludeSecretary(e.target.checked)}
          disabled={!suggestedSecretaryId}
        />
        建议拉入默认秘书（可取消）
      </label>

      <button
        type="button"
        className="w-full rounded-lg bg-blue-600 hover:bg-blue-500 py-2 text-sm font-medium disabled:opacity-40"
        disabled={pickedUsers.length === 0 && pickedAgents.length === 0 && !includeSecretary}
        onClick={async () => {
          try {
            await groupsApi.addMembers(publicId, {
              userIds: pickedUsers,
              agentIds: pickedAgents,
              includeSecretary,
            })
            toast.success('已邀请')
            onChanged()
            onClose()
          } catch (e) {
            toast.error((e as Error).message)
          }
        }}
      >
        确认邀请
      </button>
    </Drawer>
  )
}

function MediaDrawer({ publicId, onClose }: { publicId: string; onClose: () => void }) {
  const [kind, setKind] = useState<MediaKind>('IMAGE')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useQuery({
    queryKey: ['group-media', publicId, kind, page],
    queryFn: () => groupsApi.media(publicId, kind, page, 20),
  })

  return (
    <Drawer title="媒体" onClose={onClose}>
      <div className="flex flex-wrap gap-1 mb-3">
        {MEDIA_TABS.map((t) => (
          <button
            key={t.kind}
            type="button"
            onClick={() => {
              setKind(t.kind)
              setPage(0)
            }}
            className={clsx(
              'inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs',
              kind === t.kind ? 'bg-blue-600/30 text-blue-200' : 'bg-white/5 text-white/50 hover:bg-white/10',
            )}
          >
            <t.icon className="w-3.5 h-3.5" />
            {t.label}
          </button>
        ))}
      </div>
      {isLoading ? (
        <div className="text-sm text-white/40 py-8 text-center">加载中…</div>
      ) : !data?.items.length ? (
        <div className="text-sm text-white/35 py-8 text-center">暂无内容</div>
      ) : (
        <ul className="space-y-2 max-h-[60vh] overflow-auto">
          {data.items.map((item) => (
            <li key={item.id} className="rounded-lg border border-white/10 p-2">
              {kind === 'IMAGE' ? (
                <img src={item.url} alt="" className="w-full rounded-md max-h-40 object-cover" />
              ) : (
                <a href={item.url} target="_blank" rel="noreferrer" className="text-sm text-blue-300 break-all">
                  {item.title || item.url}
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
      {data && data.totalPages > 1 && (
        <div className="flex justify-between mt-3 text-xs text-white/50">
          <button type="button" disabled={page <= 0} onClick={() => setPage((p) => p - 1)} className="disabled:opacity-30">
            上一页
          </button>
          <span>
            {page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="disabled:opacity-30"
          >
            下一页
          </button>
        </div>
      )}
    </Drawer>
  )
}

function CalendarDrawer({
  publicId,
  onClose,
  onPickDay,
}: {
  publicId: string
  onClose: () => void
  onPickDay: (day: string) => void
}) {
  const { data: days = [] } = useQuery({
    queryKey: ['group-days', publicId],
    queryFn: () => groupsApi.days(publicId, 180),
  })
  const daySet = useMemo(() => new Set(days), [days])
  const [cursor, setCursor] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })

  const cells = useMemo(() => {
    const y = cursor.getFullYear()
    const m = cursor.getMonth()
    const firstDow = new Date(y, m, 1).getDay()
    const daysInMonth = new Date(y, m + 1, 0).getDate()
    const out: { label: string; iso?: string; has?: boolean }[] = []
    for (let i = 0; i < firstDow; i++) out.push({ label: '' })
    for (let d = 1; d <= daysInMonth; d++) {
      const iso = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
      out.push({ label: String(d), iso, has: daySet.has(iso) })
    }
    return out
  }, [cursor, daySet])

  return (
    <Drawer title="消息日历" onClose={onClose}>
      <div className="flex items-center justify-between mb-3">
        <button
          type="button"
          className="text-sm text-white/50 hover:text-white"
          onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() - 1, 1))}
        >
          上月
        </button>
        <span className="text-sm">
          {cursor.getFullYear()}年{cursor.getMonth() + 1}月
        </span>
        <button
          type="button"
          className="text-sm text-white/50 hover:text-white"
          onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1))}
        >
          下月
        </button>
      </div>
      <div className="grid grid-cols-7 gap-1 text-center text-[11px] text-white/35 mb-1">
        {['日', '一', '二', '三', '四', '五', '六'].map((w) => (
          <div key={w}>{w}</div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-1">
        {cells.map((c, i) => (
          <button
            key={i}
            type="button"
            disabled={!c.iso}
            onClick={() => c.iso && onPickDay(c.iso)}
            className={clsx(
              'aspect-square rounded-lg text-sm flex items-center justify-center',
              !c.iso && 'invisible',
              c.has ? 'bg-blue-600/40 text-blue-100 font-medium' : 'text-white/50 hover:bg-white/5',
            )}
          >
            {c.label}
          </button>
        ))}
      </div>
      <p className="text-[11px] text-white/35 mt-3">高亮日期表示有消息，点击可查看当日</p>
    </Drawer>
  )
}

function Drawer({
  title,
  onClose,
  children,
}: {
  title: string
  onClose: () => void
  children: React.ReactNode
}) {
  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <button type="button" className="absolute inset-0 bg-black/50" aria-label="关闭" onClick={onClose} />
      <aside className="relative w-full max-w-md h-full bg-[#141414] border-l border-white/10 p-4 overflow-y-auto shadow-2xl">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold">{title}</h2>
          <button type="button" onClick={onClose} className="text-white/40 hover:text-white text-sm">
            关闭
          </button>
        </div>
        {children}
      </aside>
    </div>
  )
}

function readReceiptTooltip(r: NonNullable<GroupMessage['reads']>[number]) {
  const name = r.name || (r.memberType === 'AGENT' ? 'Agent' : '成员')
  if (r.memberType === 'AGENT') {
    const pts = r.pointsSpent ?? 0
    if (pts > 0) return `${name} · 已分析 · 消耗 ${pts} 积分`
    return `${name} · 已分析`
  }
  return `${name} · 已读`
}
