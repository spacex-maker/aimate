import { useEffect, useState } from 'react'
import { X } from 'lucide-react'

interface Props {
  title: string
  hint?: string
  initialValue: string
  loading?: boolean
  onClose: () => void
  onConfirm: (value: string) => void
}

export function ConversationRenameModal({
  title,
  hint,
  initialValue,
  loading,
  onClose,
  onConfirm,
}: Props) {
  const [value, setValue] = useState(initialValue)

  useEffect(() => {
    setValue(initialValue)
  }, [initialValue])

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-sm rounded-xl border border-white/10 bg-[#1a1a1a] shadow-xl">
        <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
          <h2 className="text-sm font-semibold text-white">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded p-1 text-white/40 hover:text-white disabled:opacity-40"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <form
          className="space-y-3 px-4 py-3"
          onSubmit={(e) => {
            e.preventDefault()
            const next = value.trim()
            if (!next || loading) return
            onConfirm(next)
          }}
        >
          {hint && <p className="text-[11px] text-white/40">{hint}</p>}
          <input
            autoFocus
            value={value}
            maxLength={128}
            onChange={(e) => setValue(e.target.value)}
            className="w-full rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white outline-none focus:border-blue-500/40"
            placeholder="输入名称"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              disabled={loading}
              onClick={onClose}
              className="rounded-lg px-3 py-1.5 text-xs text-white/50 hover:bg-white/5 disabled:opacity-40"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={loading || !value.trim()}
              className="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-500 disabled:opacity-40"
            >
              {loading ? '保存中…' : '保存'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
