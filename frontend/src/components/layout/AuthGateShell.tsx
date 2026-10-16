import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, Bot, Cpu, Network, Sparkles, Zap } from 'lucide-react'

const NODES = [
  { x: 18, y: 22, delay: '0s', label: 'Plan' },
  { x: 72, y: 16, delay: '0.4s', label: 'Tool' },
  { x: 48, y: 48, delay: '0.2s', label: 'Core', hub: true },
  { x: 22, y: 72, delay: '0.7s', label: 'Mem' },
  { x: 78, y: 68, delay: '1s', label: 'Run' },
]

const STEPS = [
  { n: '01', t: 'Account', d: '统一账号登录 / 注册' },
  { n: '02', t: 'Authorize', d: '授权回到 Chat' },
  { n: '03', t: 'Agent', d: '继续编排与执行' },
]

export function AuthGateShell({
  children,
  title,
  lead,
  footer,
}: {
  children: ReactNode
  title: string
  lead: string
  footer?: ReactNode
}) {
  return (
    <div className="auth-gate">
      <div className="auth-gate__wash" aria-hidden />
      <div className="auth-gate__grid" aria-hidden />
      <div className="auth-gate__slash" aria-hidden />

      <header className="auth-gate__top">
        <Link to="/" className="auth-gate__brand">
          <span className="auth-gate__mark">
            <Bot className="w-5 h-5" />
          </span>
          <span>
            <span className="auth-gate__name">AIMATEX-AGENT</span>
            <span className="auth-gate__tag">Autonomous Agent</span>
          </span>
        </Link>
        <a
          href="https://aimatex.com"
          target="_blank"
          rel="noopener noreferrer"
          className="auth-gate__ext"
        >
          AIMATEX
          <ArrowUpRight className="w-3.5 h-3.5" />
        </a>
      </header>

      <main className="auth-gate__main">
        <section className="auth-gate__story">
          <p className="auth-gate__eyebrow">
            <Sparkles className="w-3.5 h-3.5" />
            Forge · Agent Runtime
          </p>
          <h1 className="auth-gate__hero">
            让 Agent
            <em>真正跑起来</em>
          </h1>
          <p className="auth-gate__copy">
            规划、调用工具、可观测执行。身份由 AIMATEX Account 统一承载，登录后回到 Chat 继续锻造。
          </p>

          <ol className="auth-gate__steps">
            {STEPS.map((s) => (
              <li key={s.n}>
                <span className="n">{s.n}</span>
                <div>
                  <div className="t">{s.t}</div>
                  <div className="d">{s.d}</div>
                </div>
              </li>
            ))}
          </ol>

          <div className="auth-gate__caps" aria-hidden>
            <span><Cpu className="w-3.5 h-3.5" /> Orchestrate</span>
            <span><Network className="w-3.5 h-3.5" /> Tool Call</span>
            <span><Zap className="w-3.5 h-3.5" /> Observe</span>
          </div>
        </section>

        <section className="auth-gate__stage" aria-hidden>
          <div className="auth-gate__orbit auth-gate__orbit--a" />
          <div className="auth-gate__orbit auth-gate__orbit--b" />
          <svg className="auth-gate__wires" viewBox="0 0 100 100" preserveAspectRatio="none">
            <line x1="18" y1="22" x2="48" y2="48" />
            <line x1="72" y1="16" x2="48" y2="48" />
            <line x1="22" y1="72" x2="48" y2="48" />
            <line x1="78" y1="68" x2="48" y2="48" />
            <line x1="18" y1="22" x2="72" y2="16" />
            <line x1="22" y1="72" x2="78" y2="68" />
          </svg>
          {NODES.map((n) => (
            <div
              key={n.label}
              className={`auth-gate__node ${n.hub ? 'is-hub' : ''}`}
              style={{ left: `${n.x}%`, top: `${n.y}%`, animationDelay: n.delay }}
            >
              <span className="dot" />
              <span className="lab">{n.label}</span>
            </div>
          ))}
          <div className="auth-gate__watermark">AGENT</div>
        </section>

        <section className="auth-gate__panel">
          <div className="auth-gate__sheet">
            <p className="auth-gate__sheet-eye">AIMATEX Account</p>
            <h2 className="auth-gate__sheet-title">{title}</h2>
            <p className="auth-gate__sheet-lead">{lead}</p>
            <div className="auth-gate__sheet-body">{children}</div>
            {footer ? <div className="auth-gate__sheet-foot">{footer}</div> : null}
          </div>
        </section>
      </main>
    </div>
  )
}
