/**
 * Central HTTP client.
 * Automatically attaches the JWT from localStorage to every request.
 * On 401, clears auth state, reminds user to re-login, and redirects to /login.
 *
 * 后端地址规则（按前端访问方式）：
 * - 前端通过本地访问（localhost / 127.0.0.1）→ 请求本地后端（默认 http://localhost:9299，可用 VITE_API_BASE 覆盖）
 * - 前端通过域名访问 → 使用 VITE_API_BASE（同源部署时可不设，用相对路径）
 */

import toast from 'react-hot-toast'
import { setAuthUser } from '../hooks/useAuth'
import { isAdminHost } from '../lib/host'

const AUTH_KEY = 'ofx_auth_user'
const ADMIN_TOKEN_KEY = 'chat_admin_jwt'
const DEFAULT_LOCAL_BACKEND = 'http://localhost:9299'

/** 并发 401 只弹一次、只跳转一次 */
let handlingUnauthorized = false

function isLocalHost(): boolean {
  if (typeof window === 'undefined') return false
  const h = window.location.hostname
  return h === 'localhost' || h === '127.0.0.1'
}

/** 根据当前访问方式返回后端 origin：本地访问用本地后端，域名访问用 VITE_API_BASE。 */
export function getApiBase(): string {
  const envBase = (import.meta.env.VITE_API_BASE ?? '').toString().trim().replace(/\/$/, '')
  if (isLocalHost()) return envBase || DEFAULT_LOCAL_BACKEND
  return envBase
}

/** WebSocket 根地址：与 getApiBase 同源，用于 STOMP/SockJS。SockJS 要求使用 http(s) 协议，不要传 ws(s)。 */
export function getWsUrl(): string {
  const base = getApiBase()
  if (!base) return '/ws'
  const origin = base.replace(/\/$/, '')
  return origin + '/ws'
}

/** API 请求完整 URL：相对路径 + 可选的 VITE_API_BASE 前缀。 */
export function fullUrl(path: string): string {
  const base = getApiBase()
  return base ? base + path : path
}

function getToken(): string | null {
  if (isAdminHost()) {
    return localStorage.getItem(ADMIN_TOKEN_KEY)
  }
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch {
    return null
  }
}

function handleUnauthorized(): never {
  const onLoginPage =
    typeof window !== 'undefined' && window.location.pathname.startsWith('/login')

  if (!handlingUnauthorized) {
    handlingUnauthorized = true
    if (isAdminHost()) {
      localStorage.removeItem(ADMIN_TOKEN_KEY)
      toast.error('后台会话已失效，请重新登录')
    } else {
      setAuthUser(null)
      toast.error('登录已失效，请重新登录')
    }
    if (!onLoginPage && typeof window !== 'undefined') {
      const from = window.location.pathname + window.location.search
      const q = from && from !== '/' && from !== '/login' ? `?from=${encodeURIComponent(from)}` : ''
      window.location.href = `/login${q}`
    } else {
      // 已在登录页：允许后续请求再次触发（例如用户重新点登录）
      handlingUnauthorized = false
    }
  }
  throw new Error('未授权（401）')
}

export async function http<T>(url: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(fullUrl(url), { ...init, headers })

  if (res.status === 401) {
    handleUnauthorized()
  }

  if (!res.ok) {
    const text = await res.text()
    let message = `HTTP ${res.status}`
    try {
      const json = JSON.parse(text)
      message = json.message ?? json.error ?? message
    } catch { /* not JSON, keep default */ }
    throw new Error(message)
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
