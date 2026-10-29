import type { GroupMessage } from '../api/groups'

/** In-memory per-group messages + scrollTop while the tab stays open. */
export type GroupViewSnapshot = {
  messages: GroupMessage[]
  scrollTop: number
  atBottom: boolean
  lastSeenMsgId: number | null
  draft: string
}

const cache = new Map<string, GroupViewSnapshot>()

export function getGroupView(publicId: string): GroupViewSnapshot | null {
  if (!publicId) return null
  return cache.get(publicId) ?? null
}

export function setGroupView(publicId: string, snap: GroupViewSnapshot): void {
  if (!publicId) return
  cache.set(publicId, snap)
}

export function patchGroupView(
  publicId: string,
  patch: Partial<GroupViewSnapshot>,
): void {
  if (!publicId) return
  const prev = cache.get(publicId)
  if (!prev) return
  cache.set(publicId, { ...prev, ...patch })
}