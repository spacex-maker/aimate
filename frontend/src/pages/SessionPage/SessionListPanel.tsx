import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { StatusBadge } from '../../components/agent/StatusBadge'
import type { SessionResponse } from '../../types/agent'

interface SessionListPanelProps {
  sessions: SessionResponse[]
  loading: boolean
  selectedSessionId?: string
  onSelect: (id: string) => void
}

export function SessionListPanel({ sessions, loading, selectedSessionId, onSelect }: SessionListPanelProps) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <aside className="w-64 border-r border-white/10 bg-black/20 flex-shrink-0 flex flex-col min-h-0">
      <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
        <span className="text-xs font-semibold text-white/60 uppercase tracking-wider">最近会话</span>
        <button
          type="button"
          onClick={() => setCollapsed(v => !v)}
          className="text-white/40 hover:text-white/80 transition-colors"
          title={collapsed ? '展开最近会话' : '收起最近会话'}
        >
          <ChevronDown
            className={`w-3 h-3 transition-transform ${collapsed ? '-rotate-90' : 'rotate-0'}`}
          />
        </button>
      </div>
      {collapsed ? (
        <div className="flex-1 flex items-center justify-center px-3 text-[11px] text-white/30">
          最近会话已收起
        </div>
      ) : loading ? (
        <div className="flex-1 flex items-center justify-center text-xs text-white/30">加载中...</div>
      ) : sessions.length === 0 ? (
        <div className="flex-1 flex items-center justify-center px-3 text-xs text-white/25 text-center">
          暂无会话
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto py-2 space-y-1">
          {sessions.map((s) => {
            const active = s.sessionId === selectedSessionId
            return (
              <button
                key={s.sessionId}
                type="button"
                onClick={() => onSelect(s.sessionId)}
                className={`w-full px-3 py-2 text-left text-xs flex items-center gap-2 transition-colors ${
                  active ? 'bg-white/10 text-white' : 'bg-transparent text-white/70 hover:bg-white/5'
                }`}
              >
                <StatusBadge status={s.status} />
                <div className="flex-1 min-w-0">
                  <div className="truncate">{s.taskDescription || '会话'}</div>
                  <div className="text-[10px] text-white/30 truncate">{s.sessionId}</div>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </aside>
  )
}

