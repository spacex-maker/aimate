export interface UserContainerStatus {
  userId: number
  username: string
  containerName: string
  status: string
  lastUsedAt?: number | null
  cpuPercent?: string | null
  memUsage?: string | null
  memPercent?: string | null
}

/** 管理员用户列表项（不含密码） */
export interface AdminUserListItem {
  id: number
  username: string
  email: string | null
  displayName: string | null
  status: string
  role: string
  pointsBalance: number
  createTime: string | null
  lastLoginTime: string | null
}

export interface AdminUserPage {
  items: AdminUserListItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface BatchAdminUsersResult {
  succeeded: number
  failed: number
  errors: { userId: number; message: string }[]
}

/** 系统配置项（供管理员修改） */
export interface SystemConfigItem {
  id: number
  configKey: string
  configValue: string | null
  description: string | null
}

/** LLM 服务商（一把 Key） */
export interface SystemProviderDto {
  id: number
  code: string
  displayName: string
  description: string | null
  enabled: boolean
  sortOrder: number
  apiKeySet: boolean
  apiKeyMasked: string | null
}

/** 宿主机负载历史记录，用于时间轴图表 */
export interface HostLoadMetric {
  hostName: string
  timestamp: string
  cpuLoadPercent: number | null
  memAvailablePercent: number | null
  rootFsUsedPercent: number | null
}

/** 各组件连接状态（与启动摘要一致） */
export interface ComponentStatusDto {
  server: { port: string; javaVersion: string; virtualThreadEnabled: boolean }
  mysql: { ok: boolean; message: string }
  milvus: { ok: boolean; host: string; port: number; collectionName: string; dimensions: number; message: string | null }
  llm: {
    primary: { name: string; model: string; keyOk: boolean }
    fallback: { name: string; model: string; keyOk: boolean }
  }
  embedding: { ok: boolean; model: string; dimensions: number; baseUrl: string }
  docker: { ok: boolean; version: string | null; enabled: boolean; image: string | null; message: string | null }
}

export interface BillingUsageSummary {
  userId: number
  username: string
  callCount: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  /** 字段名沿用后端汇总；实际为估算成本 CNY */
  estimatedCostUsd: number
}

export interface BillingUsageResponse {
  summaries: BillingUsageSummary[]
  grandTotalTokens: number
  grandTotalCostUsd: number
}

export interface BillingUsageDetail {
  id: number
  provider: string
  model: string
  callType: string | null
  sessionId: string | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  estimatedCostCny: number | null
  pointsCharged: number | null
  success: boolean
  createTime: string | null
}

export interface KbCuratorConfig {
  categoryIds: number[]
  maxBasesPerRun: number
  thinContentChars: number
  staleDays: number
  useTavily: boolean
  tavilyMaxResults: number
  tavilySearchDepth: string
  researchQueriesPerBase: number
  temperature: number
  maxTokens: number
  styleGuide: string
  updateSummaryAndTags: boolean
}

export interface SystemAgentDto {
  id: number
  type: string
  code: string
  name: string
  description: string | null
  enabled: boolean
  scheduleHour: number
  scheduleMinute: number
  systemModelId: number | null
  running: boolean
  lastRunAt: string | null
  lastRunStatus: string | null
  lastRunSummary: string | null
  tavilyConfigured: boolean
  config: KbCuratorConfig
  updateTime: string | null
}

export interface SystemAgentSaveBody {
  type?: string
  code: string
  name: string
  description?: string
  enabled?: boolean
  scheduleHour?: number
  scheduleMinute?: number
  systemModelId?: number | null
  config?: Partial<KbCuratorConfig>
}

export interface SystemAgentRunItem {
  id: number
  agentId: number
  triggerType: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  summary: string | null
  detail: unknown
}

export interface SystemAgentRunPage {
  items: SystemAgentRunItem[]
  page: number
  size: number
  total: number
  totalPages: number
}
