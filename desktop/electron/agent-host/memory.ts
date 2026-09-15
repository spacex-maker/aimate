import fs from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { ensureDir } from './projects'

export interface MemoryRecord {
  id: string
  content: string
  memoryType: string
  importance: number
  noCompress: boolean
  embedding?: number[]
  createdAt: string
  updatedAt: string
  sessionId: string
}

function memoryFile(projectPath: string, sessionId: string): string {
  return path.join(projectPath, 'sessions', `${sessionId}.memory.json`)
}

function loadAll(projectPath: string, sessionId: string): MemoryRecord[] {
  const file = memoryFile(projectPath, sessionId)
  if (!fs.existsSync(file)) return []
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as MemoryRecord[]
  } catch {
    return []
  }
}

function saveAll(projectPath: string, sessionId: string, records: MemoryRecord[]): void {
  ensureDir(path.join(projectPath, 'sessions'))
  fs.writeFileSync(memoryFile(projectPath, sessionId), JSON.stringify(records, null, 2), 'utf8')
}

function cosine(a: number[], b: number[]): number {
  const n = Math.min(a.length, b.length)
  let dot = 0
  let na = 0
  let nb = 0
  for (let i = 0; i < n; i++) {
    dot += a[i] * b[i]
    na += a[i] * a[i]
    nb += b[i] * b[i]
  }
  if (na === 0 || nb === 0) return 0
  return dot / (Math.sqrt(na) * Math.sqrt(nb))
}

export class MemoryStore {
  list(projectPath: string, sessionId: string): MemoryRecord[] {
    return loadAll(projectPath, sessionId).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  }

  add(
    projectPath: string,
    sessionId: string,
    input: { content: string; memoryType?: string; importance?: number; embedding?: number[] },
  ): MemoryRecord {
    const records = loadAll(projectPath, sessionId)
    const now = new Date().toISOString()
    const rec: MemoryRecord = {
      id: randomUUID(),
      sessionId,
      content: input.content,
      memoryType: input.memoryType || 'NOTE',
      importance: input.importance ?? 0.5,
      noCompress: false,
      embedding: input.embedding,
      createdAt: now,
      updatedAt: now,
    }
    records.push(rec)
    saveAll(projectPath, sessionId, records)
    return rec
  }

  update(
    projectPath: string,
    sessionId: string,
    id: string,
    patch: Partial<Pick<MemoryRecord, 'content' | 'noCompress' | 'importance' | 'embedding'>>,
  ): MemoryRecord | null {
    const records = loadAll(projectPath, sessionId)
    const idx = records.findIndex((r) => r.id === id)
    if (idx < 0) return null
    records[idx] = { ...records[idx], ...patch, updatedAt: new Date().toISOString() }
    saveAll(projectPath, sessionId, records)
    return records[idx]
  }

  delete(projectPath: string, sessionId: string, ids: string[]): void {
    const idSet = new Set(ids)
    saveAll(
      projectPath,
      sessionId,
      loadAll(projectPath, sessionId).filter((r) => !idSet.has(r.id)),
    )
  }

  recall(projectPath: string, sessionId: string, queryEmbedding: number[], topK = 5): MemoryRecord[] {
    const scored = loadAll(projectPath, sessionId)
      .filter((r) => r.embedding && r.embedding.length)
      .map((r) => ({ r, score: cosine(queryEmbedding, r.embedding!) }))
      .sort((a, b) => b.score - a.score)
      .slice(0, topK)
    return scored.map((s) => s.r)
  }

  searchableText(projectPath: string, sessionId: string, query: string): MemoryRecord[] {
    const q = query.toLowerCase()
    return loadAll(projectPath, sessionId).filter((r) => r.content.toLowerCase().includes(q))
  }

  deleteSessionFile(projectPath: string, sessionId: string): void {
    const file = memoryFile(projectPath, sessionId)
    if (fs.existsSync(file)) fs.unlinkSync(file)
  }
}
