type HostResult<T> = { ok: true; data: T } | { ok: false; error: string }

function bridge() {
  if (!window.aimate) {
    throw new Error('Host API unavailable（preload 未注入，请重启桌面端）')
  }
  return window.aimate
}

async function invoke<T>(method: string, params?: unknown): Promise<T> {
  const res = (await bridge().invoke(method, params)) as HostResult<T>
  if (!res || typeof res !== 'object') {
    throw new Error('Host API 返回异常')
  }
  if (!res.ok) throw new Error(res.error || '未知错误')
  return res.data
}

export type ProjectMeta = { id: string; name: string; path: string; createdAt: string }
export type SessionMeta = { id: string; title: string; createdAt: string; updatedAt: string }

export const hostApi = {
  isAvailable: () => typeof window !== 'undefined' && !!window.aimate,
  ping: () => invoke<{ pong: boolean }>('ping'),
  getConfig: () =>
    invoke<{
      serverUrl: string
      preferredModel?: string
      currentProjectId?: string
      userDataRoot: string
      auth: { userId: number; username: string; displayName: string; role: string } | null
    }>('config.get'),
  setConfig: (partial: { serverUrl?: string; preferredModel?: string; currentProjectId?: string }) =>
    invoke('config.set', partial),
  login: (identifier: string, password: string) =>
    invoke<{ userId: number; username: string; displayName: string; role: string }>('auth.login', {
      identifier,
      password,
    }),
  logout: () => invoke('auth.logout'),
  me: () =>
    invoke<{ userId: number; username: string; displayName: string; role: string } | null>('auth.me'),
  listProjects: () => invoke<ProjectMeta[]>('projects.list'),
  createProject: (name: string, path?: string) =>
    invoke<{ project: ProjectMeta; session: SessionMeta }>('projects.create', { name, path }),
  openProject: (path: string) => invoke<ProjectMeta>('projects.open', { path }),
  selectProject: (id: string) => invoke('projects.select', { id }),
  removeProject: (id: string) => invoke('projects.remove', { id }),
  ensureWorkspaceReady: () =>
    invoke<{
      project: ProjectMeta
      projects: ProjectMeta[]
      session: SessionMeta
      currentProjectId: string
    }>('workspace.ensureReady'),
  listSessions: (projectId: string) => invoke<SessionMeta[]>('sessions.list', { projectId }),
  createSession: (projectId: string, title?: string) =>
    invoke<SessionMeta>('sessions.create', { projectId, title }),
  getSession: (projectId: string, sessionId: string) =>
    invoke<{ events: Array<Record<string, unknown>> }>('sessions.get', { projectId, sessionId }),
  deleteSession: (projectId: string, sessionId: string) =>
    invoke('sessions.delete', { projectId, sessionId }),
  sendMessage: (projectId: string, sessionId: string, message: string) =>
    invoke('sessions.send', { projectId, sessionId, message }),
  cancelSession: () => invoke('sessions.cancel'),
  listSkills: (projectId?: string) =>
    invoke<
      Array<{
        name: string
        description: string
        baseDir: string
        enabled: boolean
        source: string
      }>
    >('skills.list', { projectId }),
  setSkillEnabled: (name: string, enabled: boolean) =>
    invoke('skills.setEnabled', { name, enabled }),
  listMemory: (projectId: string, sessionId: string) =>
    invoke<
      Array<{
        id: string
        content: string
        importance: number
        noCompress: boolean
        createdAt: string
      }>
    >('memory.list', { projectId, sessionId }),
  addMemory: (projectId: string, sessionId: string, content: string) =>
    invoke('memory.add', { projectId, sessionId, content }),
  updateMemory: (
    projectId: string,
    sessionId: string,
    id: string,
    patch: { noCompress?: boolean; content?: string },
  ) => invoke('memory.update', { projectId, sessionId, id, ...patch }),
  deleteMemory: (projectId: string, sessionId: string, ids: string[]) =>
    invoke('memory.delete', { projectId, sessionId, ids }),
  prepareCompress: (projectId: string, sessionId: string) =>
    invoke<{ deleteIds: string[]; newMemories: Array<{ content: string; importance: number }> }>(
      'compress.prepare',
      { projectId, sessionId },
    ),
  executeCompress: (
    projectId: string,
    sessionId: string,
    plan: { deleteIds: string[]; newMemories: Array<{ content: string; importance: number }> },
  ) => invoke<{ deleted: number; added: number }>('compress.execute', { projectId, sessionId, ...plan }),
  openDirectory: () => bridge().openDirectory(),
  onEvent: (cb: (event: { type: string; payload?: unknown }) => void) => {
    if (!window.aimate) return () => {}
    return window.aimate.onEvent(cb)
  },
}
