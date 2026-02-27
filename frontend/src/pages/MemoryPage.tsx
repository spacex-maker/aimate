import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, Plus, Trash2, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { memoryApi } from '../api/memory'
import { MemoryTable } from '../components/memory/MemoryTable'
import { AddMemoryModal } from '../components/memory/AddMemoryModal'
import type { MemoryType, AddMemoryRequest } from '../types/memory'

const PAGE_SIZE = 20

const TYPES: { value: MemoryType | ''; label: string }[] = [
  { value: '', label: '全部类型' },
  { value: 'EPISODIC', label: '情节记忆' },
  { value: 'SEMANTIC', label: '语义记忆' },
  { value: 'PROCEDURAL', label: '程序记忆' },
]

type Tab = 'browse' | 'search'

export function MemoryPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('browse')

  // Browse state
  const [page, setPage] = useState(0)
  const [typeFilter, setTypeFilter] = useState<MemoryType | ''>('')
  const [sessionFilter, setSessionFilter] = useState('')
  const [keyword, setKeyword] = useState('')
  const [showAddModal, setShowAddModal] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  // Search state
  const [searchQ, setSearchQ] = useState('')
  const [searchTrigger, setSearchTrigger] = useState('')

  // ── Queries ──────────────────────────────────────────────────────────────────
  const browseKey = ['memories', page, typeFilter, sessionFilter, keyword]
  const { data: browseData, isLoading: browseLoading, refetch } = useQuery({
    queryKey: browseKey,
    queryFn: () =>
      memoryApi.list({
        page,
        size: PAGE_SIZE,
        type: typeFilter || undefined,
        session: sessionFilter || undefined,
        keyword: keyword || undefined,
      }),
    enabled: tab === 'browse',
  })

  const { data: countData } = useQuery({
    queryKey: ['memory-count', typeFilter, sessionFilter],
    queryFn: () => memoryApi.count(typeFilter || undefined, sessionFilter || undefined),
  })

  const { data: searchResults, isLoading: searchLoading } = useQuery({
    queryKey: ['memory-search', searchTrigger],
    queryFn: () => memoryApi.search(searchTrigger, 20),
    enabled: !!searchTrigger,
  })

  // ── Mutations ────────────────────────────────────────────────────────────────
  const deleteMutation = useMutation({
    mutationFn: (id: number) => memoryApi.deleteById(id),
    onMutate: (id) => setDeletingId(id),
    onSuccess: () => {
      toast.success('已删除')
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
      if (tab === 'search') queryClient.invalidateQueries({ queryKey: ['memory-search'] })
    },
    onError: (e: Error) => toast.error(e.message),
    onSettled: () => setDeletingId(null),
  })

  const addMutation = useMutation({
    mutationFn: (body: AddMemoryRequest) => memoryApi.add(body),
    onSuccess: () => {
      toast.success('记忆已保存')
      setShowAddModal(false)
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const totalPages = browseData ? Math.ceil(browseData.total / PAGE_SIZE) : 0

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex-shrink-0 px-6 py-5 border-b border-white/10 flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-white">长期记忆库</h1>
          <p className="text-xs text-white/35 mt-0.5">
            共 {countData?.count ?? '…'} 条记忆向量
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => refetch()}
            className="p-2 text-white/30 hover:text-white/70 hover:bg-white/5 rounded-lg transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors"
          >
            <Plus className="w-4 h-4" /> 添加记忆
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex-shrink-0 flex border-b border-white/10 px-6">
        {(['browse', 'search'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={clsx(
              'px-4 py-3 text-sm border-b-2 transition-colors',
              tab === t
                ? 'border-blue-500 text-blue-400'
                : 'border-transparent text-white/40 hover:text-white/70'
            )}
          >
            {t === 'browse' ? '浏览' : '语义搜索'}
          </button>
        ))}
      </div>

      {/* Browse tab */}
      {tab === 'browse' && (
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Filters */}
          <div className="flex-shrink-0 flex gap-3 px-6 py-4 border-b border-white/[0.06]">
            <select
              value={typeFilter}
              onChange={e => { setTypeFilter(e.target.value as MemoryType | ''); setPage(0) }}
              className="bg-[#1a1a1a] border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none"
            >
              {TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>

            <input
              value={sessionFilter}
              onChange={e => { setSessionFilter(e.target.value); setPage(0) }}
              placeholder="过滤会话 ID..."
              className="bg-[#1a1a1a] border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white placeholder-white/20 focus:outline-none w-48"
            />

            <input
              value={keyword}
              onChange={e => { setKeyword(e.target.value); setPage(0) }}
              placeholder="关键词过滤..."
              className="bg-[#1a1a1a] border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white placeholder-white/20 focus:outline-none w-48"
            />

            {(typeFilter || sessionFilter || keyword) && (
              <button
                onClick={() => { setTypeFilter(''); setSessionFilter(''); setKeyword(''); setPage(0) }}
                className="text-xs text-white/40 hover:text-white/70 flex items-center gap-1"
              >
                <Trash2 className="w-3 h-3" /> 清除筛选
              </button>
            )}
          </div>

          {/* Table */}
          <div className="flex-1 overflow-y-auto px-6 py-4">
            {browseLoading ? (
              <div className="text-center py-16 text-white/25 text-sm">加载中...</div>
            ) : (
              <MemoryTable
                items={browseData?.items ?? []}
                onDelete={(id) => deleteMutation.mutate(id)}
                isDeleting={deletingId}
              />
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex-shrink-0 flex items-center justify-between px-6 py-3 border-t border-white/[0.06]">
              <span className="text-xs text-white/30">
                第 {page + 1} / {totalPages} 页 · 共 {browseData?.total ?? 0} 条
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage(p => p - 1)}
                  disabled={page === 0}
                  className="p-1.5 text-white/40 hover:text-white disabled:opacity-25 hover:bg-white/5 rounded"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setPage(p => p + 1)}
                  disabled={page >= totalPages - 1}
                  className="p-1.5 text-white/40 hover:text-white disabled:opacity-25 hover:bg-white/5 rounded"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Search tab */}
      {tab === 'search' && (
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="flex-shrink-0 px-6 py-4 border-b border-white/[0.06]">
            <div className="flex gap-3">
              <div className="flex-1 relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/25" />
                <input
                  value={searchQ}
                  onChange={e => setSearchQ(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && setSearchTrigger(searchQ)}
                  placeholder="输入语义搜索内容，按 Enter 搜索..."
                  className="w-full bg-[#1a1a1a] border border-white/10 rounded-lg pl-10 pr-4 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-blue-500/50"
                />
              </div>
              <button
                onClick={() => setSearchTrigger(searchQ)}
                disabled={!searchQ.trim()}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm text-white font-medium transition-colors disabled:opacity-50"
              >
                搜索
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-6 py-4">
            {searchLoading ? (
              <div className="text-center py-16 text-white/25 text-sm">语义检索中...</div>
            ) : searchResults ? (
              <MemoryTable
                items={searchResults}
                onDelete={(id) => deleteMutation.mutate(id)}
                isDeleting={deletingId}
                showScore
              />
            ) : (
              <div className="text-center py-16 text-white/20 text-sm">输入查询词以进行语义搜索</div>
            )}
          </div>
        </div>
      )}

      {showAddModal && (
        <AddMemoryModal
          onClose={() => setShowAddModal(false)}
          onSubmit={(data) => addMutation.mutate(data)}
          isLoading={addMutation.isPending}
        />
      )}
    </div>
  )
}
