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

export interface AgentIndustry {
  id: number
  code: string
  name: string
  description?: string | null
  sortOrder?: number
  enabled?: boolean
}

export type AgentExecutionMode = 'CLOUD_LLM' | 'LOCAL_BRIDGE'

export interface AgentCareer {
  id: number
  industryId?: number | null
  industryName?: string | null
  code: string
  name: string
  description?: string | null
  executionMode?: AgentExecutionMode
  sortOrder?: number
  enabled?: boolean
}

export interface AgentCatalogKnowledgeBrief {
  id: number
  publicId: string
  name: string
  summary?: string | null
}

export interface AgentCatalogItem {
  id: number
  careerId: number
  careerName?: string
  industryId?: number | null
  industryName?: string | null
  executionMode?: AgentExecutionMode
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
  /** 官方绑定的领域知识库（招聘时一并获得） */
  knowledgeBases?: AgentCatalogKnowledgeBrief[]
}

export interface UserAgentBrief {
  id: number
  kind: 'SECRETARY' | 'HIRED'
  catalogId?: number
  tier?: AgentTier
  name: string
  /** 招聘/创建时的原始名称；昵称改后仍可展示 */
  originalName?: string
  avatarUrl?: string
  gender?: string
  status: string
  expiresAt?: string
  currentPromptVersion: number
  defaultSystemModelId?: number
  /** Soul 平台 agentId，用于跳转 console */
  soulAgentId?: number | null
  soulBound?: boolean
  externalAgentKey?: string
  /** 每日积分上限；null/undefined=不限制 */
  dailyPointsLimit?: number | null
  executionMode?: AgentExecutionMode
  localBridge?: boolean
  localBridgeConfigured?: boolean
  localBridgeTokenPrefix?: string | null
}

export interface LocalBridgeConnectBundle {
  userAgentId: number
  agentName: string
  executionMode: string
  apiBase: string
  tokenPrefix?: string | null
  tokenRotated: boolean
  hasToken: boolean
  pollIntervalSeconds: number
  token?: string
  prompt: string
  hint?: string
}

export interface OfficePlan {
  id: number
  code: string
  name: string
  areaSqm: number
  instanceType?: string
  cpuDesc?: string
  memDesc?: string
  diskGb?: number
  priceDayPoints: number
  priceMonthPoints: number
  priceYearPoints: number
  description?: string
  enabled?: boolean
}

export interface UserOfficeView {
  id?: number
  status: string
  ready?: boolean
  planId?: number
  planName?: string
  areaSqm?: number
  expiresAt?: string | null
  provisionError?: string | null
  mock?: boolean
}

export const officeApi = {
  listPlans: () => http<OfficePlan[]>('/api/offices/plans'),
  me: () => http<UserOfficeView>('/api/offices/me'),
  quote: (planId: number, period: RentPeriod) =>
    http<{ totalPoints: number; lines: { label: string; points: number }[] }>(
      `/api/offices/quote?planId=${planId}&period=${period}`,
    ),
  rent: (planId: number, period: RentPeriod) =>
    http<UserOfficeView>('/api/offices/rent', {
      method: 'POST',
      body: JSON.stringify({ planId, period }),
    }),
  renew: (period: RentPeriod) =>
    http<UserOfficeView>('/api/offices/renew', {
      method: 'POST',
      body: JSON.stringify({ period }),
    }),
  release: () => http<UserOfficeView>('/api/offices/release', { method: 'POST' }),
}

export interface SoulMemoryItem {
  id?: number
  content: string
  kind?: string
  scope?: string | null
  project?: string | null
  importance?: number
  pinned?: boolean
  status?: string | null
  updatedAt?: string | null
}

export interface AgentSoulMemories {
  ok: boolean
  soulEnabled?: boolean
  soulBound?: boolean
  soulAgentId?: number | null
  externalAgentKey?: string
  items: SoulMemoryItem[]
  total: number
  error?: string
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
  listIndustries: () => http<AgentIndustry[]>('/api/agents/industries'),
  listCareers: () => http<AgentCareer[]>('/api/agents/careers'),
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
    http<UserAgentBrief & { promptVersions: PromptVersion[]; personality?: string; soulKeyId?: string }>(
      '/api/me/agents/' + id
    ),
  updatePersona: (
    id: number,
    body: { name?: string; avatarUrl?: string; gender?: string; personality?: string; dailyPointsLimit?: number | null },
  ) => http<UserAgentBrief>(`/api/me/agents/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  uploadAvatar: (id: number, imageDataUrl: string) =>
    http<UserAgentBrief>(`/api/me/agents/${id}/avatar`, {
      method: 'POST',
      body: JSON.stringify({ image: imageDataUrl }),
    }),
  clearAvatar: (id: number) =>
    http<UserAgentBrief>(`/api/me/agents/${id}/avatar`, {
      method: 'POST',
      body: JSON.stringify({ clear: true }),
    }),
  excludeAtoms: (id: number, atomIds: string[]) =>
    http<PromptVersion>(`/api/me/agents/${id}/prompt/exclude-atoms`, {
      method: 'POST',
      body: JSON.stringify({ atomIds }),
    }),
  ensureSecretary: () =>
    http<UserAgentBrief>('/api/me/agents/secretary/ensure', { method: 'POST' }),
  /** 按 Agent 预览绑定 Soul 的记忆（不暴露 token） */
  soulMemories: (id: number) => http<AgentSoulMemories>(`/api/me/agents/${id}/soul-memories`),
  /** 本地 AI 接入：生成/轮换令牌与可复制提示词 */
  localBridgeConnect: (id: number, rotate = false) =>
    http<LocalBridgeConnectBundle>(`/api/me/agents/${id}/local-bridge/connect`, {
      method: 'POST',
      body: JSON.stringify({ rotate }),
    }),
}

export const adminAgentApi = {
  listIndustries: () => http<Record<string, unknown>[]>('/api/admin/agents/industries'),
  saveIndustry: (body: Record<string, unknown>, id?: number) =>
    http<Record<string, unknown>>(id ? `/api/admin/agents/industries/${id}` : '/api/admin/agents/industries', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    }),
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
