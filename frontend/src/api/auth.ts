import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth'
import type { OidcPublicConfig } from '../lib/oidc'
import { fullUrl } from './httpClient'

const BASE = '/api/auth'

async function request<T>(url: string, body?: unknown, method: 'GET' | 'POST' = 'POST'): Promise<T> {
  const res = await fetch(fullUrl(url), {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    const text = await res.text()
    let message = `HTTP ${res.status}`
    try {
      const json = JSON.parse(text)
      message = json.message ?? json.error ?? message
    } catch { /* not JSON */ }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export const authApi = {
  register: (body: RegisterRequest) =>
    request<AuthResponse>(`${BASE}/register`, body),

  login: (body: LoginRequest) =>
    request<AuthResponse>(`${BASE}/login`, body),

  oidcConfig: () => request<OidcPublicConfig>(`${BASE}/oidc/config`, undefined, 'GET'),

  oidcCallback: (body: { code: string; codeVerifier: string; redirectUri: string }) =>
    request<AuthResponse>(`${BASE}/oidc/callback`, body),
}
