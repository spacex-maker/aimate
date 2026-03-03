export interface EmbeddingModelResponse {
  id: number
  name: string
  provider: string
  modelName: string
  maskedKey: string | null
  baseUrl: string
  dimension: number
  collectionName: string
  maxTokens: number
  isDefault: boolean
  isActive: boolean
  createTime: string | null
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
  // 三方 API 提供商
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
  // OpenForge 自部署（本地 / 服务器 Ollama）
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
  // 其他兼容 OpenAI 协议的云服务，可归入“三方 API”分类
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

/** 向量模型服务商分组，用于表单中展示「OpenForge 部署 / 三方 API / 自定义」三类。 */
export const EMBEDDING_PROVIDER_GROUPS: { label: string; providers: string[] }[] = [
  {
    label: 'OpenForge 部署',
    providers: ['ollama'],
  },
  {
    label: '三方 API',
    providers: ['openai', 'azure'],
  },
  {
    label: '自定义',
    providers: ['custom'],
  },
]
