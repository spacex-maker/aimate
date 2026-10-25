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
import { AdminPager } from '../../ui/AdminPager'

const PAGE_SIZE_OPTIONS = [
  { value: '20', label: '每页 20' },
  { value: '50', label: '每页 50' },
  { value: '100', label: '每页 100' },
]

const ENABLED_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'true', label: '启用' },
  { value: 'false', label: '停用' },
]

const CONTENT_HINT =
  '建议结构：概述与适用范围 → 标准/规范（可编号）→ 操作清单 → 验收标准 → 反模式与禁忌 → 示例。正文应专业、可执行，避免一句话口号。'

function flattenTree(nodes: KbCategoryNode[], depth = 0): (KbCategoryNode & { depth: number })[] {
  const out: (KbCategoryNode & { depth: number })[] = []
  for (const n of nodes) {
    out.push({ ...n, depth })
    if (n.children?.length) out.push(...flattenTree(n.children, depth + 1))
  }
  return out
}

function contentStats(text: string) {
  const trimmed = text.trim()
  const chars = trimmed.length
  const lines = trimmed ? trimmed.split(/\r?\n/).length : 0
  return { chars, lines }
}

export function AdminKnowledgePage() {
  const qc = useQueryClient()
  const [selectedCatId, setSelectedCatId] = useState<number | null>(null)
  const [q, setQ] = useState('')
  const [tag, setTag] = useState('')
  const [code, setCode] = useState('')
  const [enabledFilter, setEnabledFilter] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
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
    queryKey: ['admin-kb-bases', selectedCatId, q, tag, code, enabledFilter, page, size],
    queryFn: () =>
      adminKnowledgeApi.pageBases({
        categoryId: selectedCatId,
        q: q.trim() || undefined,
        tag: tag.trim() || undefined,
        code: code.trim() || undefined,
        enabled: enabledFilter === '' ? undefined : enabledFilter === 'true',
        page,
        size,
      }),
  })

  const filtersActive = !!(q.trim() || tag.trim() || code.trim() || enabledFilter)

  const resetFilters = () => {
    setQ('')
    setTag('')
    setCode('')
    setEnabledFilter('')
    setPage(0)
  }

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
      const body = (editing.content || '').trim()
      if (body.length > 0 && body.length < 80) {
        throw new Error('知识正文过短：请补充为可执行的专业规范（建议 ≥80 字）')
      }
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
      content: `# 标题

## 1. 概述与适用范围
说明本知识库覆盖的场景、角色与边界（非目标）。

## 2. 标准与规范
1. …
2. …

## 3. 操作清单
- [ ] …

## 4. 验收标准
- …

## 5. 反模式与禁忌
- …

## 6. 示例
\`\`\`
…
\`\`\`
`,
      tags: '',
      sortOrder: 100,
      enabled: true,
    })
  }

  const stats = contentStats(editing?.content || '')

  return (
    <div>
      <h1 className="admin-page__title">官方知识库</h1>
      <p className="admin-page__lead">
        按行业/领域分层管理原子知识库；招聘官方 Agent 时可绑定整套领域知识。列表服务端分页，支持关键词与高级筛选。
      </p>

      <div className="admin-kb-layout">
        <aside className="admin-kb-panel">
          <div className="mb-2 flex items-center justify-between gap-2 flex-shrink-0">
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
              'mb-1 w-full rounded-md px-2 py-1.5 text-left text-xs flex-shrink-0',
              selectedCatId == null ? 'bg-white/10 text-white' : 'text-white/50 hover:bg-white/5',
            )}
            onClick={() => {
              setSelectedCatId(null)
              setPage(0)
            }}
          >
            全部知识库
          </button>
          <ul className="admin-kb-panel__scroll space-y-0.5">
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
            <div className="mt-3 space-y-2 rounded-lg border border-white/10 bg-black/30 p-2 flex-shrink-0">
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

        <section className="admin-kb-panel">
          <div className="admin-kb-filters">
            <input
              className="admin-input min-w-[160px] flex-1"
              placeholder="关键词：名称 / 摘要 / 标签 / publicId / code"
              value={q}
              onChange={(e) => {
                setQ(e.target.value)
                setPage(0)
              }}
            />
            <input
              className="admin-input w-[120px]"
              placeholder="标签含"
              value={tag}
              onChange={(e) => {
                setTag(e.target.value)
                setPage(0)
              }}
            />
            <input
              className="admin-input w-[120px]"
              placeholder="code 含"
              value={code}
              onChange={(e) => {
                setCode(e.target.value)
                setPage(0)
              }}
            />
            <AppSelect
              className="admin-kb-filter-select w-[120px]"
              ariaLabel="启用状态"
              value={enabledFilter}
              options={ENABLED_OPTIONS}
              onChange={(v) => {
                setEnabledFilter(v)
                setPage(0)
              }}
            />
            <AppSelect
              className="admin-kb-filter-select w-[110px]"
              ariaLabel="每页条数"
              value={String(size)}
              options={PAGE_SIZE_OPTIONS}
              onChange={(v) => {
                setSize(Number(v) || 20)
                setPage(0)
              }}
            />
            <button
              type="button"
              className="rounded-lg border border-white/15 px-3 py-2 text-xs text-white/55 disabled:opacity-30"
              disabled={!filtersActive}
              onClick={resetFilters}
            >
              清空筛选
            </button>
            <button
              type="button"
              className="rounded-lg bg-blue-600/80 px-3 py-2 text-xs hover:bg-blue-600 ml-auto"
              onClick={openCreateKb}
              disabled={!flatCats.length}
            >
              新建知识库
            </button>
          </div>

          <div className="admin-kb-table-scroll">
            <table className="w-full text-left text-xs text-white/70">
              <thead className="text-white/40">
                <tr>
                  <th className="px-3 py-2 font-medium">publicId</th>
                  <th className="px-3 py-2 font-medium">名称</th>
                  <th className="px-3 py-2 font-medium">分类</th>
                  <th className="px-3 py-2 font-medium">标签</th>
                  <th className="px-3 py-2 font-medium">正文</th>
                  <th className="px-3 py-2 font-medium">状态</th>
                  <th className="px-3 py-2 font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {listQ.isLoading && (
                  <tr>
                    <td colSpan={7} className="px-3 py-8 text-center text-white/35">
                      加载中…
                    </td>
                  </tr>
                )}
                {!listQ.isLoading &&
                  (listQ.data?.items ?? []).map((row) => {
                    const len =
                      row.contentLength ??
                      (row.content ? row.content.trim().length : 0)
                    const thin = len > 0 && len < 80
                    return (
                      <tr key={row.id} className="border-t border-white/5">
                        <td className="px-3 py-2 font-mono text-[11px] text-white/45 whitespace-nowrap">
                          {row.publicId}
                        </td>
                        <td className="px-3 py-2 min-w-[160px]">
                          <div className="font-medium text-white/85">{row.name}</div>
                          {row.summary && (
                            <div className="mt-0.5 line-clamp-2 text-[11px] text-white/35 max-w-[280px]">
                              {row.summary}
                            </div>
                          )}
                        </td>
                        <td className="px-3 py-2 text-white/45 whitespace-nowrap">
                          {row.categoryName || row.categoryId}
                        </td>
                        <td className="px-3 py-2 text-white/40 max-w-[140px] truncate" title={row.tags || ''}>
                          {row.tags || '—'}
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <span className={clsx(thin ? 'text-amber-300/90' : 'text-white/40')}>
                            {len ? `${len} 字` : '空'}
                            {thin ? ' · 偏短' : ''}
                          </span>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">{row.enabled ? '启用' : '停用'}</td>
                        <td className="px-3 py-2 whitespace-nowrap">
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
                    )
                  })}
                {!listQ.isLoading && !(listQ.data?.items?.length) && (
                  <tr>
                    <td colSpan={7} className="px-3 py-8 text-center text-white/35">
                      暂无知识库，可调整筛选或在左侧选分类后新建
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <AdminPager
            page={page}
            size={size}
            total={listQ.data?.total ?? 0}
            loading={listQ.isFetching}
            onPageChange={setPage}
            label="条知识库"
          />
        </section>
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="admin-kb-modal rounded-xl border border-white/15 bg-[#141414] p-4 shadow-2xl">
            <h3 className="mb-3 text-sm font-semibold text-white flex-shrink-0">
              {creating ? '新建原子知识库' : `编辑 · ${editing.publicId}`}
            </h3>
            <div className="admin-kb-modal__body space-y-3">
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
              <div className="grid gap-3 sm:grid-cols-2">
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
              </div>
              <label className="block text-[11px] text-white/45">
                摘要（一句话定位，详细规范写在正文）
                <input
                  className="admin-input mt-1 w-full"
                  value={editing.summary || ''}
                  onChange={(e) => setEditing({ ...editing, summary: e.target.value })}
                />
              </label>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block text-[11px] text-white/45">
                  标签（逗号分隔）
                  <input
                    className="admin-input mt-1 w-full"
                    value={editing.tags || ''}
                    onChange={(e) => setEditing({ ...editing, tags: e.target.value })}
                  />
                </label>
                <label className="block text-[11px] text-white/45">
                  排序
                  <input
                    type="number"
                    className="admin-input mt-1 w-full"
                    value={editing.sortOrder ?? 100}
                    onChange={(e) =>
                      setEditing({ ...editing, sortOrder: Number(e.target.value) || 100 })
                    }
                  />
                </label>
              </div>
              <label className="block text-[11px] text-white/45">
                知识正文（Markdown）
                <textarea
                  className="admin-input mt-1 min-h-[320px] w-full font-mono text-[12px] leading-relaxed"
                  value={editing.content || ''}
                  onChange={(e) => setEditing({ ...editing, content: e.target.value })}
                  spellCheck={false}
                />
                <p className="admin-kb-content-hint">{CONTENT_HINT}</p>
                <div className="admin-kb-content-meta">
                  <span>
                    {stats.chars} 字 · {stats.lines} 行
                    {stats.chars > 0 && stats.chars < 80 ? ' · 偏短，建议补充' : ''}
                  </span>
                  <span>绑定商品数：{editing.linkedCatalogCount ?? 0}</span>
                </div>
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
            <div className="mt-4 flex justify-end gap-2 flex-shrink-0">
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
