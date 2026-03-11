// ── Session ──────────────────────────────────────────────────────────────────

/** 会话状态：IDLE=静默可发消息，ACTIVE=在线执行，PAUSED=暂停；其余为兼容旧数据 */
export type SessionStatus = 'IDLE' | 'ACTIVE' | 'PAUSED' | 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface SessionResponse {
  sessionId: string
  status: SessionStatus
  taskDescription: string
  iterationCount: number
  result: string | null
  errorMessage: string | null
  /** 是否在最近会话等列表中被隐藏 */
  hidden: boolean
  wsSubscribePath: string
  createTime: string
  updateTime: string
}

export interface StartSessionRequest {
  task: string
  sessionId?: string
  modelSource?: 'SYSTEM' | 'USER_KEY'
  systemModelId?: number | null
  userApiKeyId?: number | null
}

/** 单次工具调用展示（名称、参数、结果），用于在消息上方与思考一起展示 */
export interface ToolCallDisplayDto {
  name: string
  arguments: string
  result: string
}

/** 历史消息单条，用于会话页展示；seq 用于「加载更多」游标 beforeSeq；id 用于消息级中断/重试/版本查询 */
export interface ChatMessageDto {
  id?: number | null
  /** 会话内顺序，用于加载更多时传 beforeSeq */
  seq?: number | null
  role: 'user' | 'assistant' | 'tool'
  content: string
  messageStatus?: 'ANSWERING' | 'DONE' | 'INTERRUPTED' | null
  thinkingContent?: string | null
  toolCalls?: ToolCallDisplayDto[] | null
  /** 标准化思考+工具调用时间线（与实时 StreamBlock 结构一致），历史回放优先使用 */
  thinkingBlocks?: StreamBlock[] | null
  createTime?: string | null
}

/** Assistant 回复的一个历史版本 */
export interface AssistantVersionDto {
  version: number
  content: string
  createTime: string | null
}

/** 系统模型项，供输入框「模型选择」与密钥管理页服务商/默认模型；enabled 仅管理员接口返回 */
export interface SystemModelDto {
  id: number
  provider: string
  modelId: string
  displayName: string
  baseUrl: string | null
  /** system_config.config_key，用于查找该系统模型使用的系统级 API Key */
  apiKeyConfigKey?: string | null
  /** 排序权重，数值越小越靠前 */
  sortOrder: number
  enabled?: boolean
  description: string | null
}

/** 当前用户首选模型（用于恢复聊天输入框默认选中项） */
export interface UserDefaultModelDto {
  source?: 'SYSTEM' | 'USER_KEY' | null
  systemModelId?: number | null
  userApiKeyId?: number | null
}

/** 用户自有模型（来自 user_api_keys, key_type=LLM），用于聊天输入框「我的模型」区域 */
export interface UserModelDto {
  id: number
  provider: string
  label?: string | null
  model?: string | null
  baseUrl?: string | null
}

/** 聊天输入框模型选择所需的完整数据 */
export interface AvailableModelsDto {
  userModels: UserModelDto[]
  systemModels: SystemModelDto[]
  defaultModel: UserDefaultModelDto
}

/** 用户系统工具开关：长期记忆、联网搜索、AI 自主编写工具、用户系统脚本执行 */
export interface ToolSettingsDto {
  memoryEnabled: boolean
  webSearchEnabled: boolean
  createToolEnabled: boolean
  scriptExecEnabled: boolean
}

/** 脚本执行环境状态（会话页顶部展示 Docker 虚拟机状态） */
export interface ScriptEnvStatusDto {
  dockerEnabled: boolean
  containerStatus: 'running' | 'none'
  containerName?: string | null
  idleMinutes?: number | null
  message: string
  dockerAvailable?: boolean | null
  dockerVersion?: string | null
  /** 虚拟机基础镜像，如 debian:bookworm-slim */
  image?: string | null
  /** 容器内存上限，如 256m */
  memoryLimit?: string | null
  /** 容器 CPU 核数上限，如 0.5 */
  cpuLimit?: number | null
}

/** 宿主机 / Docker 资源配置摘要，用于管理端容器监控顶部展示 */
export interface HostResourceStatusDto {
  cpuCores: number | null
  systemCpuLoadPercent: number | null
  hostTotalMemoryBytes: number | null
  hostAvailableMemoryBytes: number | null
  hostAvailableMemoryPercent: number | null
  rootFsUsedPercent: number | null
  rootFsTotalBytes: number | null
  dockerImage: string | null
  dockerMemoryLimit: string | null
  dockerCpuLimit: number | null
  lowMemoryFreePercent: number | null
  message: string | null
}

/** Docker 安装说明（按操作系统返回） */
export interface DockerInstallInfoDto {
  os: string
  instructions: string
  scriptUrl?: string | null
  docUrl?: string | null
  copyCommand?: string | null
}

// ── WebSocket Events ─────────────────────────────────────────────────────────

export type EventType =
  | 'PLAN_READY'
  | 'STEP_START'
  | 'STEP_COMPLETE'
  | 'THINKING'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
  | 'TOOL_OUTPUT_CHUNK'
  | 'ITERATION_START'
  | 'PLAN_UPDATE'
  | 'FINAL_ANSWER'
  | 'STATUS_CHANGE'
  | 'ERROR'

export interface StepPayload {
  stepIndex: number
  title: string
  summary?: string | null
}

export interface ToolCall {
  id: string
  type: string
  function: {
    name: string
    arguments: string
  }
}

export interface ToolResultPayload {
  toolName: string
  output: string
  /** 执行耗时（毫秒），可选 */
  durationMs?: number | null
}

export interface AgentEvent {
  sessionId: string
  type: EventType
  content: string | null
  payload: ToolCall | ToolResultPayload | unknown | null
  iteration: number
  timestamp: number
  /** 后端每条事件唯一 id，用于前端去重（同一条事件可能因双订阅收到两次） */
  eventId?: string | null
}

// ── UI Blocks (render model) ─────────────────────────────────────────────────
// Each "block" represents a visual unit in the session stream

// Plan + steps (from PLAN_READY, STEP_START, STEP_COMPLETE)
export interface PlanState {
  steps: string[]
  currentStepIndex: number  // 1-based, 0 = not started
  stepSummaries: Record<number, string>  // stepIndex -> summary
}

export type StreamBlock =
  | { kind: 'iteration'; number: number; id: string }
  | { kind: 'thinking'; content: string; complete: boolean; id: string }
  | { kind: 'toolCall'; call: ToolCall; result: string | null; streamingOutput?: string; durationMs?: number | null; id: string }
  | { kind: 'finalAnswer'; content: string; id: string }
  | { kind: 'error'; message: string; id: string }
