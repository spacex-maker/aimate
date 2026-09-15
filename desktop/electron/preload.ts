import { contextBridge, ipcRenderer } from 'electron'

export type HostApiResult<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; error: string }

const api = {
  invoke<T = unknown>(method: string, params?: unknown): Promise<HostApiResult<T>> {
    return ipcRenderer.invoke('hostapi:invoke', method, params)
  },
  openDirectory(): Promise<string | null> {
    return ipcRenderer.invoke('dialog:openDirectory')
  },
  onEvent(callback: (event: { type: string; payload?: unknown }) => void): () => void {
    const handler = (_: Electron.IpcRendererEvent, data: { type: string; payload?: unknown }) => {
      callback(data)
    }
    ipcRenderer.on('hostapi:event', handler)
    return () => ipcRenderer.removeListener('hostapi:event', handler)
  },
}

contextBridge.exposeInMainWorld('aimate', api)

export type AimateHostBridge = typeof api
