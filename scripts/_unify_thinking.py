# -*- coding: utf-8 -*-
"""Unify local-bridge stream to a single 思考过程 concept."""
from pathlib import Path
import re

# ---------- backend ----------
p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\localbridge\LocalBridgeService.java")
t = p.read_text(encoding="utf-8")

# 1) progress 启动提示：只改状态，不落库、不当思考过程
old_prog = """        // 非流式 progress：只推思考态提示，不往群里刷气泡
        if (task.getGroupId() != null && StringUtils.hasText(message)) {
            try {
                pushStreamContent(task, message.trim());
            } catch (Exception e) {
                log.debug("[LocalBridge] progress thinking failed: {}", e.getMessage());
            }
        }
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}"""

new_prog = """        // 非流式 progress（「已开始执行」）：只改状态，绝不写入思考过程
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}"""

if old_prog not in t:
    raise SystemExit("progress block missing")
t = t.replace(old_prog, new_prog, 1)

# 2) replace pushStreamContent implementation — always thinkingText, kind=thinking
old_push = """    /**
     * 实时推送：只走 WebSocket AGENT_THINKING，不改气泡。
     * kind=thinking → Cursor 真思考；kind=progress → 本机执行/.partial（禁止冒充思考）。
     */
    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content) {
        return pushStreamContent(task, content, "progress");
    }

    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content, String kindRaw) {
        String body = content.length() > 50_000 ? content.substring(0, 50_000) + "…" : content;
        String kind = normalizeStreamKind(kindRaw);
        String prev = "thinking".equals(kind) ? task.getThinkingText() : task.getExecutionText();
        if (body.equals(prev)) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("ok", true);
            skip.put("superseded", false);
            skip.put("streaming", true);
            skip.put("unchanged", true);
            skip.put("kind", kind);
            skip.put("status", task.getStatus().name());
            return skip;
        }
        if ("thinking".equals(kind)) {
            task.setThinkingText(body);
        } else {
            // 本机执行过程须单调增长；禁止用无关长文（常见：定稿正文）覆盖已有 .partial 增量
            String prevEx = task.getExecutionText();
            if (shouldKeepPriorExecution(prevEx, body)) {
                Map<String, Object> keep = new LinkedHashMap<>();
                keep.put("ok", true);
                keep.put("superseded", false);
                keep.put("streaming", true);
                keep.put("unchanged", true);
                keep.put("keptPriorExecution", true);
                keep.put("kind", kind);
                keep.put("status", task.getStatus().name());
                return keep;
            }
            task.setExecutionText(body);
        }
        taskRepository.save(task);

        ChatGroup g = groupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new IllegalStateException("群不存在"));
        GroupMember agentMember = memberRepository
                .findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                .orElseThrow(() -> new IllegalStateException("Agent 已不在群中"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", agentMember.getId());
        payload.put("userAgentId", task.getUserAgentId());
        payload.put("taskId", task.getPublicId());
        payload.put("triggerMessageId", task.getTriggerMessageId());
        payload.put("intentEpoch", task.getIntentEpoch());
        payload.put("kind", kind);
        payload.put("content", body);
        payload.put("done", false);
        try {
            messageService.publishAgentThinking(g, payload);
        } catch (Exception e) {
            log.debug("[LocalBridge] AGENT_THINKING publish failed: {}", e.getMessage());
        }
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("superseded", false);
        out.put("streaming", true);
        out.put("kind", kind);
        out.put("status", task.getStatus().name());
        out.put("intentEpoch", task.getIntentEpoch());
        return out;
    }


    /**
     * 已有过程备注时，若新内容不是其延续/缩短重写，则保留旧过程（避免定稿正文冲掉 .partial）。
     */
    private static boolean shouldKeepPriorExecution(String prev, String next) {
        if (!StringUtils.hasText(prev) || !StringUtils.hasText(next)) return false;
        String p = prev.trim();
        String n = next.trim();
        if (p.equals(n)) return false;
        if (n.startsWith(p) || p.startsWith(n)) return false;
        int share = 0;
        int lim = Math.min(64, Math.min(p.length(), n.length()));
        while (share < lim && p.charAt(share) == n.charAt(share)) share++;
        if (share >= 24) return false;
        return p.length() >= 20;
    }"""

new_push = """    /**
     * 实时推送：只走 WebSocket AGENT_THINKING，不改气泡。
     * 统一写入 thinkingText（思考过程）：.partial / CDP 思考都算过程，不再拆「本机执行」。
     */
    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content) {
        return pushStreamContent(task, content, "thinking");
    }

    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content, String kindRaw) {
        String body = content.length() > 50_000 ? content.substring(0, 50_000) + "…" : content;
        // 对外统一 kind=thinking；忽略客户端 progress 分栏
        String kind = "thinking";
        String prev = task.getThinkingText();
        if (body.equals(prev)) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("ok", true);
            skip.put("superseded", false);
            skip.put("streaming", true);
            skip.put("unchanged", true);
            skip.put("kind", kind);
            skip.put("status", task.getStatus().name());
            return skip;
        }
        // 禁止用定稿式长文无共享前缀地覆盖已有过程（旧 Connector 曾把 result 再 stream 一次）
        if (shouldRejectTraceOverwrite(prev, body)) {
            Map<String, Object> keep = new LinkedHashMap<>();
            keep.put("ok", true);
            keep.put("superseded", false);
            keep.put("streaming", true);
            keep.put("unchanged", true);
            keep.put("keptPriorThinking", true);
            keep.put("kind", kind);
            keep.put("status", task.getStatus().name());
            return keep;
        }
        task.setThinkingText(body);
        taskRepository.save(task);

        ChatGroup g = groupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new IllegalStateException("群不存在"));
        GroupMember agentMember = memberRepository
                .findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                .orElseThrow(() -> new IllegalStateException("Agent 已不在群中"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", agentMember.getId());
        payload.put("userAgentId", task.getUserAgentId());
        payload.put("taskId", task.getPublicId());
        payload.put("triggerMessageId", task.getTriggerMessageId());
        payload.put("intentEpoch", task.getIntentEpoch());
        payload.put("kind", kind);
        payload.put("content", body);
        payload.put("done", false);
        try {
            messageService.publishAgentThinking(g, payload);
        } catch (Exception e) {
            log.debug("[LocalBridge] AGENT_THINKING publish failed: {}", e.getMessage());
        }
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("superseded", false);
        out.put("streaming", true);
        out.put("kind", kind);
        out.put("status", task.getStatus().name());
        out.put("intentEpoch", task.getIntentEpoch());
        return out;
    }

    /** 已有思考过程时，拒绝无共享前缀的换题覆盖（防定稿正文冲掉 .partial）。 */
    private static boolean shouldRejectTraceOverwrite(String prev, String next) {
        if (!StringUtils.hasText(prev) || !StringUtils.hasText(next)) return false;
        String p = prev.trim();
        String n = next.trim();
        if (p.equals(n)) return false;
        if (n.startsWith(p) || p.startsWith(n)) return false;
        int share = 0;
        int lim = Math.min(64, Math.min(p.length(), n.length()));
        while (share < lim && p.charAt(share) == n.charAt(share)) share++;
        if (share >= 16) return false;
        return p.length() >= 20;
    }"""

if old_push not in t:
    raise SystemExit("pushStreamContent block missing")
t = t.replace(old_push, new_push, 1)

# 3) complete: only thinking, coalesce from execution if needed
old_done = """        // thinking=Cursor真思考；execution=.partial过程；均须与正文明显不同才落库
        String thinking = distinctTrace(task.getThinkingText(), resultBody);
        String execution = distinctTrace(task.getExecutionText(), resultBody);
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, resultBody, quoteId, thinking, execution);
        Object mid = view.get("id");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put("memberId", agentMember.getId());
            doneThink.put("userAgentId", task.getUserAgentId());
            doneThink.put("taskId", task.getPublicId());
            doneThink.put("messageId", messageId);
            doneThink.put("kind", "thinking");
            doneThink.put("content", StringUtils.hasText(thinking) ? thinking : "");
            doneThink.put("execution", StringUtils.hasText(execution) ? execution : "");
            doneThink.put("done", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}"""

new_done = """        // 单一思考过程：优先 thinkingText，旧 execution_text 仅作兜底；与正文相同则丢弃
        String rawThink = StringUtils.hasText(task.getThinkingText())
                ? task.getThinkingText()
                : task.getExecutionText();
        String thinking = distinctTrace(rawThink, resultBody);
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, resultBody, quoteId, thinking, null);
        Object mid = view.get("id");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put("memberId", agentMember.getId());
            doneThink.put("userAgentId", task.getUserAgentId());
            doneThink.put("taskId", task.getPublicId());
            doneThink.put("messageId", messageId);
            doneThink.put("kind", "thinking");
            doneThink.put("content", StringUtils.hasText(thinking) ? thinking : "");
            doneThink.put("done", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}"""

if old_done not in t:
    raise SystemExit("complete block missing")
t = t.replace(old_done, new_done, 1)

# default reportStream kind → thinking
t = t.replace(
    'return reportStream(principal, publicId, content, deviceKey, "progress");',
    'return reportStream(principal, publicId, content, deviceKey, "thinking");',
    1,
)

p.write_text(t, encoding="utf-8")
print("backend ok")

# ---------- FE ----------
gp = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
g = gp.read_text(encoding="utf-8")

g = g.replace(
    """  /** live: thinking=Cursor; execution=.partial only while running */
  const [liveThinking, setLiveThinking] = useState<
    Record<number, { thinking?: string; execution?: string; taskId?: string }>
  >({})""",
    """  /** live 思考过程（.partial / CDP 统一一条） */
  const [liveThinking, setLiveThinking] = useState<
    Record<number, { content?: string; taskId?: string }>
  >({})""",
    1,
)

old_ws = """      } else if (ev.type === 'AGENT_THINKING') {
        const p = ev.payload as {
          memberId?: number
          content?: string
          execution?: string
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
          if (p.messageId != null) {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === p.messageId
                  ? {
                      ...m,
                      thinking: p.content || m.thinking,
                      execution: p.execution || m.execution,
                    }
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
      }"""

new_ws = """      } else if (ev.type === 'AGENT_THINKING') {
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
      }"""

if old_ws not in g:
    raise SystemExit("WS block missing")
g = g.replace(old_ws, new_ws, 1)

g = g.replace(
    """              <LiveThinkingPanel
                key={`think-${memberId}`}
                title={mem?.displayTitle || mem?.name || 'Agent'}
                thinking={th.thinking}
                execution={th.execution}
              />""",
    """              <LiveThinkingPanel
                key={`think-${memberId}`}
                title={mem?.displayTitle || mem?.name || 'Agent'}
                content={th.content}
              />""",
    1,
)

# LiveThinkingPanel component
lp = g.find("/** Live only:")
lp_end = g.find("/** Settled bubble:")
if lp < 0 or lp_end < 0:
    raise SystemExit("LiveThinkingPanel markers missing")

new_live = """/** 列表底部：统一「思考过程」实时区 */
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
        <span className="text-xs font-medium text-amber-100/80 truncate">{title} · """ + "\u601d\u8003\u4e2d" + """</span>
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

"""
g = g[:lp] + new_live + g[lp_end:]

# bubble: only thinking
m = re.search(
    r"\{!mine && isAgent && \(msg\.thinking \|\| msg\.execution\) \? \([\s\S]*?\) : null\}",
    g,
)
if not m:
    raise SystemExit("bubble block missing")
g = (
    g[: m.start()]
    + '{!mine && isAgent && msg.thinking ? (\n'
    + '          <MessageThinkingBlock label="\u601d\u8003\u8fc7\u7a0b" text={msg.thinking} />\n'
    + "        ) : null}"
    + g[m.end() :]
)

gp.write_text(g, encoding="utf-8", newline="\n")
print("FE ok")

# ---------- connector prompt + stream kind ----------
idx = Path(r"f:\dev\proj2\aimate-connector\src\main\index.ts")
ix = idx.read_text(encoding="utf-8")
ix2 = ix.replace(
    "2) 流式（推荐）：把「执行过程备注」写入 companion（Web 实时「本机执行」面板；定稿后不会挂到气泡上）。不要把最终结论全文再写进 .partial。Cursor 会话里的思考过程由 Connector 自动抓取，无需手写：",
    "2) 流式（推荐）：把思考/执行过程写入 companion，Web 显示为「思考过程」。不要把最终结论全文再写进 .partial：",
)
# always push as thinking
ix2 = ix2.replace(
    "const pushed = await pushStreamContent(careerCode, taskId, partial, 'progress')",
    "const pushed = await pushStreamContent(careerCode, taskId, partial, 'thinking')",
)
ix2 = ix2.replace(
    "kind === 'thinking' ? 'thinking' : 'progress'",
    "'thinking'",
)
# default kind
ix2 = ix2.replace(
    "kind: 'thinking' | 'progress' = 'progress'",
    "kind: 'thinking' | 'progress' = 'thinking'",
)
if ix2 == ix:
    print("WARN: connector prompt may already differ")
idx.write_text(ix2, encoding="utf-8")
print("connector ok")
