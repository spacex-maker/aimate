import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bot, Brain, Cpu, KeyRound, LayoutDashboard, LogOut, PanelLeftClose, PanelLeftOpen, Settings2, Wrench, Clock } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { useAuth } from '../../hooks/useAuth'
import { AdminModal } from '../admin/AdminModal'
import { agentApi } from '../../api/agent'
import { StatusBadge } from '../agent/StatusBadge'
import type { SessionResponse } from '../../types/agent'

const baseLinks = [
  { to: '/', label: '控制台', icon: LayoutDashboard },
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
  const [adminModalOpen, setAdminModalOpen] = useState(false)

  const { data: recentSessions = [], isLoading: recentLoading } = useQuery<SessionResponse[]>({
    queryKey: ['recent-sessions', user?.userId],
    queryFn: () => agentApi.getRecentSessions(6),
    enabled: !!user?.userId,
    refetchInterval: 30_000,
  })

  const handleLogout = () => {
    logout()
    toast.success('已退出登录')
    navigate('/login', { replace: true })
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
      <div className={clsx('flex items-center border-b border-white/10 flex-shrink-0', collapsed ? 'justify-center px-0 py-4' : 'gap-2.5 px-5 py-5')}>
        <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center flex-shrink-0">
          <Bot className="w-5 h-5 text-white" />
        </div>
        {!collapsed && (
          <div className="min-w-0 flex-1">
            <div className="text-sm font-bold text-white leading-none">OpenForgeX</div>
            <div className="text-[10px] text-white/40 mt-0.5">Autonomous Agent</div>
          </div>
        )}
      </div>

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
        <div className="space-y-1">
          {baseLinks.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) => linkClass(isActive)}
              title={collapsed ? label : undefined}
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </div>

        {/* 会话区：显示在「我的工具」下方，仅展开侧栏时展示 */}
        {!collapsed && (recentLoading || recentSessions.length > 0) && (
          <div className="mt-4 space-y-1">
            <div className="px-1 flex items-center justify-between text-[11px] text-white/35 uppercase tracking-wide">
              <span>会话</span>
              {recentSessions.length > 0 && (
                <span className="text-white/25">{recentSessions.length}</span>
              )}
            </div>
            <div className="space-y-1">
              {recentLoading && recentSessions.length === 0 ? (
                <div className="px-2 py-2 text-[11px] text-white/30">加载中…</div>
              ) : recentSessions.length === 0 ? (
                <div className="px-2 py-2 text-[11px] text-white/30">暂无会话</div>
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

      {/* 管理入口（仅管理员） */}
      {user?.role === 'ADMIN' && (
        <div className="px-2 pb-2 border-t border-white/10 pt-2 flex-shrink-0">
          <button
            type="button"
            onClick={() => setAdminModalOpen(true)}
            title="管理"
            className={clsx(
              'flex items-center w-full rounded-lg text-sm text-white/50 hover:text-white/80 hover:bg-white/5 transition-colors',
              collapsed ? 'justify-center p-2' : 'gap-3 px-3 py-2'
            )}
          >
            <Settings2 className="w-4 h-4 flex-shrink-0" />
            {!collapsed && <span>管理</span>}
          </button>
        </div>
      )}

      {/* User info + logout */}
      <div className={clsx('border-t border-white/10 space-y-2 flex-shrink-0', collapsed ? 'px-0 py-3 flex flex-col items-center' : 'px-3 py-3')}>
        {user && (
          <div className={clsx('flex items-center py-1.5', collapsed ? 'flex-col gap-1' : 'gap-2.5 px-2')}>
            <div
              className="w-7 h-7 rounded-full bg-blue-600/30 border border-blue-500/30 flex items-center justify-center flex-shrink-0"
              title={collapsed ? (user.displayName ?? user.username ?? '') : undefined}
            >
              <span className="text-[11px] font-bold text-blue-300 uppercase">
                {(user.displayName ?? user.username ?? '?').charAt(0)}
              </span>
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <div className="text-xs text-white/80 font-medium truncate">{user.displayName ?? user.username ?? ''}</div>
                <div className="text-[10px] text-white/30 truncate">@{user.username ?? ''}</div>
              </div>
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

      {adminModalOpen && <AdminModal onClose={() => setAdminModalOpen(false)} />}
    </nav>
  )
}
