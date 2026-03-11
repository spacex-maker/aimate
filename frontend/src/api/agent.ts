import type { AssistantVersionDto, AvailableModelsDto, ChatMessageDto, DockerInstallInfoDto, ScriptEnvStatusDto, SessionResponse, StartSessionRequest, SystemModelDto, ToolSettingsDto } from '../types/agent'
import { http } from './httpClient'

const BASE = '/api/agent/sessions'

export const agentApi = {
  /** 当前用户脚本执行环境状态（含自动检测 Docker 是否可用），用于会话页顶部展示 */
  getScriptStatus: () =>
    http<ScriptEnvStatusDto>('/api/agent/script-status'),

  /** 清除 Docker 检测缓存并触发重新检测 */
  refreshScriptStatus: () =>
    http<void>('/api/agent/script-status/refresh', { method: 'POST' }),

  /** 获取当前系统对应的 Docker 安装说明与链接 */
  getDockerInstallInfo: () =>
    http<DockerInstallInfoDto>('/api/agent/docker-install-info'),

  startSession: (body: StartSessionRequest) =>
    http<SessionResponse>(BASE, { method: 'POST', body: JSON.stringify(body) }),

  /** 当前用户最近会话列表（详细），用于首页「最近会话」 */
  getRecentSessions: (limit = 10) =>
    http<SessionResponse[]>(`${BASE}?limit=${limit}`),

  getSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}`),

  /** 会话消息列表：limit 默认 10；beforeSeq 不传则最近 N 条，传则取该 seq 之前的 N 条（加载更多） */
  getSessionMessages: (sessionId: string, limit: number = 10, beforeSeq?: number | null) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (beforeSeq != null) params.set('beforeSeq', String(beforeSeq))
    return http<ChatMessageDto[]>(`${BASE}/${sessionId}/messages?${params}`)
  },

  pauseSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}/pause`, { method: 'POST' }),

  resumeSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}/resume`, { method: 'POST' }),

  continueSession: (
    sessionId: string,
    body: { message: string; modelSource?: 'SYSTEM' | 'USER_KEY'; systemModelId?: number | null; userApiKeyId?: number | null }
  ) =>
    http<SessionResponse>(`${BASE}/${sessionId}/continue`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  abortSession: (sessionId: string) => {
    if (!sessionId || sessionId === 'undefined' || sessionId === 'null') {
      return Promise.reject(new Error('无效的会话 ID'))
    }
    return http<SessionResponse>(`${BASE}/${sessionId}`, { method: 'DELETE' })
  },

  /** 删除/清理会话：支持删除聊天记录、长期记忆或仅隐藏会话 */
  deleteSession: (
    sessionId: string,
    body: { deleteMessages: boolean; deleteMemories: boolean; hideOnly: boolean }
  ) => {
    if (!sessionId || sessionId === 'undefined' || sessionId === 'null') {
      return Promise.reject(new Error('无效的会话 ID'))
    }
    return http<SessionResponse>(`${BASE}/${sessionId}/delete`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    })
  },

  /** 消息级中断：指定 assistant 消息 id，该条标为已中断，对应 run 退出 */
  interruptSession: (sessionId: string, assistantMessageId: number) =>
    http<SessionResponse>(`${BASE}/${sessionId}/interrupt`, {
      method: 'POST',
      body: JSON.stringify({ assistantMessageId }),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 重试某条用户消息：用该条之前的上下文重新生成下一条 assistant，并追加新版本 */
  retryMessage: (
    sessionId: string,
    body: { userMessageId: number; modelSource?: 'SYSTEM' | 'USER_KEY'; systemModelId?: number | null; userApiKeyId?: number | null }
  ) =>
    http<SessionResponse>(`${BASE}/${sessionId}/retry`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 某条 assistant 回复的历史版本列表 */
  getMessageVersions: (sessionId: string, messageId: number) =>
    http<AssistantVersionDto[]>(`${BASE}/${sessionId}/messages/${messageId}/versions`),

  /** 系统已开启的模型目录（仅 enabled=true），用于下拉选择 provider / model */
  getSystemModels: () =>
    http<SystemModelDto[]>('/api/agent/system-models'),

  /** 模型选择所需的完整数据：用户模型 + 系统模型 + 用户首选模型 */
  getAvailableModels: () =>
    http<AvailableModelsDto>('/api/agent/user-default-model'),

  /** 更新当前登录用户的默认模型（聊天输入框最近一次选择） */
  setUserDefaultModel: (body: { source: 'SYSTEM' | 'USER_KEY'; systemModelId?: number; userApiKeyId?: number }) =>
    http<void>('/api/agent/user-default-model', {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 当前用户系统工具开关（长期记忆、联网搜索、AI 编写工具、脚本执行） */
  getToolSettings: () =>
    http<ToolSettingsDto>('/api/agent/tool-settings'),

  /** 更新系统工具开关 */
  updateToolSettings: (body: ToolSettingsDto) =>
    http<ToolSettingsDto>('/api/agent/tool-settings', {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),
}
