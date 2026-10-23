import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import { groupsApi } from '../api/groups'
import { useAuth } from '../hooks/useAuth'
import { Users } from 'lucide-react'

export function JoinGroupPage() {
  const { inviteCode = '' } = useParams()
  const { isLoggedIn } = useAuth()
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['group-join-preview', inviteCode],
    queryFn: () => groupsApi.previewJoin(inviteCode),
    enabled: !!inviteCode,
    retry: false,
  })

  const joinMut = useMutation({
    mutationFn: () => groupsApi.join(inviteCode),
    onSuccess: (g) => {
      toast.success('已加入群聊')
      navigate(`/groups/${g.publicId}`, { replace: true })
    },
    onError: (e: Error) => toast.error(e.message || '加入失败'),
  })

  return (
    <div className="h-full flex items-center justify-center p-6">
      <div className="w-full max-w-sm rounded-2xl border border-white/10 bg-white/[0.03] p-6 text-center">
        {isLoading ? (
          <p className="text-sm text-white/40">加载邀请…</p>
        ) : error || !data ? (
          <p className="text-sm text-red-300/80">邀请无效或已失效</p>
        ) : (
          <>
            <div className="mx-auto w-16 h-16 rounded-2xl bg-blue-600/25 flex items-center justify-center mb-4 overflow-hidden">
              {data.avatarUrl ? (
                <img src={data.avatarUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <Users className="w-8 h-8 text-blue-300" />
              )}
            </div>
            <h1 className="text-lg font-semibold text-white mb-1">{data.title}</h1>
            <p className="text-xs text-white/40 mb-6">{data.memberCount} 位成员</p>
            {!isLoggedIn ? (
              <button
                type="button"
                className="w-full rounded-xl bg-blue-600 hover:bg-blue-500 py-2.5 text-sm font-medium"
                onClick={() =>
                  navigate(`/login?from=${encodeURIComponent(`/groups/join/${inviteCode}`)}`)
                }
              >
                登录后加入
              </button>
            ) : (
              <button
                type="button"
                disabled={joinMut.isPending}
                className="w-full rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-40 py-2.5 text-sm font-medium"
                onClick={() => joinMut.mutate()}
              >
                {joinMut.isPending ? '加入中…' : '加入群聊'}
              </button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
