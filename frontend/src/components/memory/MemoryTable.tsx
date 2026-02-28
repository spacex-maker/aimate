import { Trash2 } from 'lucide-react'
import clsx from 'clsx'
import type { MemoryItem, MemoryType } from '../../types/memory'

const typeConfig: Record<MemoryType, { label: string; class: string }> = {
  EPISODIC:   { label: '情节', class: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/30' },
  SEMANTIC:   { label: '语义', class: 'text-blue-400 bg-blue-400/10 border-blue-400/30' },
  PROCEDURAL: { label: '程序', class: 'text-purple-400 bg-purple-400/10 border-purple-400/30' },
}
const defaultTypeConfig = typeConfig.SEMANTIC

interface Props {
  items: MemoryItem[]
  onDelete: (id: string) => void
  isDeleting?: string | null
  showScore?: boolean
}

export function MemoryTable({ items, onDelete, isDeleting, showScore }: Props) {
  if (items.length === 0) {
    return (
      <div className="text-center py-16 text-white/25 text-sm">
        暂无记忆数据
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-left">
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-16">类型</th>
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium">内容</th>
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-28">会话 ID</th>
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-16 text-right">重要度</th>
            {showScore && (
              <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-16 text-right">相关度</th>
            )}
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-36">创建时间</th>
            <th className="pb-3 text-xs text-white/30 font-medium w-10" />
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {items.map((item, index) => {
            const type = item.memoryType ?? (item as { memory_type?: MemoryType }).memory_type
            const tc = (type && typeConfig[type]) ? typeConfig[type] : defaultTypeConfig
            return (
              <tr key={`${item.id}-${index}`} className="group hover:bg-white/[0.03] transition-colors">
                <td className="py-3 pr-4">
                  <span className={clsx('text-[10px] font-mono border rounded px-1.5 py-0.5', tc.class)}>
                    {tc.label}
                  </span>
                </td>
                <td className="py-3 pr-4 max-w-sm">
                  <div
                    className="text-white/75 text-xs leading-relaxed line-clamp-3 font-mono"
                    title={item.content}
                  >
                    {item.content}
                  </div>
                </td>
                <td className="py-3 pr-4">
                  <span className="text-white/30 text-[10px] font-mono truncate block max-w-[6rem]">
                    {item.sessionId.slice(0, 8)}…
                  </span>
                </td>
                <td className="py-3 pr-4 text-right">
                  <ImportanceBar value={item.importance} />
                </td>
                {showScore && (
                  <td className="py-3 pr-4 text-right">
                    <span className="text-xs font-mono text-green-400">
                      {item.score != null ? item.score.toFixed(3) : '—'}
                    </span>
                  </td>
                )}
                <td className="py-3 pr-4">
                  <span className="text-white/25 text-[10px] font-mono">{item.createTime}</span>
                </td>
                <td className="py-3">
                  <button
                    onClick={() => onDelete(item.id)}
                    disabled={isDeleting === item.id}
                    className="opacity-0 group-hover:opacity-100 transition-opacity text-white/30 hover:text-red-400 disabled:opacity-50"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ImportanceBar({ value }: { value: number }) {
  const pct = Math.round(value * 100)
  const color = pct >= 80 ? 'bg-red-400' : pct >= 60 ? 'bg-orange-400' : 'bg-blue-400'
  return (
    <div className="flex items-center gap-1.5 justify-end">
      <div className="w-12 h-1.5 bg-white/10 rounded-full overflow-hidden">
        <div className={clsx('h-full rounded-full', color)} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-[10px] font-mono text-white/30 w-6">{pct}</span>
    </div>
  )
}
