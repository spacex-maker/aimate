import { Link } from 'react-router-dom'
import { ArrowLeft, Download, Monitor } from 'lucide-react'
import { CONNECTOR_DOWNLOAD, CONNECTOR_VERSION } from '../lib/connectorDownload'

export function DownloadPage() {
  return (
    <div className="min-h-screen bg-[#0a0a0b] text-white">
      <header className="mx-auto flex max-w-3xl items-center justify-between px-5 py-5">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-white/55 hover:text-white">
          <ArrowLeft className="h-4 w-4" />
          返回首页
        </Link>
        <Link to="/login" className="text-sm text-white/55 hover:text-white">
          登录
        </Link>
      </header>

      <main className="mx-auto max-w-3xl px-5 pb-16 pt-6">
        <p className="text-xs uppercase tracking-[0.22em] text-cyan-300/70">Aimate Connector</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">下载本机连接器</h1>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-white/55">
          安装后登录同一 AIMATEX 账号，打开对应连接器开关，即可让 Chat 里的 Agent 控制本机 Cursor /
          Windsurf 等应用。
        </p>

        <div className="mt-8 rounded-2xl border border-white/10 bg-white/[0.03] p-5 sm:p-6">
          <div className="flex flex-wrap items-start gap-4">
            <div className="rounded-xl bg-emerald-500/15 p-3 ring-1 ring-inset ring-emerald-400/25">
              <Monitor className="h-6 w-6 text-emerald-300" />
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-lg font-medium">Windows</h2>
              <p className="mt-1 text-sm text-white/45">
                安装包 · v{CONNECTOR_VERSION} · NSIS 安装程序
              </p>
              <a
                href={CONNECTOR_DOWNLOAD.windowsSetupUrl}
                className="mt-4 inline-flex items-center gap-2 rounded-xl bg-cyan-500 px-4 py-2.5 text-sm font-semibold text-black hover:bg-cyan-400"
              >
                <Download className="h-4 w-4" />
                下载 Windows 安装包
              </a>
              <p className="mt-3 text-[11px] text-white/35">
                直链也可访问：
                <a className="ml-1 text-cyan-300/80 hover:underline" href={CONNECTOR_DOWNLOAD.windowsSetupUrl}>
                  {CONNECTOR_DOWNLOAD.windowsSetupUrl}
                </a>
              </p>
            </div>
          </div>
        </div>

        <ol className="mt-8 space-y-3 text-sm text-white/55">
          <li>1. 安装并打开 Aimate Connector，使用 AIMATEX 账号登录。</li>
          <li>2. 在连接器列表打开 Cursor（或其它本机应用）开关。</li>
          <li>
            3. 回到 Chat →「我的 Agent」→「添加设备」→「客户端连接」，等待设备上线。
          </li>
        </ol>

        <p className="mt-10 text-xs text-white/30">macOS / Linux 安装包尚未提供。</p>
      </main>
    </div>
  )
}