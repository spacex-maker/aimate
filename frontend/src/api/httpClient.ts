/**
 * Central HTTP client.
 * Automatically attaches the JWT from localStorage to every request.
 * On 401, clears auth state, reminds user to re-login, and redirects to /login.
 * 独立部署（如 Netlify）时设置 VITE_API_BASE 为后端地址（如 https://api.example.com）。
 */

import toast from 'react-hot-toast'
import { setAuthUser } from '../hooks/useAuth'

const AUTH_KEY = 'ofx_auth_user'

/** 独立部署时的后端 origin，开发环境为空（走 Vite proxy）。 */
export function getApiBase(): string {
  const b = (import.meta.env.VITE_API_BASE ?? '').toString().trim()
  return b ? b.replace(/\/$/, '') : ''
}

/** API 请求完整 URL：相对路径 + 可选的 VITE_API_BASE 前缀。 */
export function fullUrl(path: string): string {
  const base = getApiBase()
  return base ? base + path : path
}

function getToken(): string | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch {
    return null
  }
}

function handleUnauthorized(): never {
  localStorage.removeItem(AUTH_KEY)
  setAuthUser(null)
  toast.error('登录已过期或无效，请重新登录')
  window.location.href = '/login'
  throw new Error('未登录，请重新登录')
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
