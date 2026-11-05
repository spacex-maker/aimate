# -*- coding: utf-8 -*-
from pathlib import Path

# groups.ts
g = Path(r"f:\dev\proj2\aimate\frontend\src\api\groups.ts")
gt = g.read_text(encoding="utf-8")
if "genderLabel?" not in gt:
    gt = gt.replace(
        """  careerName?: string | null
  displayTitle?: string | null
  username?: string""",
        """  careerName?: string | null
  displayTitle?: string | null
  gender?: string | null
  genderLabel?: string | null
  username?: string""",
        1,
    )
    g.write_text(gt, encoding="utf-8")
    print("groups.ts ok")
else:
    print("groups.ts already")

# MentionPicker
mp = Path(r"f:\dev\proj2\aimate\frontend\src\components\chat\MentionPicker.tsx")
mt = mp.read_text(encoding="utf-8")
mt = mt.replace(
    """export type MentionCandidate = {
  id: string
  /** 插入到文本里的名字（不含 @） */
  label: string
  kind: 'USER' | 'AGENT'
  sublabel?: string
  avatarUrl?: string | null
}""",
    """export type MentionCandidate = {
  id: string
  /** 插入到文本里的名字（不含 @） */
  label: string
  kind: 'USER' | 'AGENT'
  sublabel?: string
  /** MALE / FEMALE / … */
  gender?: string | null
  genderLabel?: string | null
  avatarUrl?: string | null
}""",
    1,
)

# gender glyph helper + render next to label
if "genderGlyph" not in mt:
    mt = mt.replace(
        """/** 输入 @ 后浮在输入框上方的快捷选择气泡 */
export function MentionPicker({ items, activeIndex, onHover, onSelect, query }: Props) {""",
        """function genderGlyph(gender?: string | null, label?: string | null): string | null {
  const g = (gender || '').trim().toUpperCase()
  const l = (label || '').trim()
  if (g === 'FEMALE' || g === 'F' || l === '女') return '♀'
  if (g === 'MALE' || g === 'M' || l === '男') return '♂'
  return null
}

/** 输入 @ 后浮在输入框上方的快捷选择气泡 */
export function MentionPicker({ items, activeIndex, onHover, onSelect, query }: Props) {""",
        1,
    )
    mt = mt.replace(
        """                  <div className="min-w-0 flex-1">
                    <div className="text-sm truncate">
                      <span className="text-white/40">@</span>
                      {item.label}
                    </div>
                    <div className="text-[11px] text-white/35 truncate">
                      {item.sublabel || (item.kind === 'AGENT' ? 'Agent' : '成员')}
                    </div>
                  </div>""",
        """                  <div className="min-w-0 flex-1">
                    <div className="text-sm truncate flex items-center gap-1.5">
                      <span className="min-w-0 truncate">
                        <span className="text-white/40">@</span>
                        {item.label}
                      </span>
                      {genderGlyph(item.gender, item.genderLabel) ? (
                        <span
                          className="text-[11px] text-white/45 flex-shrink-0"
                          title={item.genderLabel || item.gender || undefined}
                          aria-label={item.genderLabel || item.gender || 'gender'}
                        >
                          {genderGlyph(item.gender, item.genderLabel)}
                        </span>
                      ) : null}
                    </div>
                    <div className="text-[11px] text-white/35 truncate">
                      {item.sublabel || (item.kind === 'AGENT' ? 'Agent' : '成员')}
                    </div>
                  </div>""",
        1,
    )
    # also search sublabel in filter
    mt = mt.replace(
        """  return candidates.filter((c) => {
    const label = c.label.toLowerCase()
    const sub = (c.sublabel || '').toLowerCase()
    return label.includes(q) || label.startsWith(q) || sub.includes(q)
  })""",
        """  return candidates.filter((c) => {
    const label = c.label.toLowerCase()
    const sub = (c.sublabel || '').toLowerCase()
    const gl = (c.genderLabel || '').toLowerCase()
    return label.includes(q) || label.startsWith(q) || sub.includes(q) || gl.includes(q)
  })""",
        1,
    )
mp.write_text(mt, encoding="utf-8", newline="\n")
print("MentionPicker ok")

# GroupChatPage mentionCandidates
gp = Path(r"f:\dev\proj2\aimate\frontend\src\pages\GroupChatPage.tsx")
gt = gp.read_text(encoding="utf-8")
old = """    const agents = detail.members
      .filter((m) => m.memberType === 'AGENT' && m.name)
      .map((m) => ({
        id: `agent-${m.id}`,
        label: m.name!,
        kind: 'AGENT' as const,
        sublabel: m.agentKind === 'SECRETARY' ? '秘书' : 'Agent',
        avatarUrl: m.avatarUrl,
      }))"""
new = """    const agents = detail.members
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
      })"""
if old not in gt:
    raise SystemExit("mentionCandidates agents block missing")
gt = gt.replace(old, new, 1)
gp.write_text(gt, encoding="utf-8", newline="\n")
print("GroupChatPage ok")
