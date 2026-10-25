import { useEffect, useMemo, useState } from 'react'
import clsx from 'clsx'
import {
  EMOJI_CATEGORIES,
  loadRecentEmojis,
  rememberEmoji,
  type EmojiCategoryId,
} from '../../lib/emojiCatalog'

type Props = {
  onPick: (emoji: string) => void
  onClose: () => void
}

const TABS: { id: EmojiCategoryId; label: string }[] = [
  { id: 'recent', label: '常用' },
  ...EMOJI_CATEGORIES.map((c) => ({ id: c.id, label: c.label })),
]

/** 输入框旁弹出的 Unicode 表情面板（微信式分类；无版权贴纸） */
export function EmojiPicker({ onPick, onClose }: Props) {
  const [tab, setTab] = useState<EmojiCategoryId>('smileys')
  const [recent, setRecent] = useState<string[]>(() => loadRecentEmojis())

  useEffect(() => {
    if (tab === 'recent' && recent.length === 0) setTab('smileys')
  }, [tab, recent.length])

  const emojis = useMemo(() => {
    if (tab === 'recent') return recent
    return EMOJI_CATEGORIES.find((c) => c.id === tab)?.emojis ?? []
  }, [tab, recent])

  return (
    <div
      className="w-full rounded-2xl border border-white/15 bg-[#1a1a1a]/95 backdrop-blur-xl shadow-2xl overflow-hidden"
      role="dialog"
      aria-label="选择表情"
    >
      <div className="flex items-center gap-0.5 px-2 pt-2 pb-1 overflow-x-auto border-b border-white/10 scrollbar-none">
        {TABS.map((t) => {
          if (t.id === 'recent' && recent.length === 0) return null
          const active = tab === t.id
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={clsx(
                'flex-shrink-0 px-2.5 py-1 rounded-lg text-[11px] transition-colors',
                active ? 'bg-white/15 text-white' : 'text-white/45 hover:text-white/75 hover:bg-white/5',
              )}
            >
              {t.label}
            </button>
          )
        })}
      </div>
      <div className="h-56 overflow-y-auto p-2 grid grid-cols-8 gap-0.5">
        {emojis.length === 0 ? (
          <div className="col-span-8 py-6 text-center text-xs text-white/35">暂无最近使用</div>
        ) : (
          emojis.map((emoji, i) => (
            <button
              key={`${tab}-${emoji}-${i}`}
              type="button"
              className="h-9 w-full rounded-lg text-xl leading-none hover:bg-white/10 transition-colors"
              onMouseDown={(e) => {
                e.preventDefault()
                setRecent(rememberEmoji(emoji))
                onPick(emoji)
              }}
              title={emoji}
            >
              {emoji}
            </button>
          ))
        )}
      </div>
      <div className="px-3 py-1.5 border-t border-white/10 flex items-center justify-between">
        <span className="text-[10px] text-white/30">点击插入 · Esc 关闭</span>
        <button
          type="button"
          className="text-[10px] text-white/40 hover:text-white/70"
          onMouseDown={(e) => {
            e.preventDefault()
            onClose()
          }}
        >
          关闭
        </button>
      </div>
    </div>
  )
}
