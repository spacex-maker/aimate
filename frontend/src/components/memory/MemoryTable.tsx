import { useState } from 'react'
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
  onUpdateImportance?: (id: string, importance: number) => void
  onToggleNoCompress?: (id: string, noCompress: boolean) => void
  isDeleting?: string | null
  isUpdating?: string | null
  showScore?: boolean
}

const IMPORTANCE_PRESETS = [
  { label: '低', value: 0.3 },
  { label: '中', value: 0.5 },
  { label: '高', value: 0.8 },
  { label: '重要', value: 1 },
] as const

export function MemoryTable({ items, onDelete, onUpdateImportance, onToggleNoCompress, isDeleting, isUpdating, showScore }: Props) {
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
            <th className="pb-3 pr-4 text-xs text-white/30 font-medium w-16 text-center">重要度</th>
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
                  <div className="flex flex-col gap-1">
                    <div
                      className="text-white/75 text-xs leading-relaxed line-clamp-3 font-mono"
                      title={item.content}
                    >
                      {item.content}
                    </div>
                    <span
                      className="text-white/30 text-[10px] font-mono truncate"
                      title={item.sessionId}
                    >
                      {item.sessionId}
                    </span>
                  </div>
                </td>
                <td className="py-3 pr-4 text-right">
                  <div className="flex flex-col items-end gap-1">
                    <ImportanceBar
                      value={item.importance}
                      onChange={onUpdateImportance ? v => onUpdateImportance(item.id, v) : undefined}
                      disabled={isUpdating === item.id}
                    />
                    {onToggleNoCompress && (
                      <button
                        type="button"
                        onClick={() => onToggleNoCompress(item.id, !item.noCompress)}
                        disabled={isUpdating === item.id}
                        className={clsx(
                          'text-[10px] font-mono px-1.5 py-0.5 rounded border transition-colors',
                          item.noCompress
                            ? 'text-amber-300 border-amber-400/60 bg-amber-500/10'
                            : 'text-white/40 border-white/15 hover:text-white/80 hover:bg-white/10'
                        )}
                        title={item.noCompress ? '已标记为禁止压缩，压缩记忆时会跳过这条记录' : '标记为禁止压缩，以避免在压缩记忆时被合并/删除'}
                      >
                        {item.noCompress ? '禁止压缩' : '允许压缩'}
                      </button>
                    )}
                  </div>
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

function ImportanceBar({
  value,
  onChange,
  disabled,
}: {
  value: number
  onChange?: (v: number) => void
  disabled?: boolean
}) {
  const [draggingValue, setDraggingValue] = useState<number | null>(null)
  const displayValue = draggingValue ?? value
  const pct = Math.round(displayValue * 100)
  const selected = IMPORTANCE_PRESETS.reduce((a, b) =>
    Math.abs(a.value - displayValue) <= Math.abs(b.value - displayValue) ? a : b
  )
  const colorText =
    pct >= 80 ? 'text-red-400' : pct >= 60 ? 'text-orange-400' : 'text-blue-400'

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = Number(e.target.value)
    setDraggingValue(v)
  }
  const handleCommit = () => {
    if (draggingValue != null && onChange) {
      onChange(draggingValue)
      setDraggingValue(null)
    }
  }

  const slider = (
    <input
      type="range"
      min={0}
      max={1}
      step={0.05}
      value={displayValue}
      onChange={handleChange}
      onMouseUp={handleCommit}
      onTouchEnd={handleCommit}
      disabled={!onChange || disabled}
      className="w-20 h-1.5 accent-blue-500 disabled:opacity-50 cursor-pointer"
      title={`重要度 ${pct}%，松手后保存`}
    />
  )

  return (
    <div className="flex items-center gap-1.5 justify-end">
      {slider}
      <span
        className={clsx('text-[10px] font-mono w-12 text-right tabular-nums', colorText)}
        title={`${selected.label}（${pct}%）`}
      >
        {selected.label} {pct}
      </span>
    </div>
  )
}
