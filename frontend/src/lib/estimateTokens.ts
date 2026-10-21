/** 与后端 TokenCounter 一致的轻量估算：约 3 个 code point ≈ 1 token */
export function estimateTokens(text: string | null | undefined): number {
  if (!text) return 0
  const trimmed = text.trim()
  if (!trimmed) return 0
  let codePoints = 0
  for (const _ of trimmed) codePoints++
  return Math.max(1, Math.round(codePoints / 3))
}

export function formatTokenCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1)}k`
  return String(n)
}
