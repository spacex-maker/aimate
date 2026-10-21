import { http } from './httpClient'

export type SystemFeatures = {
  ragMemoryEnabled: boolean
}

export const systemApi = {
  features: () => http<SystemFeatures>('/api/system/features'),
}
