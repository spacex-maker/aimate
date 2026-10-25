import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  dismissSessionExpired,
  subscribeSessionExpired,
  type SessionExpiredState,
} from './sessionExpired'

/** 专业登录过期弹窗：去登录 / 稍后 */
export function SessionExpiredGate() {
  const [state, setState] = useState<SessionExpiredState | null>(null)

  useEffect(() => subscribeSessionExpired(setState), [])

  if (!state) return null

  const goLogin = () => {
    const path = state.loginPath || '/login'
    const next = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
    dismissSessionExpired()
    const join = path.includes('?') ? '&' : '?'
    window.location.assign(`${path}${join}next=${next}`)
  }

  return createPortal(
    <div
      role="presentation"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 100000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        background:
          'radial-gradient(1200px 600px at 50% 20%, rgba(37,99,235,0.18), transparent 55%), rgba(6,8,14,0.72)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="session-expired-title"
        style={{
          width: '100%',
          maxWidth: 420,
          borderRadius: 20,
          border: '1px solid rgba(255,255,255,0.12)',
          background: 'linear-gradient(165deg, rgba(28,32,44,0.98) 0%, rgba(14,16,22,0.99) 100%)',
          boxShadow: '0 28px 80px rgba(0,0,0,0.55)',
          overflow: 'hidden',
          color: '#fff',
          fontFamily:
            'ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif',
        }}
      >
        <div style={{ padding: '28px 28px 8px' }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'rgba(59,130,246,0.15)',
              border: '1px solid rgba(96,165,250,0.35)',
              marginBottom: 18,
            }}
            aria-hidden
          >
            <svg
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#93c5fd"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="5" y="11" width="14" height="10" rx="2" />
              <path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
          </div>
          <h2
            id="session-expired-title"
            style={{ margin: 0, fontSize: 18, fontWeight: 650, letterSpacing: '-0.01em' }}
          >
            {state.title}
          </h2>
          <p
            style={{
              margin: '10px 0 0',
              fontSize: 13.5,
              lineHeight: 1.55,
              color: 'rgba(255,255,255,0.55)',
            }}
          >
            {state.message}
          </p>
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 10,
            padding: '20px 28px 24px',
          }}
        >
          <button
            type="button"
            onClick={() => dismissSessionExpired()}
            style={{
              borderRadius: 12,
              border: '1px solid rgba(255,255,255,0.12)',
              background: 'rgba(255,255,255,0.04)',
              color: 'rgba(255,255,255,0.7)',
              padding: '10px 16px',
              fontSize: 13,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            稍后
          </button>
          <button
            type="button"
            onClick={goLogin}
            style={{
              borderRadius: 12,
              border: 'none',
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              color: '#fff',
              padding: '10px 18px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 8px 24px rgba(37,99,235,0.35)',
            }}
          >
            去登录
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
