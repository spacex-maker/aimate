import type { ApiKeyRequest, ApiKeyResponse } from '../types/apikey'
import { http } from './httpClient'

function base(userId: number) {
  if (userId == null || (typeof userId === 'number' && Number.isNaN(userId))) {
    throw new Error('userId is required')
  }
  return `/api/users/${userId}/api-keys`
}

/** 后端 ApiKeyRequest 使用 camelCase 字段名，直接按原样序列化即可。 */
function toJson(body: ApiKeyRequest) {
  return JSON.stringify({
    provider:  body.provider,
    keyType:   body.keyType,
    label:     body.label,
    keyValue:  body.keyValue,
    baseUrl:   body.baseUrl,
    model:     body.model,
    isDefault: body.isDefault,
  })
}

export const apikeyApi = {
  list: (userId: number) =>
    http<ApiKeyResponse[]>(base(userId)),

  create: (userId: number, body: ApiKeyRequest) =>
    http<ApiKeyResponse>(base(userId), { method: 'POST', body: toJson(body) }),

  update: (userId: number, id: number, body: ApiKeyRequest) =>
    http<ApiKeyResponse>(`${base(userId)}/${id}`, { method: 'PUT', body: toJson(body) }),

  setDefault: (userId: number, id: number) =>
    http<ApiKeyResponse>(`${base(userId)}/${id}/set-default`, { method: 'POST' }),

  delete: (userId: number, id: number) =>
    http<void>(`${base(userId)}/${id}`, { method: 'DELETE' }),
}
