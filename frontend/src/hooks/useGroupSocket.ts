import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import { getWsUrl } from '../api/httpClient'

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('ofx_auth_user')
    if (!raw) return null
    return (JSON.parse(raw) as { token?: string }).token ?? null
  } catch {
    return null
  }
}

export type GroupWsEvent = {
  type: string
  groupPublicId: string
  payload: unknown
}

export function useGroupSocket(
  publicId: string | null,
  onEvent: (event: GroupWsEvent) => void,
  onConnected?: () => void,
) {
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent
  const onConnectedRef = useRef(onConnected)
  onConnectedRef.current = onConnected
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (!publicId) return
    const topic = `/topic/group.${publicId}`

    import('sockjs-client').then((mod) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const SockJS = (mod as any).default ?? mod
      clientRef.current?.deactivate()
      const token = getToken()
      const client = new Client({
        webSocketFactory: () => new SockJS(getWsUrl()) as WebSocket,
        reconnectDelay: 3000,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        debug: () => {},
        onConnect: () => {
          client.subscribe(topic, (frame) => {
            try {
              const event = JSON.parse(frame.body) as GroupWsEvent
              onEventRef.current(event)
            } catch (err) {
              console.error('[GroupWS] parse failed', err)
            }
          })
          onConnectedRef.current?.()
        },
      })
      clientRef.current = client
      client.activate()
    })

    return () => {
      clientRef.current?.deactivate()
      clientRef.current = null
    }
  }, [publicId])
}
