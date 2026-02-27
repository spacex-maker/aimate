/**
 * Central HTTP client.
 * Automatically attaches the JWT from localStorage to every request.
 * On 401, clears auth state and redirects to /login.
 */

const AUTH_KEY = 'ofx_auth_user'

function getToken(): string | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch {
    return null
  }
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

  const res = await fetch(url, { ...init, headers })

  if (res.status === 401) {
    localStorage.removeItem(AUTH_KEY)
    window.location.href = '/login'
    throw new Error('未登录，请重新登录')
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
