import { useState, useCallback, useEffect } from 'react'
import type { AuthUser } from '../types/auth'

const STORAGE_KEY = 'ofx_auth_user'

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

function saveUser(user: AuthUser | null) {
  if (user) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(STORAGE_KEY)
  }
}

// ── Singleton state shared across all hook instances ──────────────────────────
let _user: AuthUser | null = loadUser()
const _listeners = new Set<() => void>()

function notify() {
  _listeners.forEach(fn => fn())
}

export function setAuthUser(user: AuthUser | null) {
  _user = user
  saveUser(user)
  notify()
}

// ── Hook ─────────────────────────────────────────────────────────────────────
export function useAuth() {
  const [, rerender] = useState(0)

  useEffect(() => {
    const fn = () => rerender(n => n + 1)
    _listeners.add(fn)
    return () => { _listeners.delete(fn) }
  }, [])

  const logout = useCallback(() => setAuthUser(null), [])

  return {
    user: _user,
    isLoggedIn: _user !== null,
    logout,
  }
}
