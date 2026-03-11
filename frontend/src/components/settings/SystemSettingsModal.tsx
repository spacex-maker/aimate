import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Box, Play, RotateCw, Square } from 'lucide-react'
import toast from 'react-hot-toast'
import { agentApi } from '../../api/agent'
import type { ScriptEnvStatusDto } from '../../types/agent'

export function SystemSettingsModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()

  const { data: scriptStatus, isLoading } = useQuery<ScriptEnvStatusDto>({
    queryKey: ['script-status', 'settings'],
    queryFn: () => agentApi.getScriptStatus(),
    staleTime: 0,
  })

  const refreshStatus = () => {
    queryClient.invalidateQueries({ queryKey: ['script-status'] })
    queryClient.invalidateQueries({ queryKey: ['script-status', 'settings'] })
  }

  const startMutation = useMutation({
    mutationFn: () => agentApi.startContainer(),
    onSuccess: (res) => {
      if (res?.success) {
        toast.success(res.containerName ? `已启动：${res.containerName}` : '已启动')
        refreshStatus()
      } else {
        toast.error(res?.message ?? '启动失败')
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const restartMutation = useMutation({
    mutationFn: () => agentApi.restartContainer(),
    onSuccess: (res) => {
      if (res?.success) {
        toast.success(res.containerName ? `已重启：${res.containerName}` : '已重启')
        refreshStatus()
      } else {
        toast.error(res?.message ?? '重启失败')
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const stopMutation = useMutation({
    mutationFn: () => agentApi.stopContainer(),
    onSuccess: (res) => {
      if (res?.success) {
        toast.success('已关闭')
        refreshStatus()
      } else {
        toast.error(res?.message ?? '关闭失败')
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const dockerEnabled = scriptStatus?.dockerEnabled ?? false
  const isRunning = scriptStatus?.containerStatus === 'running'
  const pending = startMutation.isPending || restartMutation.isPending || stopMutation.isPending

  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-900 rounded-xl border border-white/10 shadow-2xl max-w-md w-full max-h-[85vh] overflow-hidden flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/10">
          <h2 className="text-lg font-semibold text-white">系统设置</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-white/50 hover:text-white hover:bg-white/10 transition-colors"
            aria-label="关闭"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 overflow-y-auto flex-1 space-y-6">
          {/* 容器管理 */}
          <section>
            <h3 className="flex items-center gap-2 text-sm font-medium text-white/90 mb-3">
              <Box className="w-4 h-4 text-blue-400" />
              容器管理
            </h3>
            {isLoading ? (
              <div className="text-sm text-white/50">加载中…</div>
            ) : !dockerEnabled ? (
              <div className="text-sm text-white/60 rounded-lg bg-white/5 p-4">
                {scriptStatus?.message ?? 'Docker 未启用，脚本在本机执行。'}
              </div>
            ) : (
              <div className="space-y-4">
                <div className="text-sm text-white/70 rounded-lg bg-white/5 p-4">
                  <div className="font-medium text-white/90 mb-1">
                    状态：{isRunning ? '运行中' : '未运行'}
                    {scriptStatus?.containerName && (
                      <span className="ml-2 font-mono text-xs text-white/50">{scriptStatus.containerName}</span>
                    )}
                  </div>
                  {scriptStatus?.message && (
                    <p className="text-xs text-white/50 mt-1">{scriptStatus.message}</p>
                  )}
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => startMutation.mutate()}
                    disabled={pending || isRunning}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-green-600/20 text-green-400 hover:bg-green-600/30 disabled:opacity-50 disabled:cursor-not-allowed text-sm transition-colors"
                  >
                    <Play className="w-3.5 h-3.5" />
                    启动
                  </button>
                  <button
                    type="button"
                    onClick={() => restartMutation.mutate()}
                    disabled={pending || !isRunning}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-amber-600/20 text-amber-400 hover:bg-amber-600/30 disabled:opacity-50 disabled:cursor-not-allowed text-sm transition-colors"
                  >
                    <RotateCw className="w-3.5 h-3.5" />
                    重启
                  </button>
                  <button
                    type="button"
                    onClick={() => stopMutation.mutate()}
                    disabled={pending || !isRunning}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-red-600/20 text-red-400 hover:bg-red-600/30 disabled:opacity-50 disabled:cursor-not-allowed text-sm transition-colors"
                  >
                    <Square className="w-3.5 h-3.5" />
                    关闭
                  </button>
                </div>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}
