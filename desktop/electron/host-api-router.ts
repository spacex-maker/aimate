import path from 'node:path'
import fs from 'node:fs'
import { app } from 'electron'
import type { AppStore } from './cloud/store'
import type { CloudClient } from './cloud/client'
import type { AgentHost } from './agent-host'
import { createProjectAt, ensureDir, readProjectMeta } from './agent-host/projects'
import * as sessions from './agent-host/sessions'

type Deps = {
  store: AppStore
  cloud: CloudClient
  agentHost: AgentHost
}

function ok<T>(data: T) {
  return { ok: true as const, data }
}

export class HostApiRouter {
  constructor(private deps: Deps) {}

  async invoke(method: string, params?: unknown): Promise<{ ok: true; data: unknown } | { ok: false; error: string }> {
    try {
      const data = await this.dispatch(method, params)
      return ok(data)
    } catch (e) {
      return { ok: false, error: e instanceof Error ? e.message : String(e) }
    }
  }

  private async dispatch(method: string, params?: unknown): Promise<unknown> {
    const p = (params ?? {}) as Record<string, unknown>
    const { store, cloud, agentHost } = this.deps

    switch (method) {
      case 'ping':
        return { pong: true, version: app.getVersion() }

      case 'config.get':
        return {
          ...store.getConfig(),
          userDataRoot: store.userDataRoot(),
          auth: store.getAuth()
            ? {
                userId: store.getAuth()!.userId,
                username: store.getAuth()!.username,
                displayName: store.getAuth()!.displayName,
                role: store.getAuth()!.role,
              }
            : null,
        }

      case 'config.set': {
        const next = store.setConfig({
          serverUrl: typeof p.serverUrl === 'string' ? p.serverUrl : undefined,
          preferredModel: typeof p.preferredModel === 'string' ? p.preferredModel : undefined,
          currentProjectId: typeof p.currentProjectId === 'string' ? p.currentProjectId : undefined,
        })
        return next
      }

      case 'auth.login': {
        const identifier = String(p.identifier ?? '')
        const password = String(p.password ?? '')
        const session = await cloud.login(identifier, password)
        return {
          userId: session.userId,
          username: session.username,
          displayName: session.displayName,
          role: session.role,
        }
      }

      case 'auth.logout':
        cloud.logout()
        return { loggedOut: true }

      case 'auth.me': {
        const auth = store.getAuth()
        if (!auth) return null
        return {
          userId: auth.userId,
          username: auth.username,
          displayName: auth.displayName,
          role: auth.role,
        }
      }

      case 'projects.list':
        return store.getProjects()

      case 'projects.create': {
        const name = String(p.name ?? 'Untitled').trim() || 'Untitled'
        let root = typeof p.path === 'string' && p.path ? p.path : ''
        if (!root) {
          const slug =
            name
              .replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '_')
              .replace(/\s+/g, '_')
              .replace(/_+/g, '_')
              .replace(/^_|_$/g, '')
              .slice(0, 48) || 'project'
          root = path.join(store.userDataRoot(), 'projects', `${slug}_${Date.now().toString(36)}`)
        }
        ensureDir(root)
        const meta = createProjectAt(root, name)
        const projects = [...store.getProjects().filter((x) => x.path !== meta.path), meta]
        store.setProjects(projects)
        store.setConfig({ currentProjectId: meta.id })
        // 新项目默认创建一个空会话
        const session = sessions.createSession(meta.path, '新会话')
        return { project: meta, session }
      }

      case 'workspace.ensureReady': {
        // 登录后确保有项目 + 当前选中一个全新会话
        let projects = store.getProjects()
        let projectId = store.getConfig().currentProjectId
        let project = projects.find((x) => x.id === projectId) ?? projects[0]
        if (!project) {
          const root = path.join(store.userDataRoot(), 'projects', `default_${Date.now().toString(36)}`)
          ensureDir(root)
          project = createProjectAt(root, '默认项目')
          projects = [project]
          store.setProjects(projects)
        }
        store.setConfig({ currentProjectId: project.id })
        const session = sessions.createSession(project.path, '新会话')
        return {
          project,
          projects: store.getProjects(),
          session,
          currentProjectId: project.id,
        }
      }

      case 'projects.open': {
        const dir = String(p.path ?? '')
        if (!dir || !fs.existsSync(dir)) throw new Error('目录不存在')
        let meta = readProjectMeta(dir)
        if (!meta) {
          meta = createProjectAt(dir, path.basename(dir))
        }
        const existing = store.getProjects().filter((x) => x.path !== meta!.path)
        store.setProjects([...existing, meta])
        store.setConfig({ currentProjectId: meta.id })
        return meta
      }

      case 'projects.select': {
        const id = String(p.id ?? '')
        if (!store.getProjects().some((x) => x.id === id)) throw new Error('项目不存在')
        store.setConfig({ currentProjectId: id })
        return store.getConfig()
      }

      case 'projects.remove': {
        const id = String(p.id ?? '')
        store.setProjects(store.getProjects().filter((x) => x.id !== id))
        if (store.getConfig().currentProjectId === id) {
          store.setConfig({ currentProjectId: undefined })
        }
        return { removed: true }
      }

      case 'sessions.list': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const projectPath = agentHost.resolveProjectPath(projectId)
        return sessions.listSessions(projectPath)
      }

      case 'sessions.create': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const projectPath = agentHost.resolveProjectPath(projectId)
        return sessions.createSession(projectPath, typeof p.title === 'string' ? p.title : undefined)
      }

      case 'sessions.get': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        const projectPath = agentHost.resolveProjectPath(projectId)
        return {
          events: sessions.readTranscript(projectPath, sessionId),
        }
      }

      case 'sessions.delete': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        sessions.deleteSession(agentHost.resolveProjectPath(projectId), sessionId)
        return { deleted: true }
      }

      case 'sessions.send': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        const message = String(p.message ?? '')
        // fire-and-forget agent loop; events stream via hostapi:event
        void agentHost.runSession(projectId, sessionId, message)
        return { started: true }
      }

      case 'sessions.cancel':
        agentHost.cancel()
        return { cancelled: true }

      case 'skills.list': {
        const projectId = typeof p.projectId === 'string' ? p.projectId : store.getConfig().currentProjectId
        return agentHost.listSkills(projectId)
      }

      case 'skills.setEnabled': {
        const name = String(p.name ?? '').toLowerCase()
        const enabled = Boolean(p.enabled)
        const disabled = new Set(store.getDisabledSkills().map((s) => s.toLowerCase()))
        if (enabled) disabled.delete(name)
        else disabled.add(name)
        store.setDisabledSkills([...disabled])
        return agentHost.listSkills(store.getConfig().currentProjectId)
      }

      case 'memory.list': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        return agentHost.memory.list(agentHost.resolveProjectPath(projectId), sessionId)
      }

      case 'memory.add': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        const content = String(p.content ?? '')
        const projectPath = agentHost.resolveProjectPath(projectId)
        let embedding: number[] | undefined
        try {
          const [emb] = await cloud.embed([content])
          embedding = emb
        } catch {
          /* optional */
        }
        return agentHost.memory.add(projectPath, sessionId, { content, embedding })
      }

      case 'memory.update': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        const id = String(p.id ?? '')
        return agentHost.memory.update(agentHost.resolveProjectPath(projectId), sessionId, id, {
          noCompress: typeof p.noCompress === 'boolean' ? p.noCompress : undefined,
          content: typeof p.content === 'string' ? p.content : undefined,
        })
      }

      case 'memory.delete': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        const ids = Array.isArray(p.ids) ? (p.ids as string[]) : [String(p.id ?? '')]
        agentHost.memory.delete(agentHost.resolveProjectPath(projectId), sessionId, ids.filter(Boolean))
        return { deleted: true }
      }

      case 'compress.prepare': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        return agentHost.compressMemories(projectId, sessionId)
      }

      case 'compress.execute': {
        const projectId = String(p.projectId ?? store.getConfig().currentProjectId ?? '')
        const sessionId = String(p.sessionId ?? '')
        if (!sessionId) throw new Error('缺少 sessionId')
        return agentHost.executeCompress(projectId, sessionId, {
          deleteIds: (p.deleteIds as string[]) ?? [],
          newMemories: (p.newMemories as Array<{ content: string; importance: number }>) ?? [],
        })
      }

      default:
        throw new Error(`Unknown host method: ${method}`)
    }
  }
}
