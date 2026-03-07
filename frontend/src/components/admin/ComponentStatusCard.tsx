import type { ComponentStatusDto } from '../../types/admin'

function StatusBadge({ ok }: { ok: boolean }) {
  return (
    <span className={ok ? 'text-emerald-400' : 'text-amber-500/90'}>
      {ok ? '[OK]' : '[--]'}
    </span>
  )
}

export function ComponentStatusCard({ status }: { status: ComponentStatusDto }) {
  const { mysql, milvus, llm, embedding, docker } = status
  const items = [
    { label: 'MySQL', ok: mysql.ok, detail: mysql.message },
    { label: 'Milvus', ok: milvus.ok, detail: `${milvus.host}:${milvus.port} ${milvus.collectionName} dim=${milvus.dimensions}` },
    { label: 'LLM 主', ok: llm.primary.keyOk, detail: `${llm.primary.name} [${llm.primary.model}]` },
    { label: 'LLM 备', ok: llm.fallback.keyOk, detail: `${llm.fallback.name} [${llm.fallback.model}]` },
    { label: 'Embedding', ok: embedding.ok, detail: `${embedding.model} dim=${embedding.dimensions} ${embedding.baseUrl ?? ''}` },
    { label: 'Docker', ok: docker.ok, detail: docker.ok ? `v${docker.version}` : (docker.message ?? '') },
  ]
  return (
    <div className="rounded-lg border border-white/10 bg-white/[0.02] p-4 text-xs text-white/80">
      <div className="mb-2 font-medium text-white/60">组件连接状态</div>
      <div className="grid grid-cols-1 gap-y-2 sm:grid-cols-2 lg:grid-cols-3">
        {items.map(({ label, ok, detail }) => (
          <div key={label} className="flex gap-2 min-w-0">
            <StatusBadge ok={ok} />
            <div className="min-w-0 flex-1">
              <span className="shrink-0 text-white/70">{label}</span>
              <div className="break-words text-white/50" title={detail}>{detail}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
