/**
 * Agent 头像：有自定义 URL 用自定义；否则按 seed 生成唯一几何头像（data URL）。
 */

const PALETTES = [
  ['#7c3aed', '#a78bfa', '#1e1b4b'],
  ['#0891b2', '#67e8f9', '#083344'],
  ['#059669', '#6ee7b7', '#022c22'],
  ['#d97706', '#fcd34d', '#451a03'],
  ['#e11d48', '#fda4af', '#4c0519'],
  ['#4f46e5', '#a5b4fc', '#1e1b4b'],
  ['#0d9488', '#5eead4', '#042f2e'],
  ['#db2777', '#f9a8d4', '#500724'],
]

function hashSeed(seed: string): number {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** 确定性 SVG data URL，同一 seed 始终相同 */
export function generateAgentAvatarDataUrl(seed: string): string {
  const h = hashSeed(seed || 'agent')
  const [c1, c2, c3] = PALETTES[h % PALETTES.length]
  const rot = h % 360
  const cx = 20 + (h % 40)
  const cy = 20 + ((h >> 8) % 40)
  const r1 = 28 + ((h >> 4) % 20)
  const r2 = 18 + ((h >> 12) % 16)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 80">
<defs>
  <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%" stop-color="${c1}"/>
    <stop offset="100%" stop-color="${c2}"/>
  </linearGradient>
</defs>
<rect width="80" height="80" fill="${c3}"/>
<g transform="rotate(${rot} 40 40)">
  <circle cx="${cx}" cy="${cy}" r="${r1}" fill="url(#g)" opacity="0.95"/>
  <circle cx="${80 - cx}" cy="${80 - cy}" r="${r2}" fill="${c2}" opacity="0.55"/>
  <rect x="22" y="22" width="36" height="36" rx="10" fill="${c1}" opacity="0.35" transform="rotate(${(h >> 3) % 45} 40 40)"/>
</g>
</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export function agentAvatarSeed(agent: {
  id?: number | null
  externalAgentKey?: string | null
  name?: string | null
}): string {
  if (agent.externalAgentKey) return agent.externalAgentKey
  if (agent.id != null) return `agent-${agent.id}`
  return agent.name || 'agent'
}

export function resolveAgentAvatarUrl(agent: {
  id?: number | null
  avatarUrl?: string | null
  externalAgentKey?: string | null
  name?: string | null
}): string {
  const url = agent.avatarUrl?.trim()
  if (url) return url
  return generateAgentAvatarDataUrl(agentAvatarSeed(agent))
}
