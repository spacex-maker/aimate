import clsx from 'clsx'
import { Bot, User } from 'lucide-react'

export type MentionCandidate = {
  id: string
  /** 插入到文本里的名字（不含 @） */
  label: string
  kind: 'USER' | 'AGENT'
  sublabel?: string
  /** MALE / FEMALE / … */
  gender?: string | null
  genderLabel?: string | null
  avatarUrl?: string | null
}

type Props = {
  items: MentionCandidate[]
  activeIndex: number
  onHover: (index: number) => void
  onSelect: (item: MentionCandidate) => void
  query: string
}

function genderGlyph(gender?: string | null, label?: string | null): string | null {
  const g = (gender || '').trim().toUpperCase()
  const l = (label || '').trim()
  if (g === 'FEMALE' || g === 'F' || l === '女') return '♀'
  if (g === 'MALE' || g === 'M' || l === '男') return '♂'
  return null
}

/** 输入 @ 后浮在输入框上方的快捷选择气泡 */
export function MentionPicker({ items, activeIndex, onHover, onSelect, query }: Props) {
  return (
    <div
      className="absolute left-0 right-0 bottom-full mb-2 z-30 mx-1 rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"
      role="listbox"
      aria-label="选择要 @ 的成员"
    >
      <div className="px-3 py-2 border-b border-white/10 flex items-center justify-between gap-2">
        <span className="text-[11px] text-white/45 uppercase tracking-wide">选择成员</span>
        <span className="text-[11px] text-white/30 truncate">
          {query ? `@${query}` : '@'}
        </span>
      </div>
      {items.length === 0 ? (
        <div className="px-3 py-4 text-xs text-white/35">没有匹配的成员</div>
      ) : (
        <ul className="max-h-56 overflow-y-auto py-1">
          {items.map((item, i) => {
            const active = i === activeIndex
            return (
              <li key={item.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={active}
                  onMouseEnter={() => onHover(i)}
                  onMouseDown={(e) => {
                    // 避免抢 textarea 焦点导致选区丢失
                    e.preventDefault()
                    onSelect(item)
                  }}
                  className={clsx(
                    'w-full flex items-center gap-2.5 px-3 py-2 text-left transition-colors',
                    active ? 'bg-blue-600/25 text-white' : 'text-white/80 hover:bg-white/5',
                  )}
                >
                  <div className="w-8 h-8 rounded-full bg-white/10 overflow-hidden flex-shrink-0 flex items-center justify-center">
                    {item.avatarUrl ? (
                      <img src={item.avatarUrl} alt="" className="w-full h-full object-cover" />
                    ) : item.kind === 'AGENT' ? (
                      <Bot className="w-4 h-4 text-white/50" />
                    ) : (
                      <User className="w-4 h-4 text-white/50" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
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
                  </div>
                  <span
                    className={clsx(
                      'text-[10px] px-1.5 py-0.5 rounded border flex-shrink-0',
                      item.kind === 'AGENT'
                        ? 'border-cyan-500/30 text-cyan-300/80'
                        : 'border-white/15 text-white/40',
                    )}
                  >
                    {item.kind === 'AGENT' ? 'Agent' : '用户'}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
      <div className="px-3 py-1.5 border-t border-white/10 text-[10px] text-white/30">
        ↑↓ 选择 · Enter / Tab 确认 · Esc 关闭
      </div>
    </div>
  )
}

/** 光标前是否处于 @提及：返回 query 起点与当前过滤词 */
export function detectMentionAt(
  text: string,
  caret: number,
): { start: number; query: string } | null {
  const before = text.slice(0, Math.max(0, caret))
  const at = before.lastIndexOf('@')
  if (at < 0) return null
  if (at > 0) {
    const prev = before[at - 1]
    // 仅允许行首或空白后的 @（避免邮箱）
    if (prev && !/\s/.test(prev)) return null
  }
  const query = before.slice(at + 1)
  // 查询段不能含空白；否则视为已结束的提及
  if (/[\s\n]/.test(query)) return null
  return { start: at, query }
}

export function filterMentions(
  candidates: MentionCandidate[],
  query: string,
): MentionCandidate[] {
  const q = query.trim().toLowerCase()
  if (!q) return candidates
  return candidates.filter((c) => {
    const label = c.label.toLowerCase()
    const sub = (c.sublabel || '').toLowerCase()
    const gl = (c.genderLabel || '').toLowerCase()
    return label.includes(q) || label.startsWith(q) || sub.includes(q) || gl.includes(q)
  })
}
