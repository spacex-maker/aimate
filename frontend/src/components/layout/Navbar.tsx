import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Archive,
  ArchiveRestore,
  Bot,
  Briefcase,
  Building2,
  Cog,
  Copy,
  Cpu,
  KeyRound,
  LayoutDashboard,
  LogOut,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Pin,
  Plus,
  Share2,
  SquarePen,
  Trash2,
  Users,
  Wrench,
} from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { useAuth } from '../../hooks/useAuth'
import { UserCenterModal } from '../billing/UserCenterModal'
import { SystemSettingsModal } from '../settings/SystemSettingsModal'
import { conversationsApi, type ConversationItem } from '../../api/conversations'
import { CompositeAvatar } from '../chat/CompositeAvatar'
import { CreateConversationModal } from '../chat/CreateConversationModal'
import { ConversationRenameModal } from '../chat/ConversationRenameModal'
import { DeleteSessionModal } from '../agent/DeleteSessionModal'
import { generateAgentAvatarDataUrl } from '../../lib/agentAvatar'
import type { SessionResponse } from '../../types/agent'

const baseLinks = [
  { to: '/app', label: '控制台', icon: LayoutDashboard },
  { to: '/agents/hire', label: 'Agent 招聘', icon: Briefcase },
  { to: '/offices', label: '办公室租用', icon: Building2 },
  { to: '/agents/mine', label: '我的 Agent', icon: Users },
  { to: '/api-keys', label: 'API 密钥', icon: KeyRound },
  { to: '/embedding-models', label: '向量模型', icon: Cpu },
  { to: '/tools', label: '工具', icon: Wrench },
]

interface NavbarProps {
  collapsed: boolean
  onToggle: () => void
}

function sessionStub(item: ConversationItem): SessionResponse {
  return {
    sessionId: item.sessionId || item.id,
    status: 'IDLE',
    taskDescription: item.title || '会话',
    iterationCount: 0,
    result: null,
    errorMessage: null,
    hidden: !!item.archived,
    userAgentId: item.userAgentId,
    agentName: item.agentName,
    agentCareerName: item.agentCareerName,
    agentOriginalName: item.agentOriginalName,
    agentDisplayTitle: item.agentDisplayTitle,
    pinned: item.pinned,
    pinnedAt: item.pinnedAt,
    wsSubscribePath: '',
    createTime: item.createTime || '',
    updateTime: item.updateTime || '',
  }
}

/** 会话列表空闲态右侧弱提示时间 */
function formatConversationTime(iso?: string | null): string {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return ''
  const now = Date.now()
  const diff = Math.max(0, now - t)
  const min = Math.floor(diff / 60_000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}小时前`
  const day = Math.floor(hour / 24)
  if (day === 1) return '昨天'
  if (day < 7) return `${day}天前`
  const d = new Date(t)
  const y = d.getFullYear()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const dd = `${d.getDate()}`.padStart(2, '0')
  return y === new Date().getFullYear() ? `${m}/${dd}` : `${y}/${m}/${dd}`
}

export function Navbar({ collapsed, onToggle }: NavbarProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const qc = useQueryClient()
  const [systemSettingsOpen, setSystemSettingsOpen] = useState(false)
  const [userCenterOpen, setUserCenterOpen] = useState(false)
  const [menuId, setMenuId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [showArchived, setShowArchived] = useState(false)
  const [renameTarget, setRenameTarget] = useState<ConversationItem | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ConversationItem | null>(null)

  const { data: conversations = [], isLoading: recentLoading } = useQuery({
    queryKey: ['conversations', user?.userId, showArchived ? 'archived' : 'active'],
    queryFn: () => conversationsApi.list(24, { archived: showArchived }),
    enabled: !!user?.userId,
    refetchInterval: 30_000,
  })

  const invalidateInbox = () => {
    qc.invalidateQueries({ queryKey: ['conversations'] })
  }

  const pinMut = useMutation({
    mutationFn: async (item: ConversationItem) => {
      const next = !item.pinned
      if (item.kind === 'SESSION') await conversationsApi.pinSession(item.sessionId || item.id, next)
      else await conversationsApi.pinGroup(item.publicId || item.id, next)
    },
    onSuccess: () => {
      invalidateInbox()
      setMenuId(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const archiveMut = useMutation({
    mutationFn: async ({ item, archived }: { item: ConversationItem; archived: boolean }) => {
      if (item.kind === 'SESSION') await conversationsApi.archiveSession(item.sessionId || item.id, archived)
      else await conversationsApi.archiveGroup(item.publicId || item.id, archived)
    },
    onSuccess: (_d, { archived }) => {
      toast.success(archived ? '已归档' : '已取消归档')
      invalidateInbox()
      setMenuId(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const renameMut = useMutation({
    mutationFn: async ({ item, title }: { item: ConversationItem; title: string }) => {
      if (item.kind === 'SESSION') {
        await conversationsApi.renameSession(item.sessionId || item.id, title)
      } else {
        await conversationsApi.renameGroupRemark(item.publicId || item.id, title)
      }
    },
    onSuccess: () => {
      toast.success('已重命名')
      invalidateInbox()
      setRenameTarget(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const shareMut = useMutation({
    mutationFn: async (item: ConversationItem) => {
      if (item.kind === 'GROUP') {
        const r = await conversationsApi.shareGroup(item.publicId || item.id)
        return `${window.location.origin}${r.path}`
      }
      const sid = item.sessionId || item.id
      return `${window.location.origin}/session/${sid}`
    },
    onSuccess: async (url, item) => {
      try {
        await navigator.clipboard.writeText(url)
        toast.success(item.kind === 'GROUP' ? '分享链接已复制' : '会话链接已复制')
      } catch {
        toast.success(url)
      }
      setMenuId(null)
    },
    onError: (e: Error) => toast.error(e.message || '分享失败'),
  })

  const deleteMut = useMutation({
    mutationFn: (opts: {
      sessionId: string
      deleteMessages: boolean
      deleteMemories: boolean
      hideOnly: boolean
    }) =>
      conversationsApi.deleteSession(opts.sessionId, {
        deleteMessages: opts.deleteMessages,
        deleteMemories: opts.deleteMemories,
        hideOnly: opts.hideOnly,
      }),
    onSuccess: (_d, vars) => {
      toast.success('已处理')
      invalidateInbox()
      setDeleteTarget(null)
      if (location.pathname === `/session/${vars.sessionId}`) {
        navigate('/app', { replace: true })
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const handleLogout = () => {
    logout()
    toast.success('已退出登录')
    navigate('/', { replace: true })
  }

  const openConversation = (item: ConversationItem) => {
    if (item.kind === 'GROUP') navigate(`/groups/${item.publicId || item.id}`)
    else navigate(`/session/${item.sessionId || item.id}`)
  }

  const isConversationActive = (item: ConversationItem) => {
    if (item.kind === 'GROUP') {
      const gid = item.publicId || item.id
      return location.pathname === `/groups/${gid}`
    }
    const sid = item.sessionId || item.id
    return location.pathname === `/session/${sid}`
  }

  const avatarUrlsFor = (item: ConversationItem): string[] => {
    if (item.avatarUrls && item.avatarUrls.length > 0) {
      return item.avatarUrls.map((u) =>
        u || (item.kind === 'SESSION' ? generateAgentAvatarDataUrl(String(item.userAgentId || item.id)) : u),
      )
    }
    if (item.avatarUrl) return [item.avatarUrl]
    if (item.kind === 'SESSION') {
      return [generateAgentAvatarDataUrl(String(item.userAgentId || item.agentName || item.id))]
    }
    return []
  }

  const linkClass = (isActive: boolean) =>
    clsx(
      'flex items-center rounded-lg text-sm transition-colors',
      collapsed ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2',
      isActive ? 'bg-blue-600/20 text-blue-400' : 'text-white/50 hover:text-white/80 hover:bg-white/5'
    )

  return (
    <nav className="h-full flex flex-col min-w-0">
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

      <div className="flex-1 py-3 px-2 space-y-2 overflow-y-auto">
        <button
          type="button"
          onClick={() => setCreateOpen(true)}
          title="找伙伴或建群"
          className={clsx(
            'flex items-center w-full rounded-lg text-sm font-medium transition-colors',
            'bg-blue-600/15 text-blue-300 border border-blue-500/25 hover:bg-blue-600/25 hover:text-blue-200',
            collapsed ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2.5',
          )}
        >
          <SquarePen className="w-4 h-4 flex-shrink-0" />
          {!collapsed && <span>发起聊天</span>}
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

        {!collapsed && (
          <div className="mt-4 space-y-1">
            <div className="px-1 flex items-center justify-between text-[11px] text-white/35 uppercase tracking-wide">
              <span>{showArchived ? '已归档' : '聊天'}</span>
              <div className="flex items-center gap-0.5">
                <button
                  type="button"
                  onClick={() => {
                    setShowArchived((v) => !v)
                    setMenuId(null)
                  }}
                  title={showArchived ? '返回会话' : '查看已归档'}
                  className={clsx(
                    'p-0.5 rounded transition-colors',
                    showArchived
                      ? 'text-amber-300/90 hover:bg-white/5'
                      : 'text-white/35 hover:text-amber-300/80 hover:bg-white/5',
                  )}
                >
                  <Archive className="w-3.5 h-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => setCreateOpen(true)}
                  title="找伙伴或建群"
                  className="p-0.5 rounded text-white/35 hover:text-blue-300 hover:bg-white/5 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
            <div className="space-y-0.5">
              {recentLoading && conversations.length === 0 ? (
                <div className="px-2 py-2 text-[11px] text-white/30">加载中…</div>
              ) : conversations.length === 0 ? (
                <button
                  type="button"
                  onClick={() => (showArchived ? setShowArchived(false) : setCreateOpen(true))}
                  className="w-full px-2 py-2 text-[11px] text-white/40 hover:text-blue-300 text-left transition-colors"
                >
                  {showArchived ? '暂无归档，返回聊天' : '暂无聊天，点击发起'}
                </button>
              ) : (
                conversations.map((item) => {
                  const key = `${item.kind}:${item.id}`
                  const menuOpen = menuId === key
                  const relTime = formatConversationTime(item.updateTime)
                  return (
                    <div key={key} className="relative group/conv">
                      <div
                        className={clsx(
                          'relative w-full rounded-lg transition-colors',
                          isConversationActive(item)
                            ? 'bg-blue-600/20 text-blue-100'
                            : 'hover:bg-white/5',
                        )}
                      >
                        <button
                          type="button"
                          onClick={() => openConversation(item)}
                          onContextMenu={(e) => {
                            e.preventDefault()
                            setMenuId(key)
                          }}
                          className="w-full min-w-0 flex items-center gap-2.5 text-left pl-2 py-2 pr-9"
                        >
                          <div className="relative flex-shrink-0">
                            <CompositeAvatar
                              urls={avatarUrlsFor(item)}
                              size={36}
                              title={item.title}
                            />
                            {item.pinned && (
                              <Pin className="w-2.5 h-2.5 text-amber-300 absolute -top-0.5 -right-0.5" />
                            )}
                          </div>
                          <div className="flex-1 min-w-0 py-0.5">
                            <div className="truncate text-[14px] font-medium leading-snug text-white">
                              {item.kind === 'SESSION'
                                ? item.agentDisplayTitle ||
                                  item.title ||
                                  item.agentName ||
                                  '伙伴'
                                : item.title || '群聊'}
                            </div>
                            <div className="mt-0.5 truncate text-[12px] leading-snug text-white/55">
                              {item.lastMessagePreview?.trim() || '暂无消息'}
                            </div>
                          </div>
                        </button>
                        {relTime && (
                          <span
                            className={clsx(
                              'pointer-events-none absolute right-2 top-2 text-[10px] leading-none tabular-nums text-white/35 transition-opacity',
                              menuOpen
                                ? 'opacity-0'
                                : 'opacity-100 group-hover/conv:opacity-0',
                            )}
                          >
                            {relTime}
                          </span>
                        )}
                        <button
                          type="button"
                          title="更多"
                          onClick={(e) => {
                            e.stopPropagation()
                            setMenuId((cur) => (cur === key ? null : key))
                          }}
                          className={clsx(
                            'absolute right-1 top-1/2 z-10 -translate-y-1/2 p-1 rounded-md',
                            'bg-black/55 text-white/70 hover:text-white hover:bg-black/70',
                            'shadow-sm backdrop-blur-[2px] transition-opacity',
                            menuOpen
                              ? 'opacity-100'
                              : 'opacity-0 pointer-events-none group-hover/conv:opacity-100 group-hover/conv:pointer-events-auto',
                          )}
                        >
                          <MoreHorizontal className="w-3.5 h-3.5" />
                        </button>
                      </div>
                      {menuOpen && (
                        <>
                          <button
                            type="button"
                            className="fixed inset-0 z-40 cursor-default"
                            aria-label="关闭菜单"
                            onClick={() => setMenuId(null)}
                          />
                          <div className="absolute right-1 top-8 z-50 w-40 rounded-lg border border-white/15 bg-[#1a1a1a] shadow-xl py-1 text-xs">
                            {!showArchived && (
                              <button
                                type="button"
                                className="w-full px-3 py-1.5 text-left hover:bg-white/10 flex items-center gap-2"
                                onClick={() => pinMut.mutate(item)}
                              >
                                <Pin className="w-3 h-3" />
                                {item.pinned ? '取消置顶' : '置顶'}
                              </button>
                            )}
                            <button
                              type="button"
                              className="w-full px-3 py-1.5 text-left hover:bg-white/10 flex items-center gap-2 text-white/70"
                              onClick={() => {
                                setRenameTarget(item)
                                setMenuId(null)
                              }}
                            >
                              <Pencil className="w-3 h-3" />
                              {item.kind === 'GROUP' ? '设置备注' : '会话备注'}
                            </button>
                            <button
                              type="button"
                              className="w-full px-3 py-1.5 text-left hover:bg-white/10 flex items-center gap-2 text-white/70"
                              onClick={() => shareMut.mutate(item)}
                            >
                              {item.kind === 'GROUP' ? (
                                <Share2 className="w-3 h-3" />
                              ) : (
                                <Copy className="w-3 h-3" />
                              )}
                              {item.kind === 'GROUP' ? '分享记录' : '复制链接'}
                            </button>
                            <button
                              type="button"
                              className="w-full px-3 py-1.5 text-left hover:bg-white/10 flex items-center gap-2 text-white/70"
                              onClick={() =>
                                archiveMut.mutate({ item, archived: !showArchived })
                              }
                            >
                              {showArchived ? (
                                <>
                                  <ArchiveRestore className="w-3 h-3" />
                                  取消归档
                                </>
                              ) : (
                                <>
                                  <Archive className="w-3 h-3" />
                                  归档
                                </>
                              )}
                            </button>
                            {item.kind === 'SESSION' && (
                              <button
                                type="button"
                                className="w-full px-3 py-1.5 text-left hover:bg-white/10 flex items-center gap-2 text-red-300/90"
                                onClick={() => {
                                  setDeleteTarget(item)
                                  setMenuId(null)
                                }}
                              >
                                <Trash2 className="w-3 h-3" />
                                删除…
                              </button>
                            )}
                          </div>
                        </>
                      )}
                    </div>
                  )
                })
              )}
            </div>
          </div>
        )}
      </div>

      <div className={clsx('border-t border-white/10 space-y-2 flex-shrink-0', collapsed ? 'px-0 py-3 flex flex-col items-center' : 'px-3 py-3')}>
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
              className="w-7 h-7 rounded-full bg-blue-600/30 border border-blue-500/30 flex items-center justify-center flex-shrink-0 hover:border-blue-400/50 overflow-hidden"
              title="用户中心"
            >
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <span className="text-[11px] font-bold text-blue-300 uppercase">
                  {(user.displayName ?? user.username ?? '?').charAt(0)}
                </span>
              )}
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
      {createOpen && <CreateConversationModal onClose={() => setCreateOpen(false)} />}
      {renameTarget && (
        <ConversationRenameModal
          title={renameTarget.kind === 'GROUP' ? '设置群备注' : '会话备注'}
          hint={
            renameTarget.kind === 'GROUP'
              ? '备注仅自己可见，会话列表优先显示备注'
              : '单聊列表大标题始终为伙伴姓名；备注仅作内部说明'
          }
          initialValue={renameTarget.title || ''}
          loading={renameMut.isPending}
          onClose={() => setRenameTarget(null)}
          onConfirm={(title) => renameMut.mutate({ item: renameTarget, title })}
        />
      )}
      {deleteTarget && (
        <DeleteSessionModal
          session={sessionStub(deleteTarget)}
          loading={deleteMut.isPending}
          onClose={() => setDeleteTarget(null)}
          onConfirm={(opts) =>
            deleteMut.mutate({
              sessionId: deleteTarget.sessionId || deleteTarget.id,
              ...opts,
            })
          }
        />
      )}
    </nav>
  )
}
