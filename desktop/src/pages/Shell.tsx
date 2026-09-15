import { useState } from 'react'
import clsx from 'clsx'
import { FolderKanban, MessageSquare, Brain, Sparkles, Settings, Plus, FolderOpen } from 'lucide-react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore } from '../stores/app'
import { NameDialog } from '../components/NameDialog'
import { ChatPage } from './ChatPage'
import { MemoryPage } from './MemoryPage'
import { SkillsPage } from './SkillsPage'
import { SettingsPage } from './SettingsPage'

export function Shell() {
  const { auth, projects, currentProjectId, nav, setNav, setBoot } = useAppStore()
  const [creating, setCreating] = useState(false)

  const current = projects.find((p) => p.id === currentProjectId)

  const refreshProjects = async (opts?: { sessionId?: string; clearStream?: boolean }) => {
    const list = await hostApi.listProjects()
    const cfg = await hostApi.getConfig()
    const projectId = cfg.currentProjectId
    let sessions = useAppStore.getState().sessions
    let sessionId = opts?.sessionId ?? useAppStore.getState().currentSessionId
    if (projectId) {
      sessions = await hostApi.listSessions(projectId)
      if (!sessionId || !sessions.some((s) => s.id === sessionId)) {
        sessionId = sessions[0]?.id
      }
    }
    setBoot({
      auth: useAppStore.getState().auth,
      serverUrl: cfg.serverUrl,
      userDataRoot: cfg.userDataRoot,
      projects: list,
      currentProjectId: projectId,
    })
    useAppStore.setState({
      sessions,
      currentSessionId: sessionId,
      ...(opts?.clearStream ? { stream: [] } : {}),
      nav: 'chat',
    })
  }

  const createProject = async (name: string) => {
    setCreating(false)
    try {
      const { project, session } = await hostApi.createProject(name)
      await refreshProjects({ sessionId: session.id, clearStream: true })
      toast.success(`项目「${project.name}」已创建`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  const openProject = async () => {
    const dir = await hostApi.openDirectory()
    if (!dir) return
    try {
      await hostApi.openProject(dir)
      const ready = await hostApi.ensureWorkspaceReady()
      await refreshProjects({ sessionId: ready.session.id, clearStream: true })
      toast.success('项目已打开，已进入新会话')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '打开失败')
    }
  }

  const selectProject = async (id: string) => {
    try {
      await hostApi.selectProject(id)
      const session = await hostApi.createSession(id, '新会话')
      await refreshProjects({ sessionId: session.id, clearStream: true })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '切换失败')
    }
  }

  return (
    <div className="h-full flex">
      {creating && (
        <NameDialog
          title="新建项目"
          label="项目名称"
          defaultValue="My Project"
          confirmText="创建"
          onCancel={() => setCreating(false)}
          onConfirm={(name) => void createProject(name)}
        />
      )}

      <aside className="w-60 flex-shrink-0 border-r border-white/10 bg-surface-raised flex flex-col">
        <div className="px-4 py-4 border-b border-white/10">
          <div className="text-lg font-semibold tracking-tight">AIMate</div>
          <div className="text-[11px] text-white/40 mt-0.5 truncate">{auth?.displayName || auth?.username}</div>
        </div>

        <div className="px-3 py-3 border-b border-white/10">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] uppercase tracking-wide text-white/40 flex items-center gap-1">
              <FolderKanban className="w-3 h-3" /> 项目
            </span>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => void openProject()}
                className="p-1 rounded hover:bg-white/10 text-white/50"
                title="打开目录"
              >
                <FolderOpen className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => setCreating(true)}
                className="p-1 rounded hover:bg-white/10 text-white/50"
                title="新建"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
          <div className="space-y-0.5 max-h-40 overflow-auto">
            {projects.length === 0 && (
              <div className="text-xs text-white/30 px-2 py-1">暂无项目</div>
            )}
            {projects.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => void selectProject(p.id)}
                className={clsx(
                  'w-full text-left px-2 py-1.5 rounded text-xs truncate',
                  p.id === currentProjectId ? 'bg-white/10 text-white' : 'text-white/60 hover:bg-white/5',
                )}
                title={p.path}
              >
                {p.name}
              </button>
            ))}
          </div>
        </div>

        <nav className="flex-1 py-2">
          {(
            [
              { id: 'chat', label: '会话', icon: MessageSquare },
              { id: 'memory', label: '记忆', icon: Brain },
              { id: 'skills', label: 'Skills', icon: Sparkles },
              { id: 'settings', label: '设置', icon: Settings },
            ] as const
          ).map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setNav(item.id)}
              className={clsx(
                'w-full flex items-center gap-2.5 px-4 py-2.5 text-sm',
                nav === item.id ? 'bg-white/10 text-white' : 'text-white/60 hover:bg-white/5 hover:text-white/90',
              )}
            >
              <item.icon className="w-4 h-4" />
              {item.label}
            </button>
          ))}
        </nav>

        <div className="px-3 py-3 border-t border-white/10 text-[10px] text-white/30 truncate">
          {current?.path || '未选择项目'}
        </div>
      </aside>

      <main className="flex-1 min-w-0 min-h-0">
        {!currentProjectId && nav !== 'settings' ? (
          <div className="h-full flex flex-col items-center justify-center text-white/40 text-sm gap-3">
            <p>正在准备工作区…</p>
          </div>
        ) : (
          <>
            {nav === 'chat' && <ChatPage />}
            {nav === 'memory' && <MemoryPage />}
            {nav === 'skills' && <SkillsPage />}
            {nav === 'settings' && <SettingsPage />}
          </>
        )}
      </main>
    </div>
  )
}
