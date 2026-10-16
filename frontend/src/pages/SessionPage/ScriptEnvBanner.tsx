interface ScriptEnvBannerProps {
  scriptEnv: any
  refreshPending: boolean
  onRefreshDocker: () => void
  onOpenDockerInstall: () => void
}

export function ScriptEnvBanner({
  scriptEnv,
}: ScriptEnvBannerProps) {
  if (!scriptEnv) return null

  // 产线已关闭每用户 Docker 虚拟机：统一展示系统工具模式
  if (!scriptEnv.dockerEnabled) {
    return (
      <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
        <span className="font-medium text-white/70">执行环境：</span>
        <span className="text-sky-400/90">系统工具模式</span>
        <span className="text-white/50">— Agent 仅可调用系统允许的技能与工具，不再分配独立虚拟机</span>
      </div>
    )
  }

  return (
    <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
      <span className="font-medium text-white/70">脚本执行环境：</span>
      <span className="text-amber-400/90">Docker 已启用（遗留）</span>
      <span className="text-white/50">— 产线推荐关闭独立虚拟机，仅使用系统工具</span>
    </div>
  )
}
