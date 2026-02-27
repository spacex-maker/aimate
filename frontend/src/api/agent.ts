import type { SessionResponse, StartSessionRequest } from '../types/agent'
import { http } from './httpClient'

const BASE = '/api/agent/sessions'

export const agentApi = {
  startSession: (body: StartSessionRequest) =>
    http<SessionResponse>(BASE, { method: 'POST', body: JSON.stringify(body) }),

  getSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}`),

  pauseSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}/pause`, { method: 'POST' }),

  resumeSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}/resume`, { method: 'POST' }),

  abortSession: (sessionId: string) =>
    http<SessionResponse>(`${BASE}/${sessionId}`, { method: 'DELETE' }),
}
