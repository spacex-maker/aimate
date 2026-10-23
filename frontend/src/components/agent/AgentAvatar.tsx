import { resolveAgentAvatarUrl } from '../../lib/agentAvatar'
import clsx from 'clsx'

type AgentLike = {
  id?: number | null
  name?: string | null
  avatarUrl?: string | null
  externalAgentKey?: string | null
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
  return (
    <img
      src={src}
      alt={agent.name || 'Agent'}
      width={size}
      height={size}
      className={clsx('rounded-full object-cover bg-white/10 shrink-0', className)}
      style={{ width: size, height: size }}
      draggable={false}
    />
  )
}

/** 气泡顶部：头像 + 名称 */
export function AgentBubbleHeader({
  agent,
  className,
}: {
  agent: AgentLike
  className?: string
}) {
  return (
    <div className={clsx('flex items-center gap-2 px-0.5 mb-1', className)}>
      <AgentAvatar agent={agent} size={22} />
      <span className="text-xs font-medium text-white/65 truncate max-w-[14rem]">
        {agent.name || 'Agent'}
      </span>
    </div>
  )
}
