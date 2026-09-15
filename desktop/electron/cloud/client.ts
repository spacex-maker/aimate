import type { AppStore, AuthSession } from './store'

export class CloudClient {
  constructor(private store: AppStore) {}

  get baseUrl(): string {
    return this.store.getConfig().serverUrl.replace(/\/$/, '')
  }

  async login(identifier: string, password: string): Promise<AuthSession> {
    const res = await this.fetchJson<AuthSession>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ identifier, password }),
      auth: false,
    })
    this.store.setAuth(res)
    return res
  }

  logout(): void {
    this.store.setAuth(null)
  }

  async fetchJson<T>(
    path: string,
    options: {
      method?: string
      body?: string
      headers?: Record<string, string>
      auth?: boolean
    } = {},
  ): Promise<T> {
    const { method = 'GET', body, headers = {}, auth = true } = options
    const finalHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...headers,
    }
    if (auth) {
      const token = this.store.getAuth()?.token
      if (!token) throw new Error('未登录')
      finalHeaders.Authorization = `Bearer ${token}`
    }

    const res = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: finalHeaders,
      body,
    })
    if (!res.ok) {
      const text = await res.text()
      let message = `HTTP ${res.status}`
      try {
        const json = JSON.parse(text) as { message?: string; error?: string }
        message = json.message ?? json.error ?? message
      } catch {
        if (text) message = text.slice(0, 200)
      }
      throw new Error(message)
    }
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  }

  async streamChat(
    body: unknown,
    onToken: (token: string) => void,
    signal?: AbortSignal,
  ): Promise<{ content: string; toolCalls?: unknown[]; model?: string }> {
    const token = this.store.getAuth()?.token
    if (!token) throw new Error('未登录')

    const res = await fetch(`${this.baseUrl}/api/proxy/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(body),
      signal,
    })

    if (!res.ok || !res.body) {
      const text = await res.text().catch(() => '')
      throw new Error(text || `LLM proxy HTTP ${res.status}`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let content = ''
    let model: string | undefined
    let toolCalls: unknown[] | undefined

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (data === '[DONE]') continue
        try {
          const json = JSON.parse(data) as {
            type?: string
            token?: string
            content?: string
            toolCalls?: unknown[]
            model?: string
            error?: string
          }
          if (json.error) throw new Error(json.error)
          if (json.type === 'token' && json.token) {
            content += json.token
            onToken(json.token)
          } else if (json.type === 'final') {
            content = json.content ?? content
            toolCalls = json.toolCalls
            model = json.model
          }
        } catch (e) {
          if (e instanceof SyntaxError) continue
          throw e
        }
      }
    }

    return { content, toolCalls, model }
  }

  async embed(texts: string[]): Promise<number[][]> {
    const res = await this.fetchJson<{ embeddings: number[][] }>('/api/proxy/embeddings', {
      method: 'POST',
      body: JSON.stringify({ input: texts }),
    })
    return res.embeddings
  }
}
