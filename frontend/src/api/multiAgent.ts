import { http } from './httpClient'
import type { SessionResponse } from '../types/agent'

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

export type AgentGender = 'FEMALE' | 'MALE' | 'OTHER'

export interface UserAgentBrief {
  id: number
  kind: 'SECRETARY' | 'HIRED'
  catalogId?: number
  tier?: AgentTier
  /** 伙伴真名：招聘时取定，不可更改 */
  name: string
  originalName?: string
  /** 招聘时的职业名 */
  careerName?: string
  /** 真名 · 职业 */
  displayTitle?: string
  avatarUrl?: string
  gender?: string
  genderLabel?: string | null
  /** ISO date YYYY-MM-DD */
  birthday?: string | null
  ageYears?: number | null
  ageMonths?: number | null
  ageDays?: number | null
  createTime?: string | null
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
  /** 单聊是否允许派发本机任务 */
  localAssistDm?: boolean
  /** 群聊是否允许派发本机任务 */
  localAssistGroup?: boolean
  /** Agent 默认：长期记忆 */
  memoryEnabled?: boolean
  /** Agent 默认：联网搜索 */
  webSearchEnabled?: boolean
  /** Agent 默认：自主编写工具 */
  createToolEnabled?: boolean
  /** Agent 默认：办公室脚本 */
  scriptExecEnabled?: boolean
  localBridgeConfigured?: boolean
  localBridgeTokenPrefix?: string | null
  /** 至少一台本机设备在心跳窗口内 */
  localBridgeOnline?: boolean
  localBridgeOnlineCount?: number
  localBridgeLastSeenAt?: string | null
  /** 多台本机设备（同一 Agent，如全局唯一 Cursor） */
  localBridgeDevices?: LocalBridgeDeviceInfo[] | null
  /** 最近一台设备摘要（兼容） */
  localBridgeDevice?: LocalBridgeDeviceInfo | null
}

export type LocalBridgeLinkMode = 'CLIENT' | 'PROMPT'

export interface LocalBridgeDeviceInfo {
  id?: number
  deviceKey?: string
  /** CLIENT=桌面连接器；PROMPT=提示词/脚本 */
  linkMode?: LocalBridgeLinkMode
  hostname?: string
  os?: string
  arch?: string
  connectorVersion?: string
  appName?: string
  appPath?: string
  careerCode?: string
  label?: string
  lastSeenAt?: string | null
  online?: boolean
  /** 用户在网页端手动关闭连接 */
  userDisconnected?: boolean
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
  deviceKey?: string
}

export interface RegisterLocalDeviceResult {
  ok: boolean
  linkMode: LocalBridgeLinkMode
  userAgentId: number
  device?: LocalBridgeDeviceInfo | null
  bridge?: LocalBridgeConnectBundle
  clientHint?: string
  connectorDownloadHint?: string
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
  hire: (catalogId: number, period: RentPeriod, identity: { name: string; gender: AgentGender }) =>
    http<{ orderNo: string; userAgentId: number; totalPoints: number }>('/api/agents/hire', {
      method: 'POST',
      body: JSON.stringify({ catalogId, period, name: identity.name, gender: identity.gender }),
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
  /** 打开与该 Agent 的单聊（复用已有会话或新建空会话，一人一聊） */
  openChat: (id: number) =>
    http<SessionResponse>(`/api/me/agents/${id}/open-chat`, { method: 'POST' }),
  updatePersona: (
    id: number,
    body: {
      name?: string
      avatarUrl?: string
      gender?: string
      personality?: string
      dailyPointsLimit?: number | null
      localAssistDm?: boolean
      localAssistGroup?: boolean
      memoryEnabled?: boolean
      webSearchEnabled?: boolean
      createToolEnabled?: boolean
      scriptExecEnabled?: boolean
    },
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
  /** 添加设备：CLIENT=客户端指引；PROMPT=登记并返回提示词 */
  registerLocalDevice: (id: number, body: { linkMode: LocalBridgeLinkMode; label?: string }) =>
    http<RegisterLocalDeviceResult>(`/api/me/agents/${id}/local-devices`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  removeLocalDevice: (id: number, deviceKey: string) =>
    http<{ ok: boolean; deviceKey: string }>(
      `/api/me/agents/${id}/local-devices?deviceKey=${encodeURIComponent(deviceKey)}`,
      { method: 'DELETE' },
    ),
  /** 手动关闭设备连接（离线，直至允许重连） */
  disconnectLocalDevice: (id: number, deviceKey: string) =>
    http<{ ok: boolean; deviceKey: string; disconnected?: boolean; device?: LocalBridgeDeviceInfo }>(
      `/api/me/agents/${id}/local-devices/disconnect`,
      { method: 'POST', body: JSON.stringify({ deviceKey }) },
    ),
  /** 允许已关闭的设备再次上线 */
  allowLocalDeviceReconnect: (id: number, deviceKey: string) =>
    http<{ ok: boolean; deviceKey: string; disconnected?: boolean; device?: LocalBridgeDeviceInfo }>(
      `/api/me/agents/${id}/local-devices/allow-reconnect`,
      { method: 'POST', body: JSON.stringify({ deviceKey }) },
    ),
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
