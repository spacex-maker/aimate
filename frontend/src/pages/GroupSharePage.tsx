import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { useState } from 'react'
import { Paperclip, X } from 'lucide-react'
import clsx from 'clsx'
import { groupsApi, type GroupMessage, type GroupMessageReplyTo } from '../api/groups'
import { AgentBubbleHeader } from '../components/agent/AgentAvatar'
import { useAuth } from '../hooks/useAuth'

function formatMessageTime(raw?: string | null): string {
  if (!raw) return ''
  const d = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return `${hh}:${mm}`
  return `${d.getMonth() + 1}/${d.getDate()} ${hh}:${mm}`
}

function QuoteBlock({ reply }: { reply: GroupMessageReplyTo }) {
  const deleted = !!reply.deleted
  return (
    <div className="flex w-full items-start gap-2 rounded-lg bg-black/25 px-2.5 py-1.5 mb-2 text-left">
      <div className="mt-0.5 w-0.5 self-stretch rounded-full bg-cyan-400/50 flex-shrink-0" />
      {reply.imageUrl && !deleted && (
        <img src={reply.imageUrl} alt="" className="h-9 w-9 rounded object-cover flex-shrink-0 opacity-90" />
      )}
      <div className="min-w-0 flex-1">
        <div className="text-[10px] text-cyan-200/70 truncate">{deleted ? '引用' : reply.senderName || '消息'}</div>
        <div className={clsx('text-[11px] line-clamp-2', deleted ? 'text-white/30 italic' : 'text-white/45')}>
          {reply.contentPreview || (deleted ? '原消息已删除' : '…')}
        </div>
      </div>
    </div>
  )
}

function ShareBubble({ msg }: { msg: GroupMessage }) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  if (msg.messageKind === 'MENTION_APPROVAL') {
    const ap = msg.mentionApproval
    const status = ap?.status || 'PENDING'
    const name = ap?.targetAgentName || 'Agent'
    return (
      <div className="flex justify-center py-1">
        <div className="w-full max-w-md rounded-2xl border border-amber-400/15 bg-amber-500/[0.06] px-4 py-3">
          <div className="flex items-center justify-between gap-2">
            <span className="truncate text-sm text-amber-50/85">{name}</span>
            <span className="shrink-0 text-[10px] text-white/40">
              {status === 'APPROVED' ? '已同意' : status === 'REJECTED' ? '已拒绝' : '待确认'}
            </span>
          </div>
          {ap?.triggerPreview ? (
            <p className="mt-2 line-clamp-2 text-[12px] text-white/45">{ap.triggerPreview}</p>
          ) : null}
          <p className="mt-2 text-center text-[11px] text-white/30">{msg.content}</p>
        </div>
      </div>
    )
  }
  if (msg.senderType === 'SYSTEM') {
    return (
      <div className="flex items-center gap-3 py-1">
        <div className="h-px flex-1 bg-white/5" />
        <span className="text-[11px] text-white/25 text-center max-w-[80%]">{msg.content}</span>
        <div className="h-px flex-1 bg-white/5" />
      </div>
    )
  }
  const isAgent = msg.senderType === 'AGENT'
  const timeLabel = formatMessageTime(msg.createTime)

  return (
    <div className="group min-w-0 rounded-xl flex justify-start">
      <div className="min-w-0 w-full flex flex-col gap-1.5 max-w-[85%] items-start">
        {isAgent ? (
          <AgentBubbleHeader
            agent={{
              id: msg.senderAgentId,
              name: msg.senderName,
              originalName: msg.senderOriginalName,
              careerName: msg.senderCareerName,
              avatarUrl: msg.senderAvatarUrl,
              externalAgentKey: msg.senderName,
            }}
            timeLabel={timeLabel || null}
          />
        ) : (
          <div className="flex items-center gap-2 px-0.5 mb-1">
            <div className="w-8 h-8 rounded-full bg-white/10 overflow-hidden flex-shrink-0">
              {msg.senderAvatarUrl ? (
                <img src={msg.senderAvatarUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <span className="text-xs flex items-center justify-center h-full text-white/50">
                  {(msg.senderName || '?')[0]}
                </span>
              )}
            </div>
            <span className="text-xs font-medium text-white/65 truncate max-w-[14rem]">
              {msg.senderName || '用户'}
            </span>
            {timeLabel ? (
              <span className="text-[10px] text-white/35 flex-shrink-0 tabular-nums">{timeLabel}</span>
            ) : null}
          </div>
        )}
        <div className="relative min-w-0 max-w-full rounded-2xl rounded-tl-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap break-words bg-white/5 text-white/80">
          {msg.replyTo && <QuoteBlock reply={msg.replyTo} />}
          {msg.content}
          {msg.attachments && msg.attachments.length > 0 && (
            <div className="mt-2 space-y-2">
              {msg.attachments.map((a) =>
                a.kind === 'IMAGE' ? (
                  <button
                    key={a.id}
                    type="button"
                    className="block w-full max-w-[min(100%,20rem)] overflow-hidden rounded-xl bg-black/35 ring-1 ring-white/10 text-left cursor-zoom-in"
                    onClick={() => setPreviewUrl(a.url)}
                  >
                    <img src={a.url} alt="" className="max-h-64 w-full object-contain bg-black/20" />
                  </button>
                ) : (
                  <a
                    key={a.id}
                    href={a.url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-xs underline opacity-80"
                  >
                    <Paperclip className="w-3 h-3" />
                    {a.title || a.url}
                  </a>
                ),
              )}
            </div>
          )}
        </div>
      </div>
      {previewUrl &&
        createPortal(
          <div
            className="fixed inset-0 z-[100] flex items-center justify-center bg-black/85 p-4 backdrop-blur-sm"
            onClick={() => setPreviewUrl(null)}
          >
            <button
              type="button"
              className="absolute right-4 top-4 rounded-full bg-white/10 p-2 text-white/80"
              onClick={() => setPreviewUrl(null)}
            >
              <X className="h-5 w-5" />
            </button>
            <img
              src={previewUrl}
              alt=""
              className="max-h-[90vh] max-w-[min(100%,56rem)] object-contain rounded-lg"
              onClick={(e) => e.stopPropagation()}
            />
          </div>,
          document.body,
        )}
    </div>
  )
}

/** 公开只读：群聊分享快照 `/s/:code`（#root overflow:hidden，必须自管高度与滚动） */
export function GroupSharePage() {
  const { code = '' } = useParams()
  const { user } = useAuth()
  const q = useQuery({
    queryKey: ['group-share', code],
    queryFn: () => groupsApi.getPublicShare(code),
    enabled: /^[a-z]{6,32}$/.test(code),
    retry: false,
  })

  // LoginPage 读 ?from=；注册并入 Account，同样走登录并带回跳
  const loginHref = `/login?from=${encodeURIComponent(`/s/${code}`)}`

  return (
    <div className="h-full max-h-full bg-[#0d0d0d] text-white flex flex-col overflow-hidden">
      <header className="flex-shrink-0 border-b border-white/10 px-4 sm:px-6 py-3 flex items-center gap-3">
        <Link to="/" className="text-sm font-semibold tracking-wide text-white/90 hover:text-white">
          AIMATEX
        </Link>
        <div className="min-w-0 flex-1">
          <div className="text-sm text-white/70 truncate">
            {q.data?.groupTitle || '群聊分享'}
          </div>
          <div className="text-[11px] text-white/30 mt-0.5 truncate">
            只读快照
            {q.data?.messageCount != null ? ` · ${q.data.messageCount} 条` : ''}
            {q.data?.snapshotAt ? ` · ${formatMessageTime(String(q.data.snapshotAt))}` : ''}
          </div>
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          {user ? (
            <Link
              to="/app"
              className="rounded-lg bg-white/10 px-3 py-1.5 text-[11px] text-white/80 hover:bg-white/15"
            >
              进入控制台
            </Link>
          ) : (
            <>
              <Link
                to={loginHref}
                className="rounded-lg px-3 py-1.5 text-[11px] text-white/55 hover:bg-white/5 hover:text-white/80"
              >
                登录
              </Link>
              <Link
                to={loginHref}
                className="rounded-lg bg-cyan-500/90 px-3 py-1.5 text-[11px] font-medium text-black hover:bg-cyan-400"
              >
                注册
              </Link>
            </>
          )}
        </div>
      </header>

      {!user && (
        <div className="flex-shrink-0 border-b border-cyan-500/15 bg-gradient-to-r from-cyan-500/10 via-transparent to-transparent px-4 sm:px-6 py-3">
          <p className="text-[12px] text-white/65 leading-relaxed max-w-3xl">
            这是一份聊天记录快照，仅展示分享当时的内容。注册 AIMATEX 后可创建自己的 AI Agent 群聊、招聘本地 Cursor / Codex。
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Link
              to={loginHref}
              className="inline-flex rounded-lg bg-cyan-500/90 px-3 py-1.5 text-[11px] font-medium text-black hover:bg-cyan-400"
            >
              免费注册
            </Link>
            <Link
              to={loginHref}
              className="inline-flex rounded-lg border border-white/12 px-3 py-1.5 text-[11px] text-white/60 hover:bg-white/5"
            >
              已有账号登录
            </Link>
          </div>
        </div>
      )}

      <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain">
        <div className={clsx('max-w-3xl mx-auto px-4 pt-6 min-w-0 space-y-6', user ? 'pb-10' : 'pb-28')}>
          {q.isLoading && <p className="text-center text-sm text-white/30 py-16">加载中…</p>}
          {q.isError && (
            <p className="text-center text-sm text-white/40 py-16">分享不存在或已失效</p>
          )}
          {q.data?.messages?.length === 0 && (
            <p className="text-center text-sm text-white/25 py-16">快照为空</p>
          )}
          {q.data?.messages?.map((msg) => (
            <ShareBubble key={msg.id} msg={msg} />
          ))}
        </div>
      </div>

      {!user && !q.isLoading && !q.isError && (
        <div className="flex-shrink-0 border-t border-white/10 bg-[#111]/95 backdrop-blur-md px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
          <div className="max-w-3xl mx-auto flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <div className="text-[12px] text-white/75 font-medium">想开自己的 Agent 群？</div>
              <div className="text-[10px] text-white/35 mt-0.5 truncate">注册后即可招聘 Cursor / Codex 等本地 AI</div>
            </div>
            <Link
              to={loginHref}
              className="flex-shrink-0 rounded-lg bg-cyan-500/90 px-3.5 py-2 text-[11px] font-medium text-black hover:bg-cyan-400"
            >
              去注册
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
