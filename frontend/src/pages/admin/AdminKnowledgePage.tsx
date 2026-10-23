import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import {
  adminKnowledgeApi,
  type KbCategoryNode,
  type KnowledgeBaseDetail,
} from '../../api/adminKnowledge'
import { AppSelect } from '../../ui/AppSelect'

function flattenTree(nodes: KbCategoryNode[], depth = 0): (KbCategoryNode & { depth: number })[] {
  const out: (KbCategoryNode & { depth: number })[] = []
  for (const n of nodes) {
    out.push({ ...n, depth })
    if (n.children?.length) out.push(...flattenTree(n.children, depth + 1))
  }
  return out
}

export function AdminKnowledgePage() {
  const qc = useQueryClient()
  const [selectedCatId, setSelectedCatId] = useState<number | null>(null)
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<KnowledgeBaseDetail | null>(null)
  const [creating, setCreating] = useState(false)
  const [catForm, setCatForm] = useState<{
    open: boolean
    parentId: number | null
    id?: number
    code: string
    name: string
    description: string
  } | null>(null)

  const treeQ = useQuery({
    queryKey: ['admin-kb-tree'],
    queryFn: () => adminKnowledgeApi.categoryTree(false),
  })
  const flatCats = useMemo(() => flattenTree(treeQ.data ?? []), [treeQ.data])

  const listQ = useQuery({
    queryKey: ['admin-kb-bases', selectedCatId, q, page],
    queryFn: () =>
      adminKnowledgeApi.pageBases({
        categoryId: selectedCatId,
        q: q.trim() || undefined,
        page,
        size: 20,
      }),
  })

  const saveCatMut = useMutation({
    mutationFn: () => {
      if (!catForm) throw new Error('表单为空')
      return adminKnowledgeApi.saveCategory(
        {
          parentId: catForm.parentId,
          code: catForm.code.trim(),
          name: catForm.name.trim(),
          description: catForm.description.trim() || undefined,
        },
        catForm.id,
      )
    },
    onSuccess: () => {
      toast.success('分类已保存')
      setCatForm(null)
      qc.invalidateQueries({ queryKey: ['admin-kb-tree'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const saveKbMut = useMutation({
    mutationFn: async () => {
      if (!editing) throw new Error('无编辑对象')
      if (!editing.categoryId || !editing.name?.trim()) throw new Error('分类与名称必填')
      return adminKnowledgeApi.saveBase(
        {
          categoryId: editing.categoryId,
          code: editing.code || undefined,
          name: editing.name.trim(),
          summary: editing.summary || undefined,
          content: editing.content || undefined,
          tags: editing.tags || undefined,
          sortOrder: editing.sortOrder,
          enabled: editing.enabled,
        },
        creating ? undefined : editing.id,
      )
    },
    onSuccess: () => {
      toast.success(creating ? '知识库已创建' : '知识库已更新')
      setEditing(null)
      setCreating(false)
      qc.invalidateQueries({ queryKey: ['admin-kb-bases'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openCreateKb = () => {
    setCreating(true)
    setEditing({
      id: 0,
      publicId: '(保存后生成)',
      categoryId: selectedCatId ?? flatCats[0]?.id ?? 0,
      name: '',
      summary: '',
      content: '',
      tags: '',
      sortOrder: 100,
      enabled: true,
    })
  }

  return (
    <div>
      <h1 className="admin-page__title">官方知识库</h1>
      <p className="admin-page__lead">
        按行业/领域分层管理原子知识库；招聘官方 Agent 时可绑定整套领域知识，而不仅是提示词。
      </p>

      <div className="mt-4 grid gap-4 lg:grid-cols-[260px_1fr]">
        {/* 分类树 */}
        <aside className="rounded-xl border border-white/10 bg-white/[0.02] p-3">
          <div className="mb-2 flex items-center justify-between gap-2">
            <h2 className="text-xs font-medium text-white/50">行业 / 领域分类</h2>
            <button
              type="button"
              className="text-[11px] text-blue-300/80 hover:text-blue-200"
              onClick={() =>
                setCatForm({
                  open: true,
                  parentId: null,
                  code: '',
                  name: '',
                  description: '',
                })
              }
            >
              + 根分类
            </button>
          </div>
          <button
            type="button"
            className={clsx(
              'mb-1 w-full rounded-md px-2 py-1.5 text-left text-xs',
              selectedCatId == null ? 'bg-white/10 text-white' : 'text-white/50 hover:bg-white/5',
            )}
            onClick={() => {
              setSelectedCatId(null)
              setPage(0)
            }}
          >
            全部知识库
          </button>
          <ul className="max-h-[60vh] space-y-0.5 overflow-auto">
            {flatCats.map((c) => (
              <li key={c.id} style={{ paddingLeft: c.depth * 12 }}>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    className={clsx(
                      'min-w-0 flex-1 truncate rounded-md px-2 py-1.5 text-left text-xs',
                      selectedCatId === c.id
                        ? 'bg-white/10 text-white'
                        : 'text-white/60 hover:bg-white/5',
                      !c.enabled && 'opacity-40',
                    )}
                    onClick={() => {
                      setSelectedCatId(c.id)
                      setPage(0)
                    }}
                    title={c.code}
                  >
                    {c.name}
                  </button>
                  <button
                    type="button"
                    className="flex-shrink-0 px-1 text-[10px] text-white/30 hover:text-white/60"
                    title="添加子分类"
                    onClick={() =>
                      setCatForm({
                        open: true,
                        parentId: c.id,
                        code: '',
                        name: '',
                        description: '',
                      })
                    }
                  >
                    +
                  </button>
                </div>
              </li>
            ))}
          </ul>
          {catForm && (
            <div className="mt-3 space-y-2 rounded-lg border border-white/10 bg-black/30 p-2">
              <p className="text-[11px] text-white/45">
                {catForm.id ? '编辑分类' : catForm.parentId ? '新建子分类' : '新建根分类'}
              </p>
              <input
                className="admin-input w-full"
                placeholder="code（如 TECH）"
                value={catForm.code}
                onChange={(e) => setCatForm({ ...catForm, code: e.target.value })}
              />
              <input
                className="admin-input w-full"
                placeholder="名称"
                value={catForm.name}
                onChange={(e) => setCatForm({ ...catForm, name: e.target.value })}
              />
              <input
                className="admin-input w-full"
                placeholder="简介（可选）"
                value={catForm.description}
                onChange={(e) => setCatForm({ ...catForm, description: e.target.value })}
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  className="rounded-md bg-blue-600/80 px-2 py-1 text-[11px] hover:bg-blue-600"
                  disabled={saveCatMut.isPending}
                  onClick={() => saveCatMut.mutate()}
                >
                  保存
                </button>
                <button
                  type="button"
                  className="rounded-md px-2 py-1 text-[11px] text-white/45 hover:text-white"
                  onClick={() => setCatForm(null)}
                >
                  取消
                </button>
              </div>
            </div>
          )}
        </aside>

        {/* 知识列表 */}
        <section className="rounded-xl border border-white/10 bg-white/[0.02] p-3">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <input
              className="admin-input min-w-[160px] flex-1"
              placeholder="搜索名称 / 标签 / publicId"
              value={q}
              onChange={(e) => {
                setQ(e.target.value)
                setPage(0)
              }}
            />
            <button
              type="button"
              className="rounded-lg bg-blue-600/80 px-3 py-2 text-xs hover:bg-blue-600"
              onClick={openCreateKb}
              disabled={!flatCats.length}
            >
              新建知识库
            </button>
          </div>

          <div className="overflow-x-auto rounded-lg border border-white/10">
            <table className="w-full text-left text-xs text-white/70">
              <thead className="bg-white/[0.03] text-white/40">
                <tr>
                  <th className="px-3 py-2 font-medium">publicId</th>
                  <th className="px-3 py-2 font-medium">名称</th>
                  <th className="px-3 py-2 font-medium">分类</th>
                  <th className="px-3 py-2 font-medium">标签</th>
                  <th className="px-3 py-2 font-medium">状态</th>
                  <th className="px-3 py-2 font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {(listQ.data?.items ?? []).map((row) => (
                  <tr key={row.id} className="border-t border-white/5">
                    <td className="px-3 py-2 font-mono text-[11px] text-white/45">{row.publicId}</td>
                    <td className="px-3 py-2">
                      <div className="font-medium text-white/85">{row.name}</div>
                      {row.summary && (
                        <div className="mt-0.5 line-clamp-1 text-[11px] text-white/35">{row.summary}</div>
                      )}
                    </td>
                    <td className="px-3 py-2 text-white/45">{row.categoryName || row.categoryId}</td>
                    <td className="px-3 py-2 text-white/40">{row.tags || '—'}</td>
                    <td className="px-3 py-2">{row.enabled ? '启用' : '停用'}</td>
                    <td className="px-3 py-2">
                      <button
                        type="button"
                        className="text-blue-300/80 hover:text-blue-200"
                        onClick={async () => {
                          try {
                            const full = await adminKnowledgeApi.getBase(row.id)
                            setCreating(false)
                            setEditing(full)
                          } catch (e) {
                            toast.error((e as Error).message)
                          }
                        }}
                      >
                        编辑
                      </button>
                    </td>
                  </tr>
                ))}
                {!listQ.isLoading && !(listQ.data?.items?.length) && (
                  <tr>
                    <td colSpan={6} className="px-3 py-8 text-center text-white/35">
                      暂无知识库，可在左侧选分类后新建
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-3 flex items-center justify-between text-[11px] text-white/40">
            <span>
              共 {listQ.data?.total ?? 0} 条 · 第 {(listQ.data?.page ?? 0) + 1}/
              {Math.max(1, listQ.data?.totalPages ?? 1)} 页
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                className="rounded border border-white/10 px-2 py-1 disabled:opacity-30"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                上一页
              </button>
              <button
                type="button"
                className="rounded border border-white/10 px-2 py-1 disabled:opacity-30"
                disabled={page + 1 >= (listQ.data?.totalPages ?? 1)}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        </section>
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-white/15 bg-[#141414] p-4 shadow-2xl">
            <h3 className="mb-3 text-sm font-semibold text-white">
              {creating ? '新建原子知识库' : `编辑 · ${editing.publicId}`}
            </h3>
            <div className="space-y-3">
              <label className="block text-[11px] text-white/45">
                挂载分类
                <AppSelect
                  className="mt-1"
                  value={String(editing.categoryId || '')}
                  options={flatCats.map((c) => ({
                    value: String(c.id),
                    label: `${'—'.repeat(c.depth)} ${c.name}`,
                  }))}
                  onChange={(v) => setEditing({ ...editing, categoryId: Number(v) })}
                />
              </label>
              <label className="block text-[11px] text-white/45">
                名称
                <input
                  className="admin-input mt-1 w-full"
                  value={editing.name}
                  onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                />
              </label>
              <label className="block text-[11px] text-white/45">
                code（可选，唯一）
                <input
                  className="admin-input mt-1 w-full"
                  value={editing.code || ''}
                  onChange={(e) => setEditing({ ...editing, code: e.target.value })}
                />
              </label>
              <label className="block text-[11px] text-white/45">
                摘要
                <input
                  className="admin-input mt-1 w-full"
                  value={editing.summary || ''}
                  onChange={(e) => setEditing({ ...editing, summary: e.target.value })}
                />
              </label>
              <label className="block text-[11px] text-white/45">
                标签（逗号分隔）
                <input
                  className="admin-input mt-1 w-full"
                  value={editing.tags || ''}
                  onChange={(e) => setEditing({ ...editing, tags: e.target.value })}
                />
              </label>
              <label className="block text-[11px] text-white/45">
                知识正文
                <textarea
                  className="admin-input mt-1 min-h-[200px] w-full font-mono text-[12px]"
                  value={editing.content || ''}
                  onChange={(e) => setEditing({ ...editing, content: e.target.value })}
                />
              </label>
              <label className="flex items-center gap-2 text-xs text-white/60">
                <input
                  type="checkbox"
                  checked={!!editing.enabled}
                  onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
                />
                启用
              </label>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                className="rounded-lg border border-white/15 px-3 py-1.5 text-xs text-white/60"
                onClick={() => {
                  setEditing(null)
                  setCreating(false)
                }}
              >
                取消
              </button>
              <button
                type="button"
                className="rounded-lg bg-blue-600/80 px-3 py-1.5 text-xs hover:bg-blue-600"
                disabled={saveKbMut.isPending}
                onClick={() => saveKbMut.mutate()}
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
