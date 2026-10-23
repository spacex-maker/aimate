import { NavLink, Outlet } from 'react-router-dom'
import {
  Activity,
  BookOpen,
  Bot,
  Box,
  LogOut,
  Receipt,
  SlidersHorizontal,
  Users,
} from 'lucide-react'
import { useAdminAuth } from '../../auth/AdminAuthContext'
import { CLOUD_SITE_ORIGIN } from '../../lib/host'

const nav = [
  { to: '/admin', end: true, label: '模型管理', icon: Box },
  { to: '/admin/agents', end: false, label: '官方 Agent', icon: Bot },
  { to: '/admin/knowledge', end: false, label: '官方知识库', icon: BookOpen },
  { to: '/admin/host', end: false, label: '主机状态', icon: Activity },
  { to: '/admin/users', end: false, label: '用户管理', icon: Users },
  { to: '/admin/billing', end: false, label: '计费管理', icon: Receipt },
  { to: '/admin/system', end: false, label: '系统配置', icon: SlidersHorizontal },
]

export function AdminLayout() {
  const { state, logout } = useAdminAuth()
  const admin = state.status === 'authed' ? state.admin : null

  return (
    <div className="admin-shell">
      <aside className="admin-shell__aside">
        <div>
          <p className="admin-shell__brand-title">AIMATEX Chat</p>
          <p className="admin-shell__brand-sub">运营后台</p>
        </div>
        <nav className="admin-shell__nav">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `admin-shell__nav-link${isActive ? ' is-active' : ''}`
              }
            >
              <item.icon size={16} />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-2 text-sm">
          <a href={CLOUD_SITE_ORIGIN} className="admin-shell__nav-link">
            用户站
          </a>
          {admin && (
            <div className="px-2 text-[12px] text-[var(--admin-dim)]">
              <p className="truncate text-[var(--admin-ink)]">{admin.displayName}</p>
              <p className="truncate">{admin.email}</p>
            </div>
          )}
          <button
            type="button"
            className="admin-shell__nav-link w-full text-left"
            onClick={() => {
              logout()
              window.location.href = '/login'
            }}
          >
            <LogOut size={16} />
            退出
          </button>
        </div>
      </aside>
      <main className="admin-shell__main">
        <Outlet />
      </main>
    </div>
  )
}
