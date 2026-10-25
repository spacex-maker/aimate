import { useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Brain, Code2, Globe2, Terminal } from 'lucide-react'
import { agentApi } from '../../api/agent'
import type { ToolSettingsDto } from '../../types/agent'
import toast from 'react-hot-toast'
import clsx from 'clsx'

const SAVE_DEBOUNCE_MS = 400

interface Props {
  onClose?: () => void
  /** 单聊会话：有则读写会话覆盖 */
  sessionId?: string | null
  /** 首页选中的伙伴：无 session 时按 Agent 默认展示，改写时会先打开单聊再覆盖 */
  userAgentId?: number | null
}

const LABELS: Record<
  keyof Omit<ToolSettingsDto, 'overridden'>,
  { title: string; hint: string; icon: typeof Brain }
> = {
  memoryEnabled: {
    title: '长期记忆',
    hint: '对话写入 Agent 绑定的 Soul，跨会话召回',
    icon: Brain,
  },
  webSearchEnabled: {
    title: '联网搜索',
    hint: '开启后 Agent 可调用 Tavily 检索公开网页（需后台已配置 Tavily Key）',
    icon: Globe2,
  },
  createToolEnabled: {
    title: '自主编写工具',
    hint: 'Agent 可沉淀简单工具到工具箱，由平台保存并执行（无需办公室）',
    icon: Code2,
  },
  scriptExecEnabled: {
    title: '办公室脚本',
    hint: '开通办公室后：装包 / 任意命令 / 写沙箱文件等重活',
    icon: Terminal,
  },
}

type FlagKey = keyof typeof LABELS

function ToggleSwitch({
  checked,
  onChange,
}: {
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/40',
        checked ? 'bg-cyan-500' : 'bg-white/15'
      )}
    >
      <span
        className={clsx(
          'pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow transition-transform mt-0.5 ml-0.5',
          checked ? 'translate-x-4' : 'translate-x-0'
        )}
      />
    </button>
  )
}

export function ToolSettingsPanel({ onClose: _onClose, sessionId = null, userAgentId = null }: Props) {
  const queryClient = useQueryClient()
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const scope = { sessionId, userAgentId }
  const queryKey = ['tool-settings', sessionId ?? null, userAgentId ?? null] as const

  const { data: settings, isLoading } = useQuery({
    queryKey,
    queryFn: () => agentApi.getToolSettings(scope),
  })

  const updateMutation = useMutation({
    mutationFn: (body: ToolSettingsDto) => agentApi.updateToolSettings(body, scope),
    onMutate: async (next) => {
      await queryClient.cancelQueries({ queryKey })
      const prev = queryClient.getQueryData<ToolSettingsDto>(queryKey)
      queryClient.setQueryData<ToolSettingsDto>(queryKey, { ...next, overridden: true })
      return { prev }
    },
    onError: (_err, _next, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(queryKey, ctx.prev)
      toast.error('保存失败')
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['tool-settings'] }),
  })

  useEffect(
    () => () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    },
    []
  )

  const handleToggle = (key: FlagKey, value: boolean) => {
    if (!settings) return
    const next: ToolSettingsDto = { ...settings, [key]: value, overridden: true }
    queryClient.setQueryData<ToolSettingsDto>(queryKey, next)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      debounceRef.current = null
      const latest = queryClient.getQueryData<ToolSettingsDto>(queryKey)
      if (latest) updateMutation.mutate(latest)
    }, SAVE_DEBOUNCE_MS)
  }

  const followingAgent = settings != null && settings.overridden === false

  return (
    <div
      className="w-[20.5rem] overflow-hidden rounded-2xl border border-white/[0.1] bg-[#12151c]/95 backdrop-blur-xl shadow-[0_24px_64px_-16px_rgba(0,0,0,0.75)]"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="relative px-3.5 pt-3 pb-2.5 border-b border-white/[0.06]">
        <div
          className="pointer-events-none absolute inset-x-0 top-0 h-16 opacity-50"
          style={{ background: 'radial-gradient(ellipse at top, rgba(34,211,238,0.12), transparent 70%)' }}
        />
        <h3 className="relative text-[11px] font-semibold tracking-wide text-white/80 uppercase">
          系统工具
        </h3>
        <p className="relative mt-0.5 text-[10px] text-white/35">
          {followingAgent
            ? '当前跟随 Agent 默认；在此修改仅影响本会话'
            : settings?.overridden
              ? '本会话已单独设置；改 Agent 默认会同步到所有单聊'
              : '按会话能力开关，更改即时生效'}
        </p>
      </div>
      <div className="p-2 space-y-1">
        {isLoading ? (
          <p className="text-xs text-white/40 py-4 text-center">加载中…</p>
        ) : (
          (Object.keys(LABELS) as FlagKey[]).map((key) => {
            const { title, hint, icon: Icon } = LABELS[key]
            const checked = settings?.[key] ?? true
            return (
              <div
                key={key}
                className={clsx(
                  'flex items-center gap-2.5 rounded-xl px-2.5 py-2 transition-colors',
                  checked ? 'bg-white/[0.04]' : 'bg-transparent hover:bg-white/[0.03]'
                )}
              >
                <div
                  className={clsx(
                    'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border',
                    checked
                      ? 'border-cyan-400/25 bg-cyan-500/10 text-cyan-300'
                      : 'border-white/10 bg-white/[0.03] text-white/35'
                  )}
                >
                  <Icon className="h-3.5 w-3.5" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-medium text-white/90 leading-tight">{title}</div>
                  <div className="mt-0.5 text-[10px] leading-snug text-white/35 line-clamp-2">{hint}</div>
                </div>
                <ToggleSwitch checked={checked} onChange={(v) => handleToggle(key, v)} />
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
