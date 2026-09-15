import { create } from 'zustand'

export type StreamItem =
  | { kind: 'user'; content: string }
  | { kind: 'assistant'; content: string }
  | { kind: 'thinking'; content: string }
  | { kind: 'tool_call'; id: string; name: string; args: string }
  | { kind: 'tool_result'; id: string; result: string }
  | { kind: 'error'; message: string }

interface AppState {
  bootstrapped: boolean
  auth: { userId: number; username: string; displayName: string; role: string } | null
  serverUrl: string
  userDataRoot: string
  projects: Array<{ id: string; name: string; path: string; createdAt: string }>
  currentProjectId?: string
  sessions: Array<{ id: string; title: string; createdAt: string; updatedAt: string }>
  currentSessionId?: string
  stream: StreamItem[]
  running: boolean
  nav: 'chat' | 'memory' | 'skills' | 'settings'
  setBoot: (v: Partial<AppState>) => void
  setStream: (items: StreamItem[]) => void
  appendStream: (item: StreamItem) => void
  patchThinking: (token: string) => void
  setRunning: (v: boolean) => void
  setNav: (nav: AppState['nav']) => void
}

export const useAppStore = create<AppState>((set) => ({
  bootstrapped: false,
  auth: null,
  serverUrl: 'http://localhost:9299',
  userDataRoot: '',
  projects: [],
  sessions: [],
  stream: [],
  running: false,
  nav: 'chat',
  setBoot: (v) => set((s) => ({ ...s, ...v, bootstrapped: true })),
  setStream: (items) => set({ stream: items }),
  appendStream: (item) => set((s) => ({ stream: [...s.stream, item] })),
  patchThinking: (token) =>
    set((s) => {
      const stream = [...s.stream]
      const last = stream[stream.length - 1]
      if (last?.kind === 'thinking') {
        stream[stream.length - 1] = { kind: 'thinking', content: last.content + token }
      } else {
        stream.push({ kind: 'thinking', content: token })
      }
      return { stream }
    }),
  setRunning: (running) => set({ running }),
  setNav: (nav) => set({ nav }),
}))
