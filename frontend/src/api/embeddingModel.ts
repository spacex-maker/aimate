import type { EmbeddingModelRequest, EmbeddingModelResponse } from '../types/embeddingModel'
import { http } from './httpClient'

function base(userId: number) {
  return `/api/users/${userId}/embedding-models`
}

/** 使用 camelCase 与后端 Jackson 默认命名一致，避免绑定失败导致异常或 401 */
function toBody(body: EmbeddingModelRequest) {
  return JSON.stringify({
    name: body.name,
    provider: body.provider,
    modelName: body.modelName,
    apiKey: body.apiKey ?? '',
    baseUrl: body.baseUrl,
    dimension: body.dimension,
    maxTokens: body.maxTokens ?? 8192,
    isDefault: body.isDefault,
  })
}

export const embeddingModelApi = {
  list: (userId: number) =>
    http<EmbeddingModelResponse[]>(base(userId)),

  create: (userId: number, body: EmbeddingModelRequest) =>
    http<EmbeddingModelResponse>(base(userId), { method: 'POST', body: toBody(body) }),

  update: (userId: number, id: number, body: EmbeddingModelRequest) =>
    http<EmbeddingModelResponse>(`${base(userId)}/${id}`, { method: 'PUT', body: toBody(body) }),

  setDefault: (userId: number, id: number) =>
    http<EmbeddingModelResponse>(`${base(userId)}/${id}/set-default`, { method: 'POST' }),

  delete: (userId: number, id: number) =>
    http<void>(`${base(userId)}/${id}`, { method: 'DELETE' }),
}
