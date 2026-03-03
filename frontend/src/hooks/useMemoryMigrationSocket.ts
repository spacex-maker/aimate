import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import type { MemoryMigrationEvent } from '../types/memory'

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('ofx_auth_user')
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch { return null }
}

function getWsUrl(): string {
  const base = (import.meta.env.VITE_API_BASE ?? '').toString().trim()
  if (!base) return '/ws'
  const origin = base.replace(/\/$/, '')
  return (origin.startsWith('https') ? origin.replace(/^https/, 'wss') : origin.replace(/^http/, 'ws')) + '/ws'
}

export function useMemoryMigrationSocket(
  userId: number | null,
  onEvent: (event: MemoryMigrationEvent) => void,
) {
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (!userId) return

    const topic = `/topic/memory-migration/${userId}`

    import('sockjs-client').then((mod) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const SockJS = (mod as any).default ?? mod

      clientRef.current?.deactivate()
      const token = getToken()
      const wsUrl = getWsUrl()
      const client = new Client({
        webSocketFactory: () => new SockJS(wsUrl) as WebSocket,
        reconnectDelay: 0,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        debug: import.meta.env.DEV
          ? (msg) => {
              const m = msg.trim()
              if (m.startsWith('>>>')) console.log('[STOMP 发送]', m.slice(0, 120))
              else if (m.startsWith('<<<')) console.log('[STOMP 接收]', m.slice(0, 120))
            }
          : undefined,
        onConnect: () => {
          client.subscribe(topic, (frame) => {
            try {
              const event = JSON.parse(frame.body) as MemoryMigrationEvent
              onEventRef.current(event)
            } catch (err) {
              console.error('[WS] Failed to parse migration event:', err)
            }
          })
          if (import.meta.env.DEV) console.log('[WS] 已订阅', topic)
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error (migration):', frame.headers['message'])
        },
      })
      clientRef.current = client
      client.activate()
    }).catch((err) => {
      console.error('[WS] Failed to load SockJS (migration):', err)
    })

    return () => {
      clientRef.current?.deactivate()
      clientRef.current = null
    }
  }, [userId])
}

