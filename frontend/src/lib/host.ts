/** 用户站与运营后台域名隔离 */
export const CLOUD_SITE_ORIGIN = 'https://chat.aimatex.com'
export const ADMIN_SITE_ORIGIN = 'https://chat-admin.aimatex.com'

export function isAdminHost(): boolean {
  if (typeof window === 'undefined') return false
  if (import.meta.env.VITE_FORCE_ADMIN_HOST === '1') return true
  const host = window.location.hostname.toLowerCase()
  return host === 'chat-admin.aimatex.com' || host.startsWith('chat-admin.')
}
