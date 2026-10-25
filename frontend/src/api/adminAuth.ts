import { fullUrl } from './httpClient'
import { isAuthApiUrl, notifySessionExpired } from '../auth/sessionExpired'

const ADMIN_TOKEN_KEY = 'chat_admin_jwt'

export type AdminMeResponse = {
  id: number
  email: string
  displayName: string
  accountEnabled: boolean
}

export type AdminAuthResponse = {
  accessToken: string
  adminId: number
  email: string
  displayName: string
}

export function adminGetStoredToken(): string | null {
  return localStorage.getItem(ADMIN_TOKEN_KEY)
}

export function adminPersistSession(token: string) {
  localStorage.setItem(ADMIN_TOKEN_KEY, token)
}

export function adminClearSession() {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
}

async function adminRequest<T>(url: string, init: RequestInit = {}): Promise<T> {
  const token = adminGetStoredToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const res = await fetch(fullUrl(url), { ...init, headers })
  const renewed = res.headers.get('X-New-Token')
  if (renewed) adminPersistSession(renewed)
  if (!res.ok) {
    const text = await res.text()
    let message = `HTTP ${res.status}`
    try {
      const json = JSON.parse(text)
      message = json.message ?? json.error ?? message
    } catch {
      /* not JSON */
    }
    if (res.status === 401 && token && !isAuthApiUrl(url)) {
      notifySessionExpired({
        loginPath: '/login',
        clearSession: () => adminClearSession(),
      })
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export async function adminFetchCaptcha() {
  return adminRequest<{ captchaId: string; imageBase64: string }>('/api/admin/auth/captcha', {
    method: 'GET',
  })
}

export async function adminLogin(
  email: string,
  password: string,
  captchaId: string,
  captchaCode: string
) {
  return adminRequest<AdminAuthResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password, captchaId, captchaCode }),
  })
}

export async function adminStaffHandoff(ticket: string) {
  return adminRequest<AdminAuthResponse>('/api/admin/auth/staff-handoff', {
    method: 'POST',
    body: JSON.stringify({ ticket }),
  })
}

export async function adminFetchMe() {
  return adminRequest<AdminMeResponse>('/api/admin/auth/me', { method: 'GET' })
}
