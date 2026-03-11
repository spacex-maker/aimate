import { RefreshCw, BookOpen } from 'lucide-react'

interface ScriptEnvBannerProps {
  scriptEnv: any
  refreshPending: boolean
  onRefreshDocker: () => void
  onOpenDockerInstall: () => void
}

export function ScriptEnvBanner({
  scriptEnv,
  refreshPending,
  onRefreshDocker,
  onOpenDockerInstall,
}: ScriptEnvBannerProps) {
  if (!scriptEnv) return null

  return (
    <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
      <span className="font-medium text-white/70">脚本执行环境：</span>
      {scriptEnv.dockerEnabled ? (
        <>
          {scriptEnv.dockerAvailable === false ? (
            <>
              <span className="text-amber-400/90">未检测到 Docker</span>
              <button
                type="button"
                onClick={onRefreshDocker}
                disabled={refreshPending}
                className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
              >
                <RefreshCw className={`w-3 h-3 ${refreshPending ? 'animate-spin' : ''}`} />
                重新检测
              </button>
              <button
                type="button"
                onClick={onOpenDockerInstall}
                className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
              >
                <BookOpen className="w-3 h-3" />
                安装说明
              </button>
            </>
          ) : (
            <>
              <span className="text-green-400/90">独立隔离环境</span>
              <span className="text-white/50">— 您拥有专属 Linux 虚拟机，脚本与命令在隔离容器中运行</span>
              {scriptEnv.dockerVersion && (
                <span className="text-white/40">Docker v{scriptEnv.dockerVersion}</span>
              )}
              {scriptEnv.image && (
                <span className="font-mono text-white/45" title="基础镜像">镜像 {scriptEnv.image}</span>
              )}
              {scriptEnv.containerStatus === 'running' && scriptEnv.containerName && (
                <span className="font-mono text-white/50" title="当前容器">· {scriptEnv.containerName}</span>
              )}
              {scriptEnv.containerStatus === 'running' && (scriptEnv.memoryLimit || scriptEnv.cpuLimit != null) && (
                <span className="text-white/40">
                  资源 {[scriptEnv.memoryLimit, scriptEnv.cpuLimit != null ? `CPU ${scriptEnv.cpuLimit} 核` : null].filter(Boolean).join(' · ')}
                </span>
              )}
              {scriptEnv.containerStatus === 'none' && (
                <span className="text-amber-400/80">· 首次执行时自动创建</span>
              )}
              {scriptEnv.idleMinutes != null && scriptEnv.idleMinutes <= 0 ? (
                <span className="text-white/40">· 不自动回收（常驻/定时任务可一直运行）</span>
              ) : scriptEnv.idleMinutes != null ? (
                <span className="text-white/40">· 空闲 {scriptEnv.idleMinutes} 分钟后自动回收（回收即暂停，数据保留；容器内 cron 等定时任务需配置为不回收）</span>
              ) : null}
            </>
          )}
        </>
      ) : (
        <>
          <span>本机执行（Docker 未启用）</span>
          {scriptEnv.dockerAvailable === true && scriptEnv.dockerVersion && (
            <span className="text-white/40">· 已检测到 Docker v{scriptEnv.dockerVersion}</span>
          )}
          {scriptEnv.dockerAvailable === false && (
            <>
              <span className="text-white/40">· 未检测到 Docker</span>
              <button
                type="button"
                onClick={onRefreshDocker}
                disabled={refreshPending}
                className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
              >
                <RefreshCw className={`w-3 h-3 ${refreshPending ? 'animate-spin' : ''}`} />
                重新检测
              </button>
              <button
                type="button"
                onClick={onOpenDockerInstall}
                className="flex items-center gap-1 px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white/80"
              >
                <BookOpen className="w-3 h-3" />
                安装说明
              </button>
            </>
          )}
        </>
      )}
    </div>
  )
}

