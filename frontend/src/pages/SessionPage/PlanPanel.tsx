import type { PlanState } from '../../types/agent'

export function PlanPanel({ plan }: { plan: PlanState }) {
  if (!plan.steps || plan.steps.length === 0) return null

  const safeIndex = Math.min(Math.max(plan.currentStepIndex || 1, 1), plan.steps.length)
  const currentTitle = plan.steps[safeIndex - 1] ?? ''
  const currentSummary = plan.stepSummaries[safeIndex] ?? ''

  return (
    <div className="inline-flex items-center gap-2 text-[11px] text-white/60">
      <span className="px-1.5 py-0.5 rounded-full bg-blue-500/15 text-blue-200/90 border border-blue-400/30">
        执行计划
      </span>
      <span className="text-white/40">
        第 {safeIndex} / {plan.steps.length} 步
      </span>
      <span className="max-w-[220px] truncate text-white/80" title={currentSummary || currentTitle}>
        {currentSummary || currentTitle}
      </span>
    </div>
  )
}

