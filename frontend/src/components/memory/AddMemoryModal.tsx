import { useState } from 'react'
import { X } from 'lucide-react'
import type { AddMemoryRequest, MemoryType } from '../../types/memory'

interface Props {
  onClose: () => void
  onSubmit: (data: AddMemoryRequest) => void
  isLoading: boolean
}

export function AddMemoryModal({ onClose, onSubmit, isLoading }: Props) {
  const [content, setContent] = useState('')
  const [memoryType, setMemoryType] = useState<MemoryType>('SEMANTIC')
  const [importance, setImportance] = useState(0.8)
  const [sessionId, setSessionId] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!content.trim()) return
    onSubmit({ content: content.trim(), memoryType, importance, sessionId: sessionId || undefined })
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-lg">
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10">
          <h2 className="text-sm font-semibold text-white">手动添加记忆</h2>
          <button onClick={onClose} className="text-white/30 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div>
            <label className="text-xs text-white/50 mb-1.5 block">内容</label>
            <textarea
              value={content}
              onChange={e => setContent(e.target.value)}
              rows={4}
              placeholder="输入要记住的内容..."
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 resize-none focus:outline-none focus:border-blue-500/50"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">类型</label>
              <select
                value={memoryType}
                onChange={e => setMemoryType(e.target.value as MemoryType)}
                className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500/50"
              >
                <option value="SEMANTIC">SEMANTIC — 语义知识</option>
                <option value="EPISODIC">EPISODIC — 事件记忆</option>
                <option value="PROCEDURAL">PROCEDURAL — 操作流程</option>
              </select>
            </div>
            <div>
              <label className="text-xs text-white/50 mb-1.5 block">重要度 ({Math.round(importance * 100)}%)</label>
              <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                value={importance}
                onChange={e => setImportance(Number(e.target.value))}
                className="w-full mt-2 accent-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="text-xs text-white/50 mb-1.5 block">关联会话 ID（可选）</label>
            <input
              value={sessionId}
              onChange={e => setSessionId(e.target.value)}
              placeholder="留空则标记为 manual"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
            />
          </div>

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2 rounded-lg border border-white/10 text-sm text-white/60 hover:text-white hover:bg-white/5 transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isLoading || !content.trim()}
              className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-sm text-white font-medium transition-colors disabled:opacity-50"
            >
              {isLoading ? '保存中...' : '保存记忆'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
