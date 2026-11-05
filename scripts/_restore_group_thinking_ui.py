# -*- coding: utf-8 -*-
"""Re-apply group live thinking UI on clean GroupChatPage (UTF-8 safe)."""
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
t = p.read_text(encoding="utf-8")

# --- imports ---
if "Brain" not in t:
    t = t.replace(
        """  FileText,
  Video,
  X,
} from 'lucide-react'""",
        """  FileText,
  Video,
  X,
  Brain,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'""",
        1,
    )
    print("imports ok")

# --- state ---
if "liveThinking" not in t:
    t = t.replace(
        """  const [agentStatus, setAgentStatus] = useState<Record<number, string>>({})
  const bottomRef = useRef<HTMLDivElement>(null)""",
        """  const [agentStatus, setAgentStatus] = useState<Record<number, string>>({})
  /** live: thinking=Cursor; execution=.partial only while running */
  const [liveThinking, setLiveThinking] = useState<
    Record<number, { thinking?: string; execution?: string; taskId?: string }>
  >({})
  const bottomRef = useRef<HTMLDivElement>(null)""",
        1,
    )
    print("state ok")

# --- scroll when live updates ---
t = t.replace(
    """  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])""",
    """  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length, liveThinking])""",
    1,
)

# --- MESSAGE upsert ---
old_msg = """      if (ev.type === 'MESSAGE') {
        const msg = ev.payload as GroupMessage
        setMessages((prev) => {
          if (prev.some((m) => m.id === msg.id)) return prev
          return [...prev, msg]
        })
      }"""
new_msg = """      if (ev.type === 'MESSAGE') {
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
      }"""
if old_msg in t:
    t = t.replace(old_msg, new_msg, 1)
    print("message upsert ok")

# --- AGENT_THINKING ---
if "AGENT_THINKING" not in t:
    t = t.replace(
        """      } else if (ev.type === 'AGENT_STATUS') {
        const p = ev.payload as { memberId: number; status: string }
        setAgentStatus((s) => ({ ...s, [p.memberId]: p.status }))
      }
    },
    [refetchDetail],
  )""",
        """      } else if (ev.type === 'AGENT_STATUS') {
        const p = ev.payload as { memberId: number; status: string }
        setAgentStatus((s) => ({ ...s, [p.memberId]: p.status }))
      } else if (ev.type === 'AGENT_THINKING') {
        const p = ev.payload as {
          memberId?: number
          content?: string
          kind?: string
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
                m.id === p.messageId
                  ? { ...m, thinking: p.content, execution: undefined }
                  : m,
              ),
            )
          }
          return
        }
        if (p.content != null) {
          const kind = (p.kind || 'progress').toLowerCase()
          setLiveThinking((prev) => {
            const cur = prev[memberId] || {}
            const next = { ...cur, taskId: p.taskId }
            if (kind === 'thinking' || kind === 'thought' || kind === 'reasoning') {
              next.thinking = p.content || ''
            } else {
              next.execution = p.content || ''
            }
            return { ...prev, [memberId]: next }
          })
        }
      }
    },
    [refetchDetail],
  )""",
        1,
    )
    print("AGENT_THINKING ok")

# --- live panels in list ---
if "LiveThinkingPanel" not in t or "key={`think-" not in t:
    t = t.replace(
        """          )}
          <div ref={bottomRef} />
        </div>
      </div>

      <AgentInputBox""",
        """          )}
          {Object.entries(liveThinking).map(([memberId, th]) => {
            const mem = detail.members?.find((x) => x.id === Number(memberId))
            return (
              <LiveThinkingPanel
                key={`think-${memberId}`}
                title={mem?.displayTitle || mem?.name || 'Agent'}
                thinking={th.thinking}
                execution={th.execution}
              />
            )
          })}
          <div ref={bottomRef} />
        </div>
      </div>

      <AgentInputBox""",
        1,
    )
    print("live panels mounted")

# --- bubble thinking only (Cursor) ---
marker = """        {!mine && (
          isAgent ? (
            <AgentBubbleHeader"""
if "MessageThinkingBlock" not in t.split("function MessageBubble")[1][:2500]:
    # insert before content bubble div
    needle = """        )}
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
          }}"""
    insert = """        )}
        {!mine && isAgent && msg.thinking ? (
          <MessageThinkingBlock label="\u601d\u8003\u8fc7\u7a0b" text={msg.thinking} />
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
          }}"""
    if needle not in t:
        raise SystemExit("bubble insert point missing")
    t = t.replace(needle, insert, 1)
    print("bubble thinking block ok")

# --- helper components before MessageBubble ---
if "function LiveThinkingPanel" not in t:
    helpers = r'''
/** Live only: Cursor thinking + .partial progress (cleared on done) */
function LiveThinkingPanel({
  title,
  thinking,
  execution,
}: {
  title: string
  thinking?: string
  execution?: string
}) {
  const scrollRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [thinking, execution])
  if (!thinking && !execution) return null
  return (
    <div className="w-full min-w-0 rounded-xl border border-amber-400/20 bg-amber-500/[0.06] overflow-hidden animate-fade-in">
      <div className="px-3 py-2 flex items-center gap-2 border-b border-white/5 bg-white/[0.03]">
        <Brain className="w-4 h-4 shrink-0 text-amber-200/70" />
        <span className="text-xs font-medium text-amber-100/80 truncate">{title} · {'\u8fdb\u884c\u4e2d'}</span>
        <span className="inline-block w-1.5 h-3 bg-amber-300/70 ml-auto animate-pulse rounded-sm" />
      </div>
      <div ref={scrollRef} className="max-h-48 overflow-y-auto px-3 py-2 space-y-2">
        {thinking ? (
          <div>
            <div className="text-[11px] font-medium text-amber-100/70 mb-1">{'\u601d\u8003\u8fc7\u7a0b'}（Cursor）</div>
            <div className="text-[13px] text-white/65 leading-relaxed whitespace-pre-wrap break-words">{thinking}</div>
          </div>
        ) : null}
        {execution ? (
          <div>
            <div className="text-[11px] font-medium text-sky-200/70 mb-1">{'\u672c\u673a\u6267\u884c'}</div>
            <div className="text-[13px] text-white/55 leading-relaxed whitespace-pre-wrap break-words">{execution}</div>
          </div>
        ) : null}
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

'''
    # Fix the unicode in helpers - I used broken {'\u...'} inside JSX. Rewrite cleanly.
    helpers = """
/** Live only: Cursor thinking + .partial progress (cleared on done) */
function LiveThinkingPanel({
  title,
  thinking,
  execution,
}: {
  title: string
  thinking?: string
  execution?: string
}) {
  const scrollRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [thinking, execution])
  if (!thinking && !execution) return null
  return (
    <div className="w-full min-w-0 rounded-xl border border-amber-400/20 bg-amber-500/[0.06] overflow-hidden animate-fade-in">
      <div className="px-3 py-2 flex items-center gap-2 border-b border-white/5 bg-white/[0.03]">
        <Brain className="w-4 h-4 shrink-0 text-amber-200/70" />
        <span className="text-xs font-medium text-amber-100/80 truncate">{title} · """ + "\u8fdb\u884c\u4e2d" + """</span>
        <span className="inline-block w-1.5 h-3 bg-amber-300/70 ml-auto animate-pulse rounded-sm" />
      </div>
      <div ref={scrollRef} className="max-h-48 overflow-y-auto px-3 py-2 space-y-2">
        {thinking ? (
          <div>
            <div className="text-[11px] font-medium text-amber-100/70 mb-1">""" + "\u601d\u8003\u8fc7\u7a0b\uff08Cursor\uff09" + """</div>
            <div className="text-[13px] text-white/65 leading-relaxed whitespace-pre-wrap break-words">{thinking}</div>
          </div>
        ) : null}
        {execution ? (
          <div>
            <div className="text-[11px] font-medium text-sky-200/70 mb-1">""" + "\u672c\u673a\u6267\u884c" + """</div>
            <div className="text-[13px] text-white/55 leading-relaxed whitespace-pre-wrap break-words">{execution}</div>
          </div>
        ) : null}
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

"""
    anchor = "\nfunction MessageBubble({"
    if anchor not in t:
        raise SystemExit("MessageBubble anchor missing")
    t = t.replace(anchor, "\n" + helpers + anchor, 1)
    print("helpers inserted")

p.write_text(t, encoding="utf-8", newline="\n")
print("written", p)
print("checks", {
    "AGENT_THINKING": "AGENT_THINKING" in t,
    "LiveThinkingPanel": "function LiveThinkingPanel" in t,
    "MessageThinkingBlock": "function MessageThinkingBlock" in t,
    "cn_think": "\u601d\u8003\u8fc7\u7a0b" in t,
})
