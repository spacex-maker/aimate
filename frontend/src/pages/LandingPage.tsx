import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  ArrowUpRight,
  Bot,
  Boxes,
  Brain,
  Cable,
  Eye,
  Globe2,
  KeyRound,
  Layers,
  Monitor,
  Network,
  Radio,
  Shield,
  Sparkles,
  Terminal,
  Workflow,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

type Status = 'live' | 'building' | 'soon'

const NAV = [
  { href: '#loop', label: '思考循环' },
  { href: '#memory', label: '记忆' },
  { href: '#tools', label: '工具' },
  { href: '#platforms', label: '多平台' },
  { href: '#observe', label: '可观测' },
  { href: '#roadmap', label: '路线图' },
]

const LOOP = [
  {
    id: 'RECALL',
    title: '召回',
    desc: '从向量记忆中检索相关上下文，注入当前推理窗口。',
  },
  {
    id: 'THINK',
    title: '思考',
    desc: '流式调用 LLM，产出回复意图或 tool_call 决策。',
  },
  {
    id: 'DECIDE',
    title: '决策',
    desc: '判断是否需要行动：继续工具链，或结束本轮。',
  },
  {
    id: 'ACT',
    title: '执行',
    desc: '调用工具、读写记忆、跑沙箱命令，再回写上下文。',
  },
]

const MEMORY_LAYERS = [
  {
    tier: 'Working',
    name: '工作记忆',
    desc: '当前会话上下文窗口，支撑多步推理与工具结果回流。',
    status: 'live' as Status,
  },
  {
    tier: 'Episodic',
    name: '情节记忆',
    desc: '跨会话向量存储，按用户隔离；store / recall 主动读写。',
    status: 'live' as Status,
  },
  {
    tier: 'Semantic',
    name: '语义索引',
    desc: '可配置 Embedding（BGE / OpenAI 等），按维度检索长期知识。',
    status: 'live' as Status,
  },
  {
    tier: 'Procedural',
    name: '程序性记忆',
    desc: '把可复用流程沉淀为 Skills / Playbooks，跨任务复用。',
    status: 'building' as Status,
  },
]

const TOOL_CAPS = [
  {
    icon: Terminal,
    title: '沙箱执行',
    desc: '每用户独立 Linux 容器，支持 Python / Node / Shell，装环境再跑脚本。',
    status: 'live' as Status,
  },
  {
    icon: Brain,
    title: '记忆工具',
    desc: 'store_memory / recall_memory，让 Agent 主动记住与回忆。',
    status: 'live' as Status,
  },
  {
    icon: Cable,
    title: 'MCP 工具网关',
    desc: '以开放协议接入第三方工具与数据源，统一鉴权与审计。',
    status: 'building' as Status,
  },
  {
    icon: Boxes,
    title: 'Skills 生态',
    desc: 'OpenClaw 兼容 Skills，桌面端本地加载，云端逐步对齐。',
    status: 'building' as Status,
  },
]

const PLATFORMS = [
  {
    icon: Globe2,
    title: 'Web Console',
    desc: '浏览器即用：会话、思考流、记忆与工具管理。',
    status: 'live' as Status,
  },
  {
    icon: Monitor,
    title: 'Desktop Client',
    desc: '本地 RECALL→THINK→ACT，云端代理 LLM，适合长任务与本地 Skills。',
    status: 'live' as Status,
  },
  {
    icon: Radio,
    title: 'API / Proxy',
    desc: '认证与模型代理接口，便于嵌入自有产品与自动化流水线。',
    status: 'live' as Status,
  },
  {
    icon: Network,
    title: '多通道接入',
    desc: 'IM、机器人、企业协作入口——同一 Agent Runtime，多端触达。',
    status: 'soon' as Status,
  },
]

const OBSERVE = [
  { t: 'ITERATION', d: '每一轮循环起点与状态变更实时推送。' },
  { t: 'THINKING', d: 'Token 级流式思考，看见推理如何展开。' },
  { t: 'TOOL_CALL', d: '工具入参、结果与错误完整可见。' },
  { t: 'FINAL', d: '最终答案与轨迹落库，便于回放与审计。' },
]

const ROADMAP = [
  { name: '自主思考循环', detail: 'RECALL → THINK → DECIDE → ACT', status: 'live' as Status },
  { name: '长期向量记忆', detail: 'Milvus + 可配置 Embedding', status: 'live' as Status },
  { name: '实时思考流', detail: 'WebSocket 事件流', status: 'live' as Status },
  { name: 'BYO LLM / Key', detail: 'OpenAI / DeepSeek / 通义 / Kimi…', status: 'live' as Status },
  { name: '容器工具沙箱', detail: '每用户隔离执行环境', status: 'live' as Status },
  { name: 'Desktop Agent', detail: '本地循环 + 云端代理', status: 'live' as Status },
  { name: 'MCP 工具协议', detail: '开放工具与数据接入', status: 'building' as Status },
  { name: '多 Agent 编排', detail: '角色分工 / A2A 协作', status: 'soon' as Status },
  { name: '程序性记忆', detail: 'Skills / Playbooks 沉淀', status: 'building' as Status },
  { name: '多通道触达', detail: 'IM / 机器人 / 企业入口', status: 'soon' as Status },
]

const STATUS_LABEL: Record<Status, string> = {
  live: '已上线',
  building: '建设中',
  soon: '规划中',
}

function StatusPill({ status }: { status: Status }) {
  return <span className={`agent-land__status agent-land__status--${status}`}>{STATUS_LABEL[status]}</span>
}

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
      { root, threshold: 0.14, rootMargin: '0px 0px -6% 0px' },
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
        { x: 18, y: 28, l: 'PLAN' },
        { x: 82, y: 24, l: 'TOOL' },
        { x: 50, y: 50, l: 'CORE', hub: true },
        { x: 20, y: 78, l: 'MEM' },
        { x: 84, y: 76, l: 'RUN' },
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

/** AIMATEX-AGENT 官网落地页（公开，不强制登录；已登录则入口直达控制台） */
export function LandingPage() {
  const { user, isLoggedIn } = useAuth()
  const [loopIdx, setLoopIdx] = useState(0)
  const [navSolid, setNavSolid] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const displayName = user?.displayName || user?.username || ''
  const initial = (displayName || '?').charAt(0).toUpperCase()

  useEffect(() => {
    const id = window.setInterval(() => {
      setLoopIdx((i) => (i + 1) % LOOP.length)
    }, 2800)
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

      {/* —— Hero: brand + one line + CTA + full-bleed runtime visual —— */}
      <section className="agent-land__hero">
        <HeroCanvas />
        <div className="agent-land__hero-inner">
          <p className="agent-land__kicker">
            <Sparkles className="w-3.5 h-3.5" />
            Autonomous Agent Runtime
          </p>
          <h1 className="agent-land__brand">AIMATEX-AGENT</h1>
          <p className="agent-land__headline">让 Agent 真正跑起来</p>
          <p className="agent-land__lead">
            {isLoggedIn
              ? `已登录为 ${displayName}。可直接进入控制台继续会话、记忆与工具编排。`
              : '规划、记忆、工具调用与可观测执行——一套面向落地的自主 Agent 运行时。'}
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
            <a href="#loop" className="agent-land__btn-ghost">
              了解能力架构
            </a>
          </div>
        </div>
        <a href="#loop" className="agent-land__scroll" aria-label="向下滚动">
          <span />
        </a>
      </section>

      {/* —— Thinking loop —— */}
      <section id="loop" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Thinking Loop</p>
            <h2>不是一次问答，而是持续运转的循环</h2>
            <p>
              对标业界 Agent Runtime 的核心范式：召回上下文、流式思考、决定是否行动、执行工具，再持久化——直到目标完成。
            </p>
          </div>
        </Reveal>
        <Reveal className="agent-land__loop">
          <div className="agent-land__loop-rail" aria-hidden>
            {LOOP.map((step, i) => (
              <button
                key={step.id}
                type="button"
                className={`agent-land__loop-step ${i === loopIdx ? 'is-active' : ''}`}
                onClick={() => setLoopIdx(i)}
              >
                <span className="n">{String(i + 1).padStart(2, '0')}</span>
                <span className="id">{step.id}</span>
              </button>
            ))}
          </div>
          <div className="agent-land__loop-stage" key={LOOP[loopIdx].id}>
            <div className="agent-land__loop-badge">
              <Workflow className="w-4 h-4" />
              {LOOP[loopIdx].id}
            </div>
            <h3>{LOOP[loopIdx].title}</h3>
            <p>{LOOP[loopIdx].desc}</p>
            <div className="agent-land__loop-meter" aria-hidden>
              {LOOP.map((_, i) => (
                <span key={i} className={i === loopIdx ? 'is-on' : ''} />
              ))}
            </div>
          </div>
        </Reveal>
      </section>

      {/* —— Memory —— */}
      <section id="memory" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Memory Fabric</p>
            <h2>分层记忆架构，跨会话记住该记住的</h2>
            <p>
              参考短时 / 情节 / 语义 / 程序性记忆的业界分层：先把工作上下文与向量长期记忆做扎实，再演进可复用流程沉淀。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__layers">
          {MEMORY_LAYERS.map((layer, i) => (
            <Reveal key={layer.tier} className="agent-land__layer" style={{ transitionDelay: `${i * 80}ms` } as CSSProperties}>
              <div className="agent-land__layer-top">
                <span className="agent-land__mono">{layer.tier}</span>
                <StatusPill status={layer.status} />
              </div>
              <h3>{layer.name}</h3>
              <p>{layer.desc}</p>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="agent-land__note">
            <Layers className="w-4 h-4" />
            <span>
              当前以 Milvus 向量库承载长期记忆，按用户与集合隔离；会话中可通过工具主动写入与召回。
            </span>
          </div>
        </Reveal>
      </section>

      {/* —— Tools —— */}
      <section id="tools" className="agent-land__section">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Tool Calling</p>
            <h2>工具不是插件列表，而是可执行的行动面</h2>
            <p>
              从沙箱命令、记忆读写，到面向 MCP 的开放工具协议——Agent 按需编排外部能力，而不是停在聊天框里。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__grid4">
          {TOOL_CAPS.map((cap, i) => {
            const Icon = cap.icon
            return (
              <Reveal key={cap.title} className="agent-land__cap" style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
                <div className="agent-land__cap-icon">
                  <Icon className="w-5 h-5" />
                </div>
                <div className="agent-land__cap-top">
                  <h3>{cap.title}</h3>
                  <StatusPill status={cap.status} />
                </div>
                <p>{cap.desc}</p>
              </Reveal>
            )
          })}
        </div>
        <Reveal>
          <div className="agent-land__safe">
            <Shield className="w-5 h-5" />
            <div>
              <strong>隔离与安全默认开启</strong>
              <p>容器采用 no-new-privileges、cap-drop 等加固选项，兼顾可执行性与边界隔离。</p>
            </div>
          </div>
        </Reveal>
      </section>

      {/* —— Platforms —— */}
      <section id="platforms" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Multi-Platform</p>
            <h2>同一 Runtime，多端接入</h2>
            <p>
              Web 控制台、桌面客户端与 API 代理已打通；后续扩展 IM / 机器人等通道，让 Agent 出现在工作真正发生的地方。
            </p>
          </div>
        </Reveal>
        <div className="agent-land__platforms">
          {PLATFORMS.map((p, i) => {
            const Icon = p.icon
            return (
              <Reveal key={p.title} className="agent-land__platform" style={{ transitionDelay: `${i * 70}ms` } as CSSProperties}>
                <Icon className="w-6 h-6" />
                <div className="agent-land__platform-body">
                  <div className="agent-land__cap-top">
                    <h3>{p.title}</h3>
                    <StatusPill status={p.status} />
                  </div>
                  <p>{p.desc}</p>
                </div>
              </Reveal>
            )
          })}
        </div>
      </section>

      {/* —— Observe + Models —— */}
      <section id="observe" className="agent-land__section">
        <div className="agent-land__split">
          <Reveal>
            <div className="agent-land__section-head agent-land__section-head--left">
              <p className="agent-land__eye">Observability</p>
              <h2>看得见它怎么想</h2>
              <p>WebSocket 实时推送思考与工具事件，会话轨迹可回放——可观测是落地 Agent 的信任基础。</p>
            </div>
            <ul className="agent-land__obs">
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
          <Reveal>
            <div className="agent-land__section-head agent-land__section-head--left">
              <p className="agent-land__eye">Model Freedom</p>
              <h2>自带 Key，自选模型</h2>
              <p>同一套系统里切换厂商与 Embedding，少改部署、多试策略。</p>
            </div>
            <div className="agent-land__models">
              <div>
                <KeyRound className="w-5 h-5" />
                <h3>LLM BYO</h3>
                <p>OpenAI、DeepSeek、通义、Kimi 等；用户 Key 优先于系统 Key。</p>
              </div>
              <div>
                <Brain className="w-5 h-5" />
                <h3>Embedding</h3>
                <p>自选向量模型与维度，记忆检索按你的配置走。</p>
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* —— Roadmap —— */}
      <section id="roadmap" className="agent-land__section agent-land__section--alt">
        <Reveal>
          <div className="agent-land__section-head">
            <p className="agent-land__eye">Capability Map</p>
            <h2>能力会持续兑现</h2>
            <p>
              对标主流 Agent 平台：编排、记忆、工具协议、多 Agent、多通道接入。功能按路线图逐步实现——先把已上线能力用扎实。
            </p>
          </div>
        </Reveal>
        <Reveal>
          <ul className="agent-land__roadmap">
            {ROADMAP.map((item) => (
              <li key={item.name}>
                <StatusPill status={item.status} />
                <div>
                  <strong>{item.name}</strong>
                  <span>{item.detail}</span>
                </div>
              </li>
            ))}
          </ul>
        </Reveal>
      </section>

      {/* —— Closing CTA —— */}
      <section className="agent-land__closing">
        <Reveal>
          <p className="agent-land__eye">Start</p>
          <h2 className="agent-land__closing-brand">AIMATEX-AGENT</h2>
          <p>
            {isLoggedIn
              ? '会话、记忆与工具都在控制台里——点一下继续。'
              : '用统一 AIMATEX Account 登录，创建会话，亲眼看 Agent 回忆、思考、调用工具。'}
          </p>
          {isLoggedIn ? (
            <Link to="/app" className="agent-land__btn">
              <span>进入控制台</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
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
