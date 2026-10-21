import type { HostResourceStatusDto, SystemModelDto, ToolSettingsDto } from '../types/agent'
import type { AdminUserListItem, BillingUsageDetail, BillingUsageResponse, ComponentStatusDto, HostLoadMetric, SystemConfigItem, UserContainerStatus } from '../types/admin'
import { http } from './httpClient'

export const adminApi = {
  listContainers: () =>
    http<UserContainerStatus[]>('/api/admin/containers'),

  /** 管理员：获取全部系统模型（含已关闭） */
  listAllSystemModels: () =>
    http<SystemModelDto[]>('/api/admin/system-models'),

  /** 管理员：更新系统模型属性（启用状态 / 排序权重 / 积分倍率 / 成本价等） */
  updateSystemModel: (id: number, body: {
    enabled?: boolean
    sortOrder?: number
    apiKeyConfigKey?: string | null
    tip?: string | null
    pointsPer1kPromptTokens?: number
    pointsPer1kCompletionTokens?: number
    costInputCnyPer1mPeak?: number | null
    costInputCnyPer1mOffpeak?: number | null
    costOutputCnyPer1mPeak?: number | null
    costOutputCnyPer1mOffpeak?: number | null
  }) =>
    http<SystemModelDto>(`/api/admin/system-models/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取全部用户列表 */
  listUsers: () =>
    http<AdminUserListItem[]>('/api/admin/users'),

  /** 管理员：更新用户状态或角色 */
  updateUser: (id: number, body: { status?: string; role?: string }) =>
    http<AdminUserListItem>(`/api/admin/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取指定用户的系统工具设置 */
  getUserToolSettings: (userId: number) =>
    http<ToolSettingsDto>(`/api/admin/users/${userId}/tool-settings`),

  /** 管理员：更新指定用户的系统工具设置 */
  updateUserToolSettings: (userId: number, body: ToolSettingsDto) =>
    http<ToolSettingsDto>(`/api/admin/users/${userId}/tool-settings`, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取宿主机 / Docker 资源配置摘要 */
  getHostStatus: () =>
    http<HostResourceStatusDto>('/api/admin/host-status'),

  /** 管理员：获取各组件（MySQL、Milvus、LLM、Embedding、Docker）连接状态 */
  getComponentStatus: () =>
    http<ComponentStatusDto>('/api/admin/component-status'),

  /** 管理员：查询宿主机负载历史，用于图表（from/to 为 epoch ms） */
  listHostMetrics: (params: { hostName?: string; from?: number; to?: number }) => {
    const qs = new URLSearchParams()
    if (params.hostName) qs.append('hostName', params.hostName)
    if (params.from != null) qs.append('from', String(params.from))
    if (params.to != null) qs.append('to', String(params.to))
    const query = qs.toString()
    return http<HostLoadMetric[]>('/api/admin/host-metrics' + (query ? `?${query}` : ''))
  },

  /** 管理员：获取全部系统配置项 */
  listSystemConfigs: () =>
    http<SystemConfigItem[]>('/api/admin/system-configs'),

  /** 管理员：更新系统配置项的值与说明 */
  updateSystemConfig: (id: number, body: { configValue?: string | null; description?: string | null }) =>
    http<SystemConfigItem>(`/api/admin/system-configs/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  createDeepseekKey: (body: { configValue: string; description?: string }) =>
    http<SystemConfigItem>('/api/admin/system-configs', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  deleteSystemConfig: (id: number) =>
    http<void>(`/api/admin/system-configs/${id}`, { method: 'DELETE' }),

  setRagMemoryEnabled: (enabled: boolean) =>
    http<{ ragMemoryEnabled: boolean }>('/api/admin/features/rag-memory', {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：用量汇总（基于 llm_call_logs） */
  getBillingUsage: (params?: { userId?: number; from?: string; to?: string }) => {
    const qs = new URLSearchParams()
    if (params?.userId != null) qs.append('userId', String(params.userId))
    if (params?.from) qs.append('from', params.from)
    if (params?.to) qs.append('to', params.to)
    const query = qs.toString()
    return http<BillingUsageResponse>('/api/admin/billing/usage' + (query ? `?${query}` : ''))
  },

  /** 管理员：某用户用量明细 */
  getBillingUsageDetail: (userId: number, params?: { from?: string; to?: string; limit?: number }) => {
    const qs = new URLSearchParams()
    if (params?.from) qs.append('from', params.from)
    if (params?.to) qs.append('to', params.to)
    if (params?.limit != null) qs.append('limit', String(params.limit))
    const query = qs.toString()
    return http<BillingUsageDetail[]>(`/api/admin/billing/usage/${userId}` + (query ? `?${query}` : ''))
  },

  listPointPackages: () =>
    http<import('./billing').PointPackageView[]>('/api/admin/billing/packages'),

  createPointPackage: (body: {
    name: string
    priceYuan: number
    points: number
    bonusPoints?: number
    enabled?: boolean
    sortOrder?: number
    description?: string
  }) =>
    http<import('./billing').PointPackageView>('/api/admin/billing/packages', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  updatePointPackage: (id: number, body: {
    name: string
    priceYuan: number
    points: number
    bonusPoints?: number
    enabled?: boolean
    sortOrder?: number
    description?: string
  }) =>
    http<import('./billing').PointPackageView>(`/api/admin/billing/packages/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  deletePointPackage: (id: number) =>
    http<void>(`/api/admin/billing/packages/${id}`, { method: 'DELETE' }),

  listPaymentOrders: (params?: { page?: number; size?: number; status?: string }) => {
    const qs = new URLSearchParams()
    qs.set('page', String(params?.page ?? 0))
    qs.set('size', String(params?.size ?? 20))
    if (params?.status) qs.set('status', params.status)
    return http<{
      items: import('./billing').PaymentOrderView[]
      page: number
      size: number
      total: number
      totalPages: number
    }>(`/api/admin/billing/orders?${qs}`)
  },

  getUserLedger: (userId: number, params?: { reason?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams()
    qs.set('page', String(params?.page ?? 0))
    qs.set('size', String(params?.size ?? 20))
    if (params?.reason) qs.set('reason', params.reason)
    return http<{
      items: import('./billing').PointLedgerView[]
      page: number
      size: number
      total: number
      totalPages: number
      pointsBalance: number
    }>(`/api/admin/billing/users/${userId}/ledger?${qs}`)
  },

  adjustUserPoints: (userId: number, body: { delta: number; remark?: string }) =>
    http<{ pointsBalance: number }>(`/api/admin/billing/users/${userId}/adjust`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),
}
