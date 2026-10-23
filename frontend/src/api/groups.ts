import { http } from './httpClient'

export type AgentReplyMode = 'SILENT' | 'MENTION_ONLY' | 'ALWAYS' | 'SMART'
export type GroupRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type MediaKind = 'IMAGE' | 'VIDEO' | 'FILE' | 'LINK' | 'AUDIO'

export interface GroupBrief {
  id: number
  publicId: string
  title: string
  avatarUrl?: string | null
  announcement?: string | null
  inviteCode?: string | null
  ownerUserId: number
  status: string
  myRole?: GroupRole
  myMemberId?: number
  myNickname?: string | null
  myRemark?: string | null
  displayTitle?: string
  pinned?: boolean
  archived?: boolean
  memberCount?: number
  wsSubscribePath?: string
  updateTime?: string
  suggestedSecretaryAgentId?: number | null
  /** 是否允许普通成员邀请 */
  membersInviteEnabled?: boolean
  /** 当前用户是否可发起邀请 */
  canInvite?: boolean
}

export type LocalBridgeTaskStatus = 'PENDING' | 'CLAIMED' | 'DONE' | 'FAILED' | 'CANCELLED'

export interface AgentTaskCounts {
  PENDING?: number
  CLAIMED?: number
  DONE?: number
  FAILED?: number
  CANCELLED?: number
  OPEN?: number
  TOTAL?: number
  [k: string]: number | undefined
}

export interface GroupMember {
  id: number
  memberType: 'USER' | 'AGENT'
  role: GroupRole
  userId?: number
  userAgentId?: number
  name?: string
  username?: string
  nickname?: string | null
  avatarUrl?: string | null
  agentReplyMode?: AgentReplyMode
  agentKind?: string
  ownerUserId?: number
  ownerName?: string
  executionMode?: string
  localBridge?: boolean
  taskCounts?: AgentTaskCounts
  joinedAt?: string
}

export interface GroupAgentTask {
  id: number
  publicId: string
  status: LocalBridgeTaskStatus
  statusLabel: string
  instruction?: string | null
  errorMessage?: string | null
  triggerMessageId?: number
  claimedAt?: string | null
  completedAt?: string | null
  createTime?: string
  hasResult?: boolean
  resultPreview?: string | null
}

export interface GroupDetail extends GroupBrief {
  members: GroupMember[]
}

export interface GroupAttachment {
  id: number
  messageId: number
  kind: MediaKind
  url: string
  mime?: string
  sizeBytes?: number
  title?: string
  createTime?: string
}

export interface GroupRead {
  memberId: number
  memberType: 'USER' | 'AGENT'
  name?: string
  avatarUrl?: string | null
  userId?: number
  userAgentId?: number
  /** Agent 分析本条消息消耗的积分；人类客户端已读为 0 */
  pointsSpent?: number
}

export interface GroupMessageReplyTo {
  id: number
  deleted?: boolean
  senderType?: 'USER' | 'AGENT' | 'SYSTEM'
  senderName?: string
  senderAvatarUrl?: string | null
  contentPreview?: string
  imageUrl?: string | null
}

export interface GroupMessage {
  id: number
  groupId: number
  senderType: 'USER' | 'AGENT' | 'SYSTEM'
  senderUserId?: number
  senderAgentId?: number
  senderName?: string
  senderAvatarUrl?: string | null
  content?: string | null
  replyToId?: number | null
  /** 引用快照（服务端组装；原消息删除时 deleted=true） */
  replyTo?: GroupMessageReplyTo | null
  clientMsgId?: string | null
  createTime?: string
  attachments?: GroupAttachment[]
  reads?: GroupRead[]
}

export interface UserSearchItem {
  id: number
  username: string
  displayName?: string
  email?: string
  avatarUrl?: string | null
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export const groupsApi = {
  list: () => http<GroupBrief[]>('/api/groups'),

  create: (body: {
    title: string
    avatarUrl?: string
    userIds?: number[]
    agentIds?: number[]
    includeSecretary?: boolean
  }) =>
    http<GroupBrief>('/api/groups', { method: 'POST', body: JSON.stringify(body) }),

  detail: (publicId: string) => http<GroupDetail>(`/api/groups/${publicId}`),

  patch: (
    publicId: string,
    body: {
      title?: string
      avatarUrl?: string
      announcement?: string
      remark?: string
      nickname?: string
      membersInviteEnabled?: boolean
    },
  ) =>
    http<GroupBrief>(`/api/groups/${publicId}`, { method: 'PATCH', body: JSON.stringify(body) }),

  pin: (publicId: string, pinned: boolean) =>
    http<GroupBrief>(`/api/groups/${publicId}/pin`, {
      method: 'POST',
      body: JSON.stringify({ pinned }),
    }),

  archive: (publicId: string, archived = true) =>
    http<{ ok: boolean }>(`/api/groups/${publicId}/archive`, {
      method: 'POST',
      body: JSON.stringify({ archived }),
    }),

  invite: (publicId: string) =>
    http<{ publicId: string; title: string; inviteCode: string; invitePath: string }>(
      `/api/groups/${publicId}/invite`,
    ),

  refreshInvite: (publicId: string) =>
    http<{ publicId: string; title: string; inviteCode: string; invitePath: string }>(
      `/api/groups/${publicId}/invite/refresh`,
      { method: 'POST' },
    ),

  previewJoin: (inviteCode: string) =>
    http<{ publicId: string; title: string; avatarUrl?: string; memberCount: number }>(
      `/api/groups/join/${inviteCode}`,
    ),

  join: (inviteCode: string) =>
    http<GroupBrief>(`/api/groups/join/${inviteCode}`, { method: 'POST' }),

  dissolve: (publicId: string) =>
    http<{ ok: boolean }>(`/api/groups/${publicId}`, { method: 'DELETE' }),

  addMembers: (
    publicId: string,
    body: {
      userIds?: number[]
      agentIds?: number[]
      includeSecretary?: boolean
      replyMode?: AgentReplyMode
    },
  ) =>
    http<{ added: GroupMember[]; suggestSecretary?: number; members: GroupMember[] }>(
      `/api/groups/${publicId}/members`,
      { method: 'POST', body: JSON.stringify(body) },
    ),

  removeMember: (publicId: string, memberId: number) =>
    http<{ ok: boolean }>(`/api/groups/${publicId}/members/${memberId}`, { method: 'DELETE' }),

  agentTasks: (
    publicId: string,
    userAgentId: number,
    opts?: { status?: LocalBridgeTaskStatus | ''; page?: number; size?: number },
  ) => {
    const sp = new URLSearchParams()
    if (opts?.status) sp.set('status', opts.status)
    sp.set('page', String(opts?.page ?? 0))
    sp.set('size', String(opts?.size ?? 20))
    return http<
      PageResult<GroupAgentTask> & {
        counts: AgentTaskCounts
        memberId: number
        userAgentId: number
        agentName: string
      }
    >(`/api/groups/${publicId}/agents/${userAgentId}/tasks?${sp}`)
  },

  promoteAdmin: (publicId: string, memberId: number) =>
    http<GroupMember>(`/api/groups/${publicId}/admins/${memberId}`, { method: 'POST' }),

  demoteAdmin: (publicId: string, memberId: number) =>
    http<GroupMember>(`/api/groups/${publicId}/admins/${memberId}`, { method: 'DELETE' }),

  setReplyMode: (
    publicId: string,
    body: { agentMemberIds?: number[]; agentMemberId?: number; mode: AgentReplyMode },
  ) =>
    http<{ updated: GroupMember[] }>(`/api/groups/${publicId}/agents/reply-mode`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  messages: (publicId: string, params?: { beforeId?: number; on?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (params?.beforeId) q.set('beforeId', String(params.beforeId))
    if (params?.on) q.set('on', params.on)
    if (params?.limit) q.set('limit', String(params.limit))
    const qs = q.toString()
    return http<GroupMessage[]>(`/api/groups/${publicId}/messages${qs ? `?${qs}` : ''}`)
  },

  days: (publicId: string, limit = 90) =>
    http<string[]>(`/api/groups/${publicId}/messages/days?limit=${limit}`),

  media: (publicId: string, kind: MediaKind, page = 0, size = 20) =>
    http<PageResult<GroupAttachment>>(
      `/api/groups/${publicId}/media?kind=${kind}&page=${page}&size=${size}`,
    ),

  send: (
    publicId: string,
    body: {
      content?: string
      replyToId?: number
      clientMsgId?: string
      attachments?: { kind: string; url: string; mime?: string; title?: string }[]
    },
  ) =>
    http<GroupMessage>(`/api/groups/${publicId}/messages`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  read: (publicId: string, upToMessageId: number) =>
    http<{ count: number }>(`/api/groups/${publicId}/read`, {
      method: 'POST',
      body: JSON.stringify({ upToMessageId }),
    }),

  searchUsers: (q: string, page = 0, size = 20) =>
    http<PageResult<UserSearchItem>>(
      `/api/users/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`,
    ),
}
