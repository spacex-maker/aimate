import { ReactNode, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import clsx from 'clsx'

type Width = 'sm' | 'md' | 'lg' | 'xl'

const widthClass: Record<Width, string> = {
  sm: 'admin-dialog--sm',
  md: 'admin-dialog--md',
  lg: 'admin-dialog--lg',
  xl: 'admin-dialog--xl',
}

type Props = {
  open: boolean
  onClose: () => void
  title: ReactNode
  subtitle?: ReactNode
  width?: Width
  children: ReactNode
  footer?: ReactNode
  busy?: boolean
  /** 关闭时是否忽略 backdrop 点击（保存中） */
  closeOnBackdrop?: boolean
  bodyClassName?: string
}

/** 运营台统一模态框：无硬白描边，头/身/脚分区。 */
export function AdminDialog({
  open,
  onClose,
  title,
  subtitle,
  width = 'md',
  children,
  footer,
  busy,
  closeOnBackdrop = true,
  bodyClassName,
}: Props) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, busy, onClose])

  if (!open) return null

  return createPortal(
    <div
      className="admin-dialog-backdrop"
      onClick={() => {
        if (closeOnBackdrop && !busy) onClose()
      }}
    >
      <div
        className={clsx('admin-dialog', widthClass[width])}
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="admin-dialog__head">
          <div className="admin-dialog__titles">
            <h4 className="admin-dialog__title">{title}</h4>
            {subtitle ? <p className="admin-dialog__sub">{subtitle}</p> : null}
          </div>
          <button
            type="button"
            className="admin-dialog__close"
            disabled={busy}
            onClick={onClose}
            aria-label="关闭"
          >
            <X size={16} />
          </button>
        </div>
        <div className={clsx('admin-dialog__body', bodyClassName)}>{children}</div>
        {footer ? <div className="admin-dialog__foot">{footer}</div> : null}
      </div>
    </div>,
    document.body,
  )
}
