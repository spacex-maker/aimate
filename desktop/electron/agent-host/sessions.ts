import fs from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { ensureDir } from './projects'

export type TranscriptEvent =
  | { type: 'user'; content: string; ts: string }
  | { type: 'assistant'; content: string; ts: string }
  | { type: 'thinking'; content: string; ts: string }
  | { type: 'tool_call'; name: string; args: string; id: string; ts: string }
  | { type: 'tool_result'; id: string; result: string; ts: string }
  | { type: 'status'; status: string; ts: string }
  | { type: 'summary'; content: string; ts: string }

export interface SessionInfo {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

function sessionsDir(projectPath: string): string {
  return path.join(projectPath, 'sessions')
}

function sessionMetaPath(projectPath: string, sessionId: string): string {
  return path.join(sessionsDir(projectPath), `${sessionId}.json`)
}

function sessionTranscriptPath(projectPath: string, sessionId: string): string {
  return path.join(sessionsDir(projectPath), `${sessionId}.jsonl`)
}

export function listSessions(projectPath: string): SessionInfo[] {
  const dir = sessionsDir(projectPath)
  ensureDir(dir)
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json') && !f.endsWith('.memory.json'))
    .map((f) => {
      try {
        const parsed = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')) as SessionInfo
        if (!parsed?.id || !parsed?.title) return null
        return parsed
      } catch {
        return null
      }
    })
    .filter((s): s is SessionInfo => !!s)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}

export function createSession(projectPath: string, title?: string): SessionInfo {
  ensureDir(sessionsDir(projectPath))
  const now = new Date().toISOString()
  const info: SessionInfo = {
    id: randomUUID(),
    title: title || '新会话',
    createdAt: now,
    updatedAt: now,
  }
  fs.writeFileSync(sessionMetaPath(projectPath, info.id), JSON.stringify(info, null, 2), 'utf8')
  fs.writeFileSync(sessionTranscriptPath(projectPath, info.id), '', 'utf8')
  return info
}

export function updateSessionTitle(projectPath: string, sessionId: string, title: string): SessionInfo {
  const info = JSON.parse(fs.readFileSync(sessionMetaPath(projectPath, sessionId), 'utf8')) as SessionInfo
  info.title = title
  info.updatedAt = new Date().toISOString()
  fs.writeFileSync(sessionMetaPath(projectPath, sessionId), JSON.stringify(info, null, 2), 'utf8')
  return info
}

export function appendEvent(projectPath: string, sessionId: string, event: TranscriptEvent): void {
  const line = JSON.stringify(event) + '\n'
  fs.appendFileSync(sessionTranscriptPath(projectPath, sessionId), line, 'utf8')
  try {
    const info = JSON.parse(fs.readFileSync(sessionMetaPath(projectPath, sessionId), 'utf8')) as SessionInfo
    info.updatedAt = new Date().toISOString()
    if (event.type === 'user' && info.title === '新会话') {
      info.title = event.content.slice(0, 40)
    }
    fs.writeFileSync(sessionMetaPath(projectPath, sessionId), JSON.stringify(info, null, 2), 'utf8')
  } catch {
    /* ignore */
  }
}

export function readTranscript(projectPath: string, sessionId: string): TranscriptEvent[] {
  const file = sessionTranscriptPath(projectPath, sessionId)
  if (!fs.existsSync(file)) return []
  return fs
    .readFileSync(file, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      try {
        return JSON.parse(line) as TranscriptEvent
      } catch {
        return null
      }
    })
    .filter((e): e is TranscriptEvent => !!e)
}

export function deleteSession(projectPath: string, sessionId: string): void {
  for (const p of [
    sessionMetaPath(projectPath, sessionId),
    sessionTranscriptPath(projectPath, sessionId),
    path.join(sessionsDir(projectPath), `${sessionId}.memory.json`),
  ]) {
    if (fs.existsSync(p)) fs.unlinkSync(p)
  }
}
