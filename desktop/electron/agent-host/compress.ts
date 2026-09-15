import type { CloudClient } from '../cloud/client'
import type { MemoryStore } from './memory'

const KEEP_RECENT = 12

export type ChatMessage = {
  role: string
  content?: string | null
  toolCalls?: unknown[]
  toolCallId?: string
}

export async function maybeCompressContext(
  messages: ChatMessage[],
  cloud: CloudClient,
): Promise<ChatMessage[]> {
  if (messages.length <= KEEP_RECENT + 2) return messages
  const system = messages.filter((m) => m.role === 'system')
  const rest = messages.filter((m) => m.role !== 'system')
  const old = rest.slice(0, Math.max(0, rest.length - KEEP_RECENT))
  const recent = rest.slice(-KEEP_RECENT)
  if (old.length < 4) return messages

  const transcript = old
    .map((m) => `${m.role}: ${(m.content || '').slice(0, 500)}`)
    .join('\n')
    .slice(0, 12000)

  try {
    const result = await cloud.streamChat(
      {
        messages: [
          {
            role: 'system',
            content: 'Summarize the conversation history concisely for future context. Keep facts, decisions, and open tasks.',
          },
          { role: 'user', content: transcript },
        ],
        temperature: 0.2,
        maxTokens: 800,
      },
      () => {},
    )
    return [
      ...system,
      { role: 'system', content: `Earlier conversation summary:\n${result.content}` },
      ...recent,
    ]
  } catch {
    return messages
  }
}

export async function prepareMemoryCompression(
  projectPath: string,
  sessionId: string,
  memory: MemoryStore,
  cloud: CloudClient,
): Promise<{ deleteIds: string[]; newMemories: Array<{ content: string; importance: number }> }> {
  const all = memory.list(projectPath, sessionId).filter((m) => !m.noCompress)
  if (all.length < 3) {
    return { deleteIds: [], newMemories: [] }
  }
  const payload = all
    .map((m) => `- [${m.id}] (imp=${m.importance}) ${m.content}`)
    .join('\n')
    .slice(0, 14000)

  const result = await cloud.streamChat(
    {
      messages: [
        {
          role: 'system',
          content:
            'You compress memories. Reply with ONLY JSON: {"deleteIds":["..."],"newMemories":[{"content":"...","importance":0.5}]}',
        },
        { role: 'user', content: payload },
      ],
      temperature: 0.2,
      maxTokens: 2000,
    },
    () => {},
  )

  try {
    const match = result.content.match(/\{[\s\S]*\}/)
    if (!match) return { deleteIds: [], newMemories: [] }
    const parsed = JSON.parse(match[0]) as {
      deleteIds?: string[]
      newMemories?: Array<{ content: string; importance?: number }>
    }
    return {
      deleteIds: parsed.deleteIds ?? [],
      newMemories: (parsed.newMemories ?? []).map((m) => ({
        content: m.content,
        importance: m.importance ?? 0.5,
      })),
    }
  } catch {
    return { deleteIds: [], newMemories: [] }
  }
}
