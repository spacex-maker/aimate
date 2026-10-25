import type { ReactNode } from 'react'
import { resolveAgentAvatarUrl } from '../../lib/agentAvatar'
import clsx from 'clsx'

export type AgentLike = {
  id?: number | null
  name?: string | null
  /** 招聘锁定的原始商品名 */
  originalName?: string | null
  /** 招聘时的职业名（如 Cursor、产品经理） */
  careerName?: string | null
  /** 后端组装的「真名 · 职业」 */
  displayTitle?: string | null
  avatarUrl?: string | null
  externalAgentKey?: string | null
}

/** 紧凑一行：Orion · Cursor */
export function formatAgentDisplayTitle(agent: AgentLike): string {
  const canned = (agent.displayTitle || '').trim()
  if (canned) return canned
  const name = (agent.name || '').trim()
  const career = (agent.careerName || '').trim()
  if (name && career && name !== career) return `${name} · ${career}`
  return name || career || 'Agent'
}

/**
 * 气泡主标题 + 职业副标题：主标题为真名，下方始终挂招聘职业（同名时不重复）。
 */
export function resolveAgentBubbleLabels(agent: AgentLike): {
  title: string
  careerUnder?: string | null
} {
  const name = (agent.name || '').trim()
  const career = (agent.careerName || '').trim()
  if (name && career && name !== career) {
    return { title: name, careerUnder: career }
  }
  if (name) return { title: name, careerUnder: null }
  return { title: career || 'Agent', careerUnder: null }
}

export function AgentAvatar({
  agent,
  size = 28,
  className,
}: {
  agent: AgentLike
  size?: number
  className?: string
}) {
  const src = resolveAgentAvatarUrl(agent)
  const { title } = resolveAgentBubbleLabels(agent)
  return (
    <img
      src={src}
      alt={title}
      width={size}
      height={size}
      className={clsx('rounded-full object-cover bg-white/10 shrink-0', className)}
      style={className?.includes('h-full') ? undefined : { width: size, height: size }}
      draggable={false}
    />
  )
}

/** 气泡顶部：头像 + 昵称（下挂原始职业）+ 可选时间 */
export function AgentBubbleHeader({
  agent,
  timeLabel,
  className,
  avatar,
}: {
  agent: AgentLike
  timeLabel?: string | null
  className?: string
  /** 自定义头像节点（如外套上下文环）；默认普通圆形头像 */
  avatar?: ReactNode
}) {
  const { title, careerUnder } = resolveAgentBubbleLabels(agent)
  return (
    <div className={clsx('flex items-center gap-2 px-0.5 mb-1', className)}>
      {avatar ?? <AgentAvatar agent={agent} size={32} />}
      <div className="min-w-0 max-w-[14rem]">
        <div className="text-xs font-medium text-white/65 truncate">{title}</div>
        {careerUnder ? (
          <div className="text-[10px] text-white/35 truncate leading-tight mt-0.5">{careerUnder}</div>
        ) : null}
      </div>
      {timeLabel ? (
        <span className="text-[10px] text-white/35 flex-shrink-0 tabular-nums self-start mt-0.5">
          {timeLabel}
        </span>
      ) : null}
    </div>
  )
}
