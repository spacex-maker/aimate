/** 用户站与运营后台域名隔离 */
export const CLOUD_SITE_ORIGIN = 'https://chat.aimatex.com'
export const ADMIN_SITE_ORIGIN = 'https://chat-admin.aimatex.com'
/** Soul 控制台（Agent 绑定魂的记忆档案） */
export const SOUL_SITE_ORIGIN = 'https://soul.aimatex.com'

export function soulConsoleUrl(soulAgentId: number | string | null | undefined): string | null {
  if (soulAgentId == null || soulAgentId === '') return null
  return `${SOUL_SITE_ORIGIN}/console/${soulAgentId}`
}

export function isAdminHost(): boolean {
  if (typeof window === 'undefined') return false
  if (import.meta.env.VITE_FORCE_ADMIN_HOST === '1') return true
  const host = window.location.hostname.toLowerCase()
  return host === 'chat-admin.aimatex.com' || host.startsWith('chat-admin.')
}
