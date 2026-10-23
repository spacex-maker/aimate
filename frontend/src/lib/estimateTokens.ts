/**
 * 与后端 TokenCounter 一致：CJK ≈ 1 token/字，拉丁 ≈ 4 code point/token。
 */
export function estimateTokens(text: string | null | undefined): number {
  if (!text) return 0
  const trimmed = text.trim()
  if (!trimmed) return 0
  let tokens = 0
  let latinBudget = 0
  for (const ch of trimmed) {
    const cp = ch.codePointAt(0) ?? 0
    if (isDenseChar(cp)) {
      if (latinBudget > 0) {
        tokens += Math.ceil(latinBudget / 4)
        latinBudget = 0
      }
      tokens += 1
    } else {
      latinBudget += 1
    }
  }
  if (latinBudget > 0) tokens += Math.ceil(latinBudget / 4)
  return Math.max(tokens, 1)
}

function isDenseChar(cp: number): boolean {
  return (
    (cp >= 0x4e00 && cp <= 0x9fff) ||
    (cp >= 0x3400 && cp <= 0x4dbf) ||
    (cp >= 0xf900 && cp <= 0xfaff) ||
    (cp >= 0x3000 && cp <= 0x303f) ||
    (cp >= 0xff00 && cp <= 0xffef) ||
    (cp >= 0x3040 && cp <= 0x30ff) ||
    (cp >= 0xac00 && cp <= 0xd7af)
  )
}

export function formatTokenCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1)}k`
  return String(n)
}
