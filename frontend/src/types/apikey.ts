export type KeyType = 'LLM' | 'EMBEDDING' | 'VECTOR_DB' | 'OTHER'

export interface ApiKeyResponse {
  id: number
  provider: string
  key_type: KeyType
  label: string | null
  masked_key: string
  base_url: string | null
  model: string | null
  is_default: boolean
  is_active: boolean
  create_time: string | null
}

export interface ApiKeyRequest {
  provider: string
  keyType: KeyType
  label?: string
  keyValue: string
  baseUrl?: string
  model?: string
  isDefault: boolean
}

export const PROVIDERS = [
  { value: 'openai',    label: 'OpenAI',     baseUrl: 'https://api.openai.com/v1' },
  { value: 'deepseek',  label: 'DeepSeek',   baseUrl: 'https://api.deepseek.com/v1' },
  { value: 'anthropic', label: 'Anthropic',  baseUrl: 'https://api.anthropic.com/v1' },
  { value: 'azure',     label: 'Azure OpenAI', baseUrl: '' },
  { value: 'moonshot',  label: 'Moonshot (Kimi)', baseUrl: 'https://api.moonshot.cn/v1' },
  { value: 'zhipu',     label: '智谱 GLM',   baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { value: 'qwen',      label: '通义千问',    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { value: 'custom',    label: '自定义',      baseUrl: '' },
]

// 针对对话模型的默认 model，供前端表单预填使用
export const LLM_DEFAULT_MODELS: Record<string, string> = {
  openai:    'gpt-4o',
  deepseek:  'deepseek-chat',
  anthropic: 'claude-3-5-sonnet-20241022',
  moonshot:  'moonshot-v1-8k',
  zhipu:     'glm-4',
  qwen:      'qwen-plus',
}

export const KEY_TYPE_LABELS: Record<KeyType, string> = {
  LLM:       '对话模型',
  EMBEDDING: '向量嵌入',
  VECTOR_DB: '向量数据库',
  OTHER:     '其他',
}
