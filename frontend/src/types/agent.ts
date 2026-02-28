// ── Session ──────────────────────────────────────────────────────────────────

export type SessionStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED'

export interface SessionResponse {
  sessionId: string
  status: SessionStatus
  taskDescription: string
  iterationCount: number
  result: string | null
  errorMessage: string | null
  wsSubscribePath: string
  createTime: string
  updateTime: string
}

export interface StartSessionRequest {
  task: string
  sessionId?: string
}

// ── WebSocket Events ─────────────────────────────────────────────────────────

export type EventType =
  | 'PLAN_READY'
  | 'STEP_START'
  | 'STEP_COMPLETE'
  | 'THINKING'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
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
  | { kind: 'toolCall'; call: ToolCall; result: string | null; id: string }
  | { kind: 'finalAnswer'; content: string; id: string }
  | { kind: 'error'; message: string; id: string }
