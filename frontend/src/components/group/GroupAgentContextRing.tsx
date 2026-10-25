import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { groupsApi } from '../../api/groups'
import { ContextUsageRing } from '../agent/ContextUsageRing'

/**
 * 群聊 Agent 头像外套一层上下文占用环（点头像可看明细 / 压缩）。
 */
export function GroupAgentContextRing({
  publicId,
  userAgentId,
  size = 36,
  className,
  children,
  fallback,
}: {
  publicId: string
  userAgentId: number
  /** 外径（含描边） */
  size?: number
  className?: string
  children: ReactNode
  /** 占用数据尚未返回时仍显示头像 */
  fallback?: ReactNode
}) {
  const { data, isLoading } = useQuery({
    queryKey: ['group-agent-context-usage', publicId],
    queryFn: () => groupsApi.agentsContextUsage(publicId),
    staleTime: 15_000,
    refetchInterval: 45_000,
  })
  const usage = data?.items?.find((i) => i.userAgentId === userAgentId)?.usage

  if (!usage || usage.limitTokens <= 0) {
    return (
      <div
        className={`shrink-0 overflow-hidden rounded-full ${className ?? ''}`}
        style={{ width: size - 8, height: size - 8 }}
      >
        {fallback ?? children}
      </div>
    )
  }

  void isLoading

  return (
    <ContextUsageRing
      className={className}
      variant="halo"
      haloSize={size}
      percent={usage.percent}
      usedTokens={usage.usedTokens}
      limitTokens={usage.limitTokens}
      systemTokens={usage.systemTokens ?? 0}
      conversationTokens={usage.conversationTokens ?? 0}
      messageTokens={usage.messageTokens}
      toolsTokens={usage.toolsTokens}
      compressRecommended={!!usage.compressRecommended}
      compressFn={() => groupsApi.compressAgentContext(publicId, userAgentId)}
      invalidateKeys={[['group-agent-context-usage', publicId]]}
    >
      {children}
    </ContextUsageRing>
  )
}
