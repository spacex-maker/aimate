import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  ArrowUpRight,
  Bot,
  Briefcase,
  Eye,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { SOUL_SITE_ORIGIN } from '../lib/host'
import { CollabStoryReel } from '../components/landing/CollabStoryReel'

const NAV: { label: string; href?: string; to?: string }[] = [
  { href: '#hire', label: '招聘同事' },
  { href: '#memory', label: 'Soul 记忆' },
  { href: '#focus', label: '智能压缩' },
  { href: '#collab', label: '一起干活' },
  { href: '#trust', label: '放心交给它' },
  { to: '/emergence', label: '智能涌现' },
  { to: '/download', label: '下载客户端' },
]

const HIRE_SCENES = [
  {
    k: '01',
    title: '忙不过来时，有人接得住',
    desc: '周五下午竞品表还没动？招一位同事进群，它接着做，你去开下一场会。',
  },
  {
    k: '02',
    title: '招的是岗位，不是又一个账号',
    desc: '秘书、研究、全栈——按职业选人，按等级定能力。入职就能开工。',
  },
  {
    k: '03',
    title: '换会话，不换同事',
    desc: '同一位 Agent 跟你走。今天聊方案，明天接着改，不用重新自我介绍。',
  },
]

const MEMORY_SCENES = [
  {
    title: '每位同事，一颗独立 Soul',
    desc: '偏好、禁忌、已定结论写进灵魂档案——跟着这位 Agent 走，而不是散落在某个聊天窗口。',
  },
  {
    title: '跨会话、跨工具可延续',
    desc: '今天在 Chat 里定下的规矩，明天仍能召回。Soul 是记忆层，Chat 是协作现场。',
  },
  {
    title: '记忆你看得见、管得住',
    desc: '想核对它记得什么，可在 Chat 预览，也可打开 Soul 平台查看完整档案与心智图。',
  },
]

const FOCUS_SCENES = [
  {
    title: '自己知道何时该压',
    desc: '占用压力、可折段落、对话占比一起判断——不是死数「轮次够不够」，超限也能安全收束。',
  },
  {
    title: '钉住与近期原文保留',
    desc: '关键约束钉在桌上；早期轮次收成结构化状态。聊天记录仍可回看，不等于删故事。',
  },
  {
    title: '架构可迭代',
    desc: '智能压缩 ICA 分层管线（策略可升版）。今天 v1.0，明天可换更强策略，不必推倒重来。',
  },
]

const COLLAB_STORY = [
  {
    k: '01',
    phase: '组成',
    title: '你 + 你的 AI',
    desc: '每个人带着自己的专属同事进场。它懂你的习惯，也守你的边界。',
  },
  {
    k: '02',
    phase: '相遇',
    title: '群里流动的是协作',
    desc: '话给人听，也给背后的 AI。该谁出手，送到谁那里——不是一窝蜂抢答。',
  },
  {
    k: '03',
    phase: '边界',
    title: '请对方帮忙，先过主人这一关',
    desc: '你可以 @ 同事的 AI；对方点头，它才动手。协作有礼，不是代执行。',
  },
]

const TRUST_SCENES = [
  {
    title: '能查、能改、能汇报',
    desc: '在你允许的范围内，它自己去找资料、处理任务、回进度——少一次来回问。',
  },
  {
    title: '想法看得见',
    desc: '它怎么想、调了什么工具，过程可回看。信任来自透明，不是黑箱。',
  },
  {
    title: '花费算得清',
    desc: '对话与搜索花了多少，流水清楚。用多少，心里有数。',
  },
]

function useReveal<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [on, setOn] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const root = el.closest('.agent-land') as HTMLElement | null
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setOn(true)
          io.disconnect()
        }
      },
      { root, threshold: 0.12, rootMargin: '0px 0px -6% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return { ref, on }
}

function Reveal({
  children,
  className = '',
  style,
}: {
  children: ReactNode
  className?: string
  style?: CSSProperties
}) {
  const { ref, on } = useReveal<HTMLDivElement>()
  return (
    <div
      ref={ref}
      className={`agent-land__reveal ${on ? 'is-on' : ''} ${className}`.trim()}
      style={style}
    >
      {children}
    </div>
  )
}

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

/** AIMATEX-AGENT 官网：场景与收益优先 */
export function LandingPage() {
  const { user, isLoggedIn } = useAuth()
  const [navSolid, setNavSolid] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const displayName = user?.displayName || user?.username || ''
  const initial = (displayName || '?').charAt(0).toUpperCase()

  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    const onScroll = () => setNavSolid(root.scrollTop > 24)
    onScroll()
    root.addEventListener('scroll', onScroll, { passive: true })
    return () => root.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <div className="agent-land" ref={rootRef}>
      <div className="agent-land__wash" aria-hidden />
      <div className="agent-land__noise" aria-hidden />

      <header className={`agent-land__nav ${navSolid ? 'is-solid' : ''}`}>
        <Link to="/" className="agent-land__brand-mini">
          <span className="agent-land__mark">
            <Bot className="w-4 h-4" />
          </span>
          <span className="agent-land__brand-mini-text">AIMATEX-AGENT</span>
        </Link>
        <nav className="agent-land__nav-links" aria-label="页面导航">
          {NAV.map((item) =>
            item.to ? (
              <Link key={item.to} to={item.to}>
                {item.label}
              </Link>
            ) : (
              <a key={item.href} href={item.href}>
                {item.label}
              </a>
            ),
          )}
        </nav>
        <div className="agent-land__nav-actions">
          <a
            href={SOUL_SITE_ORIGIN}
            target="_blank"
            rel="noopener noreferrer"
            className="agent-land__ext"
            title="Soul 记忆平台"
          >
            Soul
            <ArrowUpRight className="w-3.5 h-3.5" />
          </a>
          <a
            href="https://aimatex.com"
            target="_blank"
            rel="noopener noreferrer"
            className="agent-land__ext"
          >
            AIMATEX
            <ArrowUpRight className="w-3.5 h-3.5" />
          </a>
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

      <section className="agent-land__hero">
        <HeroCanvas />
        <div className="agent-land__hero-inner">
          <h1 className="agent-land__brand">AIMATEX-AGENT</h1>
          <p className="agent-land__headline">招聘会思考的同事。</p>
          <p className="agent-land__lead">
            {isLoggedIn
              ? `${displayName}，欢迎回来。你的同事们都在控制台里。`
              : '帮你做事，记得偏好，长聊不乱。需要时，技术在背后安静运转。'}
          </p>
          <div className="agent-land__cta-row">
            {isLoggedIn ? (
              <Link to="/app" className="agent-land__btn">
                <span>进入控制台</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            ) : (
              <Link to="/login" className="agent-land__btn">
                <span>开始使用</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            )}
            <a href="#hire" className="agent-land__btn-ghost">
              看看它能做什么
            </a>
            <Link to="/download" className="agent-land__btn-ghost">
              下载客户端
            </Link>
          </div>
        </div>
        <a href="#hire" className="agent-land__scroll" aria-label="向下滚动">
          <span />
        </a>
      </section>

      <section id="hire" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head agent-land__section-head--apple">
            <p className="agent-land__eye">Hire</p>
            <h2>一位同事。<br />而不是又一个聊天窗口。</h2>
            <p>把它招进来，交给它一件事。你腾出手，去做更重要的决定。</p>
          </div>
        </Reveal>
        <div className="agent-land__scenes">
          {HIRE_SCENES.map((s, i) => (
            <Reveal key={s.k} className="agent-land__scene" style={{ transitionDelay: `${i * 80}ms` } as CSSProperties}>
              <span className="agent-land__scene-k">{s.k}</span>
              <h3>{s.title}</h3>
              <p>{s.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="agent-land__whisper">
            <Briefcase className="w-3.5 h-3.5" />
            <span>职业目录 · Soul 席位 · 清晰计价</span>
          </div>
        </Reveal>
        <Reveal>
          <div className="agent-land__inline-cta agent-land__inline-cta--center">
            {isLoggedIn ? (
              <Link to="/agents/hire" className="agent-land__btn agent-land__btn--sm">
                <span>去招聘大厅</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            ) : (
              <Link to="/login" className="agent-land__btn agent-land__btn--sm">
                <span>登录后招聘</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            )}
          </div>
        </Reveal>
      </section>

      <section id="memory" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head agent-land__section-head--apple">
            <p className="agent-land__eye">Soul</p>
            <h2>它记得你怎么工作。</h2>
            <p>
              Chat 已接入 Soul：每位 Agent 绑定独立灵魂。偏好、口径、已定的事跟着同事走，
              而不是散落在某个聊天窗口里。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__statement-grid">
          {MEMORY_SCENES.map((s, i) => (
            <Reveal key={s.title} className="agent-land__statement" style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
              <h3>{s.title}</h3>
              <p>{s.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="agent-land__product-bridge">
            <div className="agent-land__product-bridge-copy">
              <p className="agent-land__product-bridge-kicker">Chat × Soul</p>
              <p>
                对话现场在 Chat，耐久记忆在 Soul。压缩前也可把关键事实写入灵魂，
                长项目不断档、跨工具仍认得你。
              </p>
            </div>
            <a
              href={SOUL_SITE_ORIGIN}
              target="_blank"
              rel="noopener noreferrer"
              className="agent-land__btn agent-land__btn--sm"
            >
              <span>打开 Soul</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </a>
          </div>
        </Reveal>
      </section>

      <section id="focus" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head agent-land__section-head--apple">
            <p className="agent-land__eye">Intelligent Compress</p>
            <h2>聊到很晚。<br />它依然清醒。</h2>
            <p>
              长项目不怕拖。智能压缩（ICA）按占用压力与可折段落决定何时收束——
              目标钉住，旧讨论折进结构化状态，新回合接着干。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__focus-band">
          {FOCUS_SCENES.map((s, i) => (
            <Reveal key={s.title} className="agent-land__focus-item" style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
              <h3>{s.title}</h3>
              <p>{s.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="agent-land__product-bridge agent-land__product-bridge--ica">
            <div className="agent-land__product-bridge-copy">
              <p className="agent-land__product-bridge-kicker">智能压缩 · ICA v1.0</p>
              <p>
                分层管线：裁剪摘要稿 → 结构化 SessionState → 可选写入 Soul → 排除老化轮次。
                策略版本可迭代，宣传名与版本号随产品升级。
              </p>
            </div>
            <a href="#memory" className="agent-land__btn-ghost agent-land__btn-ghost--compact">
              也看看 Soul
            </a>
          </div>
        </Reveal>
      </section>

      <section id="collab" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head agent-land__section-head--apple agent-land__section-head--collab">
            <p className="agent-land__eye">Together</p>
            <h2>一起干活，各自带着 AI。</h2>
            <p className="agent-land__collab-lead">
              群里不再是人对人、或人喊公共机器人，而是<strong>协作体</strong>之间的配合。
              想用别人的 AI？先过主人这一关。
            </p>
            <p className="agent-land__collab-equation" aria-hidden="true">
              <span>人</span>
              <span className="agent-land__collab-equation-op">+</span>
              <span>专属 AI</span>
              <span className="agent-land__collab-equation-op">=</span>
              <span className="agent-land__collab-equation-result">协作体</span>
            </p>
          </div>
        </Reveal>

        <Reveal>
          <CollabStoryReel />
        </Reveal>

        <ol className="agent-land__collab-story">
          {COLLAB_STORY.map((s, i) => (
            <li key={s.k} className="agent-land__collab-story-item">
              <Reveal style={{ transitionDelay: `${i * 80}ms` } as CSSProperties}>
                <div className="agent-land__collab-story-meta">
                  <span className="agent-land__mono">{s.k}</span>
                  <span className="agent-land__collab-story-phase">{s.phase}</span>
                </div>
                <h3>{s.title}</h3>
                <p>{s.desc}</p>
              </Reveal>
            </li>
          ))}
        </ol>
      </section>

      <section id="trust" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head agent-land__section-head--apple">
            <p className="agent-land__eye">Trust</p>
            <h2>交给它。<br />你依然说了算。</h2>
            <p>能做事，也守边界。过程看得见，花费算得清。</p>
          </div>
        </Reveal>
        <div className="agent-land__trust-grid">
          {TRUST_SCENES.map((s, i) => (
            <Reveal key={s.title} className="agent-land__trust-card" style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
              <Eye className="w-4 h-4 agent-land__trust-icon" aria-hidden />
              <h3>{s.title}</h3>
              <p>{s.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <p className="agent-land__tech-line">授权边界 · 过程可回看 · 积分流水</p>
        </Reveal>
      </section>

      <section className="agent-land__closing">
        <Reveal>
          <p className="agent-land__eye">Begin</p>
          <h2 className="agent-land__closing-brand">AIMATEX-AGENT</h2>
          <p>
            {isLoggedIn
              ? '招一位同事。把今晚留给更重要的事。'
              : '招一位会思考的同事。现在就开始。'}
          </p>
          {isLoggedIn ? (
            <div className="agent-land__closing-actions">
              <Link to="/app" className="agent-land__btn">
                <span>进入控制台</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
              <Link to="/agents/hire" className="agent-land__btn-ghost">
                去招聘大厅
              </Link>
            </div>
          ) : (
            <Link to="/login" className="agent-land__btn">
              <span>免费开始</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          )}
        </Reveal>
      </section>

      <footer className="agent-land__footer">
        <span>© {new Date().getFullYear()} AIMATEX</span>
        <Link to="/emergence">智能涌现</Link>
        <Link to="/download">下载客户端</Link>
        <a href={SOUL_SITE_ORIGIN} target="_blank" rel="noopener noreferrer">
          Soul
        </a>
        <a href="https://aimatex.com" target="_blank" rel="noopener noreferrer">
          aimatex.com
        </a>
      </footer>
    </div>
  )
}
