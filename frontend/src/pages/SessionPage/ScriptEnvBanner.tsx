import { Link } from 'react-router-dom'

interface ScriptEnvBannerProps {
  scriptEnv: any
  refreshPending: boolean
  onRefreshDocker: () => void
  onOpenDockerInstall: () => void
  hasOffice?: boolean
}

export function ScriptEnvBanner({
  scriptEnv,
  hasOffice,
}: ScriptEnvBannerProps) {
  if (!scriptEnv) return null

  if (!scriptEnv.dockerEnabled) {
    return (
      <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
        <span className="font-medium text-white/70">执行环境：</span>
        {hasOffice ? (
          <>
            <span className="text-emerald-400/90">官方 + Agent 工具箱 + 办公室</span>
            <span className="text-white/50">— 简单脚本平台执行；装包/任意命令走办公室</span>
          </>
        ) : (
          <>
            <span className="text-sky-400/90">官方 + Agent 工具箱</span>
            <span className="text-white/50">— 简单工具平台执行；JDK/任意 shell 等重活需</span>
            <Link to="/offices" className="text-sky-400/90 hover:underline">
              开通办公室
            </Link>
          </>
        )}
      </div>
    )
  }

  return (
    <div className="flex-shrink-0 px-6 py-2 border-b border-white/10 bg-black/20 flex items-center flex-wrap gap-x-3 gap-y-1.5 text-xs text-white/60">
      <span className="font-medium text-white/70">执行环境：</span>
      <span className="text-amber-400/90">Docker 遗留隔离</span>
      <span className="text-white/50">— Agent 脚本可进用户容器；产线重活请用办公室</span>
    </div>
  )
}
