import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Bot } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

const NAV = [
  { href: '#define', label: '定义' },
  { href: '#origin', label: '起源' },
  { href: '#mechanism', label: '机制' },
  { href: '#distinguish', label: '辨析' },
  { href: '#apply', label: '应用' },
]

const TIMELINE = [
  {
    year: '19 世纪',
    title: '乔治·亨利·刘易斯',
    body: '首次用「涌现」描述整体大于部分之和——那些无法从部件性质直接推出的整体特性。',
  },
  {
    year: '1972',
    title: 'More Is Different',
    body: '物理学家菲利普·安德森阐明：规模上升会带来质变。复杂性本身会诞生新的法则。',
  },
  {
    year: '2020s',
    title: '规模定律时代',
    body: '当模型越过百亿、千亿参数临界点，逻辑推理、创作与工具使用等能力以非线性方式跃迁。',
  },
]

const CONDITIONS = [
  {
    n: '01',
    title: '大量相互作用的个体',
    body: '参数、数据、智能体——足够多的简单单元彼此连接，才有整体可谈。',
  },
  {
    n: '02',
    title: '一组简单规则',
    body: '注意力、梯度、提示与反馈：规则本身朴素，却足以驱动复杂行为。',
  },
  {
    n: '03',
    title: '非线性互动',
    body: '不是线性叠加，而是相互放大。越过临界点，系统改写自己的能力边界。',
  },
]

const DISTINCTIONS = [
  {
    title: '智能涌现',
    en: 'Emergent Intelligence',
    body: '规模越过临界后，系统自发产生远超设计预期、未经显式编程的复杂能力——推理、创造、跨任务泛化。',
  },
  {
    title: '功能涌现',
    en: 'Capability Jump',
    body: '特定任务上的局部质变与显著提升，仍建立在对既有数据模式的学习与重组之上，是能力地图上的跃迁点。',
  },
  {
    title: '并非意识',
    en: 'Not Consciousness',
    body: '涌现不等于主观体验。识别「猫」并不意味着「看到」猫；复杂行为可以没有自我感知。',
  },
]

const DRIVERS = [
  { k: '数据', d: '维度扩展与多模态融合，撑开更完整的语义空间。' },
  { k: '架构', d: 'Transformer 等创新让简单规则在巨量单元间高效协作。' },
  { k: '训练', d: '自监督、强化学习等范式，让能力在临界附近相变式生长。' },
]

const APPLIES = [
  {
    title: '自然语言与创作',
    body: '少样本提示、长程对话、代码与数学——规模跨过临界后，能力曲线不再是平滑斜坡。',
  },
  {
    title: '科学发现',
    body: '从蛋白质折叠到复杂推理，涌现把「搜索」推进到「结构洞察」的边缘。',
  },
  {
    title: '集体智能',
    body: '多元主体在开放网络中非线性协同，知识生产方式本身也会涌现出新形态。',
  },
  {
    title: '产业与传播',
    body: 'AI 融入金融、教育、医疗；传播从媒介工具跃迁为可行动的社会操作系统。',
  },
]

function useReveal<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [on, setOn] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const root = el.closest('.emerge') as HTMLElement | null
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
    <div ref={ref} className={`emerge__reveal ${on ? 'is-on' : ''} ${className}`.trim()} style={style}>
      {children}
    </div>
  )
}

function EmergenceField() {
  const nodes = Array.from({ length: 48 }, (_, i) => {
    const angle = (i / 48) * Math.PI * 2
    const ring = 18 + (i % 5) * 12
    const x = 50 + Math.cos(angle) * ring * (0.55 + (i % 3) * 0.12)
    const y = 50 + Math.sin(angle) * ring * (0.42 + (i % 4) * 0.1)
    return { x, y, d: (i % 7) * 0.18, s: 0.55 + (i % 4) * 0.2 }
  })

  return (
    <div className="emerge__field" aria-hidden>
      <div className="emerge__field-glow" />
      <div className="emerge__field-core" />
      <div className="emerge__field-ring emerge__field-ring--a" />
      <div className="emerge__field-ring emerge__field-ring--b" />
      <div className="emerge__field-wave" />
      {nodes.map((n, i) => (
        <span
          key={i}
          className="emerge__particle"
          style={{
            left: `${n.x}%`,
            top: `${n.y}%`,
            animationDelay: `${n.d}s`,
            ['--ps' as string]: String(n.s),
          }}
        />
      ))}
    </div>
  )
}

/** 智能涌现概念页 — chat.aimatex.com/emergence */
export function EmergencePage() {
  const { user, isLoggedIn } = useAuth()
  const [navSolid, setNavSolid] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const displayName = user?.displayName || user?.username || ''

  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    const onScroll = () => setNavSolid(root.scrollTop > 24)
    onScroll()
    root.addEventListener('scroll', onScroll, { passive: true })
    return () => root.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    document.title = '智能涌现 · Emergent Intelligence — AIMATEX-AGENT'
  }, [])

  return (
    <div className="emerge" ref={rootRef}>
      <div className="emerge__wash" aria-hidden />
      <div className="emerge__noise" aria-hidden />

      <header className={`emerge__nav ${navSolid ? 'is-solid' : ''}`}>
        <Link to="/" className="emerge__brand-mini">
          <span className="emerge__mark">
            <Bot className="w-4 h-4" />
          </span>
          <span>AIMATEX-AGENT</span>
        </Link>
        <nav className="emerge__nav-links" aria-label="章节导航">
          {NAV.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="emerge__nav-actions">
          <Link to="/" className="emerge__ghost-link">
            <ArrowLeft className="w-3.5 h-3.5" />
            产品首页
          </Link>
          <Link to={isLoggedIn ? '/app' : '/login'} className="emerge__btn emerge__btn--sm">
            <span>{isLoggedIn ? '控制台' : '开始使用'}</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      </header>

      <section className="emerge__hero">
        <EmergenceField />
        <div className="emerge__hero-inner">
          <p className="emerge__brand-signal">AIMATEX-AGENT</p>
          <h1 className="emerge__title">
            <span className="emerge__title-zh">智能涌现</span>
            <span className="emerge__title-en">Emergent Intelligence</span>
          </h1>
          <p className="emerge__headline">整体，终将大于部分之和</p>
          <p className="emerge__lead">
            当规模越过临界点，简单规则在混沌边缘自组织——智能并非被一行行写死，而是从相互作用中生长出来。
          </p>
          <div className="emerge__cta-row">
            <a href="#define" className="emerge__btn">
              <span>进入概念</span>
              <ArrowRight className="w-4 h-4" />
            </a>
            <Link to="/" className="emerge__btn-ghost">
              回到 AIMATEX-AGENT
            </Link>
          </div>
        </div>
        <a href="#define" className="emerge__scroll" aria-label="向下滚动">
          <span />
        </a>
      </section>

      <section className="emerge__quote">
        <Reveal>
          <blockquote>
            <p>More Is Different.</p>
            <footer>Philip W. Anderson · 1972</footer>
          </blockquote>
          <p className="emerge__quote-note">
            量变不是无限稀释的渐近线。越过某个阈值，系统会改写自己的语言——新能力、新秩序、新的可能。
          </p>
        </Reveal>
      </section>

      <section id="define" className="emerge__section">
        <Reveal>
          <p className="emerge__eye">定义</p>
          <h2>规模触及临界，能力自发跃迁</h2>
          <p className="emerge__prose">
            智能涌现（Emergent Intelligence）指：当 AI 系统的数据量、参数量与算力达到一定临界点后，系统自发产生远超设计预期、未经明确编程的复杂能力——逻辑推理、创造性生成、复杂问题解决。它与「规模定律」紧密相连：性能随规模扩大发生的，往往不是平滑的线性增长，而是非线性跃迁。
          </p>
          <p className="emerge__prose emerge__prose--accent">
            由小生大，由简入繁。独立要素通过相互作用聚合成整体，涌现出个体单独存在时所没有的行为。
          </p>
        </Reveal>
      </section>

      <section id="origin" className="emerge__section emerge__section--alt">
        <Reveal>
          <p className="emerge__eye">发展历程</p>
          <h2>从哲学洞见到工程现实</h2>
          <p className="emerge__section-lead">一个古老的词，正在成为理解 AI 能力跃迁的关键视角。</p>
        </Reveal>
        <div className="emerge__timeline">
          {TIMELINE.map((item, i) => (
            <Reveal key={item.year} style={{ transitionDelay: `${i * 80}ms` }}>
              <article className="emerge__tl-item">
                <p className="emerge__tl-year">{item.year}</p>
                <h3>{item.title}</h3>
                <p>{item.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      <section id="mechanism" className="emerge__section">
        <Reveal>
          <p className="emerge__eye">基本特征与机制</p>
          <h2>三个条件，一次相变</h2>
          <p className="emerge__section-lead">
            涌现发生在「混沌边缘」——既非僵硬规则，也非完全随机。规模定律告诉我们：越过临界，性能曲线会断裂成新的形状。
          </p>
        </Reveal>
        <div className="emerge__conditions">
          {CONDITIONS.map((c, i) => (
            <Reveal key={c.n} style={{ transitionDelay: `${i * 70}ms` }}>
              <article className="emerge__cond">
                <span className="emerge__cond-n">{c.n}</span>
                <h3>{c.title}</h3>
                <p>{c.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
        <Reveal>
          <div className="emerge__drivers">
            <p className="emerge__drivers-label">技术协同</p>
            <ul>
              {DRIVERS.map((d) => (
                <li key={d.k}>
                  <strong>{d.k}</strong>
                  <span>{d.d}</span>
                </li>
              ))}
            </ul>
          </div>
        </Reveal>
      </section>

      <section id="distinguish" className="emerge__section emerge__section--alt">
        <Reveal>
          <p className="emerge__eye">概念辨析</p>
          <h2>看清跃迁，也看清边界</h2>
          <p className="emerge__section-lead">
            学界对「是否真正涌现」仍有讨论：有人看到规模触发的质变，有人提醒度量标准可能制造假象，提示工程也可能放大表象。保持清醒，并不削弱奇迹本身。
          </p>
        </Reveal>
        <div className="emerge__dist">
          {DISTINCTIONS.map((d, i) => (
            <Reveal key={d.title} style={{ transitionDelay: `${i * 70}ms` }}>
              <article className="emerge__dist-item">
                <p className="emerge__dist-en">{d.en}</p>
                <h3>{d.title}</h3>
                <p>{d.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      <section id="apply" className="emerge__section">
        <Reveal>
          <p className="emerge__eye">应用领域</p>
          <h2>当涌现进入现实世界</h2>
          <p className="emerge__section-lead">
            它拓展技术边界，也重塑产业与传播——从「人找功能」走向「能力主动汇聚」。
          </p>
        </Reveal>
        <div className="emerge__applies">
          {APPLIES.map((a, i) => (
            <Reveal key={a.title} style={{ transitionDelay: `${i * 60}ms` }}>
              <article className="emerge__apply">
                <h3>{a.title}</h3>
                <p>{a.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="emerge__future">
        <Reveal>
          <p className="emerge__eye">展望</p>
          <h2 className="emerge__future-title">从静态涌现，走向动态涌现</h2>
          <p>
            今日多数智能涌现仍偏「静态」——在给定规模与数据上完成一次相变。未来，类脑智能、神经符号融合、价值对齐与绿色
            AI，可能让系统在持续互动中不断自我改写：真正拥有动态涌现的能力。
          </p>
          <p className="emerge__future-punch">量变积累勇气。质变回应想象。</p>
        </Reveal>
      </section>

      <section className="emerge__closing">
        <Reveal>
          <p className="emerge__brand-signal">AIMATEX-AGENT</p>
          <h2>让涌现发生在你的团队里</h2>
          <p>
            {isLoggedIn
              ? `${displayName}，招聘会思考的同事，绑定 Soul，在长会话中看见能力如何生长。`
              : '招聘职业 Agent、绑定 Soul 记忆、压缩上下文——把规模定律落到每一次协作。'}
          </p>
          <div className="emerge__cta-row emerge__cta-row--center">
            <Link to={isLoggedIn ? '/app' : '/login'} className="emerge__btn">
              <span>{isLoggedIn ? '进入控制台' : '开始使用'}</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
            <Link to="/" className="emerge__btn-ghost">
              了解产品
            </Link>
          </div>
        </Reveal>
      </section>

      <footer className="emerge__footer">
        <span>© {new Date().getFullYear()} AIMATEX</span>
        <Link to="/">产品首页</Link>
        <a href="https://soul.aimatex.com/emergence" target="_blank" rel="noopener noreferrer">
          Soul · 智能涌现
        </a>
      </footer>
    </div>
  )
}
