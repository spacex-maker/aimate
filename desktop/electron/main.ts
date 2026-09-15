import { app, BrowserWindow, ipcMain, shell, dialog } from 'electron'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { HostApiRouter } from './host-api-router'
import { AppStore } from './cloud/store'
import { CloudClient } from './cloud/client'
import { AgentHost } from './agent-host'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

process.env.DIST_ELECTRON = path.join(__dirname)
process.env.DIST = path.join(__dirname, '../dist')
process.env.VITE_PUBLIC = process.env.VITE_DEV_SERVER_URL
  ? path.join(__dirname, '../public')
  : process.env.DIST

let mainWindow: BrowserWindow | null = null
let store: AppStore
let cloud: CloudClient
let agentHost: AgentHost
let router: HostApiRouter

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 840,
    minWidth: 960,
    minHeight: 640,
    title: 'AIMate',
    show: false,
    backgroundColor: '#12141a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('did-fail-load', (_e, code, desc, url) => {
    console.error('[AIMate] did-fail-load', code, desc, url)
  })

  if (process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL)
    mainWindow.webContents.openDevTools({ mode: 'detach' })
  } else {
    mainWindow.loadFile(path.join(process.env.DIST!, 'index.html'))
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  store = new AppStore()
  cloud = new CloudClient(store)
  agentHost = new AgentHost({ store, cloud, getWindow: () => mainWindow })
  router = new HostApiRouter({ store, cloud, agentHost })

  ipcMain.handle('hostapi:invoke', async (_event, method: string, params?: unknown) => {
    try {
      return await router.invoke(method, params)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      console.error('[AIMate] hostapi error', method, message)
      return { ok: false, error: message }
    }
  })

  ipcMain.handle('dialog:openDirectory', async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ['openDirectory', 'createDirectory'],
    })
    if (result.canceled || !result.filePaths[0]) return null
    return result.filePaths[0]
  })

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  agentHost?.dispose()
})
