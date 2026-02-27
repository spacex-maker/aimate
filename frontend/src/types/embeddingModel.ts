export interface EmbeddingModelResponse {
  id: number
  name: string
  provider: string
  model_name: string
  masked_key: string | null
  base_url: string
  dimension: number
  collection_name: string
  max_tokens: number
  is_default: boolean
  is_active: boolean
  create_time: string | null
}

export interface EmbeddingModelRequest {
  name: string
  provider: string
  modelName: string
  apiKey?: string
  baseUrl: string
  dimension: number
  maxTokens?: number
  isDefault: boolean
}

export const EMBEDDING_PROVIDERS = [
  {
    value: 'openai',
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    models: [
      { name: 'text-embedding-3-small', dim: 1536 },
      { name: 'text-embedding-3-large', dim: 3072 },
      { name: 'text-embedding-ada-002', dim: 1536 },
    ],
  },
  {
    value: 'ollama',
    label: 'Ollama',
    baseUrl: 'http://175.27.255.29:11434/v1',
    models: [
      { name: 'nomic-embed-text', dim: 768 },
      { name: 'mxbai-embed-large', dim: 1024 },
      { name: 'all-minilm', dim: 384 },
      { name: 'bge-m3', dim: 1024 },
    ],
  },
  {
    value: 'azure',
    label: 'Azure OpenAI',
    baseUrl: '',
    models: [
      { name: 'text-embedding-3-small', dim: 1536 },
      { name: 'text-embedding-ada-002', dim: 1536 },
    ],
  },
  {
    value: 'custom',
    label: '自定义',
    baseUrl: '',
    models: [],
  },
]
