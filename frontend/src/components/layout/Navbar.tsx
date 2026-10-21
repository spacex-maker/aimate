import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bot, Brain, Briefcase, Cog, Cpu, KeyRound, LayoutDashboard, LogOut, PanelLeftClose, PanelLeftOpen, Plus, SquarePen, Users, Wrench, Clock } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { useAuth } from '../../hooks/useAuth'
import { UserCenterModal } from '../billing/UserCenterModal'
import { SystemSettingsModal } from '../settings/SystemSettingsModal'
import { agentApi } from '../../api/agent'
import { StatusBadge } from '../agent/StatusBadge'
import type { SessionResponse } from '../../types/agent'

const baseLinks = [
  { to: '/app', label: '控制台', icon: LayoutDashboard },
  { to: '/agents/hire', label: 'Agent 招聘', icon: Briefcase },
  { to: '/agents/mine', label: '我的 Agent', icon: Users },
  { to: '/memory', label: '长期记忆', icon: Brain },
  { to: '/api-keys', label: 'API 密钥', icon: KeyRound },
  { to: '/embedding-models', label: '向量模型', icon: Cpu },
  { to: '/tools', label: '我的工具', icon: Wrench },
]

interface NavbarProps {
  collapsed: boolean
  onToggle: () => void
}

export function Navbar({ collapsed, onToggle }: NavbarProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [systemSettingsOpen, setSystemSettingsOpen] = useState(false)
  const [userCenterOpen, setUserCenterOpen] = useState(false)

  const { data: recentSessions = [], isLoading: recentLoading } = useQuery<SessionResponse[]>({
    queryKey: ['recent-sessions', user?.userId],
    queryFn: () => agentApi.getRecentSessions(6),
    enabled: !!user?.userId,
    refetchInterval: 30_000,
  })

  const handleLogout = () => {
    logout()
    toast.success('已退出登录')
    navigate('/', { replace: true })
  }

  const linkClass = (isActive: boolean) =>
    clsx(
      'flex items-center rounded-lg text-sm transition-colors',
      collapsed ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2',
      isActive ? 'bg-blue-600/20 text-blue-400' : 'text-white/50 hover:text-white/80 hover:bg-white/5'
    )

  return (
    <nav className="h-full flex flex-col min-w-0">
      {/* Logo + 收起按钮 */}
      <NavLink
        to="/app"
        end
        className={clsx(
          'flex items-center border-b border-white/10 flex-shrink-0 text-inherit no-underline',
          collapsed ? 'justify-center px-0 py-4' : 'gap-2.5 px-5 py-5'
        )}
        title="控制台"
      >
        <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center flex-shrink-0">
          <Bot className="w-5 h-5 text-white" />
        </div>
        {!collapsed && (
          <div className="min-w-0 flex-1">
            <div className="text-sm font-bold text-white leading-none">AIMATEX-AGENT</div>
            <div className="text-[10px] text-white/40 mt-0.5">Autonomous Agent</div>
          </div>
        )}
      </NavLink>

      {/* 收起/展开 */}
      <div className="px-2 pt-2 flex-shrink-0">
        <button
          type="button"
          onClick={onToggle}
          title={collapsed ? '展开菜单' : '收起菜单'}
          className={clsx(
            'flex items-center rounded-lg text-white/50 hover:text-white/80 hover:bg-white/5 transition-colors w-full',
            collapsed ? 'justify-center p-2' : 'gap-3 px-3 py-2'
          )}
        >
          {collapsed ? <PanelLeftOpen className="w-5 h-5" /> : <PanelLeftClose className="w-4 h-4 flex-shrink-0" />}
          {!collapsed && <span className="text-sm">收起菜单</span>}
        </button>
      </div>

      {/* Nav links + 最近会话 */}
      <div className="flex-1 py-3 px-2 space-y-2 overflow-y-auto">
        <button
          type="button"
          onClick={() => navigate('/app')}
          title="新建会话"
          className={clsx(
            'flex items-center w-full rounded-lg text-sm font-medium transition-colors',
            'bg-blue-600/15 text-blue-300 border border-blue-500/25 hover:bg-blue-600/25 hover:text-blue-200',
            collapsed ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2.5',
          )}
        >
          <SquarePen className="w-4 h-4 flex-shrink-0" />
          {!collapsed && <span>新建会话</span>}
        </button>

        <div className="space-y-1">
          {baseLinks.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/app'}
              className={({ isActive }) => linkClass(isActive)}
              title={collapsed ? label : undefined}
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </div>

        {/* 会话区：显示在「我的工具」下方，仅展开侧栏时展示 */}
        {!collapsed && (
          <div className="mt-4 space-y-1">
            <div className="px-1 flex items-center justify-between text-[11px] text-white/35 uppercase tracking-wide">
              <span>会话</span>
              <button
                type="button"
                onClick={() => navigate('/app')}
                title="新建会话"
                className="p-0.5 rounded text-white/35 hover:text-blue-300 hover:bg-white/5 transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>
            <div className="space-y-1">
              {recentLoading && recentSessions.length === 0 ? (
                <div className="px-2 py-2 text-[11px] text-white/30">加载中…</div>
              ) : recentSessions.length === 0 ? (
                <button
                  type="button"
                  onClick={() => navigate('/app')}
                  className="w-full px-2 py-2 text-[11px] text-white/40 hover:text-blue-300 text-left transition-colors"
                >
                  暂无会话，点击新建
                </button>
              ) : (
                recentSessions.map((s) => (
                  <button
                    key={s.sessionId}
                    type="button"
                    onClick={() => navigate(`/session/${s.sessionId}`)}
                    className="w-full px-2.5 py-2 rounded-lg bg-white/0 hover:bg-white/5 text-left text-[11px] text-white/70 flex items-center gap-2 transition-colors"
                  >
                    <StatusBadge status={s.status} />
                    <div className="flex-1 min-w-0">
                      <div className="truncate text-xs text-white/80">
                        {s.taskDescription || '会话'}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 text-[10px] text-white/35">
                        <span className="font-mono truncate">{s.sessionId.slice(0, 8)}…</span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-2.5 h-2.5" />
                          {s.iterationCount} 轮
                        </span>
                      </div>
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>
        )}
      </div>

      {/* 系统设置 + 个人信息 */}
      <div className={clsx('border-t border-white/10 space-y-2 flex-shrink-0', collapsed ? 'px-0 py-3 flex flex-col items-center' : 'px-3 py-3')}>
        {/* 系统设置：个人信息上方 */}
        <button
          type="button"
          onClick={() => setSystemSettingsOpen(true)}
          title="系统设置"
          className={clsx(
            'flex items-center w-full rounded-lg text-sm text-white/50 hover:text-white/80 hover:bg-white/5 transition-colors',
            collapsed ? 'justify-center p-2' : 'gap-3 px-3 py-2'
          )}
        >
          <Cog className="w-4 h-4 flex-shrink-0" />
          {!collapsed && <span>系统设置</span>}
        </button>
        {user && (
          <div className={clsx('flex items-center py-1.5', collapsed ? 'flex-col gap-1' : 'gap-2.5 px-2')}>
            <button
              type="button"
              onClick={() => setUserCenterOpen(true)}
              className="w-7 h-7 rounded-full bg-blue-600/30 border border-blue-500/30 flex items-center justify-center flex-shrink-0 hover:border-blue-400/50"
              title="用户中心"
            >
              <span className="text-[11px] font-bold text-blue-300 uppercase">
                {(user.displayName ?? user.username ?? '?').charAt(0)}
              </span>
            </button>
            {!collapsed && (
              <button
                type="button"
                onClick={() => setUserCenterOpen(true)}
                className="flex-1 min-w-0 text-left"
                title="用户中心"
              >
                <div className="text-xs text-white/80 font-medium truncate">{user.displayName ?? user.username ?? ''}</div>
                <div className="text-[10px] text-white/30 truncate">@{user.username ?? ''}</div>
              </button>
            )}
            <button
              onClick={handleLogout}
              title="退出登录"
              className="text-white/25 hover:text-red-400 transition-colors flex-shrink-0"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        )}
        {!collapsed && (
          <div className="px-2">
            <div className="text-[10px] text-white/20 font-mono">v0.1.0-SNAPSHOT</div>
          </div>
        )}
      </div>

      {systemSettingsOpen && <SystemSettingsModal onClose={() => setSystemSettingsOpen(false)} />}
      {userCenterOpen && <UserCenterModal onClose={() => setUserCenterOpen(false)} />}
    </nav>
  )
}
