import { http } from './httpClient'

export type RentPeriod = 'DAY' | 'MONTH' | 'YEAR'
export type AgentTier = 'JUNIOR' | 'MID' | 'SENIOR' | 'EXPERT'

export interface UserProfile {
  completed: boolean
  occupation?: string | null
  age?: number | null
  gender?: string | null
  bio?: string | null
  extraJson?: string | null
}

export interface AgentCatalogItem {
  id: number
  careerId: number
  careerName?: string
  tier: AgentTier
  name: string
  description?: string
  priceDayPoints: number
  priceMonthPoints: number
  priceYearPoints: number
  soulPriceDayPoints: number
  soulPriceMonthPoints: number
  soulPriceYearPoints: number
  enabled: boolean
}

export interface UserAgentBrief {
  id: number
  kind: 'SECRETARY' | 'HIRED'
  catalogId?: number
  tier?: AgentTier
  name: string
  avatarUrl?: string
  gender?: string
  status: string
  expiresAt?: string
  currentPromptVersion: number
  defaultSystemModelId?: number
}

export interface PromptVersion {
  id: number
  versionNo: number
  changeSummary?: string
  atomsJson?: string
  active: boolean
  promptText: string
  createTime?: string
}

export interface HireQuote {
  catalogId: number
  name: string
  tier: string
  period: string
  agentPoints: number
  soulPoints: number
  totalPoints: number
  lines: { label: string; points: number }[]
}

export const multiAgentApi = {
  getProfile: () => http<UserProfile>('/api/me/profile'),
  saveProfile: (body: Partial<UserProfile>) =>
    http<UserProfile>('/api/me/profile', { method: 'PUT', body: JSON.stringify(body) }),

  listCatalog: () => http<AgentCatalogItem[]>('/api/agents/catalog'),
  listCareers: () => http<{ id: number; code: string; name: string }[]>('/api/agents/careers'),
  quote: (catalogId: number, period: RentPeriod) =>
    http<HireQuote>(`/api/agents/quote?catalogId=${catalogId}&period=${period}`),
  hire: (catalogId: number, period: RentPeriod) =>
    http<{ orderNo: string; userAgentId: number; totalPoints: number }>('/api/agents/hire', {
      method: 'POST',
      body: JSON.stringify({ catalogId, period }),
    }),
  renew: (id: number, period: RentPeriod) =>
    http<{ orderNo: string; totalPoints: number }>(`/api/agents/${id}/renew`, {
      method: 'POST',
      body: JSON.stringify({ period }),
    }),

  listMine: () => http<UserAgentBrief[]>('/api/me/agents'),
  detail: (id: number) =>
    http<UserAgentBrief & { promptVersions: PromptVersion[]; personality?: string }>('/api/me/agents/' + id),
  updatePersona: (id: number, body: { name?: string; avatarUrl?: string; gender?: string; personality?: string }) =>
    http<UserAgentBrief>(`/api/me/agents/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  excludeAtoms: (id: number, atomIds: string[]) =>
    http<PromptVersion>(`/api/me/agents/${id}/prompt/exclude-atoms`, {
      method: 'POST',
      body: JSON.stringify({ atomIds }),
    }),
  ensureSecretary: () =>
    http<UserAgentBrief>('/api/me/agents/secretary/ensure', { method: 'POST' }),
}

export const adminAgentApi = {
  listCareers: () => http<Record<string, unknown>[]>('/api/admin/agents/careers'),
  saveCareer: (body: Record<string, unknown>, id?: number) =>
    http<Record<string, unknown>>(id ? `/api/admin/agents/careers/${id}` : '/api/admin/agents/careers', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    }),
  listCatalog: () => http<Record<string, unknown>[]>('/api/admin/agents/catalog'),
  saveCatalog: (body: Record<string, unknown>, id?: number) =>
    http<Record<string, unknown>>(id ? `/api/admin/agents/catalog/${id}` : '/api/admin/agents/catalog', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    }),
  listTierModels: () => http<Record<string, unknown>[]>('/api/admin/agents/tier-models'),
  replaceTierModels: (tier: string, items: { systemModelId: number; isDefault?: boolean; sortOrder?: number }[]) =>
    http<Record<string, unknown>[]>(`/api/admin/agents/tier-models/${tier}`, {
      method: 'PUT',
      body: JSON.stringify(items),
    }),
  getPromptModel: () => http<{ systemModelId: string | null }>('/api/admin/agents/prompt-model'),
  setPromptModel: (systemModelId: number | null) =>
    http<{ systemModelId: string | null }>('/api/admin/agents/prompt-model', {
      method: 'PUT',
      body: JSON.stringify({ systemModelId }),
    }),
}
