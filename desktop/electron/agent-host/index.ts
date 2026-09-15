import path from 'node:path'
import type { BrowserWindow } from 'electron'
import type { AppStore } from '../cloud/store'
import type { CloudClient } from '../cloud/client'
import { bundledSkillsDir } from './projects'
import { discoverSkills, resolveForcedSkill, skillSummariesPrompt, type SkillInfo } from './skills'
import { MemoryStore } from './memory'
import * as sessions from './sessions'
import { BUILTIN_TOOLS, executeTool } from './tools'
import { maybeCompressContext, prepareMemoryCompression, type ChatMessage } from './compress'

const MAX_ITERATIONS = 30

export type AgentEvent = {
  type: string
  payload?: unknown
}

type Deps = {
  store: AppStore
  cloud: CloudClient
  getWindow: () => BrowserWindow | null
}

export class AgentHost {
  readonly memory = new MemoryStore()
  private abort: AbortController | null = null

  constructor(private deps: Deps) {}

  emit(event: AgentEvent): void {
    const win = this.deps.getWindow()
    win?.webContents.send('hostapi:event', event)
  }

  dispose(): void {
    this.abort?.abort()
  }

  cancel(): void {
    this.abort?.abort()
    this.abort = null
    this.emit({ type: 'STATUS_CHANGE', payload: { status: 'CANCELLED' } })
  }

  resolveProjectPath(projectId: string): string {
    const p = this.deps.store.getProjects().find((x) => x.id === projectId)
    if (!p) throw new Error('项目不存在')
    return p.path
  }

  listSkills(projectId?: string): SkillInfo[] {
    const projectPath = projectId ? this.resolveProjectPath(projectId) : undefined
    return discoverSkills(
      {
        bundled: bundledSkillsDir(),
        managed: this.deps.store.managedSkillsDir(),
        project: projectPath ? path.join(projectPath, 'skills') : undefined,
      },
      this.deps.store.getDisabledSkills(),
    )
  }

  async runSession(projectId: string, sessionId: string, userMessage: string): Promise<void> {
    this.abort?.abort()
    this.abort = new AbortController()
    const signal = this.abort.signal
    const projectPath = this.resolveProjectPath(projectId)
    const skills = this.listSkills(projectId)

    sessions.appendEvent(projectPath, sessionId, {
      type: 'user',
      content: userMessage,
      ts: new Date().toISOString(),
    })
    this.emit({ type: 'STATUS_CHANGE', payload: { status: 'RUNNING', sessionId } })

    const forced = resolveForcedSkill(userMessage, skills)
    let recalled: string[] = []
    try {
      const [emb] = await this.deps.cloud.embed([userMessage])
      recalled = this.memory.recall(projectPath, sessionId, emb, 5).map((m) => m.content)
    } catch {
      recalled = this.memory.searchableText(projectPath, sessionId, userMessage.slice(0, 40)).map((m) => m.content)
    }

    this.emit({ type: 'ITERATION_START', payload: { phase: 'RECALL', count: recalled.length } })

    const history = sessions.readTranscript(projectPath, sessionId)
    const chatMessages: ChatMessage[] = [
      {
        role: 'system',
        content: [
          'You are AIMate, a desktop autonomous agent.',
          'Use tools when needed. Prefer concise answers.',
          skillSummariesPrompt(skills),
          forced ? `\nActive skill /${forced.name}:\n${forced.body}` : '',
          recalled.length ? `\nRelevant memories:\n${recalled.map((c) => `- ${c}`).join('\n')}` : '',
        ]
          .filter(Boolean)
          .join('\n\n'),
      },
    ]

    for (const ev of history) {
      if (ev.type === 'user') chatMessages.push({ role: 'user', content: ev.content })
      if (ev.type === 'assistant') chatMessages.push({ role: 'assistant', content: ev.content })
      if (ev.type === 'summary') chatMessages.push({ role: 'system', content: ev.content })
    }

    let messages = await maybeCompressContext(chatMessages, this.deps.cloud)

    try {
      for (let i = 0; i < MAX_ITERATIONS; i++) {
        if (signal.aborted) throw new Error('cancelled')
        this.emit({ type: 'ITERATION_START', payload: { iteration: i + 1 } })

        let thinking = ''
        const result = await this.deps.cloud.streamChat(
          {
            messages,
            tools: BUILTIN_TOOLS,
            toolChoice: 'auto',
            temperature: 0.7,
            maxTokens: 8192,
            sessionId,
          },
          (token) => {
            thinking += token
            this.emit({ type: 'THINKING', payload: { token, sessionId } })
          },
          signal,
        )

        const toolCalls = (result.toolCalls ?? []) as Array<{
          id: string
          type?: string
          function?: { name: string; arguments: string }
        }>

        if (toolCalls.length) {
          messages.push({
            role: 'assistant',
            content: result.content || null,
            toolCalls: toolCalls,
          })
          sessions.appendEvent(projectPath, sessionId, {
            type: 'thinking',
            content: thinking || result.content || '',
            ts: new Date().toISOString(),
          })

          for (const tc of toolCalls) {
            const name = tc.function?.name || 'unknown'
            const args = tc.function?.arguments || '{}'
            this.emit({ type: 'TOOL_CALL', payload: { id: tc.id, name, args, sessionId } })
            sessions.appendEvent(projectPath, sessionId, {
              type: 'tool_call',
              id: tc.id,
              name,
              args,
              ts: new Date().toISOString(),
            })

            const toolResult = await executeTool(name, args, {
              projectPath,
              storeMemory: async (content, importance) => {
                let embedding: number[] | undefined
                try {
                  const [emb] = await this.deps.cloud.embed([content])
                  embedding = emb
                } catch {
                  /* optional */
                }
                this.memory.add(projectPath, sessionId, { content, importance, embedding })
                return 'Memory stored.'
              },
            })

            this.emit({ type: 'TOOL_RESULT', payload: { id: tc.id, result: toolResult, sessionId } })
            sessions.appendEvent(projectPath, sessionId, {
              type: 'tool_result',
              id: tc.id,
              result: toolResult,
              ts: new Date().toISOString(),
            })
            messages.push({ role: 'tool', toolCallId: tc.id, content: toolResult })
          }
          continue
        }

        const finalText = result.content || thinking
        sessions.appendEvent(projectPath, sessionId, {
          type: 'assistant',
          content: finalText,
          ts: new Date().toISOString(),
        })
        this.emit({ type: 'FINAL_ANSWER', payload: { content: finalText, sessionId } })
        this.emit({ type: 'STATUS_CHANGE', payload: { status: 'DONE', sessionId } })
        return
      }

      this.emit({ type: 'ERROR', payload: { message: 'Exceeded max iterations', sessionId } })
      this.emit({ type: 'STATUS_CHANGE', payload: { status: 'ERROR', sessionId } })
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      if (message === 'cancelled') return
      this.emit({ type: 'ERROR', payload: { message, sessionId } })
      this.emit({ type: 'STATUS_CHANGE', payload: { status: 'ERROR', sessionId } })
    }
  }

  async compressMemories(projectId: string, sessionId: string): Promise<unknown> {
    const projectPath = this.resolveProjectPath(projectId)
    return prepareMemoryCompression(projectPath, sessionId, this.memory, this.deps.cloud)
  }

  async executeCompress(
    projectId: string,
    sessionId: string,
    plan: { deleteIds: string[]; newMemories: Array<{ content: string; importance: number }> },
  ): Promise<{ deleted: number; added: number }> {
    const projectPath = this.resolveProjectPath(projectId)
    if (plan.deleteIds?.length) this.memory.delete(projectPath, sessionId, plan.deleteIds)
    let added = 0
    for (const m of plan.newMemories ?? []) {
      let embedding: number[] | undefined
      try {
        const [emb] = await this.deps.cloud.embed([m.content])
        embedding = emb
      } catch {
        /* optional */
      }
      this.memory.add(projectPath, sessionId, { content: m.content, importance: m.importance, embedding })
      added++
    }
    return { deleted: plan.deleteIds?.length ?? 0, added }
  }
}
