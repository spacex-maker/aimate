# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
t = p.read_text(encoding="utf-8")

bad_start = t.find("function formatMessageTime")
bad_end = t.find("export function GroupChatPage()", bad_start)
if bad_start < 0 or bad_end < 0:
    raise SystemExit("bounds missing")

fn = """function formatMessageTime(raw?: string | null): string {
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

"""

t = t[:bad_start] + fn + t[bad_end:]
p.write_text(t, encoding="utf-8", newline="\n")
print("formatMessageTime fixed")
print("sample", repr(t[bad_start : bad_start + 280]))
