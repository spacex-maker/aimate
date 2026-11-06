import { FormEvent, useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Plus, Play, History } from 'lucide-react'
import toast from 'react-hot-toast'
import { adminApi, type SystemAgentDto, type SystemAgentSaveBody } from '../../api/admin'
import type { KbCuratorConfig } from '../../types/admin'
import { adminKnowledgeApi, type KbCategoryNode } from '../../api/adminKnowledge'
import { AdminToggle } from '../../ui/AdminToggle'
import { AdminQueryBar } from '../../ui/AdminQueryBar'
import { AdminPager } from '../../ui/AdminPager'
import { AppSelect } from '../../ui/AppSelect'
import { AdminDialog } from './AdminDialog'

const defaultConfig = (): KbCuratorConfig => ({
  categoryIds: [],
  maxBasesPerRun: 5,
  thinContentChars: 800,
  staleDays: 7,
  useTavily: true,
  tavilyMaxResults: 5,
  tavilySearchDepth: 'basic',
  researchQueriesPerBase: 2,
  temperature: 0.35,
  maxTokens: 8192,
  styleGuide: '',
  updateSummaryAndTags: true,
})

type FormState = {
  code: string
  name: string
  description: string
  enabled: boolean
  scheduleHour: number
  scheduleMinute: number
  systemModelId: number | null
  config: KbCuratorConfig
}

function emptyForm(): FormState {
  return {
    code: 'KB_CURATOR_DAILY',
    name: '官方知识库策展',
    description: '每日固定时间联网检索并完善薄弱/过期的官方知识库条目',
    enabled: true,
    scheduleHour: 3,
    scheduleMinute: 0,
    systemModelId: null,
    config: defaultConfig(),
  }
}

function flattenCategories(nodes: KbCategoryNode[], depth = 0): { id: number; label: string; depth: number }[] {
  const out: { id: number; label: string; depth: number }[] = []
  for (const n of nodes) {
    out.push({ id: n.id, label: n.name, depth })
    if (n.children?.length) out.push(...flattenCategories(n.children, depth + 1))
  }
  return out
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function agentToForm(a: SystemAgentDto): FormState {
  return {
    code: a.code,
    name: a.name,
    description: a.description ?? '',
    enabled: a.enabled,
    scheduleHour: a.scheduleHour,
    scheduleMinute: a.scheduleMinute,
    systemModelId: a.systemModelId,
    config: { ...defaultConfig(), ...(a.config ?? {}) },
  }
}

export function AdminSystemAgentsPanel() {
  const qc = useQueryClient()
  const [qInput, setQInput] = useState('')
  const [q, setQ] = useState('')
  const [enabledFilter, setEnabledFilter] = useState('')
  const [modal, setModal] = useState<'create' | 'edit' | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [runsFor, setRunsFor] = useState<SystemAgentDto | null>(null)
  const [runsPage, setRunsPage] = useState(0)

  const listQ = useQuery({
    queryKey: ['admin-system-agents', q, enabledFilter],
    queryFn: () =>
      adminApi.listSystemAgents({
        q: q || undefined,
        enabled: enabledFilter === '' ? undefined : enabledFilter === 'true',
      }),
  })

  const modelsQ = useQuery({
    queryKey: ['admin-system-models'],
    queryFn: () => adminApi.listAllSystemModels(),
  })

  const catsQ = useQuery({
    queryKey: ['admin-kb-category-tree'],
    queryFn: () => adminKnowledgeApi.categoryTree(true),
  })

  const runsQ = useQuery({
    queryKey: ['admin-system-agent-runs', runsFor?.id, runsPage],
    queryFn: () => adminApi.listSystemAgentRuns(runsFor!.id, { page: runsPage, size: 20 }),
    enabled: runsFor != null,
  })

  const catOptions = useMemo(() => flattenCategories(catsQ.data ?? []), [catsQ.data])
  const modelOptions = useMemo(
    () =>
      (modelsQ.data ?? [])
        .filter((m) => m.enabled !== false)
        .map((m) => ({ value: String(m.id), label: `${m.displayName} (${m.provider}/${m.modelId})` })),
    [modelsQ.data],
  )

  const saveMut = useMutation({
    mutationFn: async () => {
      const body: SystemAgentSaveBody = {
        type: 'KB_CURATOR',
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        enabled: form.enabled,
        scheduleHour: form.scheduleHour,
        scheduleMinute: form.scheduleMinute,
        systemModelId: form.systemModelId,
        config: form.config,
      }
      if (modal === 'edit' && editingId != null) {
        return adminApi.updateSystemAgent(editingId, body)
      }
      return adminApi.createSystemAgent(body)
    },
    onSuccess: () => {
      toast.success(modal === 'edit' ? '已保存' : '已创建系统 Agent')
      setModal(null)
      setEditingId(null)
      qc.invalidateQueries({ queryKey: ['admin-system-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const toggleMut = useMutation({
    mutationFn: (row: SystemAgentDto) =>
      adminApi.updateSystemAgent(row.id, {
        code: row.code,
        name: row.name,
        description: row.description ?? undefined,
        enabled: !row.enabled,
        scheduleHour: row.scheduleHour,
        scheduleMinute: row.scheduleMinute,
        systemModelId: row.systemModelId,
        config: row.config,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-system-agents'] }),
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => adminApi.deleteSystemAgent(id),
    onSuccess: () => {
      toast.success('已删除')
      qc.invalidateQueries({ queryKey: ['admin-system-agents'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const runMut = useMutation({
    mutationFn: (id: number) => adminApi.runSystemAgent(id),
    onSuccess: (r) => {
      toast.success(r.message || '已触发执行')
      qc.invalidateQueries({ queryKey: ['admin-system-agents'] })
      if (runsFor) qc.invalidateQueries({ queryKey: ['admin-system-agent-runs', runsFor.id] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  useEffect(() => {
    if (!runsFor) return
    const t = window.setInterval(() => {
      qc.invalidateQueries({ queryKey: ['admin-system-agent-runs', runsFor.id] })
      qc.invalidateQueries({ queryKey: ['admin-system-agents'] })
    }, 5000)
    return () => window.clearInterval(t)
  }, [runsFor, qc])

  function openCreate() {
    setForm(emptyForm())
    setEditingId(null)
    setModal('create')
  }

  function openEdit(row: SystemAgentDto) {
    setForm(agentToForm(row))
    setEditingId(row.id)
    setModal('edit')
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form.code.trim() || !form.name.trim()) {
      toast.error('请填写编码与名称')
      return
    }
    if (form.systemModelId == null) {
      toast.error('请选择策展用系统模型')
      return
    }
    saveMut.mutate()
  }

  function setCfg<K extends keyof KbCuratorConfig>(key: K, value: KbCuratorConfig[K]) {
    setForm((f) => ({ ...f, config: { ...f.config, [key]: value } }))
  }

  function toggleCategory(id: number) {
    setForm((f) => {
      const set = new Set(f.config.categoryIds)
      if (set.has(id)) set.delete(id)
      else set.add(id)
      return { ...f, config: { ...f.config, categoryIds: [...set] } }
    })
  }

  const agents = listQ.data ?? []
  const tavilyOk = agents.some((a) => a.tavilyConfigured)
  const scheduleValue = `${pad2(form.scheduleHour)}:${pad2(form.scheduleMinute)}`

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-medium text-white">系统 Agent</h3>
          <p className="text-xs text-white/40 mt-0.5 max-w-2xl">
            定时策展官方知识库：挑选内容偏薄或久未更新的条目，可选 Tavily 联网检索后由系统模型重写正文，并更新摘要与标签。
          </p>
          <p className="mt-1 text-[11px] text-white/35">
            Tavily：{tavilyOk ? (
              <span className="text-emerald-300/90">已配置</span>
            ) : (
              <>
                <span className="text-amber-300/80">未配置</span>
                {' · '}
                <Link to="/admin/system" className="text-blue-300 hover:underline">
                  去系统配置填写
                </Link>
              </>
            )}
          </p>
        </div>
        <button type="button" className="admin-btn admin-btn--sm" onClick={openCreate}>
          <Plus size={14} aria-hidden />
          新建系统 Agent
        </button>
      </div>

      <AdminQueryBar
        fields={[
          {
            key: 'q',
            label: '关键词',
            value: qInput,
            onChange: setQInput,
            placeholder: '编码 / 名称 / 说明',
            width: 'grow',
          },
          {
            key: 'enabled',
            label: '状态',
            value: enabledFilter,
            onChange: setEnabledFilter,
            width: 'sm',
            render: (
              <AppSelect
                value={enabledFilter}
                onChange={setEnabledFilter}
                options={[
                  { value: '', label: '全部' },
                  { value: 'true', label: '启用' },
                  { value: 'false', label: '停用' },
                ]}
                ariaLabel="启用状态"
              />
            ),
          },
        ]}
        onSubmit={() => setQ(qInput.trim())}
        onReset={() => {
          setQInput('')
          setQ('')
          setEnabledFilter('')
        }}
        active={!!q || !!enabledFilter || !!qInput.trim()}
      />

      {listQ.isLoading ? (
        <div className="text-sm text-white/40 py-8 text-center">加载中…</div>
      ) : agents.length === 0 ? (
        <div className="text-sm text-white/35 py-8 text-center">暂无系统 Agent，点击右上角新建知识库策展任务</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/[0.06]">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/[0.06] text-left bg-white/[0.02]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">编码</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">名称</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">日程</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">上次运行</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-20">启用</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-48">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/[0.06]">
              {agents.map((a) => (
                <tr key={a.id} className="hover:bg-white/[0.02] align-top">
                  <td className="py-2.5 px-3 font-mono text-white/60">
                    {a.code}
                    {a.running ? (
                      <span className="ml-2 rounded bg-cyan-500/20 px-1.5 py-0.5 text-[10px] text-cyan-200">运行中</span>
                    ) : null}
                  </td>
                  <td className="py-2.5 px-3">
                    <div>{a.name}</div>
                    <div className="mt-0.5 text-[11px] text-white/35">{a.description || '—'}</div>
                  </td>
                  <td className="py-2.5 px-3 text-white/55">
                    每天 {pad2(a.scheduleHour)}:{pad2(a.scheduleMinute)}（上海）
                  </td>
                  <td className="py-2.5 px-3 text-white/50">
                    {a.lastRunAt ? (
                      <>
                        <div>{a.lastRunStatus || '—'}</div>
                        <div className="text-[11px] text-white/35">{a.lastRunAt}</div>
                        {a.lastRunSummary ? (
                          <div className="mt-0.5 text-[11px] text-white/40 line-clamp-2">{a.lastRunSummary}</div>
                        ) : null}
                      </>
                    ) : (
                      '尚未运行'
                    )}
                  </td>
                  <td className="py-2.5 px-3">
                    <AdminToggle
                      checked={a.enabled}
                      disabled={toggleMut.isPending || a.running}
                      onChange={() => toggleMut.mutate(a)}
                      title={a.enabled ? '点击停用' : '点击启用'}
                    />
                  </td>
                  <td className="py-2.5 px-3">
                    <div className="flex flex-wrap gap-2">
                      <button type="button" className="text-blue-300 hover:underline" onClick={() => openEdit(a)}>
                        配置
                      </button>
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 text-emerald-300/90 hover:underline disabled:opacity-40"
                        disabled={runMut.isPending || a.running || !a.enabled}
                        onClick={() => runMut.mutate(a.id)}
                      >
                        <Play size={12} />
                        立即运行
                      </button>
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 text-white/50 hover:text-white hover:underline"
                        onClick={() => {
                          setRunsFor(a)
                          setRunsPage(0)
                        }}
                      >
                        <History size={12} />
                        记录
                      </button>
                      <button
                        type="button"
                        className="text-white/35 hover:text-red-300"
                        disabled={deleteMut.isPending || a.running}
                        onClick={() => {
                          if (window.confirm(`删除系统 Agent ${a.code}？`)) deleteMut.mutate(a.id)
                        }}
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AdminDialog
        open={modal != null}
        onClose={() => !saveMut.isPending && setModal(null)}
        busy={saveMut.isPending}
        width="lg"
        title={modal === 'create' ? '新建系统 Agent' : '配置系统 Agent'}
        subtitle="知识库策展 · 每日 Asia/Shanghai 定点执行"
        footer={
          <>
            <button
              type="button"
              className="admin-btn admin-btn--ghost admin-btn--sm"
              disabled={saveMut.isPending}
              onClick={() => setModal(null)}
            >
              取消
            </button>
            <button
              type="submit"
              form="admin-system-agent-form"
              className="admin-btn admin-btn--sm"
              disabled={saveMut.isPending}
            >
              {saveMut.isPending ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <form id="admin-system-agent-form" onSubmit={onSubmit}>
          <section className="admin-dialog__section">
            <div className="admin-dialog__section-head">
              <div className="admin-dialog__section-title">基本信息</div>
              <div className="admin-dialog__row-toggle" style={{ padding: '0.35rem 0.55rem' }}>
                <span>启用</span>
                <AdminToggle
                  checked={form.enabled}
                  onChange={() => setForm({ ...form, enabled: !form.enabled })}
                />
              </div>
            </div>
            <div className="admin-dialog__grid admin-dialog__grid--2">
              <label className="admin-field">
                编码
                <input
                  className="admin-input"
                  value={form.code}
                  disabled={modal === 'edit'}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                  placeholder="KB_CURATOR_DAILY"
                />
              </label>
              <label className="admin-field">
                显示名
                <input
                  className="admin-input"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </label>
            </div>
            <label className="admin-field mt-3">
              说明
              <input
                className="admin-input"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <div className="admin-dialog__grid admin-dialog__grid--2 mt-3">
              <label className="admin-field">
                每日执行时间
                <input
                  type="time"
                  className="admin-input"
                  value={scheduleValue}
                  onChange={(e) => {
                    const [h, m] = e.target.value.split(':').map((x) => Number(x))
                    setForm({
                      ...form,
                      scheduleHour: Number.isFinite(h) ? h : 0,
                      scheduleMinute: Number.isFinite(m) ? m : 0,
                    })
                  }}
                />
              </label>
              <label className="admin-field">
                策展模型
                <AppSelect
                  value={form.systemModelId == null ? '' : String(form.systemModelId)}
                  onChange={(v) => setForm({ ...form, systemModelId: v ? Number(v) : null })}
                  options={[{ value: '', label: '请选择…' }, ...modelOptions]}
                  ariaLabel="系统模型"
                />
              </label>
            </div>
          </section>

          <section className="admin-dialog__section">
            <div className="admin-dialog__section-head">
              <div className="admin-dialog__section-title">策展规则</div>
            </div>
            <p className="admin-dialog__hint">优先维护正文偏短或超过「过期天数」未更新的启用知识库。</p>
            <div className="admin-dialog__grid admin-dialog__grid--3">
              <label className="admin-field">
                每轮最多条目
                <input
                  type="number"
                  min={1}
                  max={50}
                  className="admin-input"
                  value={form.config.maxBasesPerRun}
                  onChange={(e) => setCfg('maxBasesPerRun', Number(e.target.value) || 1)}
                />
              </label>
              <label className="admin-field">
                薄弱阈值（字）
                <input
                  type="number"
                  min={0}
                  className="admin-input"
                  value={form.config.thinContentChars}
                  onChange={(e) => setCfg('thinContentChars', Number(e.target.value) || 0)}
                />
              </label>
              <label className="admin-field">
                过期天数
                <input
                  type="number"
                  min={0}
                  className="admin-input"
                  value={form.config.staleDays}
                  onChange={(e) => setCfg('staleDays', Number(e.target.value) || 0)}
                />
              </label>
              <label className="admin-field">
                温度
                <input
                  type="number"
                  step="0.05"
                  min={0}
                  max={1.5}
                  className="admin-input"
                  value={form.config.temperature}
                  onChange={(e) => setCfg('temperature', Number(e.target.value) || 0)}
                />
              </label>
              <label className="admin-field">
                最大 Tokens
                <input
                  type="number"
                  min={512}
                  max={32000}
                  className="admin-input"
                  value={form.config.maxTokens}
                  onChange={(e) => setCfg('maxTokens', Number(e.target.value) || 512)}
                />
              </label>
              <div className="admin-dialog__row-toggle self-end">
                <span>更新摘要/标签</span>
                <AdminToggle
                  checked={form.config.updateSummaryAndTags}
                  onChange={() => setCfg('updateSummaryAndTags', !form.config.updateSummaryAndTags)}
                />
              </div>
            </div>
            <label className="admin-field mt-3">
              风格 / 领域指令
              <textarea
                className="admin-input min-h-[72px]"
                value={form.config.styleGuide}
                onChange={(e) => setCfg('styleGuide', e.target.value)}
                placeholder="例如：偏跨境合规、保留术语英中对照、操作清单优先…"
              />
            </label>
            <div className="mt-3">
              <div className="admin-field mb-1.5">限定分类（不选 = 全部启用库）</div>
              {catOptions.length === 0 ? (
                <p className="admin-dialog__hint" style={{ marginBottom: 0 }}>
                  暂无分类
                </p>
              ) : (
                <div className="admin-dialog__chips">
                  {catOptions.map((c) => {
                    const on = form.config.categoryIds.includes(c.id)
                    return (
                      <label
                        key={c.id}
                        className={`admin-dialog__chip${on ? ' is-on' : ''}`}
                        style={{ marginLeft: c.depth ? `${c.depth * 0.35}rem` : undefined }}
                      >
                        <input
                          type="checkbox"
                          checked={on}
                          onChange={() => toggleCategory(c.id)}
                        />
                        {c.label}
                      </label>
                    )
                  })}
                </div>
              )}
            </div>
          </section>

          <section className="admin-dialog__section">
            <div className="admin-dialog__section-head">
              <div className="admin-dialog__section-title">Tavily 联网检索</div>
              <AdminToggle
                checked={form.config.useTavily}
                onChange={() => setCfg('useTavily', !form.config.useTavily)}
                title="是否在策展时调用 Tavily"
              />
            </div>
            <p className="admin-dialog__hint">
              使用「系统配置」中的 TAVILY_API_KEY。未配置时跳过检索，仍可改写正文。
            </p>
            <div className="admin-dialog__grid admin-dialog__grid--3">
              <label className="admin-field">
                每条检索次数
                <input
                  type="number"
                  min={1}
                  max={5}
                  className="admin-input"
                  disabled={!form.config.useTavily}
                  value={form.config.researchQueriesPerBase}
                  onChange={(e) => setCfg('researchQueriesPerBase', Number(e.target.value) || 1)}
                />
              </label>
              <label className="admin-field">
                每次结果数
                <input
                  type="number"
                  min={1}
                  max={20}
                  className="admin-input"
                  disabled={!form.config.useTavily}
                  value={form.config.tavilyMaxResults}
                  onChange={(e) => setCfg('tavilyMaxResults', Number(e.target.value) || 1)}
                />
              </label>
              <label className="admin-field">
                搜索深度
                <AppSelect
                  value={form.config.tavilySearchDepth}
                  onChange={(v) => setCfg('tavilySearchDepth', v)}
                  disabled={!form.config.useTavily}
                  options={[
                    { value: 'basic', label: 'basic' },
                    { value: 'advanced', label: 'advanced' },
                  ]}
                  ariaLabel="Tavily 搜索深度"
                />
              </label>
            </div>
          </section>
        </form>
      </AdminDialog>

      <AdminDialog
        open={runsFor != null}
        onClose={() => setRunsFor(null)}
        width="md"
        title={runsFor ? `运行记录 · ${runsFor.code}` : '运行记录'}
        subtitle={runsFor?.name}
      >
        {runsQ.isLoading ? (
          <div className="py-8 text-center text-sm text-white/40">加载中…</div>
        ) : (runsQ.data?.items.length ?? 0) === 0 ? (
          <div className="py-8 text-center text-sm text-white/35">暂无运行记录</div>
        ) : (
          <ul className="space-y-2">
            {runsQ.data!.items.map((r) => (
              <li key={r.id} className="rounded-xl bg-white/[0.03] px-3 py-2.5 text-xs">
                <div className="flex flex-wrap items-center gap-2 text-white/70">
                  <span className="font-mono text-white/45">#{r.id}</span>
                  <span>{r.triggerType}</span>
                  <span
                    className={
                      r.status === 'SUCCESS'
                        ? 'text-emerald-300/90'
                        : r.status === 'FAILED'
                          ? 'text-red-300/90'
                          : 'text-amber-200/90'
                    }
                  >
                    {r.status}
                  </span>
                  <span className="text-white/35">{r.startedAt}</span>
                </div>
                {r.summary ? <p className="mt-1 text-white/50">{r.summary}</p> : null}
              </li>
            ))}
          </ul>
        )}
        {runsQ.data ? (
          <div className="mt-3">
            <AdminPager
              page={runsQ.data.page}
              size={runsQ.data.size}
              total={runsQ.data.total}
              onPageChange={setRunsPage}
              label="次"
              loading={runsQ.isFetching}
            />
          </div>
        ) : null}
      </AdminDialog>
    </div>
  )
}
