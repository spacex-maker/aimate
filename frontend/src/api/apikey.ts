import type { ApiKeyRequest, ApiKeyResponse } from '../types/apikey'
import { http } from './httpClient'

function base(userId: number) {
  return `/api/users/${userId}/api-keys`
}

/** Backend uses SNAKE_CASE for deserialization — convert before sending. */
function toSnake(body: ApiKeyRequest) {
  return JSON.stringify({
    provider:   body.provider,
    key_type:   body.keyType,
    label:      body.label,
    key_value:  body.keyValue,
    base_url:   body.baseUrl,
    model:      body.model,
    is_default: body.isDefault,
  })
}

export const apikeyApi = {
  list: (userId: number) =>
    http<ApiKeyResponse[]>(base(userId)),

  create: (userId: number, body: ApiKeyRequest) =>
    http<ApiKeyResponse>(base(userId), { method: 'POST', body: toSnake(body) }),

  update: (userId: number, id: number, body: ApiKeyRequest) =>
    http<ApiKeyResponse>(`${base(userId)}/${id}`, { method: 'PUT', body: toSnake(body) }),

  setDefault: (userId: number, id: number) =>
    http<ApiKeyResponse>(`${base(userId)}/${id}/set-default`, { method: 'POST' }),

  delete: (userId: number, id: number) =>
    http<void>(`${base(userId)}/${id}`, { method: 'DELETE' }),
}
