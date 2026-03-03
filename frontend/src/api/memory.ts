import type {
  AddMemoryRequest,
  CompressPrepareResult,
  CountResponse,
  ExecuteCompressRequest,
  MemoryItem,
  MemoryPage,
  MemoryMeta,
  MemoryMigrationEvent,
  MemorySearchParams,
  MemoryType,
  MigrationStatusResponse,
} from '../types/memory'
import { http } from './httpClient'

const BASE = '/api/memories'

/** Backend returns snake_case; normalize to frontend camelCase. */
function fromMemoryItem(raw: Record<string, unknown>): MemoryItem {
  return {
    id: raw.id != null ? String(raw.id) : '',
    sessionId: String(raw.session_id ?? raw.sessionId ?? ''),
    content: String(raw.content ?? ''),
    memoryType: String(raw.memory_type ?? raw.memoryType ?? 'SEMANTIC') as MemoryItem['memoryType'],
    importance: Number(raw.importance ?? 0),
    createTime: String(raw.create_time ?? raw.createTime ?? ''),
    score: raw.score != null ? Number(raw.score) : null,
  }
}

function buildQuery(params: Record<string, string | number | undefined>): string {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const memoryApi = {
  list: async (params: MemorySearchParams = {}): Promise<MemoryPage> => {
    const qs = buildQuery({
      type:    params.type,
      session: params.session,
      keyword: params.keyword,
      page:    params.page ?? 0,
      size:    params.size ?? 20,
    })
    const data = await http<{ items: Record<string, unknown>[]; total: number; page: number; size: number }>(`${BASE}${qs}`)
    return { ...data, items: (data.items ?? []).map(fromMemoryItem) }
  },

  search: async (q: string, topK = 10): Promise<MemoryItem[]> => {
    const arr = await http<Record<string, unknown>[]>(`${BASE}/search?q=${encodeURIComponent(q)}&topK=${topK}`)
    return (arr ?? []).map(fromMemoryItem)
  },

  count: (type?: MemoryType, session?: string) => {
    const qs = buildQuery({ type, session })
    return http<CountResponse>(`${BASE}/count${qs}`)
  },

  meta: async (): Promise<MemoryMeta> => {
    const raw = await http<{ collectionName?: string; collection_name?: string }>(`${BASE}/meta`)
    return {
      collectionName: (raw.collectionName || raw.collection_name || '').toString(),
    }
  },

  /** 将当前用户所有会话记录重新向量化到当前默认 Collection（追加写入，不删除旧数据）。 */
  migrateToCurrentCollection: () =>
    http<{ started: boolean }>(`${BASE}/migrate-to-current-collection`, { method: 'POST' }),

  /** 获取当前用户同步进度（刷新后恢复显示）. */
  migrationStatus: () =>
    http<MigrationStatusResponse>(`${BASE}/migration-status`),

  /** 请求中断当前用户的同步. */
  migrationCancel: () =>
    http<{ message: string }>(`${BASE}/migration/cancel`, { method: 'POST' }),

  add: (body: AddMemoryRequest) =>
    http<void>(BASE, { method: 'POST', body: JSON.stringify(body) }),

  deleteById: (id: string) =>
    http<void>(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  deleteBySession: (sessionId: string) =>
    http<void>(`${BASE}/session/${sessionId}`, { method: 'DELETE' }),

  deleteByType: (type: MemoryType) =>
    http<void>(`${BASE}/type/${type}`, { method: 'DELETE' }),

  /** 清空当前用户全部长期记忆 */
  clearAll: () => http<void>(`${BASE}/clear`, { method: 'DELETE' }),

  /** 重建当前用户的向量集合（删除后按当前 schema 新建，含 user_id）。重建后需使用「同步对话到记忆」重新写入。 */
  recreateCollection: () =>
    http<{ message: string; collectionName: string }>(`${BASE}/recreate-collection`, { method: 'POST' }),

  /** Prepare compression: get current + LLM-proposed merged list for comparison. */
  compressPrepare: async (): Promise<CompressPrepareResult> => {
    const raw = await http<{
      current: Record<string, unknown>[]
      proposed: { content: string; memory_type: string; importance: number }[]
      error: string | null
    }>(`${BASE}/compress/prepare`, { method: 'POST' })
    return {
      current: (raw.current ?? []).map(fromMemoryItem),
      proposed: raw.proposed ?? [],
      error: raw.error ?? null,
    }
  },

  /** Execute compression: delete given IDs and insert new compressed memories. */
  compressExecute: (body: ExecuteCompressRequest) =>
    http<void>(`${BASE}/compress/execute`, { method: 'POST', body: JSON.stringify(body) }),
}
