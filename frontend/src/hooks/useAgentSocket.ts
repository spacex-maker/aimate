import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import type { AgentEvent } from '../types/agent'

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
 * WebSocket 聊天链路（整理）：
 *
 * 1. 连接：SockJS(wsUrl) → STOMP CONNECT（带 token）→ CONNECTED
 * 2. 订阅：CONNECTED 后 SUBSCRIBE /topic/agent/{sessionId}，只收该会话的 AgentEvent
 * 3. 收事件：后端 convertAndSend("/topic/agent/"+sessionId, event)，前端 frame.body 为 JSON
 * 4. 兜底：若前端连接/订阅晚于 Agent 开始（例如先创建会话再跳转），会漏事件；
 *    因此 onConnected 时必须用 HTTP 拉一次会话 + 消息列表，保证界面有最新状态。
 *
 * 开发环境：Vite proxy 把 /api 和 /ws 转到后端，wsUrl 用相对路径 /ws 即可。
 * 独立部署（如 Netlify）：设置 VITE_API_BASE 后，WS 地址为同源的 ws(s) + /ws。
 */
function getWsUrl(): string {
  const base = (import.meta.env.VITE_API_BASE ?? '').toString().trim()
  if (!base) return '/ws'
  const origin = base.replace(/\/$/, '')
  return (origin.startsWith('https') ? origin.replace(/^https/, 'wss') : origin.replace(/^http/, 'ws')) + '/ws'
}

export function useAgentSocket(
  sessionId: string | null,
  onEvent: (event: AgentEvent) => void,
  onConnected?: () => void
) {
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent
  const onConnectedRef = useRef(onConnected)
  onConnectedRef.current = onConnected
  /** 始终只保留一个 Client：避免 React Strict Mode 下第一次 cleanup 在 async 创建前执行导致旧连接未关闭，形成双订阅、每条消息收两次。 */
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (!sessionId) return

    const topic = `/topic/agent/${sessionId}`

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
            if (import.meta.env.DEV) {
              console.log(
                '[WS] 收到',
                frame.headers?.destination ?? '(no-destination)',
                'body length:',
                frame.body?.length ?? 0
              )
            }
            try {
              const event = JSON.parse(frame.body) as AgentEvent
              onEventRef.current(event)
            } catch (err) {
              console.error('[WS] Failed to parse event:', err)
            }
          })
          if (import.meta.env.DEV) console.log('[WS] 已订阅', topic)
          onConnectedRef.current?.()
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error:', frame.headers['message'])
        },
      })
      clientRef.current = client
      client.activate()
    }).catch((err) => {
      console.error('[WS] Failed to load SockJS:', err)
    })

    return () => {
      clientRef.current?.deactivate()
      clientRef.current = null
    }
  }, [sessionId])
}

