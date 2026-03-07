import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import type { HostResourceStatusDto } from '../types/agent'
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

/**
 * 订阅管理端宿主机资源状态：/topic/admin/host-status
 * 每秒收到一条 HostResourceStatusDto，用于容器监控页顶部实时展示。
 */
export function useHostStatusSocket(
  enabled: boolean,
  onStatus: (status: HostResourceStatusDto) => void,
) {
  const onStatusRef = useRef(onStatus)
  onStatusRef.current = onStatus
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (!enabled) return

    const topic = '/topic/admin/host-status'

    import('sockjs-client').then((mod) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const SockJS = (mod as any).default ?? mod

      clientRef.current?.deactivate()
      const token = getToken()
      const wsUrl = getWsUrl()
      const client = new Client({
        webSocketFactory: () => new SockJS(wsUrl) as WebSocket,
        reconnectDelay: 3000,
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
              const status = JSON.parse(frame.body) as HostResourceStatusDto
              onStatusRef.current(status)
            } catch (err) {
              console.error('[WS] Failed to parse host status:', err)
            }
          })
          if (import.meta.env.DEV) console.log('[WS] 已订阅', topic)
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error (host-status):', frame.headers['message'])
        },
      })
      clientRef.current = client
      client.activate()
    }).catch((err) => {
      console.error('[WS] Failed to load SockJS (host-status):', err)
    })

    return () => {
      clientRef.current?.deactivate()
      clientRef.current = null
    }
  }, [enabled])
}

