import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { MessagesSquare, Plus, Users } from 'lucide-react'
import toast from 'react-hot-toast'
import { groupsApi, type GroupBrief } from '../api/groups'
import { multiAgentApi, type UserAgentBrief } from '../api/multiAgent'
import { useAuth } from '../hooks/useAuth'

export function GroupsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [includeSecretary, setIncludeSecretary] = useState(true)

  const { data: groups = [], isLoading } = useQuery({
    queryKey: ['groups', user?.userId],
    queryFn: () => groupsApi.list(),
    enabled: !!user?.userId,
  })

  const { data: agents = [] } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId && creating,
  })

  const secretary = useMemo(
    () => agents.find((a: UserAgentBrief) => a.kind === 'SECRETARY' && a.status === 'ACTIVE'),
    [agents],
  )

  const createMut = useMutation({
    mutationFn: () =>
      groupsApi.create({
        title: title.trim(),
        includeSecretary: includeSecretary && !!secretary,
      }),
    onSuccess: (g) => {
      toast.success('群组已创建')
      qc.invalidateQueries({ queryKey: ['groups'] })
      qc.invalidateQueries({ queryKey: ['conversations'] })
      setCreating(false)
      setTitle('')
      navigate(`/groups/${g.publicId}`)
    },
    onError: (e: Error) => toast.error(e.message || '创建失败'),
  })

  return (
    <div className="h-full overflow-y-auto p-6 md:p-8">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center justify-between gap-3 mb-6">
          <div>
            <h1 className="text-xl font-semibold text-white flex items-center gap-2">
              <MessagesSquare className="w-5 h-5 text-blue-400" />
              群组
            </h1>
            <p className="text-sm text-white/45 mt-1">多用户 · 多 Agent · 独立消息域</p>
          </div>
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-500 px-3.5 py-2 text-sm font-medium"
          >
            <Plus className="w-4 h-4" />
            新建群组
          </button>
        </div>

        {creating && (
          <div className="mb-6 rounded-xl border border-white/10 bg-white/[0.03] p-4 space-y-3">
            <label className="block text-xs text-white/50">群名称</label>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="例如：产品周会"
              className="w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-blue-500/50"
            />
            <label className="flex items-center gap-2 text-sm text-white/70 cursor-pointer">
              <input
                type="checkbox"
                checked={includeSecretary}
                onChange={(e) => setIncludeSecretary(e.target.checked)}
                disabled={!secretary}
                className="rounded border-white/30"
              />
              建议一并拉入默认秘书（记上下文与事件）
              {!secretary && <span className="text-white/35 text-xs">（暂无可用秘书）</span>}
            </label>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                className="px-3 py-1.5 text-sm text-white/60 hover:text-white"
                onClick={() => setCreating(false)}
              >
                取消
              </button>
              <button
                type="button"
                disabled={!title.trim() || createMut.isPending}
                onClick={() => createMut.mutate()}
                className="rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-40 px-3.5 py-1.5 text-sm"
              >
                {createMut.isPending ? '创建中…' : '创建'}
              </button>
            </div>
          </div>
        )}

        {isLoading ? (
          <div className="text-sm text-white/40 py-12 text-center">加载中…</div>
        ) : groups.length === 0 ? (
          <div className="rounded-xl border border-dashed border-white/15 py-16 text-center text-white/40 text-sm">
            还没有群组，点右上角新建
          </div>
        ) : (
          <ul className="space-y-2">
            {groups.map((g: GroupBrief) => (
              <li key={g.publicId}>
                <button
                  type="button"
                  onClick={() => navigate(`/groups/${g.publicId}`)}
                  className="w-full flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] px-4 py-3 text-left transition-colors"
                >
                  <div className="w-10 h-10 rounded-full bg-blue-600/30 flex items-center justify-center text-blue-200 flex-shrink-0 overflow-hidden">
                    {g.avatarUrl ? (
                      <img src={g.avatarUrl} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <Users className="w-5 h-5" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium text-white truncate">{g.title}</div>
                    <div className="text-xs text-white/40 mt-0.5">
                      {g.memberCount ?? 0} 位成员
                      {g.myRole ? ` · ${roleLabel(g.myRole)}` : ''}
                    </div>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function roleLabel(role: string) {
  if (role === 'OWNER') return '群主'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}
