import { http } from './httpClient'
import { groupsApi } from './groups'
import { agentApi } from './agent'

export type ConversationKind = 'SESSION' | 'GROUP'

export interface ConversationItem {
  kind: ConversationKind
  id: string
  title: string
  avatarUrl?: string | null
  avatarUrls?: string[]
  pinned?: boolean
  pinnedAt?: string | null
  updateTime?: string
  createTime?: string
  status?: string
  sessionId?: string
  publicId?: string
  userAgentId?: number | null
  agentName?: string
  groupTitle?: string
  remark?: string | null
  myRole?: string
}

export const conversationsApi = {
  list: (limit = 30) => http<ConversationItem[]>(`/api/conversations?limit=${limit}`),

  pinSession: (sessionId: string, pinned: boolean) =>
    agentApi.pinSession(sessionId, pinned),

  archiveSession: (sessionId: string, archived = true) =>
    agentApi.archiveSession(sessionId, archived),

  pinGroup: (publicId: string, pinned: boolean) =>
    groupsApi.pin(publicId, pinned),

  archiveGroup: (publicId: string, archived = true) =>
    groupsApi.archive(publicId, archived),
}
