import { Link } from 'react-router-dom'
import { ArrowRight, Bot, Download } from 'lucide-react'
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

      <section className="agent-land__hero agent-land__hero--download">
        <div className="agent-land__hero-inner">
          <h1 className="agent-land__brand">AIMATEX-AGENT</h1>
          <p className="agent-land__headline">下载客户端</p>
          <p className="agent-land__lead">让同事操控本机 Cursor。</p>
          <div className="agent-land__cta-row">
            <a href={CONNECTOR_DOWNLOAD.windowsSetupUrl} className="agent-land__btn">
              <Download className="w-4 h-4" />
              <span>下载 Windows 版</span>
            </a>
            <Link to="/" className="agent-land__btn-ghost">
              返回首页
            </Link>
          </div>
          <p className="agent-land__dl-meta">v{CONNECTOR_VERSION}</p>
        </div>
      </section>
    </div>
  )
}