import { useState, useCallback, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, Plus, Trash2, ChevronLeft, ChevronRight, RefreshCw, Merge, Database, HelpCircle, X } from 'lucide-react'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import { memoryApi } from '../api/memory'
import { MemoryTable } from '../components/memory/MemoryTable'
import { AddMemoryModal } from '../components/memory/AddMemoryModal'
import { CompressMemoryModal } from '../components/memory/CompressMemoryModal'
import {
  MemoryMigrationModal,
  INITIAL_MIGRATION_PROGRESS,
  type MigrationProgressState,
} from '../components/memory/MemoryMigrationModal'
import { useAuth } from '../hooks/useAuth'
import { useMemoryMigrationSocket } from '../hooks/useMemoryMigrationSocket'
import type { MemoryType, AddMemoryRequest } from '../types/memory'
import type { MemoryMigrationEvent } from '../types/memory'

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
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('browse')

  // Browse state
  const [page, setPage] = useState(0)
  const [typeFilter, setTypeFilter] = useState<MemoryType | ''>('')
  const [sessionFilter, setSessionFilter] = useState('')
  const [keyword, setKeyword] = useState('')
  const [showAddModal, setShowAddModal] = useState(false)
  const [showCompressModal, setShowCompressModal] = useState(false)
  const [showClearConfirm, setShowClearConfirm] = useState(false)
  const [showRecreateConfirm, setShowRecreateConfirm] = useState(false)
  const [showHelpModal, setShowHelpModal] = useState(false)
  const [showMigrationModal, setShowMigrationModal] = useState(false)
  const [migrationProgress, setMigrationProgress] = useState<MigrationProgressState>(INITIAL_MIGRATION_PROGRESS)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const { data: migrationStatusData } = useQuery({
    queryKey: ['migration-status', user?.userId],
    queryFn: () => memoryApi.migrationStatus(),
    enabled: !!user?.userId,
  })

  const migrationRestoredRef = useRef(false)
  useEffect(() => {
    if (!migrationStatusData || migrationRestoredRef.current) return
    if (migrationStatusData.status === 'IDLE') return
    migrationRestoredRef.current = true
    setMigrationProgress({
      status: migrationStatusData.status as MigrationProgressState['status'],
      totalSessions: migrationStatusData.totalSessions ?? 0,
      processedSessions: migrationStatusData.processedSessions ?? 0,
      writtenMemories: migrationStatusData.writtenMemories ?? 0,
      currentTask: migrationStatusData.currentTask ?? null,
      error: migrationStatusData.error ?? null,
      stepLog: Array.isArray(migrationStatusData.stepLog) ? migrationStatusData.stepLog : [],
    })
  }, [migrationStatusData])

  useMemoryMigrationSocket(user?.userId ?? null, useCallback((event: MemoryMigrationEvent) => {
    if (event.type === 'START') {
      migrationRestoredRef.current = true
      setMigrationProgress({
        status: 'RUNNING',
        totalSessions: 0,
        processedSessions: 0,
        writtenMemories: 0,
        stepLog: ['开始同步…'],
      })
    } else if (event.type === 'PROGRESS') {
      setMigrationProgress(prev => {
        const nextLog =
          event.stepDetail != null ? [...prev.stepLog, event.stepDetail] : prev.stepLog
        return {
          ...prev,
          status: 'RUNNING',
          totalSessions: event.totalSessions,
          processedSessions: event.processedSessions,
          writtenMemories: event.writtenMemories,
          currentTask: event.currentTaskDescription ?? prev.currentTask,
          stepLog: nextLog,
        }
      })
    } else if (event.type === 'DONE') {
      setMigrationProgress(prev => ({
        ...prev,
        status: 'DONE',
        totalSessions: event.totalSessions,
        processedSessions: event.totalSessions,
        writtenMemories: event.writtenMemories,
        currentTask: null,
        stepLog: [...prev.stepLog, '同步完成'],
      }))
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
    } else if (event.type === 'ERROR') {
      setMigrationProgress(prev => ({
        ...prev,
        status: 'ERROR',
        error: event.error ?? '同步过程中发生错误',
        stepLog: [...prev.stepLog, `错误: ${event.error ?? '未知'}`],
      }))
    } else if (event.type === 'CANCELLED') {
      setMigrationProgress(prev => ({
        ...prev,
        status: 'CANCELLED',
        totalSessions: event.totalSessions,
        processedSessions: event.processedSessions,
        writtenMemories: event.writtenMemories,
        currentTask: null,
        stepLog: [...prev.stepLog, '已中断同步'],
      }))
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
    }
  }, [queryClient]))

  // Search state
  const [searchQ, setSearchQ] = useState('')
  const [searchTrigger, setSearchTrigger] = useState('')

  // ── Queries ──────────────────────────────────────────────────────────────────
  const browseKey = ['memories', page, typeFilter, sessionFilter, keyword]
  const {
    data: browseData,
    isLoading: browseLoading,
    isFetching: browseFetching,
    refetch,
  } = useQuery({
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

  const { data: meta } = useQuery({
    queryKey: ['memory-meta'],
    queryFn: () => memoryApi.meta(),
  })

  const migrateMutation = useMutation({
    mutationFn: () => memoryApi.migrateToCurrentCollection(),
    onSuccess: () => {
      toast.success('已开始同步对话到记忆，请在弹出的窗口中查看进度')
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const cancelMigrationMutation = useMutation({
    mutationFn: () => memoryApi.migrationCancel(),
    onSuccess: (data) => toast.success(data?.message ?? '已请求中断'),
    onError: (e: Error) => toast.error(e.message),
  })

  const { data: searchResults, isLoading: searchLoading } = useQuery({
    queryKey: ['memory-search', searchTrigger],
    queryFn: () => memoryApi.search(searchTrigger, 20),
    enabled: !!searchTrigger,
  })

  // ── Mutations ────────────────────────────────────────────────────────────────
  const deleteMutation = useMutation({
    mutationFn: (id: string) => memoryApi.deleteById(id),
    onMutate: (id: string) => setDeletingId(id),
    onSuccess: (_data, id) => {
      // Optimistically更新当前内存列表和搜索结果，避免需要点击两次才能看到变化
      queryClient.setQueriesData(
        { queryKey: ['memories'] },
        (old: unknown) => {
          const page = old as { items: any[]; total: number; page: number; size: number } | undefined
          if (!page) return old
          return {
            ...page,
            items: page.items.filter(m => m.id !== id),
            total: Math.max(0, page.total - 1),
          }
        },
      )

      if (tab === 'search') {
        queryClient.setQueriesData(
          { queryKey: ['memory-search'] },
          (old: unknown) => {
            const items = old as any[] | undefined
            if (!Array.isArray(items)) return old
            return items.filter(m => m.id !== id)
          },
        )
      }

      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
      if (tab === 'search') queryClient.invalidateQueries({ queryKey: ['memory-search'] })

      toast.success('已删除')
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

  const clearAllMutation = useMutation({
    mutationFn: () => memoryApi.clearAll(),
    onSuccess: () => {
      toast.success('已清空全部记忆')
      setShowClearConfirm(false)
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const recreateCollectionMutation = useMutation({
    mutationFn: () => memoryApi.recreateCollection(),
    onSuccess: (data) => {
      toast.success(data?.message ?? '集合已重建，请使用「同步对话到记忆」重新写入')
      setShowRecreateConfirm(false)
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
      queryClient.invalidateQueries({ queryKey: ['memory-meta'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const compressExecuteMutation = useMutation({
    mutationFn: (body: { delete_ids: string[]; new_memories: { content: string; memory_type: string; importance: number }[] }) =>
      memoryApi.compressExecute(body),
    onSuccess: () => {
      toast.success('压缩已完成')
      queryClient.invalidateQueries({ queryKey: ['memories'] })
      queryClient.invalidateQueries({ queryKey: ['memory-count'] })
      if (tab === 'search') queryClient.invalidateQueries({ queryKey: ['memory-search'] })
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
            {meta?.collectionName && (
              <span className="ml-2 text-[11px] text-white/25">
                （Collection: <span className="font-mono">{meta.collectionName}</span>）
              </span>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowHelpModal(true)}
            className="p-2 text-white/40 hover:text-white/80 hover:bg-white/5 rounded-lg transition-colors"
            title="功能说明"
          >
            <HelpCircle className="w-4 h-4" />
          </button>
          <button
            onClick={() => refetch()}
            disabled={browseFetching}
            className="p-2 text-white/30 hover:text-white/70 hover:bg-white/5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <RefreshCw className={clsx('w-4 h-4', browseFetching && 'animate-spin')} />
          </button>
          <button
            onClick={() => {
              migrateMutation.mutate()
              setShowMigrationModal(true)
            }}
            disabled={migrateMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 text-xs text-white/80 hover:text-white hover:bg-white/10 rounded-lg font-medium transition-colors border border-white/15 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            同步对话到记忆
          </button>
          <button
            onClick={() => setShowRecreateConfirm(true)}
            disabled={recreateCollectionMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 text-xs text-amber-400/90 hover:text-amber-300 hover:bg-amber-500/10 rounded-lg font-medium transition-colors border border-amber-500/30 disabled:opacity-50 disabled:cursor-not-allowed"
            title="删除当前集合并按新结构（含 user_id）重建，重建后需点「同步对话到记忆」重新写入"
          >
            <Database className="w-4 h-4" /> 重建集合
          </button>
          <button
            onClick={() => setShowClearConfirm(true)}
            disabled={clearAllMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 text-white/80 hover:text-white hover:bg-white/10 rounded-lg text-sm font-medium transition-colors border border-white/10 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Trash2 className="w-4 h-4" /> 清空记忆
          </button>
          <button
            onClick={() => setShowCompressModal(true)}
            className="flex items-center gap-2 px-4 py-2 text-white/80 hover:text-white hover:bg-white/10 rounded-lg text-sm font-medium transition-colors border border-white/10"
          >
            <Merge className="w-4 h-4" /> 压缩记忆
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

      {showCompressModal && (
        <CompressMemoryModal
          onClose={() => setShowCompressModal(false)}
          onPrepare={() => memoryApi.compressPrepare()}
          onExecute={(body) => compressExecuteMutation.mutateAsync(body)}
          isExecuting={compressExecuteMutation.isPending}
        />
      )}

      {showMigrationModal && (
        <MemoryMigrationModal
          open={showMigrationModal}
          onClose={() => setShowMigrationModal(false)}
          progress={migrationProgress}
          onCancel={() => cancelMigrationMutation.mutate()}
          isCancelling={cancelMigrationMutation.isPending}
        />
      )}

      {showClearConfirm && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-[#111111] border border-white/10 rounded-2xl w-full max-w-sm shadow-2xl p-5">
            <h3 className="text-sm font-semibold text-white mb-2">清空记忆</h3>
            <p className="text-xs text-white/60 mb-4">
              确定要清空当前账号下的全部长期记忆吗？此操作不可恢复。
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowClearConfirm(false)}
                disabled={clearAllMutation.isPending}
                className="px-3 py-1.5 text-xs text-white/70 hover:text-white rounded-lg border border-white/20 hover:bg-white/10 transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => clearAllMutation.mutate()}
                disabled={clearAllMutation.isPending}
                className="px-3 py-1.5 text-xs text-white bg-red-600 hover:bg-red-500 rounded-lg font-medium transition-colors disabled:opacity-50"
              >
                {clearAllMutation.isPending ? '清空中…' : '确定清空'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showHelpModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-[#111111] border border-white/10 rounded-2xl w-full max-w-md shadow-2xl overflow-hidden">
            <div className="px-5 py-3 border-b border-white/10 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-white">长期记忆库 · 功能说明</h2>
              <button
                type="button"
                onClick={() => setShowHelpModal(false)}
                className="text-white/40 hover:text-white/80 rounded-lg p-1 hover:bg-white/10 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="px-5 py-4 space-y-4 max-h-[70vh] overflow-y-auto">
              <div className="space-y-3 text-xs text-white/80">
                <div>
                  <span className="font-medium text-white/90">刷新</span>
                  <p className="mt-0.5 text-white/60">重新拉取当前筛选条件下的记忆列表与数量。</p>
                </div>
                <div>
                  <span className="font-medium text-white/90">同步对话到记忆</span>
                  <p className="mt-0.5 text-white/60">将当前账号下的历史会话内容重新向量化并写入当前集合，用于迁移或补全记忆。</p>
                </div>
                <div>
                  <span className="font-medium text-amber-400/90">重建集合</span>
                  <p className="mt-0.5 text-white/60">删除当前向量集合并按新结构（含 user_id）重新创建。适用于旧版集合升级；重建后需再点「同步对话到记忆」重新写入数据。</p>
                </div>
                <div>
                  <span className="font-medium text-white/90">清空记忆</span>
                  <p className="mt-0.5 text-white/60">删除当前账号在本集合内的全部长期记忆，不可恢复。</p>
                </div>
                <div>
                  <span className="font-medium text-white/90">压缩记忆</span>
                  <p className="mt-0.5 text-white/60">由 AI 合并、去重相似记忆并生成更精简的条目，确认后替换原有记录，减少冗余、保留要点。</p>
                </div>
                <div>
                  <span className="font-medium text-white/90">添加记忆</span>
                  <p className="mt-0.5 text-white/60">手动添加一条记忆并向量化写入当前集合，可指定类型（情节/语义/程序）与重要性。</p>
                </div>
              </div>
            </div>
            <div className="px-5 py-3 border-t border-white/10 flex justify-end">
              <button
                type="button"
                onClick={() => setShowHelpModal(false)}
                className="px-3 py-1.5 text-xs text-white/80 hover:text-white rounded-lg border border-white/20 hover:bg-white/10 transition-colors"
              >
                知道了
              </button>
            </div>
          </div>
        </div>
      )}

      {showRecreateConfirm && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-[#111111] border border-amber-500/20 rounded-2xl w-full max-w-sm shadow-2xl p-5">
            <h3 className="text-sm font-semibold text-amber-400/90 mb-2">重建集合</h3>
            <p className="text-xs text-white/60 mb-4">
              将删除当前集合并按新结构（含 user_id）重新创建，集合内数据会清空。重建后请点击「同步对话到记忆」重新写入。
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowRecreateConfirm(false)}
                disabled={recreateCollectionMutation.isPending}
                className="px-3 py-1.5 text-xs text-white/70 hover:text-white rounded-lg border border-white/20 hover:bg-white/10 transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => recreateCollectionMutation.mutate()}
                disabled={recreateCollectionMutation.isPending}
                className="px-3 py-1.5 text-xs text-white bg-amber-600 hover:bg-amber-500 rounded-lg font-medium transition-colors disabled:opacity-50"
              >
                {recreateCollectionMutation.isPending ? '重建中…' : '确定重建'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
