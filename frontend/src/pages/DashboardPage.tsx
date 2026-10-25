import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import {
  Briefcase,
  Building2,
  SquarePen,
  Users,
  ArrowRight,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { multiAgentApi } from '../api/multiAgent'
import { officeApi } from '../api/multiAgent'
import { conversationsApi } from '../api/conversations'
import { CompositeAvatar } from '../components/chat/CompositeAvatar'
import { CreateConversationModal } from '../components/chat/CreateConversationModal'
import { generateAgentAvatarDataUrl } from '../lib/agentAvatar'

const QUICK = [
  { to: '#create-dm', label: '找伙伴聊天', desc: '一人一聊，继续同一窗口', icon: SquarePen, accent: 'text-blue-300 bg-blue-600/15 border-blue-500/25', action: 'session' as const },
  { to: '#create-group', label: '新建群组', desc: '多用户多 Agent', icon: Users, accent: 'text-cyan-300 bg-cyan-600/15 border-cyan-500/25', action: 'group' as const },
  { to: '/agents/hire', label: '招聘 Agent', desc: '按行业岗位雇佣', icon: Briefcase, accent: 'text-violet-300 bg-violet-600/15 border-violet-500/25', action: 'link' as const },
  { to: '/agents/mine', label: '我的 Agent', desc: '人设与 Soul', icon: Users, accent: 'text-emerald-300 bg-emerald-600/15 border-emerald-500/25', action: 'link' as const },
  { to: '/offices', label: '办公室', desc: '租用私有 CVM', icon: Building2, accent: 'text-amber-300 bg-amber-600/15 border-amber-500/25', action: 'link' as const },
]

export function DashboardPage() {
  const { user } = useAuth()
  const [createStep, setCreateStep] = useState<'choose' | 'dm' | 'group' | null>(null)

  const { data: agents = [] } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId,
  })
  const { data: office } = useQuery({
    queryKey: ['office-me', user?.userId],
    queryFn: () => officeApi.me(),
    enabled: !!user?.userId,
  })
  const { data: conversations = [] } = useQuery({
    queryKey: ['conversations', user?.userId],
    queryFn: () => conversationsApi.list(8),
    enabled: !!user?.userId,
  })

  const activeAgents = agents.filter((a) => a.status === 'ACTIVE')
  const name = user?.displayName || user?.username || '你好'

  return (
    <div className="h-full overflow-y-auto">
      <div className="max-w-4xl mx-auto px-6 py-10 space-y-10">
        <div>
          <h1 className="text-2xl font-bold text-white">控制台</h1>
          <p className="text-sm text-white/45 mt-1">
            {name}，从这里进入会话与 Agent 管理
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {QUICK.map((q) =>
            q.action === 'group' || q.action === 'session' ? (
              <button
                key={q.to}
                type="button"
                onClick={() => setCreateStep(q.action === 'group' ? 'group' : 'dm')}
                className={`rounded-xl border px-4 py-4 flex items-start gap-3 hover:bg-white/[0.04] transition-colors text-left ${q.accent}`}
              >
                <q.icon className="w-5 h-5 flex-shrink-0 mt-0.5" />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-white">{q.label}</div>
                  <div className="text-xs text-white/45 mt-0.5">{q.desc}</div>
                </div>
                <ArrowRight className="w-4 h-4 text-white/25 flex-shrink-0" />
              </button>
            ) : (
              <Link
                key={q.to}
                to={q.to}
                className={`rounded-xl border px-4 py-4 flex items-start gap-3 hover:bg-white/[0.04] transition-colors ${q.accent}`}
              >
                <q.icon className="w-5 h-5 flex-shrink-0 mt-0.5" />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-white">{q.label}</div>
                  <div className="text-xs text-white/45 mt-0.5">{q.desc}</div>
                </div>
                <ArrowRight className="w-4 h-4 text-white/25 flex-shrink-0" />
              </Link>
            ),
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="rounded-xl border border-white/10 bg-white/[0.02] px-4 py-3">
            <div className="text-[11px] text-white/35 uppercase tracking-wide">在岗 Agent</div>
            <div className="text-2xl font-semibold text-white mt-1">{activeAgents.length}</div>
          </div>
          <div className="rounded-xl border border-white/10 bg-white/[0.02] px-4 py-3">
            <div className="text-[11px] text-white/35 uppercase tracking-wide">会话</div>
            <div className="text-2xl font-semibold text-white mt-1">{conversations.length}</div>
          </div>
          <div className="rounded-xl border border-white/10 bg-white/[0.02] px-4 py-3">
            <div className="text-[11px] text-white/35 uppercase tracking-wide">办公室</div>
            <div className="text-sm font-medium text-white mt-2">
              {office?.ready || office?.status === 'ACTIVE'
                ? office.planName || '已租用'
                : '未租用'}
            </div>
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-medium text-white/70">最近聊天</h2>
            <button
              type="button"
              onClick={() => setCreateStep('dm')}
              className="text-xs text-blue-300/80 hover:text-blue-200"
            >
              找伙伴
            </button>
          </div>
          {conversations.length === 0 ? (
            <div className="rounded-xl border border-dashed border-white/15 px-4 py-10 text-center text-sm text-white/35">
              还没有聊天，
              <button type="button" onClick={() => setCreateStep('dm')} className="text-blue-300 ml-1">
                去开聊
              </button>
            </div>
          ) : (
            <ul className="space-y-1.5">
              {conversations.map((c) => {
                const href =
                  c.kind === 'GROUP'
                    ? `/groups/${c.publicId || c.id}`
                    : `/session/${c.sessionId || c.id}`
                const urls =
                  c.avatarUrls?.length
                    ? c.avatarUrls
                    : c.avatarUrl
                      ? [c.avatarUrl]
                      : c.kind === 'SESSION'
                        ? [generateAgentAvatarDataUrl(String(c.userAgentId || c.id))]
                        : []
                const title =
                  c.kind === 'SESSION'
                    ? c.agentDisplayTitle || c.title || c.agentName || '伙伴'
                    : c.title || '群聊'
                return (
                  <li key={`${c.kind}:${c.id}`}>
                    <Link
                      to={href}
                      className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.02] hover:bg-white/[0.05] px-3 py-2.5 transition-colors"
                    >
                      <CompositeAvatar urls={urls} size={40} />
                      <div className="min-w-0 flex-1 py-0.5">
                        <div className="text-[15px] font-medium text-white truncate leading-snug">
                          {title}
                          {c.pinned ? <span className="ml-1 text-[10px] text-amber-300/70">置顶</span> : null}
                        </div>
                        <div className="mt-0.5 text-[12px] text-white/55 truncate leading-snug">
                          {c.lastMessagePreview?.trim() || '暂无消息'}
                        </div>
                      </div>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </div>
      {createStep && (
        <CreateConversationModal
          initialStep={createStep}
          onClose={() => setCreateStep(null)}
        />
      )}
    </div>
  )
}
