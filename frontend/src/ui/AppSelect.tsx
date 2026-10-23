import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import clsx from 'clsx'

export type AppSelectOption = { value: string; label: string }

type Props = {
  value: string
  onChange: (value: string) => void
  options: AppSelectOption[]
  placeholder?: string
  disabled?: boolean
  className?: string
  ariaLabel?: string
}

/** 项目统一下拉：禁止原生 select。 */
export function AppSelect({
  value,
  onChange,
  options,
  placeholder = '请选择',
  disabled,
  className,
  ariaLabel,
}: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const current = options.find((o) => o.value === value)?.label

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [open])

  return (
    <div ref={rootRef} className={clsx('relative', className)}>
      <button
        type="button"
        disabled={disabled}
        aria-label={ariaLabel || placeholder}
        aria-expanded={open}
        onClick={() => !disabled && setOpen((v) => !v)}
        className={clsx(
          'w-full flex items-center justify-between gap-2 rounded-lg bg-white/[0.06] px-3 py-2 text-sm text-left ring-1 ring-inset ring-white/[0.06]',
          'hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed',
        )}
      >
        <span className={clsx(!current && 'text-white/40')}>{current || placeholder}</span>
        <ChevronDown className="w-4 h-4 text-white/50 flex-shrink-0" />
      </button>
      {open && (
        <div className="absolute z-50 mt-1 w-full max-h-56 overflow-auto rounded-lg bg-[#1c1c1c] shadow-xl ring-1 ring-inset ring-white/[0.06] py-1">
          {options.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={clsx(
                'w-full flex items-center justify-between gap-2 px-3 py-2 text-sm text-left hover:bg-white/10',
                opt.value === value && 'text-blue-300',
              )}
              onClick={() => {
                onChange(opt.value)
                setOpen(false)
              }}
            >
              <span>{opt.label}</span>
              {opt.value === value && <Check className="w-3.5 h-3.5" />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
