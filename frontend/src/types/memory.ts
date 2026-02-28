export type MemoryType = 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL'

export interface MemoryItem {
  /** Milvus ID (string to preserve precision beyond JS safe integer). */
  id: string
  sessionId: string
  content: string
  memoryType: MemoryType
  importance: number
  createTime: string
  score: number | null
}

export interface MemoryPage {
  items: MemoryItem[]
  total: number
  page: number
  size: number
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
  delete_ids: string[]
  new_memories: CompressedMemoryDto[]
}
