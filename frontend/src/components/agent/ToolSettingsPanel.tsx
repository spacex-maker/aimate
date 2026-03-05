import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { agentApi } from '../../api/agent'
import type { ToolSettingsDto } from '../../types/agent'
import toast from 'react-hot-toast'

interface Props {
  onClose?: () => void
}

const LABELS: Record<keyof ToolSettingsDto, { title: string }> = {
  memoryEnabled: { title: '长期记忆' },
  webSearchEnabled: { title: '联网搜索' },
  createToolEnabled: { title: 'AI 自主编写工具' },
  scriptExecEnabled: { title: '用户系统脚本执行' },
}

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
      className={`relative inline-flex h-6 w-11 shrink-0 rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white/50 ${
        checked ? 'bg-blue-500' : 'bg-white/20'
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow ring-0 transition-transform mt-0.5 ml-0.5 ${
          checked ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  )
}

export function ToolSettingsPanel({ onClose: _onClose }: Props) {
  const queryClient = useQueryClient()
  const { data: settings, isLoading } = useQuery({
    queryKey: ['tool-settings'],
    queryFn: () => agentApi.getToolSettings(),
  })

  const updateMutation = useMutation({
    mutationFn: (body: ToolSettingsDto) => agentApi.updateToolSettings(body),
    onMutate: async (next) => {
      await queryClient.cancelQueries({ queryKey: ['tool-settings'] })
      const prev = queryClient.getQueryData<ToolSettingsDto>(['tool-settings'])
      queryClient.setQueryData<ToolSettingsDto>(['tool-settings'], next)
      return { prev }
    },
    onError: (_err, _next, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(['tool-settings'], ctx.prev)
      toast.error('保存失败')
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['tool-settings'] }),
  })

  const handleToggle = (key: keyof ToolSettingsDto, value: boolean) => {
    if (!settings) return
    const next: ToolSettingsDto = { ...settings, [key]: value }
    updateMutation.mutate(next)
  }

  return (
    <div
      className="bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl w-72 overflow-hidden"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="px-3 py-2 border-b border-white/10">
        <h3 className="text-xs font-semibold text-white/80">系统工具开关</h3>
      </div>
      <div className="p-2 space-y-1.5">
        {isLoading ? (
          <p className="text-xs text-white/40 py-2">加载中…</p>
        ) : (
          (Object.keys(LABELS) as (keyof ToolSettingsDto)[]).map((key) => {
            const { title } = LABELS[key]
            const checked = settings?.[key] ?? true
            return (
              <div
                key={key}
                className="flex items-center justify-between gap-3 py-1.5 px-2 rounded-lg hover:bg-white/[0.04] transition-colors"
              >
                <span className="text-xs text-white/90">{title}</span>
                <ToggleSwitch
                  checked={checked}
                  onChange={(v) => handleToggle(key, v)}
                />
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
