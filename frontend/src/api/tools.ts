import type { UpdateUserToolRequest, UserToolDto } from '../types/tools'
import { http } from './httpClient'

function base(userId: number) {
  return `/api/users/${userId}/tools`
}

export const toolsApi = {
  list: (userId: number) =>
    http<UserToolDto[]>(base(userId)),

  update: (userId: number, toolId: number, body: UpdateUserToolRequest) =>
    http<UserToolDto>(`${base(userId)}/${toolId}`, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  delete: (userId: number, toolId: number) =>
    http<void>(`${base(userId)}/${toolId}`, { method: 'DELETE' }),
}
