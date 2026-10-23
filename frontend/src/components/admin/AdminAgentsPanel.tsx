import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { adminAgentApi } from '../../api/multiAgent'
import { adminApi } from '../../api/admin'
import { adminKnowledgeApi, type KnowledgeBaseBrief } from '../../api/adminKnowledge'
import clsx from 'clsx'

const TIERS = ['JUNIOR', 'MID', 'SENIOR', 'EXPERT'] as const

export function AdminAgentsPanel() {
  const qc = useQueryClient()
  const [sub, setSub] = useState<'catalog' | 'tierModels' | 'prompt'>('catalog')
  const [linkCatalogId, setLinkCatalogId] = useState<number | null>(null)
  const [linkSelected, setLinkSelected] = useState<number[]>([])
  const [kbPickQ, setKbPickQ] = useState('')

  const catalogQ = useQuery({
    queryKey: ['admin-agent-catalog'],
    queryFn: () => adminAgentApi.listCatalog(),
    enabled: sub === 'catalog',
  })
  const tierQ = useQuery({
    queryKey: ['admin-agent-tier-models'],
    queryFn: () => adminAgentApi.listTierModels(),
    enabled: sub === 'tierModels',
  })
  const modelsQ = useQuery({
    queryKey: ['admin-system-models'],
    queryFn: () => adminApi.listAllSystemModels(),
    enabled: sub === 'tierModels' || sub === 'prompt',
  })
  const promptQ = useQuery({
    queryKey: ['admin-agent-prompt-model'],
    queryFn: () => adminAgentApi.getPromptModel(),
    enabled: sub === 'prompt',
  })

  const [tier, setTier] = useState<(typeof TIERS)[number]>('JUNIOR')
  const [selectedModelIds, setSelectedModelIds] = useState<number[]>([])
  const [defaultModelId, setDefaultModelId] = useState<number | null>(null)

  const saveTierMut = useMutation({
    mutationFn: () =>
      adminAgentApi.replaceTierModels(
        tier,
        selectedModelIds.map((id, i) => ({
          systemModelId: id,
          isDefault: id === defaultModelId,
          sortOrder: i,
        }))
      ),
    onSuccess: () => {
      toast.success('等级模型已保存')
      qc.invalidateQueries({ queryKey: ['admin-agent-tier-models'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const savePromptMut = useMutation({
    mutationFn: (id: number | null) => adminAgentApi.setPromptModel(id),
    onSuccess: () => {
      toast.success('提示词生成模型已更新')
      qc.invalidateQueries({ queryKey: ['admin-agent-prompt-model'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const toggleCatalogMut = useMutation({
    mutationFn: async (row: Record<string, unknown>) => {
      const id = Number(row.id)
      return adminAgentApi.saveCatalog(
        {
          careerId: row.careerId,
          tier: row.tier,
          name: row.name,
          description: row.description,
          enabled: !row.enabled,
          priceDayPoints: row.priceDayPoints,
          priceMonthPoints: row.priceMonthPoints,
          priceYearPoints: row.priceYearPoints,
          soulPriceDayPoints: row.soulPriceDayPoints,
          soulPriceMonthPoints: row.soulPriceMonthPoints,
          soulPriceYearPoints: row.soulPriceYearPoints,
        },
        id
      )
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-agent-catalog'] }),
    onError: (e: Error) => toast.error(e.message),
  })

  const kbPoolQ = useQuery({
    queryKey: ['admin-kb-pick-pool', kbPickQ],
    queryFn: () => adminKnowledgeApi.pageBases({ q: kbPickQ || undefined, enabled: true, page: 0, size: 100 }),
    enabled: linkCatalogId != null,
  })

  const openLinkKb = async (catalogId: number) => {
    try {
      const linked = await adminKnowledgeApi.catalogBases(catalogId)
      setLinkCatalogId(catalogId)
      setLinkSelected(linked.map((x) => x.id))
      setKbPickQ('')
    } catch (e) {
      toast.error((e as Error).message)
    }
  }

  const saveLinkMut = useMutation({
    mutationFn: () => {
      if (linkCatalogId == null) throw new Error('未选择商品')
      return adminKnowledgeApi.replaceCatalogBases(linkCatalogId, linkSelected)
    },
    onSuccess: () => {
      toast.success('知识库关联已保存')
      setLinkCatalogId(null)
      qc.invalidateQueries({ queryKey: ['admin-agent-catalog'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const kbOptions = useMemo(() => kbPoolQ.data?.items ?? [], [kbPoolQ.data])

  return (
    <div className="space-y-4">
      <div className="flex gap-1 rounded-lg border border-white/10 p-0.5 w-fit">
        {(
          [
            ['catalog', '招聘目录'],
            ['tierModels', '等级↔模型'],
            ['prompt', '提示词模型'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setSub(id)}
            className={clsx(
              'rounded-md px-3 py-1.5 text-xs',
              sub === id ? 'bg-white/10 text-white' : 'text-white/45'
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {sub === 'catalog' && (
        <div className="overflow-x-auto rounded-xl border border-white/10">
          <table className="w-full text-left text-xs text-white/70">
            <thead className="bg-white/[0.03] text-white/40">
              <tr>
                <th className="px-3 py-2 font-medium">名称</th>
                <th className="px-3 py-2 font-medium">行业</th>
                <th className="px-3 py-2 font-medium">职业</th>
                <th className="px-3 py-2 font-medium">等级</th>
                <th className="px-3 py-2 font-medium">月价(Agent+Soul)</th>
                <th className="px-3 py-2 font-medium">知识库</th>
                <th className="px-3 py-2 font-medium">上架</th>
              </tr>
            </thead>
            <tbody>
              {(catalogQ.data ?? []).map((row) => {
                const kbs = (row.knowledgeBases as KnowledgeBaseBrief[] | undefined) ?? []
                return (
                <tr key={String(row.id)} className="border-t border-white/5">
                  <td className="px-3 py-2 text-white/85">{String(row.name)}</td>
                  <td className="px-3 py-2">{String(row.industryName ?? '—')}</td>
                  <td className="px-3 py-2">{String(row.careerName ?? '')}</td>
                  <td className="px-3 py-2">{String(row.tier)}</td>
                  <td className="px-3 py-2 font-mono">
                    {Number(row.priceMonthPoints) + Number(row.soulPriceMonthPoints)}
                  </td>
                  <td className="px-3 py-2">
                    <button
                      type="button"
                      className="text-left text-blue-300/80 hover:text-blue-200"
                      onClick={() => openLinkKb(Number(row.id))}
                    >
                      {kbs.length ? `${kbs.length} 个` : '关联'}
                    </button>
                  </td>
                  <td className="px-3 py-2">
                    <button
                      type="button"
                      className="text-cyan-300/80 hover:text-cyan-200"
                      onClick={() => toggleCatalogMut.mutate(row)}
                    >
                      {row.enabled ? '已上架' : '已下架'}
                    </button>
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {linkCatalogId != null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-xl border border-white/15 bg-[#141414] p-4">
            <h3 className="mb-2 text-sm font-semibold">关联官方知识库</h3>
            <p className="mb-3 text-[11px] text-white/40">
              用户招聘该 Agent 时，将同时获得勾选的领域知识库（不仅是提示词）。
            </p>
            <input
              className="admin-input mb-2 w-full"
              placeholder="筛选知识库…"
              value={kbPickQ}
              onChange={(e) => setKbPickQ(e.target.value)}
            />
            <div className="max-h-72 space-y-1 overflow-y-auto rounded-lg border border-white/10 p-2">
              {kbOptions.map((kb) => (
                <label
                  key={kb.id}
                  className="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 text-xs hover:bg-white/[0.04]"
                >
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={linkSelected.includes(kb.id)}
                    onChange={(e) => {
                      setLinkSelected((prev) =>
                        e.target.checked ? [...prev, kb.id] : prev.filter((x) => x !== kb.id),
                      )
                    }}
                  />
                  <span className="min-w-0">
                    <span className="block text-white/80">{kb.name}</span>
                    <span className="font-mono text-[10px] text-white/35">{kb.publicId}</span>
                  </span>
                </label>
              ))}
              {!kbOptions.length && (
                <p className="py-4 text-center text-white/35">暂无知识库，请先在「官方知识库」创建</p>
              )}
            </div>
            <div className="mt-3 flex justify-end gap-2">
              <button
                type="button"
                className="rounded-lg border border-white/15 px-3 py-1.5 text-xs"
                onClick={() => setLinkCatalogId(null)}
              >
                取消
              </button>
              <button
                type="button"
                className="rounded-lg bg-blue-600/80 px-3 py-1.5 text-xs hover:bg-blue-600"
                disabled={saveLinkMut.isPending}
                onClick={() => saveLinkMut.mutate()}
              >
                保存（已选 {linkSelected.length}）
              </button>
            </div>
          </div>
        </div>
      )}

      {sub === 'tierModels' && (
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {TIERS.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => {
                  setTier(t)
                  const existing = (tierQ.data ?? []).filter((x) => x.tier === t)
                  setSelectedModelIds(existing.map((x) => Number(x.systemModelId)))
                  const def = existing.find((x) => x.isDefault)
                  setDefaultModelId(def ? Number(def.systemModelId) : null)
                }}
                className={clsx(
                  'rounded-lg border px-3 py-1.5 text-xs',
                  tier === t ? 'border-cyan-400/40 text-cyan-100' : 'border-white/10 text-white/45'
                )}
              >
                {t}
              </button>
            ))}
          </div>
          <div className="grid max-h-64 gap-1 overflow-y-auto rounded-xl border border-white/10 p-2">
            {(modelsQ.data ?? []).map((m) => (
              <label key={m.id} className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-xs text-white/70 hover:bg-white/[0.04]">
                <input
                  type="checkbox"
                  checked={selectedModelIds.includes(m.id)}
                  onChange={(e) => {
                    setSelectedModelIds((prev) =>
                      e.target.checked ? [...prev, m.id] : prev.filter((x) => x !== m.id)
                    )
                  }}
                />
                <span className="flex-1">{m.displayName}</span>
                <button
                  type="button"
                  className={clsx('text-[10px]', defaultModelId === m.id ? 'text-amber-300' : 'text-white/30')}
                  onClick={() => setDefaultModelId(m.id)}
                >
                  默认
                </button>
              </label>
            ))}
          </div>
          <button
            type="button"
            disabled={saveTierMut.isPending}
            onClick={() => saveTierMut.mutate()}
            className="rounded-lg bg-cyan-500/90 px-4 py-2 text-xs font-medium text-black"
          >
            保存 {tier} 模型池
          </button>
        </div>
      )}

      {sub === 'prompt' && (
        <div className="space-y-3">
          <p className="text-xs text-white/40">秘书提示词生成 / 日更所用的系统模型（建议 DeepSeek 最强档）</p>
          <div className="space-y-1">
            {(modelsQ.data ?? []).map((m) => (
              <button
                key={m.id}
                type="button"
                onClick={() => savePromptMut.mutate(m.id)}
                className={clsx(
                  'block w-full rounded-lg border px-3 py-2 text-left text-xs',
                  String(promptQ.data?.systemModelId) === String(m.id)
                    ? 'border-cyan-400/40 bg-cyan-400/10 text-white'
                    : 'border-white/10 text-white/55'
                )}
              >
                {m.displayName}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
