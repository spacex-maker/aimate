import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { MessagesSquare, SquarePen, Users, X } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { groupsApi } from '../../api/groups'
import { multiAgentApi, type UserAgentBrief } from '../../api/multiAgent'
import { useAuth } from '../../hooks/useAuth'
import { AgentAvatar } from '../agent/AgentAvatar'
import { fileToChatImage } from '../../lib/chatImage'

type Step = 'choose' | 'group'

type Props = {
  onClose: () => void
  /** 打开时直接进入建群（跳过选择） */
  initialStep?: Step
}

export function CreateConversationModal({ onClose, initialStep = 'choose' }: Props) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [step, setStep] = useState<Step>(initialStep)
  const [title, setTitle] = useState('')
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [pickedAgents, setPickedAgents] = useState<number[]>([])
  const [includeSecretary, setIncludeSecretary] = useState(true)
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: agents = [], isLoading: agentsLoading } = useQuery({
    queryKey: ['my-agents', user?.userId],
    queryFn: () => multiAgentApi.listMine(),
    enabled: !!user?.userId && step === 'group',
  })

  const activeAgents = useMemo(
    () => agents.filter((a: UserAgentBrief) => a.status === 'ACTIVE'),
    [agents],
  )
  const secretary = useMemo(
    () => activeAgents.find((a) => a.kind === 'SECRETARY'),
    [activeAgents],
  )

  useEffect(() => {
    if (step !== 'group' || !secretary) return
    setPickedAgents((prev) => {
      if (prev.includes(secretary.id)) return prev
      if (!includeSecretary) return prev
      return [...prev, secretary.id]
    })
  }, [step, secretary, includeSecretary])

  const createMut = useMutation({
    mutationFn: async () => {
      const agentIds = pickedAgents.filter((id) => !secretary || id !== secretary.id || includeSecretary)
      // includeSecretary 单独传；同时避免重复传秘书 id
      const withoutSec = secretary
        ? agentIds.filter((id) => id !== secretary.id)
        : agentIds
      // 先建群（不带超大 data URL，避免拖垮请求）；头像成功后再 PATCH
      const g = await groupsApi.create({
        title: title.trim(),
        agentIds: withoutSec,
        includeSecretary: includeSecretary && !!secretary,
      })
      if (avatarUrl) {
        try {
          await groupsApi.patch(g.publicId, { avatarUrl })
        } catch {
          toast.error('群已创建，但头像上传失败，可在群设置中重试')
        }
      }
      return g
    },
    onSuccess: (g) => {
      toast.success('群聊已创建')
      qc.invalidateQueries({ queryKey: ['conversations'] })
      qc.invalidateQueries({ queryKey: ['groups'] })
      onClose()
      navigate(`/groups/${g.publicId}`)
    },
    onError: (e: Error) => {
      console.error('[createGroup]', e)
      toast.error(e.message || '创建失败')
    },
  })

  const onPickAvatar = async (file: File | null) => {
    if (!file) return
    try {
      const dataUrl = await fileToChatImage(file)
      setAvatarUrl(dataUrl)
    } catch {
      toast.error('头像读取失败')
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <button type="button" className="absolute inset-0 bg-black/60" aria-label="关闭" onClick={onClose} />
      <div className="relative w-full max-w-md rounded-2xl border border-white/10 bg-[#161616] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <h2 className="text-sm font-semibold text-white">
            {step === 'choose' ? '新建' : '新建群组'}
          </h2>
          <button type="button" onClick={onClose} className="p-1 rounded-lg text-white/40 hover:text-white hover:bg-white/5">
            <X className="w-4 h-4" />
          </button>
        </div>

        {step === 'choose' ? (
          <div className="p-4 space-y-2">
            <button
              type="button"
              onClick={() => {
                onClose()
                navigate('/app/new')
              }}
              className="w-full flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] px-4 py-3.5 text-left transition-colors"
            >
              <div className="w-10 h-10 rounded-xl bg-blue-600/20 text-blue-300 flex items-center justify-center">
                <SquarePen className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-white">新建会话</div>
                <div className="text-xs text-white/40 mt-0.5">与单个 Agent 一对一对话</div>
              </div>
            </button>
            <button
              type="button"
              onClick={() => setStep('group')}
              className="w-full flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] px-4 py-3.5 text-left transition-colors"
            >
              <div className="w-10 h-10 rounded-xl bg-cyan-600/20 text-cyan-300 flex items-center justify-center">
                <MessagesSquare className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-white">新建群组</div>
                <div className="text-xs text-white/40 mt-0.5">拉入多个 Agent / 用户一起聊</div>
              </div>
            </button>
          </div>
        ) : (
          <div className="p-4 space-y-4 max-h-[70vh] overflow-y-auto">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="w-14 h-14 rounded-xl border border-dashed border-white/20 bg-white/5 overflow-hidden flex items-center justify-center hover:border-blue-400/40"
                title="设置群头像"
              >
                {avatarUrl ? (
                  <img src={avatarUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <Users className="w-6 h-6 text-white/35" />
                )}
              </button>
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => onPickAvatar(e.target.files?.[0] ?? null)}
              />
              <div className="flex-1 min-w-0 space-y-1">
                <label className="block text-xs text-white/45">群名称</label>
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="例如：产品周会"
                  className="w-full rounded-lg border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-blue-500/40"
                  autoFocus
                />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm text-white/70 cursor-pointer">
              <input
                type="checkbox"
                checked={includeSecretary}
                disabled={!secretary}
                onChange={(e) => {
                  const on = e.target.checked
                  setIncludeSecretary(on)
                  if (!secretary) return
                  setPickedAgents((prev) => {
                    if (on) return prev.includes(secretary.id) ? prev : [...prev, secretary.id]
                    return prev.filter((id) => id !== secretary.id)
                  })
                }}
              />
              建议拉入默认秘书（记上下文）
              {!secretary && <span className="text-white/30 text-xs">暂无可用秘书</span>}
            </label>

            <div>
              <div className="text-xs text-white/45 mb-2">选择我的 Agent</div>
              {agentsLoading ? (
                <div className="text-xs text-white/30 py-4 text-center">加载中…</div>
              ) : activeAgents.length === 0 ? (
                <div className="text-xs text-white/35 py-3">暂无可用 Agent，可先去招聘</div>
              ) : (
                <ul className="space-y-1 max-h-48 overflow-y-auto">
                  {activeAgents.map((a) => {
                    const checked = pickedAgents.includes(a.id)
                    const isSec = a.kind === 'SECRETARY'
                    return (
                      <li key={a.id}>
                        <label
                          className={clsx(
                            'flex items-center gap-2.5 rounded-lg px-2 py-2 cursor-pointer hover:bg-white/5',
                            checked && 'bg-white/[0.04]',
                          )}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={(e) => {
                              const on = e.target.checked
                              if (isSec) setIncludeSecretary(on)
                              setPickedAgents((prev) =>
                                on ? [...prev, a.id] : prev.filter((x) => x !== a.id),
                              )
                            }}
                          />
                          <AgentAvatar agent={a} size={28} />
                          <span className="text-sm text-white/80 truncate">
                            {a.name}
                            {isSec && <span className="text-white/35 text-xs ml-1">秘书</span>}
                          </span>
                        </label>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>

            <div className="flex gap-2 pt-1">
              <button
                type="button"
                className="flex-1 rounded-xl border border-white/15 py-2.5 text-sm text-white/60 hover:bg-white/5"
                onClick={() => (initialStep === 'group' ? onClose() : setStep('choose'))}
              >
                返回
              </button>
              <button
                type="button"
                disabled={!title.trim() || createMut.isPending}
                className="flex-1 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-40 py-2.5 text-sm font-medium"
                onClick={() => createMut.mutate()}
              >
                {createMut.isPending ? '创建中…' : '创建群聊'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
