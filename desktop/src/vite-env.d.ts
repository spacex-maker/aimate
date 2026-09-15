/// <reference types="vite/client" />

interface AimateHostBridge {
  invoke<T = unknown>(method: string, params?: unknown): Promise<{ ok: true; data: T } | { ok: false; error: string }>
  openDirectory(): Promise<string | null>
  onEvent(callback: (event: { type: string; payload?: unknown }) => void): () => void
}

declare global {
  interface Window {
    aimate: AimateHostBridge
  }
}

export {}
