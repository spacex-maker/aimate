/**
 * Central HTTP client.
 * Automatically attaches the JWT from localStorage to every request.
 *
 * 401 策略：只抛错 + console.error，绝不清会话、绝不跳转登录页。
 * 真正要登出由用户点退出或 RequireAuth（本地无 token）处理。
 *
 * 后端地址规则（按前端访问方式）：
 * - 前端通过本地访问（localhost / 127.0.0.1）→ 请求本地后端（默认 http://localhost:9299，可用 VITE_API_BASE 覆盖）
 * - 前端通过域名访问 → 使用 VITE_API_BASE（同源部署时可不设，用相对路径）
 */

import { isAdminHost } from '../lib/host'

const AUTH_KEY = 'ofx_auth_user'
const ADMIN_TOKEN_KEY = 'chat_admin_jwt'
const DEFAULT_LOCAL_BACKEND = 'http://localhost:9299'

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

function parseErrorMessage(text: string, status: number): string {
  let message = `HTTP ${status}`
  if (!text) return message
  try {
    const json = JSON.parse(text) as { message?: string; error?: string; detail?: string }
    message = json.message ?? json.error ?? json.detail ?? message
  } catch {
    if (text.length < 300) message = text
  }
  return message
}

export async function http<T>(url: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const method = (init.method ?? 'GET').toUpperCase()
  const resolved = fullUrl(url)

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(resolved, { ...init, headers })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    const message = parseErrorMessage(text, res.status)
    console.error('[http]', method, resolved, res.status, {
      message,
      body: text.slice(0, 2000),
      hasToken: !!token,
    })
    throw new Error(message)
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
