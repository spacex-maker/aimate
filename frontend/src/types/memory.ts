export type MemoryType = 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL'

export interface MemoryItem {
  id: number
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
