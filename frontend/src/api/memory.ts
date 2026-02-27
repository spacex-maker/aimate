import type {
  AddMemoryRequest,
  CountResponse,
  MemoryItem,
  MemoryPage,
  MemorySearchParams,
  MemoryType,
} from '../types/memory'
import { http } from './httpClient'

const BASE = '/api/memories'

function buildQuery(params: Record<string, string | number | undefined>): string {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const memoryApi = {
  list: (params: MemorySearchParams = {}) => {
    const qs = buildQuery({
      type:    params.type,
      session: params.session,
      keyword: params.keyword,
      page:    params.page ?? 0,
      size:    params.size ?? 20,
    })
    return http<MemoryPage>(`${BASE}${qs}`)
  },

  search: (q: string, topK = 10) =>
    http<MemoryItem[]>(`${BASE}/search?q=${encodeURIComponent(q)}&topK=${topK}`),

  count: (type?: MemoryType, session?: string) => {
    const qs = buildQuery({ type, session })
    return http<CountResponse>(`${BASE}/count${qs}`)
  },

  add: (body: AddMemoryRequest) =>
    http<void>(BASE, { method: 'POST', body: JSON.stringify(body) }),

  deleteById: (id: number) =>
    http<void>(`${BASE}/${id}`, { method: 'DELETE' }),

  deleteBySession: (sessionId: string) =>
    http<void>(`${BASE}/session/${sessionId}`, { method: 'DELETE' }),

  deleteByType: (type: MemoryType) =>
    http<void>(`${BASE}/type/${type}`, { method: 'DELETE' }),
}
