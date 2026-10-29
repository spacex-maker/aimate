import { useEffect, useState } from 'react'
import { useMatch } from 'react-router-dom'
import { GroupChatPage } from '../../pages/GroupChatPage'
import { SessionPage } from '../../pages/SessionPage'

const MAX_CACHED = 12

/**
 * 会话保活：切群/切 Agent 会话时不卸载页面，只隐藏。
 * 滚动位置留在 DOM 里，不刷新页面就停在哪还在哪。
 */
export function ConversationKeepAlive() {
  const groupMatch = useMatch('/groups/:publicId')
  const sessionMatch = useMatch('/session/:sessionId')
  const activeGroupId = groupMatch?.params.publicId
  const activeSessionId = sessionMatch?.params.sessionId

  const [groupIds, setGroupIds] = useState<string[]>(() => (activeGroupId ? [activeGroupId] : []))
  const [sessionIds, setSessionIds] = useState<string[]>(() =>
    activeSessionId ? [activeSessionId] : [],
  )

  useEffect(() => {
    if (!activeGroupId) return
    setGroupIds((ids) => {
      if (ids[ids.length - 1] === activeGroupId) return ids
      const next = ids.filter((id) => id !== activeGroupId).concat(activeGroupId)
      return next.length > MAX_CACHED ? next.slice(next.length - MAX_CACHED) : next
    })
  }, [activeGroupId])

  useEffect(() => {
    if (!activeSessionId) return
    setSessionIds((ids) => {
      if (ids[ids.length - 1] === activeSessionId) return ids
      const next = ids.filter((id) => id !== activeSessionId).concat(activeSessionId)
      return next.length > MAX_CACHED ? next.slice(next.length - MAX_CACHED) : next
    })
  }, [activeSessionId])

  const showingChat = !!(activeGroupId || activeSessionId)

  return (
    <div className={showingChat ? 'h-full min-h-0 relative' : 'hidden'} aria-hidden={!showingChat}>
      {groupIds.map((id) => {
        const active = id === activeGroupId
        return (
          <div
            key={`group-${id}`}
            className={active ? 'h-full min-h-0 flex flex-col' : 'hidden'}
            aria-hidden={!active}
          >
            <GroupChatPage publicId={id} active={active} />
          </div>
        )
      })}
      {sessionIds.map((id) => {
        const active = id === activeSessionId
        return (
          <div
            key={`session-${id}`}
            className={active ? 'h-full min-h-0 flex flex-col' : 'hidden'}
            aria-hidden={!active}
          >
            <SessionPage sessionId={id} active={active} />
          </div>
        )
      })}
    </div>
  )
}