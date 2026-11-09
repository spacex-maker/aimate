import { Link } from 'react-router-dom'
import { ArrowRight, Bot, Download } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { CONNECTOR_DOWNLOAD, CONNECTOR_VERSION } from '../lib/connectorDownload'

function HeroCanvas() {
  return (
    <div className="agent-land__canvas" aria-hidden>
      <div className="agent-land__canvas-glow" />
      <div className="agent-land__canvas-ring agent-land__canvas-ring--a" />
      <div className="agent-land__canvas-ring agent-land__canvas-ring--b" />
      <div className="agent-land__canvas-ring agent-land__canvas-ring--c" />
      <svg className="agent-land__canvas-wires" viewBox="0 0 100 100" preserveAspectRatio="none">
        <path d="M18 28 C 32 18, 40 42, 50 50" />
        <path d="M82 24 C 68 20, 60 40, 50 50" />
        <path d="M20 78 C 34 70, 40 58, 50 50" />
        <path d="M84 76 C 70 72, 62 58, 50 50" />
        <path d="M50 12 C 52 28, 48 36, 50 50" />
        <path d="M50 50 C 48 64, 52 78, 50 90" />
      </svg>
      {[
        { x: 18, y: 28, l: 'HIRE' },
        { x: 82, y: 24, l: 'SOUL' },
        { x: 50, y: 50, l: 'YOU', hub: true },
        { x: 20, y: 78, l: 'WORK' },
        { x: 84, y: 76, l: 'TEAM' },
        { x: 50, y: 12, l: 'TRUST' },
      ].map((n, i) => (
        <div
          key={n.l}
          className={`agent-land__canvas-node ${n.hub ? 'is-hub' : ''}`}
          style={{ left: `${n.x}%`, top: `${n.y}%`, animationDelay: `${i * 0.35}s` }}
        >
          <span className="dot" />
          <span className="lab">{n.l}</span>
        </div>
      ))}
      <div className="agent-land__canvas-beam" />
    </div>
  )
}

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

      <section className="agent-land__hero agent-land__hero--dl">
        <HeroCanvas />
        <div className="agent-land__hero-inner">
          <h1 className="agent-land__brand">AIMATEX-AGENT</h1>
          <p className="agent-land__headline">下载客户端</p>
          <p className="agent-land__lead">让同事操控本机 AI。</p>
          <div className="agent-land__cta-row">
            <a href={CONNECTOR_DOWNLOAD.windowsSetupUrl} className="agent-land__btn">
              <Download className="w-4 h-4" />
              <span>下载 Windows 版</span>
            </a>
            <a href="#install" className="agent-land__btn-ghost">
              安装说明
            </a>
          </div>
          <p className="dl-ver">Windows · v{CONNECTOR_VERSION}</p>
        </div>
        <a href="#install" className="agent-land__scroll" aria-label="查看安装说明">
          <span />
        </a>
      </section>

      <section id="install" className="agent-land__section dl-install">
        <div className="agent-land__section-head agent-land__section-head--apple">
          <p className="agent-land__eye">Install</p>
          <h2>
            出现绿色提示？
            <br />
            两步继续。
          </h2>
          <p>新客户端常见拦截。不是病毒，按图操作即可。</p>
        </div>

        <div className="dl-rail">
          <article className="dl-rail__item">
            <div className="dl-rail__cap">
              <span>01</span>
              <h3>点「更多信息」</h3>
            </div>
            <img
              src="/guide/smartscreen-1.png"
              alt="Windows SmartScreen：点击更多信息"
              width={720}
              height={420}
              loading="lazy"
            />
          </article>
          <article className="dl-rail__item">
            <div className="dl-rail__cap">
              <span>02</span>
              <h3>再点「仍要运行」</h3>
            </div>
            <img
              src="/guide/smartscreen-2.png"
              alt="Windows SmartScreen：点击仍要运行"
              width={720}
              height={420}
              loading="lazy"
            />
          </article>
        </div>
      </section>

      <footer className="agent-land__footer">
        <span>© {new Date().getFullYear()} AIMATEX</span>
        <Link to="/">首页</Link>
        <a href={CONNECTOR_DOWNLOAD.windowsSetupUrl}>重新下载</a>
      </footer>
    </div>
  )
}
