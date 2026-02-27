import type { EmbeddingModelRequest, EmbeddingModelResponse } from '../types/embeddingModel'
import { http } from './httpClient'

function base(userId: number) {
  return `/api/users/${userId}/embedding-models`
}

function toSnake(body: EmbeddingModelRequest) {
  return JSON.stringify({
    name:       body.name,
    provider:   body.provider,
    model_name: body.modelName,
    api_key:    body.apiKey,
    base_url:   body.baseUrl,
    dimension:  body.dimension,
    max_tokens: body.maxTokens,
    is_default: body.isDefault,
  })
}

export const embeddingModelApi = {
  list: (userId: number) =>
    http<EmbeddingModelResponse[]>(base(userId)),

  create: (userId: number, body: EmbeddingModelRequest) =>
    http<EmbeddingModelResponse>(base(userId), { method: 'POST', body: toSnake(body) }),

  update: (userId: number, id: number, body: EmbeddingModelRequest) =>
    http<EmbeddingModelResponse>(`${base(userId)}/${id}`, { method: 'PUT', body: toSnake(body) }),

  setDefault: (userId: number, id: number) =>
    http<EmbeddingModelResponse>(`${base(userId)}/${id}/set-default`, { method: 'POST' }),

  delete: (userId: number, id: number) =>
    http<void>(`${base(userId)}/${id}`, { method: 'DELETE' }),
}
