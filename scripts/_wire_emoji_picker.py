# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\frontend\src\components\agent\AgentInputBox.tsx")
t = p.read_text(encoding="utf-8")

if "EmojiPicker" not in t:
    t = t.replace(
        "import {\n  MentionPicker,\n  detectMentionAt,\n  filterMentions,\n  type MentionCandidate,\n} from '../chat/MentionPicker'\n",
        "import {\n  MentionPicker,\n  detectMentionAt,\n  filterMentions,\n  type MentionCandidate,\n} from '../chat/MentionPicker'\n"
        "import { EmojiPicker } from '../chat/EmojiPicker'\n",
        1,
    )
    t = t.replace(
        "  Send, ImagePlus, SlidersHorizontal, Mic, ChevronDown, X ",
        "  Send, ImagePlus, SlidersHorizontal, Mic, ChevronDown, X, Smile ",
        1,
    )
    # state after mentionIndex
    t = t.replace(
        "  const [mentionIndex, setMentionIndex] = useState(0)\n",
        "  const [mentionIndex, setMentionIndex] = useState(0)\n"
        "  const [emojiOpen, setEmojiOpen] = useState(false)\n",
        1,
    )

    insert_fn = """
  const insertAtCaret = (text: string) => {
    const el = textareaRef.current
    if (!el) {
      onChange(value + text)
      return
    }
    const start = el.selectionStart ?? value.length
    const end = el.selectionEnd ?? value.length
    const next = value.slice(0, start) + text + value.slice(end)
    onChange(next)
    requestAnimationFrame(() => {
      const pos = start + text.length
      el.focus()
      el.setSelectionRange(pos, pos)
      syncMentionFromCaret(next, pos)
    })
  }

"""
    # place before addImageFiles
    if "const insertAtCaret" not in t:
        t = t.replace("  const addImageFiles = async (files: FileList | File[]) => {", insert_fn + "  const addImageFiles = async (files: FileList | File[]) => {", 1)

    # Escape closes emoji
    t = t.replace(
        """              if (mention) {
                if (e.key === 'Escape') {
                  e.preventDefault()
                  setMention(null)
                  return
                }""",
        """              if (emojiOpen && e.key === 'Escape') {
                e.preventDefault()
                setEmojiOpen(false)
                return
              }
              if (mention) {
                if (e.key === 'Escape') {
                  e.preventDefault()
                  setMention(null)
                  return
                }""",
        1,
    )

    # button before ImagePlus
    old_btn = """            <div className="flex items-center gap-0.5 relative">
              <input
                ref={imageInputRef}"""
    new_btn = """            <div className="flex items-center gap-0.5 relative">
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
              </button>
              <input
                ref={imageInputRef}"""
    if old_btn not in t:
        raise SystemExit("toolbar anchor missing")
    t = t.replace(old_btn, new_btn, 1)
    p.write_text(t, encoding="utf-8", newline="\n")
    print("AgentInputBox wired")
else:
    print("already wired")
