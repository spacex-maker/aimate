# -*- coding: utf-8 -*-
"""Generate CollabStoryReel.tsx with ASCII-safe \\u escapes."""
from pathlib import Path
import codecs

RAW = r'''
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
    title: '\u5148\u660e\u767d\u89c4\u5219',
    caption: '\u6bcf\u4e2a\u4eba\u90fd\u6709\u81ea\u5df1\u7684 AI\u3002\u60f3\u7528\u522b\u4eba\u7684 AI\uff0c\u5fc5\u987b\u5148\u83b7\u5f97\u4ed6\u4e3b\u4eba\u7684\u8bb8\u53ef\u3002',
  },
  {
    id: 'ask',
    ms: 5200,
    step: '01',
    title: '\u5728\u7fa4\u91cc @ \u5bf9\u65b9 AI',
    caption: '\u5b89\u6709\u4e00\u4efd\u6025\u6d3b\uff0c\u76f4\u63a5 @ \u4e86\u540c\u4e8b MAX \u7684 Agent\u300cAria\u300d\u2014\u2014\u4e0d\u662f @ MAX \u672c\u4eba\u3002',
  },
  {
    id: 'permit',
    ms: 5000,
    step: '02',
    title: 'AI \u5148\u5411\u4e3b\u4eba\u8981\u8bb8\u53ef',
    caption: 'Aria \u4e0d\u4f1a\u81ea\u4f5c\u4e3b\u5f20\u3002\u5b83\u628a\u4efb\u52a1\u6458\u8981\u53d1\u7ed9\u4e3b\u4eba MAX\uff0c\u7b49\u4ed6\u51b3\u5b9a\u3002',
  },
  {
    id: 'approve',
    ms: 4200,
    step: '03',
    title: '\u4e3b\u4eba\u70b9\u540c\u610f\u624d\u6267\u884c',
    caption: 'MAX \u770b\u6e05\u695a\u8981\u505a\u4ec0\u4e48\uff0c\u70b9\u4e86\u300c\u540c\u610f\u6267\u884c\u300d\u3002\u8fb9\u754c\u59cb\u7ec8\u5728\u4eba\u624b\u91cc\u3002',
  },
  {
    id: 'work',
    ms: 4800,
    step: '04',
    title: '\u62ff\u5230\u8bb8\u53ef\uff0c\u56de\u7fa4\u5e72\u6d3b',
    caption: 'Aria \u56de\u7fa4\u63a5\u5355\u5e76\u5f00\u59cb\u5904\u7406\u2014\u2014MAX \u4e0d\u7528\u88ab\u6253\u6270\uff0c\u540c\u4e8b\u4e5f\u7acb\u523b\u6709\u56de\u5e94\u3002',
  },
  {
    id: 'family',
    ms: 5600,
    step: '05',
    title: '\u4e3b\u4eba\u628a\u65f6\u95f4\u8fd8\u7ed9\u751f\u6d3b',
    caption: '\u6d3b\u4ea4\u7ed9\u81ea\u5df1\u7684 AI\uff0cMAX \u53ef\u4ee5\u628a\u665a\u4e0a\u8fd8\u7ed9\u5b69\u5b50\u3002\u8fd9\u5c31\u662f\u534f\u4f5c\u4f53\u7684\u610f\u4e49\u3002',
  },
]

const STEPS = BEATS.filter((b) => b.id !== 'intro')

/** Landing collab story reel \u2014 clearer copy + UI */
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
      aria-label={'\u56e2\u961f\u534f\u4f5c\u6d41\u7a0b\u6f14\u793a'}
      data-beat={id}
    >
      <div className="collab-reel__top">
        <div className="collab-reel__eyebrow">{'\u770b\u4e00\u904d\u5c31\u61c2'} \u00b7 {'\u7ea6'} 30s</div>
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
            aria-label={playing ? '\u6682\u505c' : '\u64ad\u653e'}
            disabled={reduceMotion}
          >
            {playing ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
          </button>
          <button type="button" className="collab-reel__btn" onClick={restart} aria-label={'\u91cd\u64ad'}>
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
            <span>{'\u4ea7\u54c1\u5468\u4f1a\u7fa4'}</span>
            <span className="collab-reel__panel-tag">{'\u7fa4\u804a'}</span>
          </div>
          <div className="collab-reel__cast">
            <span><i className="collab-reel__cast-an" />{'\u5b89'} \u00b7 {'\u53d1\u8d77\u4eba'}</span>
            <span><i className="collab-reel__cast-max" />MAX \u00b7 {'\u540c\u4e8b'}</span>
            <span><i className="collab-reel__cast-aria" />Aria \u00b7 MAX {'\u7684'} AI</span>
          </div>
          <div className="collab-reel__msgs">
            <div className={`collab-reel__msg ${id !== 'intro' && id !== 'family' ? 'is-in' : ''}`}>
              <div className="collab-reel__avatar collab-reel__avatar--an">{'\u5b89'}</div>
              <div className="collab-reel__bubble">
                <div className="collab-reel__who">{'\u5b89'} <em>{'\u53d1\u8d77\u4eba'}</em></div>
                <p>
                  {'\u8fd9\u4efd\u7ade\u54c1\u5bf9\u6bd4\u6211\u6765\u4e0d\u53ca\u4e86\u2014\u2014'}
                  <span className="collab-reel__at">@Aria</span>
                  {'\u80fd\u5e2e MAX \u63a5\u4e00\u4e0b\u5417\uff1f\u6750\u6599\u5728\u7fa4\u6587\u4ef6\u91cc\u3002'}
                </p>
              </div>
            </div>
            <div className={`collab-reel__msg collab-reel__msg--agent ${id === 'work' ? 'is-in' : ''}`}>
              <div className="collab-reel__avatar collab-reel__avatar--aria">
                <Bot className="w-3.5 h-3.5" />
              </div>
              <div className="collab-reel__bubble">
                <div className="collab-reel__who">Aria <em>MAX {'\u7684'} AI</em></div>
                <p className="collab-reel__typing">
                  {'\u4e3b\u4eba\u5df2\u540c\u610f\u3002\u6211\u6765\u62c9\u8868\u3001\u5bf9\u53e3\u5f84\uff0c\u534a\u5c0f\u65f6\u5185\u56de\u8349\u7a3f\u3002'}
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
                {'\u2192'} {'\u53f3\u4fa7'} \u00b7 Aria {'\u6b63\u5411'} MAX {'\u8bf7\u6c42\u8bb8\u53ef'}
              </p>
            )}
          </div>
        </div>

        <div className={`collab-reel__panel collab-reel__permit ${showPermit ? 'is-active' : ''}${split ? ' is-side' : ''}`}>
          <div className="collab-reel__panel-head">
            <Bot className="w-3.5 h-3.5" />
            <span>{'\u53d1\u7ed9'} MAX {'\u7684\u8bb8\u53ef\u901a\u77e5'}</span>
            <span className="collab-reel__panel-tag collab-reel__panel-tag--warn">{'\u5f85\u786e\u8ba4'}</span>
          </div>
          <div className="collab-reel__card">
            <p className="collab-reel__card-eye">
              {'\u7fa4\u300c\u4ea7\u54c1\u5468\u4f1a\u300d'} \u00b7 {'\u5b89'} @ {'\u4e86\u4f60\u7684'} Aria
            </p>
            <h4>{'\u5e2e\u5fd9\u505a\u7ade\u54c1\u5bf9\u6bd4\u8868'}</h4>
            <p className="collab-reel__card-lead">
              {'\u622a\u6b62\u5468\u4e94'} \u00b7 {'\u53ea\u8bfb\u7fa4\u6587\u4ef6'} \u00b7 {'\u4e0d\u52a8\u4f60\u7684\u672c\u673a'}
            </p>
            <ul>
              <li>{'\u4ece\u7fa4\u6587\u4ef6\u8bfb\u53d6\u6570\u636e'}</li>
              <li>{'\u6574\u7406\u5bf9\u6bd4\u7ef4\u5ea6\u5e76\u51fa\u8349\u7a3f'}</li>
              <li>{'\u56de\u7fa4\u540c\u6b65\u8fdb\u5ea6'}</li>
            </ul>
            <div className="collab-reel__actions">
              <span className="collab-reel__ghost">{'\u62d2\u7edd'}</span>
              <span className={`collab-reel__ok ${id === 'approve' ? 'is-pressed' : ''}`}>
                <Check className="w-3.5 h-3.5" />
                {'\u540c\u610f\u6267\u884c'}
              </span>
            </div>
            {id === 'approve' && (
              <p className="collab-reel__toast">{'\u5df2\u6388\u6743'} \u2014 Aria {'\u5f00\u59cb\u5de5\u4f5c'}</p>
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
            {'\u6d3b\u4ea4\u7ed9 AI\uff0c\u665a\u4e0a\u8fd8\u7ed9\u5bb6\u4eba'}
          </p>
          <p className="collab-reel__family-sub">
            {'\u534f\u4f5c\u4f53\u5728\u7fa4\u91cc\u63a8\u8fdb'} \u00b7 {'\u4eba\u5728\u73b0\u573a\u966a\u4f34'}
          </p>
        </div>

        <div className={`collab-reel__intro ${showIntro ? 'is-on' : ''}`}>
          <p className="collab-reel__intro-eq">
            {'\u4eba'} <em>+</em> {'\u4e13\u5c5e'} AI <em>=</em> <strong>{'\u534f\u4f5c\u4f53'}</strong>
          </p>
          <p className="collab-reel__intro-sub">
            {'\u60f3\u7528\u522b\u4eba\u7684 AI\uff1f\u5148\u5f97\u5230\u4ed6\u4e3b\u4eba\u7684\u8bb8\u53ef\u3002'}
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
'''

def main() -> None:
    text = codecs.decode(RAW, "unicode_escape")
    out = Path(__file__).with_name("CollabStoryReel.tsx")
    out.write_text(text.lstrip("\n"), encoding="utf-8")
    sample = out.read_text(encoding="utf-8")
    assert "在群里" in sample or "\u5728\u7fa4" not in RAW
    # real chinese after decode
    assert "发起人" in sample or "安" in sample
    print("wrote", out.stat().st_size)


if __name__ == "__main__":
    main()
