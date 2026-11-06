import { FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { adminApi, type SystemProviderDto } from '../../api/admin'
import { AdminToggle } from '../../ui/AdminToggle'
import { AdminQueryBar } from '../../ui/AdminQueryBar'
import { AppSelect } from '../../ui/AppSelect'

const emptyForm = {
  code: '',
  displayName: '',
  description: '',
  apiKey: '',
}

export function AdminProvidersPanel() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editKey, setEditKey] = useState('')
  const [editName, setEditName] = useState('')
  const [editDesc, setEditDesc] = useState('')
  const [qInput, setQInput] = useState('')
  const [q, setQ] = useState('')
  const [enabledFilter, setEnabledFilter] = useState('')
  const [keyFilter, setKeyFilter] = useState('')

  const { data: providers = [], isLoading } = useQuery({
    queryKey: ['admin-system-providers'],
    queryFn: () => adminApi.listSystemProviders(),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      adminApi.createSystemProvider({
        code: form.code.trim(),
        displayName: form.displayName.trim(),
        description: form.description.trim() || undefined,
        apiKey: form.apiKey.trim() || undefined,
        enabled: true,
      }),
    onSuccess: () => {
      toast.success('已添加服务商')
      setForm(emptyForm)
      queryClient.invalidateQueries({ queryKey: ['admin-system-providers'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof adminApi.updateSystemProvider>[1] }) =>
      adminApi.updateSystemProvider(id, body),
    onSuccess: () => {
      toast.success('已更新服务商')
      setEditingId(null)
      setEditKey('')
      queryClient.invalidateQueries({ queryKey: ['admin-system-providers'] })
      queryClient.invalidateQueries({ queryKey: ['admin-system-models'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminApi.deleteSystemProvider(id),
    onSuccess: () => {
      toast.success('已删除服务商')
      queryClient.invalidateQueries({ queryKey: ['admin-system-providers'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  function startEdit(p: SystemProviderDto) {
    setEditingId(p.id)
    setEditName(p.displayName)
    setEditDesc(p.description ?? '')
    setEditKey('')
  }

  function onCreate(e: FormEvent) {
    e.preventDefault()
    if (!form.code.trim() || !form.displayName.trim()) {
      toast.error('请填写编码与名称')
      return
    }
    createMutation.mutate()
  }

  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase()
    return providers.filter((p) => {
      if (enabledFilter === 'true' && !p.enabled) return false
      if (enabledFilter === 'false' && p.enabled) return false
      if (keyFilter === 'set' && !p.apiKeySet) return false
      if (keyFilter === 'missing' && p.apiKeySet) return false
      if (!kw) return true
      return `${p.code} ${p.displayName} ${p.description ?? ''}`.toLowerCase().includes(kw)
    })
  }, [providers, q, enabledFilter, keyFilter])

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-sm font-medium text-white">服务商管理</h3>
        <p className="text-xs text-white/40 mt-0.5">
          一个服务商配置一把 API Key；模型管理里同一服务商下的模型共用该密钥。
        </p>
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
          {
            key: 'key',
            label: '密钥',
            value: keyFilter,
            onChange: setKeyFilter,
            width: 'sm',
            render: (
              <AppSelect
                value={keyFilter}
                onChange={setKeyFilter}
                options={[
                  { value: '', label: '全部' },
                  { value: 'set', label: '已配置' },
                  { value: 'missing', label: '未配置' },
                ]}
                ariaLabel="密钥状态"
              />
            ),
          },
        ]}
        onSubmit={() => setQ(qInput.trim())}
        onReset={() => {
          setQInput('')
          setQ('')
          setEnabledFilter('')
          setKeyFilter('')
        }}
        active={!!q || !!enabledFilter || !!keyFilter || !!qInput.trim()}
      />

      <form onSubmit={onCreate} className="rounded-lg border border-white/10 p-3 space-y-3">
        <div className="text-xs text-white/70">新增服务商</div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
          <input
            className="admin-input"
            placeholder="编码，如 deepseek"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
          />
          <input
            className="admin-input"
            placeholder="显示名"
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          />
          <input
            className="admin-input"
            placeholder="说明（可选）"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <input
            type="password"
            className="admin-input"
            placeholder="API Key"
            autoComplete="off"
            value={form.apiKey}
            onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
          />
        </div>
        <button type="submit" className="admin-btn" disabled={createMutation.isPending}>
          {createMutation.isPending ? '添加中…' : '添加'}
        </button>
      </form>

      {isLoading ? (
        <div className="text-sm text-white/40 py-8 text-center">加载中…</div>
      ) : providers.length === 0 ? (
        <div className="text-sm text-white/35 py-8 text-center">暂无服务商</div>
      ) : filtered.length === 0 ? (
        <div className="text-sm text-white/35 py-8 text-center">无匹配服务商</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-white/10">
          <table className="w-full text-xs text-white/80">
            <thead>
              <tr className="border-b border-white/10 text-left bg-white/[0.03]">
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">编码</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">名称</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">API Key</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40">说明</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-24">启用</th>
                <th className="pb-2 pt-2 px-3 font-medium text-white/40 w-36">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {filtered.map((p) => (
                <tr key={p.id} className="hover:bg-white/[0.03] align-top">
                  <td className="py-2.5 px-3 font-mono text-white/60">{p.code}</td>
                  <td className="py-2.5 px-3">
                    {editingId === p.id ? (
                      <input
                        className="admin-input"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                      />
                    ) : (
                      p.displayName
                    )}
                  </td>
                  <td className="py-2.5 px-3 min-w-[12rem]">
                    {editingId === p.id ? (
                      <input
                        type="password"
                        className="admin-input"
                        placeholder={p.apiKeySet ? `已配置 ${p.apiKeyMasked ?? ''}，留空保留` : '填入 API Key'}
                        autoComplete="off"
                        value={editKey}
                        onChange={(e) => setEditKey(e.target.value)}
                      />
                    ) : p.apiKeySet ? (
                      <span className="font-mono text-emerald-300/90">{p.apiKeyMasked}</span>
                    ) : (
                      <span className="text-amber-300/80">未配置</span>
                    )}
                  </td>
                  <td className="py-2.5 px-3 text-white/50">
                    {editingId === p.id ? (
                      <input
                        className="admin-input"
                        value={editDesc}
                        onChange={(e) => setEditDesc(e.target.value)}
                      />
                    ) : (
                      p.description || '—'
                    )}
                  </td>
                  <td className="py-2.5 px-3">
                    <AdminToggle
                      checked={p.enabled}
                      disabled={updateMutation.isPending}
                      onChange={() => updateMutation.mutate({ id: p.id, body: { enabled: !p.enabled } })}
                      title={p.enabled ? '点击停用' : '点击启用'}
                    />
                  </td>
                  <td className="py-2.5 px-3">
                    {editingId === p.id ? (
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          className="text-blue-300 hover:underline"
                          disabled={updateMutation.isPending}
                          onClick={() =>
                            updateMutation.mutate({
                              id: p.id,
                              body: {
                                displayName: editName.trim(),
                                description: editDesc.trim(),
                                ...(editKey.trim() ? { apiKey: editKey.trim() } : {}),
                              },
                            })
                          }
                        >
                          保存
                        </button>
                        <button
                          type="button"
                          className="text-white/45 hover:underline"
                          onClick={() => {
                            setEditingId(null)
                            setEditKey('')
                          }}
                        >
                          取消
                        </button>
                        {p.apiKeySet ? (
                          <button
                            type="button"
                            className="text-amber-300/80 hover:underline"
                            disabled={updateMutation.isPending}
                            onClick={() => updateMutation.mutate({ id: p.id, body: { apiKey: '' } })}
                          >
                            清空 Key
                          </button>
                        ) : null}
                      </div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        <button type="button" className="text-blue-300 hover:underline" onClick={() => startEdit(p)}>
                          编辑
                        </button>
                        <button
                          type="button"
                          className="text-white/40 hover:text-red-300"
                          disabled={deleteMutation.isPending}
                          onClick={() => {
                            if (window.confirm(`删除服务商 ${p.code}？`)) deleteMutation.mutate(p.id)
                          }}
                        >
                          删除
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}