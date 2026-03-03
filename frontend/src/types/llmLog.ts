export interface LlmCallLogItem {
  id: number
  provider: string
  model: string
  callType: string | null
  toolName: string | null
  endpoint: string | null
  userId: number | null
  sessionId: string | null
  latencyMs: number | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  httpStatus: number | null
  success: boolean
  createTime: string | null
}

export interface LlmCallLogPage {
  items: LlmCallLogItem[]
  total: number
  page: number
  size: number
}

