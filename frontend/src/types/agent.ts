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
}

/** 单次工具调用展示（名称、参数、结果），用于在消息上方与思考一起展示 */
export interface ToolCallDisplayDto {
  name: string
  arguments: string
  result: string
}

/** 历史消息单条，用于会话页展示；id 用于消息级中断/重试/版本查询；messageStatus 仅 assistant；thinkingContent、toolCalls 仅 assistant 可折叠展示 */
export interface ChatMessageDto {
  id?: number | null
  role: 'user' | 'assistant' | 'tool'
  content: string
  messageStatus?: 'ANSWERING' | 'DONE' | 'INTERRUPTED' | null
  thinkingContent?: string | null
  toolCalls?: ToolCallDisplayDto[] | null
  createTime?: string | null
}

/** Assistant 回复的一个历史版本 */
export interface AssistantVersionDto {
  version: number
  content: string
  createTime: string | null
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
  | { kind: 'toolCall'; call: ToolCall; result: string | null; streamingOutput?: string; id: string }
  | { kind: 'finalAnswer'; content: string; id: string }
  | { kind: 'error'; message: string; id: string }
