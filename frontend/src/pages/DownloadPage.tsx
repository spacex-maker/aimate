import { Link } from 'react-router-dom'
import { ArrowRight, Bot, Download, Monitor } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { CONNECTOR_DOWNLOAD, CONNECTOR_VERSION } from '../lib/connectorDownload'

export function DownloadPage() {
  const { isLoggedIn, user } = useAuth()
  const displayName = user?.displayName || user?.username || ''
  const initial = (displayName.trim()[0] || 'A').toUpperCase()

  return (
    <div className="agent-land agent-land--download">
      <div className="agent-land__wash" aria-hidden />
      <div className="agent-land__noise" aria-hidden />

      <header className="agent-land__nav is-solid">
        <Link to="/" className="agent-land__brand-mini">
          <span className="agent-land__mark">
            <Bot className="w-4 h-4" />
          </span>
          <span className="agent-land__brand-mini-text">AIMATEX-AGENT</span>
        </Link>
        <div className="agent-land__nav-actions">
          {isLoggedIn ? (
            <Link to="/app" className="agent-land__user" title={`${displayName} · 进入控制台`}>
              <span className="agent-land__user-avatar" aria-hidden>
                {initial}
              </span>
              <span className="agent-land__user-name">{displayName}</span>
              <span className="agent-land__user-go">控制台</span>
            </Link>
          ) : (
            <Link to="/login" className="agent-land__btn agent-land__btn--sm">
              <span>登录</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          )}
        </div>
      </header>

      <main className="dl-page">
        <section className="dl-hero">
          <div className="dl-hero__visual" aria-hidden>
            <div className="dl-hero__glow" />
            <div className="dl-hero__window">
              <div className="dl-hero__titlebar">
                <span />
                <span />
                <span />
              </div>
              <div className="dl-hero__body">
                <div className="dl-hero__mark">
                  <Bot className="w-7 h-7" />
                </div>
                <div className="dl-hero__lines">
                  <i />
                  <i />
                  <i />
                </div>
              </div>
            </div>
          </div>

          <div className="dl-hero__copy">
            <p className="dl-kicker">Aimate Connector</p>
            <h1 className="dl-title">下载客户端</h1>
            <p className="dl-lead">让同事操控本机 AI。</p>

            <a href={CONNECTOR_DOWNLOAD.windowsSetupUrl} className="dl-cta">
              <Download className="w-5 h-5" />
              <span>下载 Windows 版</span>
            </a>

            <div className="dl-meta">
              <span className="dl-chip">
                <Monitor className="w-3.5 h-3.5" />
                Windows
              </span>
              <span>v{CONNECTOR_VERSION}</span>
            </div>
          </div>
        </section>

        <section className="dl-guide" id="install">
          <div className="dl-guide__head">
            <p className="dl-kicker">Install</p>
            <h2>安装时出现绿色提示？</h2>
            <p>新发布客户端常见，不是病毒。按两步继续即可。</p>
          </div>

          <ol className="dl-steps">
            <li className="dl-step">
              <div className="dl-step__text">
                <span className="dl-step__n">01</span>
                <h3>点击「更多信息」</h3>
                <p>Windows SmartScreen 会先拦截未广泛下载的安装包。</p>
              </div>
              <figure className="dl-step__shot">
                <img
                  src="/guide/smartscreen-1.png"
                  alt="Windows 提示：点击更多信息"
                  width={560}
                  height={320}
                  loading="lazy"
                />
              </figure>
            </li>
            <li className="dl-step">
              <div className="dl-step__text">
                <span className="dl-step__n">02</span>
                <h3>再点「仍要运行」</h3>
                <p>展开后选择继续安装，即可打开安装向导。</p>
              </div>
              <figure className="dl-step__shot">
                <img
                  src="/guide/smartscreen-2.png"
                  alt="Windows 提示：点击仍要运行"
                  width={560}
                  height={320}
                  loading="lazy"
                />
              </figure>
            </li>
          </ol>
        </section>

        <footer className="dl-foot">
          <Link to="/">返回首页</Link>
          <span>© {new Date().getFullYear()} AIMATEX</span>
        </footer>
      </main>
    </div>
  )
}