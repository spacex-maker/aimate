import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  ArrowUpRight,
  Bot,
  Briefcase,
  Eye,
  Globe2,
  KeyRound,
  Minimize2,
  Pin,
  Sparkles,
  Terminal,
  Workflow,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

const NAV = [
  { href: '#compress', label: '上下文压缩' },
  { href: '#hire', label: 'Agent 招聘' },
  { href: '#soul', label: 'Soul 记忆' },
  { href: '#runtime', label: '运行时' },
  { href: '#observe', label: '可观测' },
]

const COMPRESS_LAYERS = [
  {
    id: 'L0',
    title: '确定性裁剪',
    lead: '零模型成本，先砍水分',
    points: ['重复工具结果只留最新', '已解决错误自动收束', '超长 payload 摘要用稿截断'],
  },
  {
    id: 'L1',
    title: '结构化状态',
    lead: '滚动 SessionState，不是散文摘要',
    points: ['intent / decisions / constraints', '实体只存 path 与引用', '再次压缩增量合并，少漂移'],
  },
  {
    id: 'L2',
    title: '写前外置 Soul',
    lead: '耐久事实不靠摘要碰巧存活',
    points: ['决策与偏好先写入 Soul', '跨会话可召回', '记忆开关关闭则跳过'],
  },
  {
    id: 'L3',
    title: '阈值与保留',
    lead: '占用见底前主动建议',
    points: ['≥72% 圆环提示压缩', '近期原文 + pin 永不丢', '聊天记录仍可完整回看'],
  },
]

const HIRE_POINTS = [
  {
    title: '按职业招聘',
    desc: '全栈、运营、研究等官方职业目录，选等级与租期，一键入职你的团队。',
  },
  {
    title: '一人一座 Soul',
    desc: '每位 Agent 绑定独立 Soul 席位：长期记忆跟 Agent 走，而不是散落在会话里。',
  },
  {
    title: '积分清晰结算',
    desc: '租用费与 Soul 席位分项计价，控制台可查流水；用多少、花多少一目了然。',
  },
]

const SOUL_POINTS = [
  {
    k: '01',
    title: '跨会话连续',
    desc: '偏好、约束、已确认决策写入 Soul，下次对话无需从头交代。',
  },
  {
    k: '02',
    title: '按需召回',
    desc: 'Agent 主动 recall；压缩时 write-before-compact，关键事实先外置再折叠。',
  },
  {
    k: '03',
    title: '人可审阅',
    desc: '在「我的 Agent」预览 Soul 记忆，跳转 Soul 控制台深度管理。',
  },
]

const RUNTIME = [
  {
    icon: Workflow,
    title: '思考循环',
    desc: '召回 → 思考 → 决策 → 执行，多轮工具链直到目标完成。',
  },
  {
    icon: Terminal,
    title: '工具行动面',
    desc: '联网搜索、HTTP、沙箱脚本、自主编写工具——按开关精细授权。',
  },
  {
    icon: KeyRound,
    title: '模型自由',
    desc: '系统模型或自带 Key；上下文窗口按模型上限估算，圆环实时可见。',
  },
  {
    icon: Globe2,
    title: 'Web 即控制台',
    desc: '浏览器打开就能聊、招聘、管记忆；桌面与 API 同源 Runtime。',
  },
]

const OBSERVE = [
  { t: 'THINKING', d: 'Token 级流式思考，推理过程可折叠回放。' },
  { t: 'TOOL', d: '工具入参、结果与耗时完整可见。' },
  { t: 'CONTEXT', d: '占用圆环 + 分项拆解；建议压缩一键触发。' },
  { t: 'LEDGER', d: '对话扣费与搜索成本入账，流水可查。' },
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
        { x: 50, y: 50, l: 'CHAT', hub: true },
        { x: 20, y: 78, l: 'CTX' },
        { x: 84, y: 76, l: 'TOOL' },
        { x: 50, y: 12, l: 'OBS' },
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

/** AIMATEX-AGENT 官网：产品亮点分板块介绍 */
export function LandingPage() {
  const { user, isLoggedIn } = useAuth()
  const [layerIdx, setLayerIdx] = useState(0)
  const [navSolid, setNavSolid] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const displayName = user?.displayName || user?.username || ''
  const initial = (displayName || '?').charAt(0).toUpperCase()

  useEffect(() => {
    const id = window.setInterval(() => {
      setLayerIdx((i) => (i + 1) % COMPRESS_LAYERS.length)
    }, 3200)
    return () => window.clearInterval(id)
  }, [])

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
          {NAV.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="agent-land__nav-actions">
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

      {/* Hero */}
      <section className="agent-land__hero">
        <HeroCanvas />
        <div className="agent-land__hero-inner">
          <p className="agent-land__kicker">
            <Sparkles className="w-3.5 h-3.5" />
            Hire · Remember · Compress
          </p>
          <h1 className="agent-land__brand">AIMATEX-AGENT</h1>
          <p className="agent-land__headline">招聘会思考的同事，而不是多开一个聊天框</p>
          <p className="agent-land__lead">
            {isLoggedIn
              ? `已登录为 ${displayName}。招聘 Agent、绑定 Soul、长会话压缩——都在控制台里。`
              : '职业 Agent + 独立 Soul + 四层上下文压缩：长对话不糊、记忆跟岗位走、成本看得见。'}
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
            <a href="#compress" className="agent-land__btn-ghost">
              看上下文如何压缩
            </a>
          </div>
        </div>
        <a href="#compress" className="agent-land__scroll" aria-label="向下滚动">
          <span />
        </a>
      </section>

      {/* Context compress */}
      <section id="compress" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Context Fabric</p>
            <h2>长会话不靠硬截断，靠可审计的压缩管线</h2>
            <p>
              对齐 Cursor / Claude Code compact 与 Session Memory 共识：系统提示保留、近期原文保留、更早轮次收成结构化状态；聊天记录永不删除。
            </p>
          </div>
        </Reveal>

        <Reveal className="agent-land__pipe">
          <div className="agent-land__pipe-rail" role="tablist" aria-label="压缩层级">
            {COMPRESS_LAYERS.map((layer, i) => (
              <button
                key={layer.id}
                type="button"
                role="tab"
                aria-selected={i === layerIdx}
                className={`agent-land__pipe-step ${i === layerIdx ? 'is-active' : ''}`}
                onClick={() => setLayerIdx(i)}
              >
                <span className="id">{layer.id}</span>
                <span className="title">{layer.title}</span>
              </button>
            ))}
          </div>
          <div className="agent-land__pipe-stage" key={COMPRESS_LAYERS[layerIdx].id}>
            <div className="agent-land__pipe-badge">
              <Minimize2 className="w-4 h-4" />
              {COMPRESS_LAYERS[layerIdx].id}
            </div>
            <h3>{COMPRESS_LAYERS[layerIdx].title}</h3>
            <p className="agent-land__pipe-lead">{COMPRESS_LAYERS[layerIdx].lead}</p>
            <ul className="agent-land__pipe-list">
              {COMPRESS_LAYERS[layerIdx].points.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
            <div className="agent-land__loop-meter" aria-hidden>
              {COMPRESS_LAYERS.map((_, i) => (
                <span key={i} className={i === layerIdx ? 'is-on' : ''} />
              ))}
            </div>
          </div>
        </Reveal>

        <Reveal>
          <div className="agent-land__assemble">
            <p className="agent-land__assemble-label">每次送模的固定顺序</p>
            <div className="agent-land__assemble-flow">
              <span>System / Persona</span>
              <span className="arr" aria-hidden>
                →
              </span>
              <span>SessionState</span>
              <span className="arr" aria-hidden>
                →
              </span>
              <span>近期原文 + Pin</span>
              <span className="arr" aria-hidden>
                →
              </span>
              <span>本轮 User</span>
            </div>
            <p className="agent-land__assemble-hint">
              <Pin className="w-3.5 h-3.5" />
              钉住的约束与关键 ID 永不进入压缩排除列表；代码细节用路径引用，需要时再重读原文。
            </p>
          </div>
        </Reveal>
      </section>

      {/* Hire */}
      <section id="hire" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Agent Hire</p>
            <h2>把岗位招进来，而不是再注册一个账号</h2>
            <p>
              官方职业目录 × 等级 × 租期。入职即绑定 Soul：这位同事有记忆、有人格、可在会话里直接换班。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__hire">
          <Reveal className="agent-land__hire-visual" aria-hidden>
            <div className="agent-land__hire-orb">
              <Briefcase className="w-8 h-8" />
            </div>
            <div className="agent-land__hire-chip agent-land__hire-chip--a">全栈秘书</div>
            <div className="agent-land__hire-chip agent-land__hire-chip--b">Soul 席位</div>
            <div className="agent-land__hire-chip agent-land__hire-chip--c">包月 / 包年</div>
          </Reveal>
          <div className="agent-land__hire-list">
            {HIRE_POINTS.map((item, i) => (
              <Reveal key={item.title} style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
                <article className="agent-land__hire-item">
                  <h3>{item.title}</h3>
                  <p>{item.desc}</p>
                </article>
              </Reveal>
            ))}
            <Reveal>
              <div className="agent-land__inline-cta">
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
          </div>
        </div>
      </section>

      {/* Soul */}
      <section id="soul" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Soul Memory</p>
            <h2>记忆跟着 Agent，而不是跟着某一个聊天窗口</h2>
            <p>
              Soul 是跨工具可延续的灵魂层：Chat 负责对话与行动，Soul 负责耐久偏好、决策与实体锚点。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__soul-grid">
          {SOUL_POINTS.map((s, i) => (
            <Reveal key={s.k} className="agent-land__soul-cell" style={{ transitionDelay: `${i * 80}ms` } as CSSProperties}>
              <span className="agent-land__mono">{s.k}</span>
              <h3>{s.title}</h3>
              <p>{s.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="agent-land__note">
            <Sparkles className="w-4 h-4" />
            <span>
              压缩触发时会把新决策 / 约束 / 实体差集写入 Soul（需开启长期记忆），摘要只负责本会话连贯。
            </span>
          </div>
        </Reveal>
      </section>

      {/* Runtime */}
      <section id="runtime" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Runtime</p>
            <h2>能想、能做、能管边界</h2>
            <p>同一套 Runtime 支撑控制台对话：工具开关、模型选择、沙箱与联网搜索均可按用户配置。</p>
          </div>
        </Reveal>
        <div className="agent-land__runtime">
          {RUNTIME.map((r, i) => {
            const Icon = r.icon
            return (
              <Reveal key={r.title} className="agent-land__runtime-row" style={{ transitionDelay: `${i * 60}ms` } as CSSProperties}>
                <div className="agent-land__runtime-icon">
                  <Icon className="w-5 h-5" />
                </div>
                <div>
                  <h3>{r.title}</h3>
                  <p>{r.desc}</p>
                </div>
              </Reveal>
            )
          })}
        </div>
      </section>

      {/* Observe */}
      <section id="observe" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Observability</p>
            <h2>看得见它怎么想、怎么花</h2>
            <p>实时事件流 + 上下文占用 + 积分流水，让 Agent 落地时有信任、有账本。</p>
          </div>
        </Reveal>
        <Reveal>
          <ul className="agent-land__obs agent-land__obs--wide">
            {OBSERVE.map((o) => (
              <li key={o.t}>
                <Eye className="w-4 h-4" />
                <div>
                  <span className="agent-land__mono">{o.t}</span>
                  <p>{o.d}</p>
                </div>
              </li>
            ))}
          </ul>
        </Reveal>
      </section>

      {/* Closing */}
      <section className="agent-land__closing">
        <Reveal>
          <p className="agent-land__eye">Start</p>
          <h2 className="agent-land__closing-brand">AIMATEX-AGENT</h2>
          <p>
            {isLoggedIn
              ? '招聘一位同事，绑定 Soul，开一场不会糊掉的长会话。'
              : '登录后即可招聘 Agent、开启 Soul，并在长对话里一键压缩上下文。'}
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
        <a href="https://aimatex.com" target="_blank" rel="noopener noreferrer">
          aimatex.com
        </a>
      </footer>
    </div>
  )
}
