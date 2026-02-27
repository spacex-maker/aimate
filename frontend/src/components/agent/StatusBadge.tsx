import clsx from 'clsx'
import type { SessionStatus } from '../../types/agent'

const config: Record<SessionStatus, { label: string; dot: string; text: string }> = {
  PENDING:   { label: '等待中', dot: 'bg-yellow-400',           text: 'text-yellow-400' },
  RUNNING:   { label: '运行中', dot: 'bg-green-400 animate-pulse', text: 'text-green-400' },
  PAUSED:    { label: '已暂停', dot: 'bg-orange-400',           text: 'text-orange-400' },
  COMPLETED: { label: '已完成', dot: 'bg-blue-400',             text: 'text-blue-400' },
  FAILED:    { label: '失败',   dot: 'bg-red-400',              text: 'text-red-400' },
}

export function StatusBadge({ status }: { status: SessionStatus }) {
  const c = config[status]
  return (
    <span className={clsx('inline-flex items-center gap-1.5 text-xs font-medium', c.text)}>
      <span className={clsx('w-1.5 h-1.5 rounded-full', c.dot)} />
      {c.label}
    </span>
  )
}
