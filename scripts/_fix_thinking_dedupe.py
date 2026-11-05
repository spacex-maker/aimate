# -*- coding: utf-8 -*-
from pathlib import Path

# --- backend complete ---
p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\localbridge\LocalBridgeService.java")
t = p.read_text(encoding="utf-8")

old = """        Long quoteId = replyToId != null ? replyToId : task.getTriggerMessageId();
        String thinking = task.getThinkingText();
        String execution = task.getExecutionText();
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, result.trim(), quoteId, thinking, execution);
        Object mid = view.get(\"id\");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put(\"memberId\", agentMember.getId());
            doneThink.put(\"userAgentId\", task.getUserAgentId());
            doneThink.put(\"taskId\", task.getPublicId());
            doneThink.put(\"messageId\", messageId);
            doneThink.put(\"kind\", \"thinking\");
            doneThink.put(\"content\", StringUtils.hasText(thinking) ? thinking.trim() : \"\");
            doneThink.put(\"execution\", StringUtils.hasText(execution) ? execution.trim() : \"\");
            doneThink.put(\"done\", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}"""

new = """        Long quoteId = replyToId != null ? replyToId : task.getTriggerMessageId();
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

if old not in t:
    raise SystemExit("complete block not found")
t = t.replace(old, new, 1)

helper = """
    /** 过程痕迹须与最终气泡正文明显不同，否则视为重复丢弃 */
    private static String distinctTrace(String trace, String result) {
        if (!StringUtils.hasText(trace)) return null;
        String tr = trace.trim();
        if (!StringUtils.hasText(result)) return tr;
        String r = result.trim();
        if (tr.equals(r)) return null;
        String tn = tr.replaceAll("\\\\s+", " ");
        String rn = r.replaceAll("\\\\s+", " ");
        if (tn.equals(rn)) return null;
        if (rn.contains(tn) && tn.length() >= Math.max(40, (int) (rn.length() * 0.6))) return null;
        if (tn.contains(rn) && rn.length() >= 40) return null;
        if (tn.endsWith(rn) || rn.endsWith(tn)) {
            int overlap = Math.min(tn.length(), rn.length());
            if (overlap >= Math.max(80, (int) (Math.max(tn.length(), rn.length()) * 0.7))) return null;
        }
        return tr;
    }

"""

if "private static String distinctTrace" not in t:
    anchor = "    private static String normalizeStreamKind(String kind) {"
    if anchor not in t:
        raise SystemExit("normalizeStreamKind not found")
    t = t.replace(anchor, helper + anchor, 1)
    print("helper inserted")

p.write_text(t, encoding="utf-8")
print("LocalBridgeService ok")

# --- FE: bubble only shows thinking (Cursor), not execution duplicate ---
gp = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
g = gp.read_text(encoding="utf-8")
old_fe = """        {!mine && isAgent && (msg.thinking || msg.execution) ? (
          <>
            {msg.thinking ? <MessageThinkingBlock label=\"思考过程\" text={msg.thinking} /> : null}
            {msg.execution ? <MessageThinkingBlock label=\"本机执行\" text={msg.execution} /> : null}
          </>
        ) : null}"""
new_fe = """        {!mine && isAgent && msg.thinking ? (
          <MessageThinkingBlock label=\"思考过程\" text={msg.thinking} />
        ) : null}"""
# handle both quote styles
if old_fe not in g:
    old_fe2 = old_fe.replace('\\"', '"')
    new_fe2 = new_fe.replace('\\"', '"')
    if old_fe2 in g:
        g = g.replace(old_fe2, new_fe2, 1)
        print("FE bubble replaced")
    else:
        print("FE bubble block not found")
        i = g.find("msg.thinking || msg.execution")
        print(repr(g[i - 40 : i + 200]) if i >= 0 else "no hit")
else:
    g = g.replace(old_fe, new_fe, 1)
    print("FE bubble replaced escaped")

# live panel title: 思考过程 must be Cursor thinking; 本机执行 is interim only
gp.write_text(g, encoding="utf-8")

# --- connector: do not push final result as progress; CDP only push thinking ---
idx = Path(r"f:\dev\proj2\aimate-connector\src\main\index.ts")
ix = idx.read_text(encoding="utf-8")
old_push_final = """          // 定稿前再推一次全文
          if (text !== lastStreamed) {
            await pushStreamContent(careerCode, taskId, text, 'progress')
          }
"""
if old_push_final in ix:
    ix = ix.replace(
        old_push_final,
        """          // 定稿正文只走 complete → 气泡；勿再当 progress，避免「本机执行」= 最终回复
""",
        1,
    )
    print("removed final-as-progress push")
else:
    print("final-as-progress block missing")

# prompt: clarify partial vs thinking
ix = ix.replace(
    "2) 流式（推荐）：本机执行中间输出写入 companion（Web 显示为「本机执行」，不是思考过程）：",
    "2) 流式（推荐）：把「执行过程备注」写入 companion（Web 实时「本机执行」面板；定稿后不会挂到气泡上）。"
    "不要把最终结论全文再写进 .partial。Cursor 会话里的思考过程由 Connector 自动抓取，无需手写：",
)

idx.write_text(ix, encoding="utf-8")
print("index.ts ok")
