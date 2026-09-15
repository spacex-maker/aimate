import fs from 'node:fs'
import path from 'node:path'

export interface SkillInfo {
  name: string
  description: string
  baseDir: string
  body: string
  enabled: boolean
  source: 'project' | 'managed' | 'bundled'
}

function parseFrontmatter(raw: string): { meta: Record<string, string>; body: string } {
  if (!raw.startsWith('---')) {
    return { meta: {}, body: raw }
  }
  const end = raw.indexOf('\n---', 3)
  if (end < 0) return { meta: {}, body: raw }
  const fm = raw.slice(3, end).trim()
  const body = raw.slice(end + 4).replace(/^\n/, '')
  const meta: Record<string, string> = {}
  for (const line of fm.split('\n')) {
    const m = line.match(/^([A-Za-z0-9_-]+)\s*:\s*(.*)$/)
    if (m) meta[m[1]] = m[2].replace(/^["']|["']$/g, '').trim()
  }
  return { meta, body }
}

function walkSkills(root: string, source: SkillInfo['source'], depth = 0, out: SkillInfo[] = []): SkillInfo[] {
  if (!fs.existsSync(root) || depth > 6) return out
  const entries = fs.readdirSync(root, { withFileTypes: true })
  const skillMd = entries.find((e) => e.isFile() && e.name === 'SKILL.md')
  if (skillMd) {
    const baseDir = root
    const raw = fs.readFileSync(path.join(root, 'SKILL.md'), 'utf8')
    const { meta, body } = parseFrontmatter(raw)
    const name = (meta.name || path.basename(root)).toLowerCase()
    const description = meta.description || ''
    const resolvedBody = body.replaceAll('{baseDir}', baseDir)
    out.push({ name, description, baseDir, body: resolvedBody, enabled: true, source })
    return out
  }
  for (const e of entries) {
    if (e.isDirectory() && !e.name.startsWith('.')) {
      walkSkills(path.join(root, e.name), source, depth + 1, out)
    }
  }
  return out
}

/** OpenClaw-compatible discovery: later sources override earlier by name. Priority: bundled < managed < project */
export function discoverSkills(roots: {
  bundled?: string
  managed?: string
  project?: string
}, disabled: string[] = []): SkillInfo[] {
  const map = new Map<string, SkillInfo>()
  const order: Array<{ dir?: string; source: SkillInfo['source'] }> = [
    { dir: roots.bundled, source: 'bundled' },
    { dir: roots.managed, source: 'managed' },
    { dir: roots.project, source: 'project' },
  ]
  for (const { dir, source } of order) {
    if (!dir) continue
    for (const skill of walkSkills(dir, source)) {
      map.set(skill.name, skill)
    }
  }
  const disabledSet = new Set(disabled.map((s) => s.toLowerCase()))
  return [...map.values()].map((s) => ({
    ...s,
    enabled: !disabledSet.has(s.name),
  }))
}

export function skillSummariesPrompt(skills: SkillInfo[]): string {
  const enabled = skills.filter((s) => s.enabled)
  if (!enabled.length) return ''
  const lines = enabled.map((s) => `- /${s.name}: ${s.description}`)
  return [
    'You have the following skills available. Use /skill-name in reasoning when relevant.',
    'When a skill is invoked, follow its instructions carefully.',
    ...lines,
  ].join('\n')
}

export function resolveForcedSkill(message: string, skills: SkillInfo[]): SkillInfo | null {
  const m = message.match(/^\/([a-z0-9-]+)\b/i)
  if (!m) return null
  return skills.find((s) => s.enabled && s.name === m[1].toLowerCase()) ?? null
}
