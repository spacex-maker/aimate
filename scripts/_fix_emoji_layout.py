# -*- coding: utf-8 -*-
from pathlib import Path

# --- EmojiPicker size ---
ep = Path(r"f:\dev\proj2\aimate\frontend\src\components\chat\EmojiPicker.tsx")
et = ep.read_text(encoding="utf-8")
et = et.replace(
    'className="absolute left-0 bottom-full mb-2 z-40 w-[min(100%,22rem)] rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"',
    'className="w-[20rem] sm:w-[22rem] rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"',
    1,
)
et = et.replace(
    'className="max-h-52 overflow-y-auto p-2 grid grid-cols-8 gap-0.5"',
    'className="h-56 overflow-y-auto p-2 grid grid-cols-8 gap-0.5"',
    1,
)
ep.write_text(et, encoding="utf-8", newline="\n")
print("EmojiPicker sized")

# --- AgentInputBox portal ---
p = Path(r"f:\dev\proj2\aimate\frontend\src\components\agent\AgentInputBox.tsx")
t = p.read_text(encoding="utf-8")

if "emojiButtonRef" not in t:
    t = t.replace(
        "  const [emojiOpen, setEmojiOpen] = useState(false)\n",
        "  const [emojiOpen, setEmojiOpen] = useState(false)\n"
        "  const [emojiAnchor, setEmojiAnchor] = useState<{ left: number; bottom: number } | null>(null)\n"
        "  const emojiButtonRef = useRef<HTMLButtonElement>(null)\n",
        1,
    )
    print("state+ref added")

old_inline = """            <div className="flex items-center gap-0.5 relative">
              {emojiOpen ? (
                <EmojiPicker
                  onPick={(emoji) => {
                    insertAtCaret(emoji)
                  }}
                  onClose={() => setEmojiOpen(false)}
                />
              ) : null}
              <button
                type="button"
                onClick={() => {
                  setEmojiOpen((v) => !v)
                  setMention(null)
                }}
                className="p-1.5 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
                title="表情"
                aria-label="表情"
                aria-expanded={emojiOpen}
              >
                <Smile className="w-4 h-4" />
              </button>"""

new_inline = """            <div className="flex items-center gap-0.5 relative">
              <button
                ref={emojiButtonRef}
                type="button"
                onClick={() => {
                  const el = emojiButtonRef.current
                  if (el) {
                    const rect = el.getBoundingClientRect()
                    setEmojiAnchor({ left: rect.left, bottom: window.innerHeight - rect.top })
                  }
                  setEmojiOpen((v) => !v)
                  setMention(null)
                }}
                className="p-1.5 rounded-lg text-white/50 hover:text-white/80 hover:bg-white/10 transition-colors"
                title="表情"
                aria-label="表情"
                aria-expanded={emojiOpen}
              >
                <Smile className="w-4 h-4" />
              </button>"""

if old_inline not in t:
    raise SystemExit("inline block missing")
t = t.replace(old_inline, new_inline, 1)
print("toolbar cleaned")

portal = """
          {emojiOpen && emojiAnchor && createPortal(
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
          )}
"""

needle = """          {mention && (
            <MentionPicker
              items={mentionItems}
              activeIndex={Math.min(mentionIndex, Math.max(0, mentionItems.length - 1))}
              onHover={setMentionIndex}
              onSelect={applyMention}
              query={mention.query}
            />
          )}
"""
if "emojiOpen && emojiAnchor && createPortal" not in t:
    if needle not in t:
        raise SystemExit("mention needle missing")
    t = t.replace(needle, needle + portal, 1)
    print("portal added")
else:
    print("portal exists")

p.write_text(t, encoding="utf-8", newline="\n")
print("done")
