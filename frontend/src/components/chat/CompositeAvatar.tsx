import clsx from 'clsx'
import { generateAgentAvatarDataUrl } from '../../lib/agentAvatar'

type Props = {
  urls?: (string | null | undefined)[]
  size?: number
  className?: string
  title?: string
}

/** 微信式九宫格合成头像（最多 9 格） */
export function CompositeAvatar({ urls = [], size = 36, className, title }: Props) {
  const cells = urls
    .filter(Boolean)
    .slice(0, 9)
    .map((u) => resolveUrl(u!))

  if (cells.length === 0) {
    return (
      <div
        title={title}
        className={clsx('rounded-lg bg-white/10 flex-shrink-0', className)}
        style={{ width: size, height: size }}
      />
    )
  }

  if (cells.length === 1) {
    return (
      <div
        title={title}
        className={clsx('rounded-lg overflow-hidden bg-white/10 flex-shrink-0', className)}
        style={{ width: size, height: size }}
      >
        <AvatarCell src={cells[0]} />
      </div>
    )
  }

  const cols = cells.length <= 4 ? 2 : 3
  const gap = 1

  return (
    <div
      title={title}
      className={clsx('rounded-lg overflow-hidden bg-[#2a2a2a] flex-shrink-0 p-[1px]', className)}
      style={{
        width: size,
        height: size,
        display: 'grid',
        gridTemplateColumns: `repeat(${cols}, 1fr)`,
        gap,
      }}
    >
      {cells.map((src, i) => (
        <div key={i} className="overflow-hidden bg-white/10 min-w-0 min-h-0">
          <AvatarCell src={src} />
        </div>
      ))}
    </div>
  )
}

function AvatarCell({ src }: { src: string }) {
  if (src.startsWith('letter:')) {
    const ch = src.slice(7).trim().charAt(0) || '?'
    return (
      <div className="w-full h-full flex items-center justify-center text-[10px] font-semibold text-white/80 bg-blue-600/40">
        {ch.toUpperCase()}
      </div>
    )
  }
  return <img src={src} alt="" className="w-full h-full object-cover" />
}

function resolveUrl(raw: string): string {
  if (raw.startsWith('agent:')) {
    return generateAgentAvatarDataUrl(raw.slice(6) || 'agent')
  }
  if (raw.startsWith('letter:')) return raw
  if (raw.startsWith('http') || raw.startsWith('data:')) return raw
  return generateAgentAvatarDataUrl(raw)
}
