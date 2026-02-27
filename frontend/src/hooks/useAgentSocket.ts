import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import type { AgentEvent } from '../types/agent'

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('ofx_auth_user')
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch { return null }
}

/**
 * Connects to the Spring STOMP/WebSocket endpoint and subscribes to the
 * agent event stream for the given sessionId.
 *
 * SockJS is imported dynamically inside the effect to avoid CJS/ESM interop
 * issues with Vite's dev server.
 */
export function useAgentSocket(
  sessionId: string | null,
  onEvent: (event: AgentEvent) => void
) {
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent

  useEffect(() => {
    if (!sessionId) return

    let client: Client

    // Dynamic import makes SockJS safe in both dev (CJS) and prod (ESM bundle)
    import('sockjs-client').then((mod) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const SockJS = (mod as any).default ?? mod

      const token = getToken()
      client = new Client({
        webSocketFactory: () => new SockJS('/ws') as WebSocket,
        reconnectDelay: 3000,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        onConnect: () => {
          client.subscribe(`/topic/agent/${sessionId}`, (frame) => {
            try {
              const event = JSON.parse(frame.body) as AgentEvent
              onEventRef.current(event)
            } catch (err) {
              console.error('[WS] Failed to parse event:', err)
            }
          })
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error:', frame.headers['message'])
        },
      })

      client.activate()
    }).catch((err) => {
      console.error('[WS] Failed to load SockJS:', err)
    })

    return () => {
      client?.deactivate()
    }
  }, [sessionId])
}
