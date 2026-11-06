import { FormEvent, type ReactNode } from 'react'
import { RotateCcw, Search } from 'lucide-react'

export type AdminQueryField = {
  key: string
  label: string
  /** text | search — 文本输入；自定义走 render */
  type?: 'text' | 'search'
  value: string
  onChange: (value: string) => void
  placeholder?: string
  width?: 'sm' | 'md' | 'lg' | 'grow'
  render?: ReactNode
}

type Props = {
  fields: AdminQueryField[]
  onSubmit: () => void
  onReset?: () => void
  active?: boolean
  submitLabel?: string
  trailing?: ReactNode
}

export function AdminQueryBar({
  fields,
  onSubmit,
  onReset,
  active,
  submitLabel = '查询',
  trailing,
}: Props) {
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit()
  }

  return (
    <form className="admin-query" onSubmit={handleSubmit}>
      <div className="admin-query__fields">
        {fields.map((f) => (
          <label key={f.key} className={`admin-query__field admin-query__field--${f.width ?? 'md'}`}>
            <span className="admin-query__label">{f.label}</span>
            {f.render ? (
              f.render
            ) : (
              <span className="admin-query__control">
                {f.type !== 'text' ? <Search className="admin-query__icon" size={14} aria-hidden /> : null}
                <input
                  type={f.type === 'text' ? 'text' : 'search'}
                  value={f.value}
                  onChange={(e) => f.onChange(e.target.value)}
                  placeholder={f.placeholder}
                  className={`admin-query__input${f.type !== 'text' ? ' admin-query__input--icon' : ''}`}
                />
              </span>
            )}
          </label>
        ))}
      </div>
      <div className="admin-query__actions">
        <button type="submit" className="admin-btn admin-btn--sm">
          <Search size={14} aria-hidden />
          {submitLabel}
        </button>
        {onReset ? (
          <button
            type="button"
            className="admin-btn admin-btn--ghost admin-btn--sm"
            disabled={!active}
            onClick={onReset}
          >
            <RotateCcw size={14} aria-hidden />
            重置
          </button>
        ) : null}
        {trailing}
      </div>
    </form>
  )
}