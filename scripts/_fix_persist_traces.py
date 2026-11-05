# -*- coding: utf-8 -*-
from pathlib import Path
import re

# --- backend ---
p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\localbridge\LocalBridgeService.java")
t = p.read_text(encoding="utf-8")

old = """        Long quoteId = replyToId != null ? replyToId : task.getTriggerMessageId();
        String resultBody = result.trim();
        // 气泡上方只挂 Cursor 真思考；.partial 仅实时面板，禁止与正文重复落库
        String thinking = distinctTrace(task.getThinkingText(), resultBody);
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, resultBody, quoteId, thinking, null);
        Object mid = view.get(\"id\");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put(\"memberId\", agentMember.getId());
            doneThink.put(\"userAgentId\", task.getUserAgentId());
            doneThink.put(\"taskId\", task.getPublicId());
            doneThink.put(\"messageId\", messageId);
            doneThink.put(\"kind\", \"thinking\");
            doneThink.put(\"content\", StringUtils.hasText(thinking) ? thinking : \"\");
            doneThink.put(\"execution\", \"\");
            doneThink.put(\"done\", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}"""

new = """        Long quoteId = replyToId != null ? replyToId : task.getTriggerMessageId();
        String resultBody = result.trim();
        // thinking=Cursor真思考；execution=.partial过程；均须与正文明显不同才落库
        String thinking = distinctTrace(task.getThinkingText(), resultBody);
        String execution = distinctTrace(task.getExecutionText(), resultBody);
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, resultBody, quoteId, thinking, execution);
        Object mid = view.get(\"id\");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put(\"memberId\", agentMember.getId());
            doneThink.put(\"userAgentId\", task.getUserAgentId());
            doneThink.put(\"taskId\", task.getPublicId());
            doneThink.put(\"messageId\", messageId);
            doneThink.put(\"kind\", \"thinking\");
            doneThink.put(\"content\", StringUtils.hasText(thinking) ? thinking : \"\");
            doneThink.put(\"execution\", StringUtils.hasText(execution) ? execution : \"\");
            doneThink.put(\"done\", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}"""

# In the file, quotes are normal " not escaped
old = old.replace('\\"', '"')
new = new.replace('\\"', '"')

if old not in t:
    raise SystemExit("backend block missing")
t = t.replace(old, new, 1)
p.write_text(t, encoding="utf-8")
print("backend ok")

# --- FE ---
gp = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
g = gp.read_text(encoding="utf-8")

g = g.replace(
    """        const p = ev.payload as {
          memberId?: number
          content?: string
          kind?: string
          taskId?: string
          messageId?: number
          done?: boolean
        }""",
    """        const p = ev.payload as {
          memberId?: number
          content?: string
          execution?: string
          kind?: string
          taskId?: string
          messageId?: number
          done?: boolean
        }""",
    1,
)

old_done = """        if (p.done) {
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
        }"""

new_done = """        if (p.done) {
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
        }"""

if old_done not in g:
    raise SystemExit("done handler missing")
g = g.replace(old_done, new_done, 1)

m = re.search(r"\{!mine && isAgent && msg\.thinking \? \([\s\S]*?\) : null\}", g)
if not m:
    raise SystemExit("bubble block missing")

new_bubble = (
    "{!mine && isAgent && (msg.thinking || msg.execution) ? (\n"
    "          <>\n"
    '            {msg.thinking ? <MessageThinkingBlock label="\u601d\u8003\u8fc7\u7a0b" text={msg.thinking} /> : null}\n'
    '            {msg.execution ? <MessageThinkingBlock label="\u672c\u673a\u6267\u884c" text={msg.execution} /> : null}\n'
    "          </>\n"
    "        ) : null}"
)
g = g[: m.start()] + new_bubble + g[m.end() :]
gp.write_text(g, encoding="utf-8", newline="\n")
print("FE ok")
