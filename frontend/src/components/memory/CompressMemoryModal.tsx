import { useEffect, useState } from 'react'
import { X, Loader2, ArrowRight } from 'lucide-react'
import type { CompressPrepareResult, CompressedMemoryDto, MemoryItem } from '../../types/memory'

const TYPE_LABEL: Record<string, string> = {
  SEMANTIC: '语义',
  EPISODIC: '情节',
  PROCEDURAL: '程序',
}

interface Props {
  onClose: () => void
  onPrepare: () => Promise<CompressPrepareResult>
  onExecute: (body: { delete_ids: string[]; new_memories: CompressedMemoryDto[] }) => Promise<void>
  isExecuting: boolean
}

export function CompressMemoryModal({ onClose, onPrepare, onExecute, isExecuting }: Props) {
  const [loading, setLoading] = useState(true)
  const [result, setResult] = useState<CompressPrepareResult | null>(null)

  useEffect(() => {
    let cancelled = false
    onPrepare()
      .then((data) => {
        if (!cancelled) {
          setResult(data)
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setResult({ current: [], proposed: [], error: e?.message ?? '请求失败' })
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
  }, [onPrepare])

  const handleConfirm = () => {
    if (!result || result.error || result.current.length === 0) return
    const delete_ids = result.current.map((m) => m.id)
    const new_memories = result.proposed
    onExecute({ delete_ids, new_memories }).then(() => onClose())
  }

  const canConfirm = result && !result.error && result.proposed.length > 0 && !isExecuting

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10 flex-shrink-0">
          <h2 className="text-sm font-semibold text-white">压缩长期记忆</h2>
          <button onClick={onClose} className="text-white/30 hover:text-white p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-hidden flex flex-col min-h-0 p-6">
          {loading ? (
            <div className="flex-1 flex items-center justify-center text-white/50">
              <Loader2 className="w-8 h-8 animate-spin mr-2" />
              <span>正在生成压缩建议…</span>
            </div>
          ) : result?.error ? (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-amber-400 text-sm">{result.error}</p>
            </div>
          ) : result && result.current.length === 0 ? (
            <div className="flex-1 flex items-center justify-center text-white/40 text-sm">
              当前没有可压缩的记忆
            </div>
          ) : result ? (
            <>
              <p className="text-xs text-white/40 mb-4">
                下方对比压缩前与压缩后的内容，确认后将删除左侧记录并写入右侧合并后的记忆。
              </p>
              <div className="flex-1 grid grid-cols-2 gap-4 min-h-0 overflow-hidden">
                <div className="flex flex-col min-h-0 border border-white/10 rounded-lg overflow-hidden">
                  <div className="px-3 py-2 bg-white/5 border-b border-white/10 text-xs font-medium text-white/80">
                    压缩前（{result.current.length} 条）
                  </div>
                  <div className="flex-1 overflow-y-auto p-3 space-y-2">
                    {result.current.map((m) => (
                      <MemoryCard key={m.id} item={m} />
                    ))}
                  </div>
                </div>
                <div className="flex flex-col min-h-0 border border-white/10 rounded-lg overflow-hidden">
                  <div className="px-3 py-2 bg-white/5 border-b border-white/10 text-xs font-medium text-white/80 flex items-center gap-2">
                    <ArrowRight className="w-3.5 h-3.5 text-blue-400" />
                    压缩后（{result.proposed.length} 条）
                  </div>
                  <div className="flex-1 overflow-y-auto p-3 space-y-2">
                    {result.proposed.length === 0 ? (
                      <p className="text-white/30 text-xs">模型未返回压缩结果</p>
                    ) : (
                      result.proposed.map((m, i) => (
                        <ProposedCard key={i} item={m} />
                      ))
                    )}
                  </div>
                </div>
              </div>
            </>
          ) : null}
        </div>

        {result && !result.error && result.current.length > 0 && (
          <div className="flex-shrink-0 px-6 py-4 border-t border-white/10 flex justify-end gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm text-white/70 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
            >
              取消
            </button>
            <button
              onClick={handleConfirm}
              disabled={!canConfirm}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:pointer-events-none rounded-lg text-sm text-white font-medium flex items-center gap-2 transition-colors"
            >
              {isExecuting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  执行中…
                </>
              ) : (
                '确认压缩'
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function MemoryCard({ item }: { item: MemoryItem }) {
  const typeLabel = TYPE_LABEL[item.memoryType] ?? item.memoryType
  return (
    <div className="bg-black/20 rounded-lg p-2.5 text-xs border border-white/5">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-white/50">{typeLabel}</span>
        <span className="text-white/40">重要度 {Math.round(item.importance * 100)}%</span>
      </div>
      <p className="text-white/90 break-words line-clamp-4">{item.content}</p>
    </div>
  )
}

function ProposedCard({ item }: { item: CompressedMemoryDto }) {
  const typeLabel = TYPE_LABEL[item.memory_type] ?? item.memory_type
  const imp = typeof item.importance === 'number' ? Math.round(item.importance * 100) : 80
  return (
    <div className="bg-blue-500/5 border border-blue-500/20 rounded-lg p-2.5 text-xs">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-white/50">{typeLabel}</span>
        <span className="text-white/40">重要度 {imp}%</span>
      </div>
      <p className="text-white/90 break-words line-clamp-4">{item.content}</p>
    </div>
  )
}
