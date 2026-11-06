/** Sliding toggle with fixed geometry. */
export function AppToggle({
  checked,
  onChange,
  disabled,
  ariaLabel,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
  ariaLabel?: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={() => !disabled && onChange(!checked)}
      className={`admin-switch${checked ? ' is-on' : ''}`}
    >
      <span className="admin-switch__knob" aria-hidden />
    </button>
  )
}