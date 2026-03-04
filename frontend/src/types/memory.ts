export type MemoryType = 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL'

export interface MemoryItem {
  /** Milvus ID (string to preserve precision beyond JS safe integer). */
  id: string
  sessionId: string
  content: string
  memoryType: MemoryType
  importance: number
  /** 是否禁止参与压缩（true = 压缩时跳过这条记忆） */
  noCompress: boolean
  createTime: string
  score: number | null
}

export interface MemoryPage {
  items: MemoryItem[]
  total: number
  page: number
  size: number
}

export interface MemoryMeta {
  collectionName: string
}

export type MemoryMigrationEventType = 'START' | 'PROGRESS' | 'DONE' | 'ERROR' | 'CANCELLED'

export interface MemoryMigrationEvent {
  type: MemoryMigrationEventType
  timestamp: number
  totalSessions: number
  processedSessions: number
  writtenMemories: number
  currentSessionId?: string | null
  currentTaskDescription?: string | null
  error?: string | null
  /** 当前步骤说明，用于详细日志（如：向量化并写入 EPISODIC 记忆） */
  stepDetail?: string | null
}

/** GET /api/memories/migration-status 响应，用于刷新后恢复进度 */
export interface MigrationStatusResponse {
  status: 'IDLE' | 'RUNNING' | 'DONE' | 'ERROR' | 'CANCELLED'
  totalSessions: number
  processedSessions: number
  writtenMemories: number
  currentTask?: string | null
  error?: string | null
  stepLog: string[]
}

export interface CountResponse {
  count: number
  type: MemoryType | null
  session: string | null
}

export interface AddMemoryRequest {
  content: string
  memoryType?: MemoryType
  sessionId?: string
  importance?: number
}

export interface MemorySearchParams {
  type?: MemoryType
  session?: string
  keyword?: string
  page?: number
  size?: number
}

/** Proposed compressed memory from LLM (snake_case from API). */
export interface CompressedMemoryDto {
  content: string
  memory_type: string
  importance: number
}

export interface CompressPrepareResult {
  current: MemoryItem[]
  proposed: CompressedMemoryDto[]
  error: string | null
}

export interface ExecuteCompressRequest {
  delete_ids?: string[]
  new_memories?: CompressedMemoryDto[]
  /** 仅压缩这些 ID（不传 delete_ids/new_memories 时后端会按此子集重新跑 LLM 并执行） */
  include_ids?: string[]
}
