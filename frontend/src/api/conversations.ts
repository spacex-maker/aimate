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
  archived?: boolean
  updateTime?: string
  createTime?: string
  status?: string
  sessionId?: string
  publicId?: string
  userAgentId?: number | null
  agentName?: string
  agentOriginalName?: string | null
  agentCareerName?: string | null
  agentDisplayTitle?: string | null
  /** 最近一条消息摘要（微信式小字） */
  lastMessagePreview?: string | null
  groupTitle?: string
  remark?: string | null
  myRole?: string
}

export const conversationsApi = {
  list: (limit = 30, opts?: { archived?: boolean }) => {
    const archived = opts?.archived ? 'true' : 'false'
    return http<ConversationItem[]>(`/api/conversations?limit=${limit}&archived=${archived}`)
  },

  pinSession: (sessionId: string, pinned: boolean) =>
    agentApi.pinSession(sessionId, pinned),

  archiveSession: (sessionId: string, archived = true) =>
    agentApi.archiveSession(sessionId, archived),

  renameSession: (sessionId: string, title: string) =>
    agentApi.renameSession(sessionId, title),

  deleteSession: (
    sessionId: string,
    body: { deleteMessages: boolean; deleteMemories: boolean; hideOnly: boolean },
  ) => agentApi.deleteSession(sessionId, body),

  pinGroup: (publicId: string, pinned: boolean) =>
    groupsApi.pin(publicId, pinned),

  archiveGroup: (publicId: string, archived = true) =>
    groupsApi.archive(publicId, archived),

  renameGroupRemark: (publicId: string, remark: string) =>
    groupsApi.patch(publicId, { remark }),

  shareGroup: (publicId: string) => groupsApi.createShare(publicId),
}
