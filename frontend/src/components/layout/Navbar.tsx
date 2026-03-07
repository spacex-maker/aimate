import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { Bot, Brain, Cpu, KeyRound, LayoutDashboard, LogOut, Settings2, Wrench } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { useAuth } from '../../hooks/useAuth'
import { AdminModal } from '../admin/AdminModal'

const baseLinks = [
  { to: '/', label: '控制台', icon: LayoutDashboard },
  { to: '/memory', label: '长期记忆', icon: Brain },
  { to: '/api-keys', label: 'API 密钥', icon: KeyRound },
  { to: '/embedding-models', label: '向量模型', icon: Cpu },
  { to: '/tools', label: '我的工具', icon: Wrench },
]

export function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [adminModalOpen, setAdminModalOpen] = useState(false)

  const handleLogout = () => {
    logout()
    toast.success('已退出登录')
    navigate('/login', { replace: true })
  }

  return (
    <nav className="h-full flex flex-col bg-[#111] border-r border-white/10">
      {/* Logo */}
      <div className="flex items-center gap-2.5 px-5 py-5 border-b border-white/10">
        <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center flex-shrink-0">
          <Bot className="w-5 h-5 text-white" />
        </div>
        <div>
          <div className="text-sm font-bold text-white leading-none">OpenForgeX</div>
          <div className="text-[10px] text-white/40 mt-0.5">Autonomous Agent</div>
        </div>
      </div>

      {/* Nav links */}
      <div className="flex-1 py-3 px-2 space-y-1">
        {baseLinks.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors',
                isActive
                  ? 'bg-blue-600/20 text-blue-400'
                  : 'text-white/50 hover:text-white/80 hover:bg-white/5'
              )
            }
          >
            <Icon className="w-4 h-4 flex-shrink-0" />
            {label}
          </NavLink>
        ))}
      </div>

      {/* 管理入口（仅管理员） */}
      {user?.role === 'ADMIN' && (
        <div className="px-2 pb-2 border-t border-white/10 pt-2">
          <button
            type="button"
            onClick={() => setAdminModalOpen(true)}
            className="flex items-center gap-3 w-full px-3 py-2 rounded-lg text-sm text-white/50 hover:text-white/80 hover:bg-white/5 transition-colors"
          >
            <Settings2 className="w-4 h-4 flex-shrink-0" />
            管理
          </button>
        </div>
      )}

      {/* User info + logout */}
      <div className="px-3 py-3 border-t border-white/10 space-y-2">
        {user && (
          <div className="flex items-center gap-2.5 px-2 py-1.5">
            {/* Avatar placeholder */}
            <div className="w-7 h-7 rounded-full bg-blue-600/30 border border-blue-500/30 flex items-center justify-center flex-shrink-0">
              <span className="text-[11px] font-bold text-blue-300 uppercase">
                {(user.displayName ?? user.username ?? '?').charAt(0)}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-xs text-white/80 font-medium truncate">{user.displayName ?? user.username ?? ''}</div>
              <div className="text-[10px] text-white/30 truncate">@{user.username ?? ''}</div>
            </div>
            <button
              onClick={handleLogout}
              title="退出登录"
              className="text-white/25 hover:text-red-400 transition-colors flex-shrink-0"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        )}
        <div className="px-2">
          <div className="text-[10px] text-white/20 font-mono">v0.1.0-SNAPSHOT</div>
        </div>
      </div>

      {adminModalOpen && <AdminModal onClose={() => setAdminModalOpen(false)} />}
    </nav>
  )
}
