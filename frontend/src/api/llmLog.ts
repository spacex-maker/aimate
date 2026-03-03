import type { LlmCallLogPage } from '../types/llmLog'
import { http } from './httpClient'

function base(userId: number) {
  if (userId == null || Number.isNaN(userId)) {
    throw new Error('userId is required for LLM call logs')
  }
  return `/api/users/${userId}/llm-calls`
}

export const llmLogApi = {
  list: (userId: number, page = 0, size = 20) =>
    http<LlmCallLogPage>(`${base(userId)}?page=${page}&size=${size}`),
}

