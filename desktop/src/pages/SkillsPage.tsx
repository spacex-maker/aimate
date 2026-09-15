import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { hostApi } from '../lib/host-api'
import { useAppStore } from '../stores/app'

type Skill = {
  name: string
  description: string
  baseDir: string
  enabled: boolean
  source: string
}

export function SkillsPage() {
  const { currentProjectId } = useAppStore()
  const [skills, setSkills] = useState<Skill[]>([])

  const reload = async () => {
    setSkills(await hostApi.listSkills(currentProjectId))
  }

  useEffect(() => {
    void reload().catch((e) => toast.error(e instanceof Error ? e.message : '加载失败'))
  }, [currentProjectId])

  const toggle = async (s: Skill) => {
    const next = await hostApi.setSkillEnabled(s.name, !s.enabled)
    setSkills(next as Skill[])
  }

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mb-5">
        <h2 className="text-base font-medium">OpenClaw Skills</h2>
        <p className="text-xs text-white/40 mt-1">
          兼容 SKILL.md 发现顺序：bundled → 用户 managed → 项目 skills/。聊天中可用 /skill-name 强制加载。
        </p>
      </div>
      <div className="space-y-2">
        {skills.length === 0 && <div className="text-sm text-white/30">未发现技能</div>}
        {skills.map((s) => (
          <div key={`${s.source}-${s.name}`} className="rounded-xl border border-white/10 bg-surface-overlay/40 px-4 py-3 flex gap-4">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm text-accent">/{s.name}</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/50">{s.source}</span>
              </div>
              <p className="text-xs text-white/60 mt-1">{s.description || '（无描述）'}</p>
              <p className="text-[10px] text-white/30 mt-1 truncate" title={s.baseDir}>
                {s.baseDir}
              </p>
            </div>
            <button
              type="button"
              onClick={() => void toggle(s)}
              className={`self-start px-3 py-1.5 rounded-lg text-xs border ${
                s.enabled
                  ? 'border-emerald-500/40 text-emerald-300 bg-emerald-500/10'
                  : 'border-white/15 text-white/40'
              }`}
            >
              {s.enabled ? '已启用' : '已禁用'}
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
