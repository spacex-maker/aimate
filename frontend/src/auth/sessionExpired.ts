/** 登录过期：跨请求去重弹窗总线 */
export type SessionExpiredState = {
  loginPath: string
  title: string
  message: string
}

type Listener = (state: SessionExpiredState | null) => void

let current: SessionExpiredState | null = null
const listeners = new Set<Listener>()

const LOGIN_PAGE_RE =
  /\/(login|register|signup|oauth\/callback|console\/login|admin\/login|partner\/ops\/login)(\/|$|\?)/i

function onLoginLikePage(): boolean {
  if (typeof window === 'undefined') return true
  return LOGIN_PAGE_RE.test(window.location.pathname)
}

export function isAuthApiUrl(url?: string | null): boolean {
  if (!url) return false
  const u = url.toLowerCase()
  if (!u.includes('/auth') && !u.includes('/oauth') && !u.includes('/captcha')) return false
  return /(login|register|signup|captcha|token|callback|oidc|handoff|staff)/i.test(u)
}

export function notifySessionExpired(opts: {
  loginPath: string
  clearSession: () => void
  title?: string
  message?: string
}): void {
  try {
    opts.clearSession()
  } catch {
    /* ignore */
  }
  if (onLoginLikePage()) return
  if (current) return
  current = {
    loginPath: opts.loginPath,
    title: opts.title ?? '登录已过期',
    message: opts.message ?? '当前登录状态已失效或凭证无效，请重新登录后继续。',
  }
  listeners.forEach((l) => l(current))
}

export function dismissSessionExpired(): void {
  current = null
  listeners.forEach((l) => l(null))
}

export function subscribeSessionExpired(listener: Listener): () => void {
  listeners.add(listener)
  listener(current)
  return () => {
    listeners.delete(listener)
  }
}
