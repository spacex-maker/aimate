import { Copy } from 'lucide-react'

interface DockerInstallModalProps {
  open: boolean
  dockerInstallInfo: any
  onClose: () => void
  onCopyCommand: (command: string) => void
}

export function DockerInstallModal({
  open,
  dockerInstallInfo,
  onClose,
  onCopyCommand,
}: DockerInstallModalProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={onClose}>
      <div className="bg-gray-900 border border-white/20 rounded-xl shadow-xl max-w-md w-full mx-4 p-5 text-left" onClick={e => e.stopPropagation()}>
        <h3 className="text-sm font-semibold text-white/90 mb-3">安装 Docker</h3>
        {dockerInstallInfo ? (
          <>
            <p className="text-xs text-white/70 whitespace-pre-wrap mb-3">{dockerInstallInfo.instructions}</p>
            {dockerInstallInfo.copyCommand && (
              <div className="flex gap-2 mb-3">
                <code className="flex-1 px-3 py-2 rounded bg-black/40 text-xs text-green-300 font-mono break-all">
                  {dockerInstallInfo.copyCommand}
                </code>
                <button
                  type="button"
                  onClick={() => onCopyCommand(dockerInstallInfo.copyCommand)}
                  className="flex-shrink-0 px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-white/80 text-xs"
                >
                  <Copy className="w-4 h-4" />
                </button>
              </div>
            )}
            {dockerInstallInfo.docUrl && (
              <a
                href={dockerInstallInfo.docUrl}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-blue-400 hover:underline"
              >
                官方安装文档 →
              </a>
            )}
          </>
        ) : (
          <p className="text-xs text-white/50">加载中…</p>
        )}
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 text-xs rounded bg-white/10 hover:bg-white/20 text-white/80"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  )
}

