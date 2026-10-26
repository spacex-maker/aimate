import { useCallback, useEffect, useRef, useState } from 'react'
import { Bot, Check, Pause, Play, RotateCcw, Users } from 'lucide-react'

type Beat = 'intro' | 'ask' | 'permit' | 'approve' | 'work' | 'family'

type BeatDef = {
  id: Beat
  ms: number
  step: string
  title: string
  caption: string
}

const BEATS: BeatDef[] = [
  {
    id: 'intro',
    ms: 2600,
    step: '00',
    title: '先明白规则',
    caption: '每个人都有自己的 AI。想用别人的 AI，必须先获得他主人的许可。',
  },
  {
    id: 'ask',
    ms: 5200,
    step: '01',
    title: '在群里 @ 对方 AI',
    caption: '安有一份急活，直接 @ 了同事 MAX 的 Agent「Aria」——不是 @ MAX 本人。',
  },
  {
    id: 'permit',
    ms: 5000,
    step: '02',
    title: 'AI 先向主人要许可',
    caption: 'Aria 不会自作主张。它把任务摘要发给主人 MAX，等他决定。',
  },
  {
    id: 'approve',
    ms: 4200,
    step: '03',
    title: '主人点同意才执行',
    caption: 'MAX 看清楚要做什么，点了「同意执行」。边界始终在人手里。',
  },
  {
    id: 'work',
    ms: 4800,
    step: '04',
    title: '拿到许可，回群干活',
    caption: 'Aria 回群接单并开始处理——MAX 不用被打扰，同事也立刻有回应。',
  },
  {
    id: 'family',
    ms: 5600,
    step: '05',
    title: '主人把时间还给生活',
    caption: '活交给自己的 AI，MAX 可以把晚上还给孩子。这就是协作体的意义。',
  },
]

const STEPS = BEATS.filter((b) => b.id !== 'intro')

/** Landing collab story reel — clearer copy + UI */
export function CollabStoryReel() {
  const rootRef = useRef<HTMLDivElement>(null)
  const [beat, setBeat] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [inView, setInView] = useState(false)
  const [tick, setTick] = useState(0)
  const reduceMotion = usePrefersReducedMotion()

  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const io = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting) setInView(true)
      },
      { threshold: 0.3 }
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  useEffect(() => {
    if (inView && !reduceMotion) setPlaying(true)
  }, [inView, reduceMotion])

  useEffect(() => {
    if (!playing || reduceMotion) return
    const cur = BEATS[beat]
    const started = performance.now()
    let raf = 0
    const loop = (now: number) => {
      const p = Math.min(1, (now - started) / cur.ms)
      setTick(p)
      if (p >= 1) {
        setBeat((b) => (b + 1) % BEATS.length)
        setTick(0)
        return
      }
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(raf)
  }, [beat, playing, reduceMotion])

  const restart = useCallback(() => {
    setBeat(0)
    setTick(0)
    setPlaying(true)
  }, [])

  const jump = useCallback((i: number) => {
    setBeat(i)
    setTick(0)
    setPlaying(false)
  }, [])

  const cur = BEATS[beat]
  const id = cur.id
  const progress = ((beat + tick) / BEATS.length) * 100
  const showChat = id === 'ask' || id === 'permit' || id === 'approve' || id === 'work'
  const showPermit = id === 'permit' || id === 'approve'
  const showFamily = id === 'family'
  const showIntro = id === 'intro'
  const split = showPermit

  return (
    <div
      ref={rootRef}
      className={`collab-reel${split ? ' collab-reel--split' : ''}`}
      role="region"
      aria-label={'团队协作流程演示'}
      data-beat={id}
    >
      <div className="collab-reel__top">
        <div className="collab-reel__eyebrow">{'看一遍就懂'} · {'约'} 30s</div>
        <ol className="collab-reel__rail" aria-hidden="true">
          {STEPS.map((s) => {
            const idx = BEATS.findIndex((b) => b.id === s.id)
            const on = beat === idx
            const done = beat > idx
            return (
              <li key={s.id} className={on ? 'is-on' : done ? 'is-done' : ''}>
                <button type="button" onClick={() => jump(idx)}>
                  <span className="collab-reel__rail-n">{s.step}</span>
                  <span className="collab-reel__rail-t">{s.title}</span>
                </button>
              </li>
            )
          })}
        </ol>
      </div>

      <div className="collab-reel__chrome">
        <div className="collab-reel__caption">
          <p className="collab-reel__title">
            <span className="collab-reel__step">{cur.step}</span>
            {cur.title}
          </p>
          <p className="collab-reel__desc">{cur.caption}</p>
        </div>
        <div className="collab-reel__controls">
          <button
            type="button"
            className="collab-reel__btn"
            onClick={() => setPlaying((p) => !p)}
            aria-label={playing ? '暂停' : '播放'}
            disabled={reduceMotion}
          >
            {playing ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
          </button>
          <button type="button" className="collab-reel__btn" onClick={restart} aria-label={'重播'}>
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="collab-reel__track" aria-hidden="true">
        <div className="collab-reel__track-fill" style={{ width: `${progress}%` }} />
      </div>

      <div className="collab-reel__stage">
        <div className={`collab-reel__panel collab-reel__chat ${showChat ? 'is-active' : ''}${split ? ' is-side' : ''}`}>
          <div className="collab-reel__panel-head">
            <span className="collab-reel__dot" />
            <span>{'产品周会群'}</span>
            <span className="collab-reel__panel-tag">{'群聊'}</span>
          </div>
          <div className="collab-reel__cast">
            <span><i className="collab-reel__cast-an" />{'安'} · {'发起人'}</span>
            <span><i className="collab-reel__cast-max" />MAX · {'同事'}</span>
            <span><i className="collab-reel__cast-aria" />Aria · MAX {'的'} AI</span>
          </div>
          <div className="collab-reel__msgs">
            <div className={`collab-reel__msg ${id !== 'intro' && id !== 'family' ? 'is-in' : ''}`}>
              <div className="collab-reel__avatar collab-reel__avatar--an">{'安'}</div>
              <div className="collab-reel__bubble">
                <div className="collab-reel__who">{'安'} <em>{'发起人'}</em></div>
                <p>
                  {'这份竞品对比我来不及了——'}
                  <span className="collab-reel__at">@Aria</span>
                  {'能帮 MAX 接一下吗？材料在群文件里。'}
                </p>
              </div>
            </div>
            <div className={`collab-reel__msg collab-reel__msg--agent ${id === 'work' ? 'is-in' : ''}`}>
              <div className="collab-reel__avatar collab-reel__avatar--aria">
                <Bot className="w-3.5 h-3.5" />
              </div>
              <div className="collab-reel__bubble">
                <div className="collab-reel__who">Aria <em>MAX {'的'} AI</em></div>
                <p className="collab-reel__typing">
                  {'主人已同意。我来拉表、对口径，半小时内回草稿。'}
                </p>
                <div className="collab-reel__bars" data-on={id === 'work' ? '1' : '0'}>
                  <i />
                  <i />
                  <i />
                </div>
              </div>
            </div>
            {(id === 'permit' || id === 'approve') && (
              <p className="collab-reel__hint">
                {'→'} {'右侧'} · Aria {'正向'} MAX {'请求许可'}
              </p>
            )}
          </div>
        </div>

        <div className={`collab-reel__panel collab-reel__permit ${showPermit ? 'is-active' : ''}${split ? ' is-side' : ''}`}>
          <div className="collab-reel__panel-head">
            <Bot className="w-3.5 h-3.5" />
            <span>{'发给'} MAX {'的许可通知'}</span>
            <span className="collab-reel__panel-tag collab-reel__panel-tag--warn">{'待确认'}</span>
          </div>
          <div className="collab-reel__card">
            <p className="collab-reel__card-eye">
              {'群「产品周会」'} · {'安'} @ {'了你的'} Aria
            </p>
            <h4>{'帮忙做竞品对比表'}</h4>
            <p className="collab-reel__card-lead">
              {'截止周五'} · {'只读群文件'} · {'不动你的本机'}
            </p>
            <ul>
              <li>{'从群文件读取数据'}</li>
              <li>{'整理对比维度并出草稿'}</li>
              <li>{'回群同步进度'}</li>
            </ul>
            <div className="collab-reel__actions">
              <span className="collab-reel__ghost">{'拒绝'}</span>
              <span className={`collab-reel__ok ${id === 'approve' ? 'is-pressed' : ''}`}>
                <Check className="w-3.5 h-3.5" />
                {'同意执行'}
              </span>
            </div>
            {id === 'approve' && (
              <p className="collab-reel__toast">{'已授权'} — Aria {'开始工作'}</p>
            )}
          </div>
        </div>

        <div className={`collab-reel__panel collab-reel__family ${showFamily ? 'is-active' : ''}`}>
          <div className="collab-reel__family-sky" />
          <div className="collab-reel__family-scene">
            <div className="collab-reel__family-adult">
              <Users className="w-5 h-5" />
              <span>MAX</span>
            </div>
            <div className="collab-reel__family-kid" aria-hidden />
            <div className="collab-reel__family-ball" aria-hidden />
          </div>
          <p className="collab-reel__family-line">
            {'活交给 AI，晚上还给家人'}
          </p>
          <p className="collab-reel__family-sub">
            {'协作体在群里推进'} · {'人在现场陪伴'}
          </p>
        </div>

        <div className={`collab-reel__intro ${showIntro ? 'is-on' : ''}`}>
          <p className="collab-reel__intro-eq">
            {'人'} <em>+</em> {'专属'} AI <em>=</em> <strong>{'协作体'}</strong>
          </p>
          <p className="collab-reel__intro-sub">
            {'想用别人的 AI？先得到他主人的许可。'}
          </p>
        </div>
      </div>
    </div>
  )
}

function usePrefersReducedMotion() {
  const [v, setV] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    setV(mq.matches)
    const fn = () => setV(mq.matches)
    mq.addEventListener('change', fn)
    return () => mq.removeEventListener('change', fn)
  }, [])
  return v
}
