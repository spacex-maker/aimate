import { useEffect, useRef, useState } from 'react'

export function NameDialog({
  title,
  label,
  defaultValue,
  confirmText = '确定',
  onConfirm,
  onCancel,
}: {
  title: string
  label: string
  defaultValue?: string
  confirmText?: string
  onConfirm: (value: string) => void
  onCancel: () => void
}) {
  const [value, setValue] = useState(defaultValue ?? '')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
    inputRef.current?.select()
  }, [])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <form
        className="w-full max-w-sm rounded-xl border border-white/10 bg-surface-raised p-5 shadow-xl"
        onSubmit={(e) => {
          e.preventDefault()
          const v = value.trim()
          if (!v) return
          onConfirm(v)
        }}
      >
        <h3 className="text-sm font-medium text-white mb-3">{title}</h3>
        <label className="block text-xs text-white/50 mb-1">{label}</label>
        <input
          ref={inputRef}
          className="w-full mb-4 px-3 py-2 rounded-lg bg-black/30 border border-white/10 text-sm outline-none focus:border-accent"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-1.5 rounded-lg border border-white/15 text-xs text-white/70 hover:bg-white/5"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={!value.trim()}
            className="px-3 py-1.5 rounded-lg bg-accent text-xs disabled:opacity-40"
          >
            {confirmText}
          </button>
        </div>
      </form>
    </div>
  )
}
