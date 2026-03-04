import { useState } from 'react'
import { X, Trash2 } from 'lucide-react'
import type { SessionResponse } from '../../types/agent'

interface Props {
  session: SessionResponse
  onClose: () => void
  onConfirm: (options: { deleteMessages: boolean; deleteMemories: boolean; hideOnly: boolean }) => void
  loading: boolean
}

export function DeleteSessionModal({ session, onClose, onConfirm, loading }: Props) {
  const [mode, setMode] = useState<'hide' | 'custom'>('hide')
  const [deleteMessages, setDeleteMessages] = useState(true)
  const [deleteMemories, setDeleteMemories] = useState(false)

  const handleSubmit = () => {
    if (mode === 'hide') {
      onConfirm({ deleteMessages: false, deleteMemories: false, hideOnly: true })
    } else {
      if (!deleteMessages && !deleteMemories) return
      onConfirm({ deleteMessages, deleteMemories, hideOnly: false })
    }
  }

  const disabled = loading || (mode === 'custom' && !deleteMessages && !deleteMemories)

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl w-full max-w-md shadow-xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <div className="flex items-center gap-2">
            <Trash2 className="w-4 h-4 text-red-400" />
            <h2 className="text-sm font-semibold text-white">删除/隐藏会话</h2>
          </div>
          <button
            onClick={onClose}
            className="text-white/40 hover:text-white p-1 rounded transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="px-4 py-3 space-y-3 text-sm">
          <p className="text-white/60">
            确定要对该会话进行删除/隐藏操作吗？
          </p>
          <div className="px-3 py-2 rounded bg-white/[0.03] border border-white/[0.06] text-xs text-white/70">
            <div className="truncate mb-0.5">
              <span className="text-white/40 mr-1">任务：</span>
              {session.taskDescription || '会话'}
            </div>
            <div className="text-white/35 text-[10px]">
              ID: {session.sessionId.slice(0, 8)}…
            </div>
          </div>

          <div className="space-y-2">
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="radio"
                name="delete-mode"
                value="hide"
                checked={mode === 'hide'}
                onChange={() => setMode('hide')}
                className="mt-0.5"
              />
              <div>
                <div className="text-white text-sm">仅隐藏会话</div>
                <div className="text-xs text-white/45">
                  会话将从「最近会话」列表中隐藏，但聊天记录和长期记忆都会保留。
                </div>
              </div>
            </label>

            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="radio"
                name="delete-mode"
                value="custom"
                checked={mode === 'custom'}
                onChange={() => setMode('custom')}
                className="mt-0.5"
              />
              <div>
                <div className="text-white text-sm">自定义删除内容</div>
                <div className="text-xs text-white/45">
                  可选择删除聊天记录、长期记忆中的任一项或两者。
                </div>
              </div>
            </label>
          </div>

          {mode === 'custom' && (
            <div className="mt-1 space-y-2 pl-6">
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={deleteMessages}
                  onChange={e => setDeleteMessages(e.target.checked)}
                  className="mt-0.5 rounded border-white/30 bg-black/40 text-red-500 focus:ring-red-500"
                />
                <div>
                  <div className="text-white text-sm">删除会话聊天记录</div>
                  <div className="text-xs text-white/45">
                    删除该会话下所有消息与上下文，不可恢复。
                  </div>
                </div>
              </label>
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={deleteMemories}
                  onChange={e => setDeleteMemories(e.target.checked)}
                  className="mt-0.5 rounded border-white/30 bg-black/40 text-red-500 focus:ring-red-500"
                />
                <div>
                  <div className="text-white text-sm">删除会话关联的长期记忆</div>
                  <div className="text-xs text-white/45">
                    删除该会话在长期记忆库中的所有记录，不可恢复。
                  </div>
                </div>
              </label>
            </div>
          )}
        </div>

        <div className="px-4 py-3 border-t border-white/10 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 rounded-lg text-xs text-white/70 hover:text-white hover:bg-white/5 transition-colors"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={disabled}
            className="px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5
              bg-red-600 hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed text-white transition-colors"
          >
            {loading ? '执行中…' : '确认'}
          </button>
        </div>
      </div>
    </div>
  )
}

