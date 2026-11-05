# -*- coding: utf-8 -*-
from pathlib import Path
import re

p = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
raw = p.read_bytes()
t = raw.decode("utf-16") if len(raw) > 2 and raw[1] == 0 else raw.decode("utf-8")

m = re.search(
    r"\{!mine && isAgent && \(msg\.thinking \|\| msg\.execution\) \? \([\s\S]*?\) : null\}",
    t,
)
if not m:
    raise SystemExit("bubble block not found")

new = (
    "{!mine && isAgent && msg.thinking ? (\n"
    '          <MessageThinkingBlock label="思考过程" text={msg.thinking} />\n'
    "        ) : null}"
)
t = t[: m.start()] + new + t[m.end() :]

old = """                m.id === p.messageId
                  ? {
                      ...m,
                      thinking: p.content || m.thinking,
                      execution: p.execution || m.execution,
                    }
                  : m,"""
new_done = """                m.id === p.messageId
                  ? {
                      ...m,
                      thinking: p.content || m.thinking,
                      execution: undefined,
                    }
                  : m,"""
# tolerate blank lines
old_re = re.compile(
    r"m\.id === p\.messageId\s*\?\s*\{\s*\.\.\.m,\s*"
    r"thinking: p\.content \|\| m\.thinking,\s*"
    r"execution: p\.execution \|\| m\.execution,\s*"
    r"\}\s*: m,",
    re.M,
)
if old in t:
    t = t.replace(old, new_done, 1)
    print("done handler fixed exact")
elif old_re.search(t):
    t = old_re.sub(
        "m.id === p.messageId\n"
        "                  ? {\n"
        "                      ...m,\n"
        "                      thinking: p.content || m.thinking,\n"
        "                      execution: undefined,\n"
        "                    }\n"
        "                  : m,",
        t,
        count=1,
    )
    print("done handler fixed regex")
else:
    print("done handler not found")

# Live panel: rename 思考过程 subtitle to clarify it's Cursor thinking only
t = t.replace(
    '>思考过程</div>\n            <div className="text-[13px] text-white/65',
    '>思考过程（Cursor）</div>\n            <div className="text-[13px] text-white/65',
    1,
)

p.write_text(t, encoding="utf-8")
print("FE ok")
