import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth'

const BASE = '/api/auth'

async function request<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
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
}
