type Props = {
  checked: boolean
  onChange: () => void
  disabled?: boolean
  title?: string
  'aria-label'?: string
}

/** 运营台启用开关：固定几何，避免豆豆错位 */
export function AdminToggle({ checked, onChange, disabled, title, ...rest }: Props) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={rest['aria-label'] ?? title ?? (checked ? '已启用' : '已停用')}
      title={title}
      disabled={disabled}
      onClick={onChange}
      className={`admin-switch${checked ? ' is-on' : ''}`}
    >
      <span className="admin-switch__knob" aria-hidden />
    </button>
  )
}