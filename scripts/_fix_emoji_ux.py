# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\frontend\src\components\agent\AgentInputBox.tsx")
t = p.read_text(encoding="utf-8")

# 1) refs + anchor shape
t = t.replace(
    """  const [emojiOpen, setEmojiOpen] = useState(false)
  const [emojiAnchor, setEmojiAnchor] = useState<{ left: number; bottom: number } | null>(null)
  const emojiButtonRef = useRef<HTMLButtonElement>(null)
""",
    """  const [emojiOpen, setEmojiOpen] = useState(false)
  const [emojiAnchor, setEmojiAnchor] = useState<{ left: number; bottom: number; width: number } | null>(null)
  const emojiButtonRef = useRef<HTMLButtonElement>(null)
  const emojiPanelRef = useRef<HTMLDivElement>(null)
  const composerShellRef = useRef<HTMLDivElement>(null)
""",
    1,
)

# 2) click-outside effect — insert after mentionEnabled block or after glow state effects
effect = """
  useEffect(() => {
    if (!emojiOpen) return
    const onDown = (e: MouseEvent) => {
      const node = e.target as Node
      if (emojiPanelRef.current?.contains(node)) return
      if (emojiButtonRef.current?.contains(node)) return
      setEmojiOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setEmojiOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [emojiOpen])

"""
if "emojiPanelRef.current?.contains" not in t:
    # place after canSubmit line
    anchor = "  const canSubmit = (value.trim() !== '' || images.length > 0) && !isPending && !disabled\n"
    if anchor not in t:
        raise SystemExit("canSubmit anchor missing")
    t = t.replace(anchor, anchor + effect, 1)
    print("click-outside effect added")

# 3) shell ref on card
t = t.replace(
    """        <div
          className={`relative rounded-3xl bg-white/[0.06] backdrop-blur-xl border border-white/10 shadow-xl focus-within:border-white/20 focus-within:ring-1 focus-within:ring-white/10 transition-all overflow-visible flex flex-col min-h-[128px] w-full ${glow ? 'animate-input-glow border-white/20' : ''}`}
        >""",
    """        <div
          ref={composerShellRef}
          className={`relative rounded-3xl bg-white/[0.06] backdrop-blur-xl border border-white/10 shadow-xl focus-within:border-white/20 focus-within:ring-1 focus-within:ring-white/10 transition-all overflow-visible flex flex-col min-h-[128px] w-full ${glow ? 'animate-input-glow border-white/20' : ''}`}
        >""",
    1,
)

# 4) portal position: flush above whole composer shell
old_portal = """          {emojiOpen && emojiAnchor && createPortal(
            <div
              className="fixed z-[80]"
              style={{
                left: Math.min(emojiAnchor.left, typeof window !== 'undefined' ? window.innerWidth - 360 : emojiAnchor.left),
                bottom: emojiAnchor.bottom + 8,
              }}
            >
              <EmojiPicker
                onPick={(emoji) => {
                  insertAtCaret(emoji)
                }}
                onClose={() => setEmojiOpen(false)}
              />
            </div>,
            document.body,
          )}"""

new_portal = """          {emojiOpen && emojiAnchor && createPortal(
            <div
              ref={emojiPanelRef}
              className="fixed z-[80]"
              style={{
                left: emojiAnchor.left,
                bottom: emojiAnchor.bottom + 6,
                width: Math.min(emojiAnchor.width, 384),
              }}
            >
              <EmojiPicker
                onPick={(emoji) => {
                  insertAtCaret(emoji)
                }}
                onClose={() => setEmojiOpen(false)}
              />
            </div>,
            document.body,
          )}"""

if old_portal not in t:
    raise SystemExit("portal block missing")
t = t.replace(old_portal, new_portal, 1)

# 5) open: measure shell top, not button top
old_click = """                onClick={() => {
                  const el = emojiButtonRef.current
                  if (el) {
                    const rect = el.getBoundingClientRect()
                    setEmojiAnchor({ left: rect.left, bottom: window.innerHeight - rect.top })
                  }
                  setEmojiOpen((v) => !v)
                  setMention(null)
                }}"""

new_click = """                onClick={() => {
                  const shell = composerShellRef.current
                  if (shell) {
                    const rect = shell.getBoundingClientRect()
                    // 贴在整块输入卡片上方，不挡 textarea
                    setEmojiAnchor({
                      left: rect.left,
                      bottom: window.innerHeight - rect.top,
                      width: rect.width,
                    })
                  }
                  setEmojiOpen((v) => !v)
                  setMention(null)
                }}"""

if old_click not in t:
    raise SystemExit("click handler missing")
t = t.replace(old_click, new_click, 1)

p.write_text(t, encoding="utf-8", newline="\n")
print("AgentInputBox ux ok")

# EmojiPicker fill width of portal
ep = Path(r"f:\dev\proj2\aimate\frontend\src\components\chat\EmojiPicker.tsx")
et = ep.read_text(encoding="utf-8")
et = et.replace(
    'className="w-[20rem] sm:w-[22rem] rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"',
    'className="w-full rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"',
    1,
)
ep.write_text(et, encoding="utf-8", newline="\n")
print("EmojiPicker full width")
