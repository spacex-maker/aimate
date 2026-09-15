import fs from 'node:fs'
import path from 'node:path'
import { app } from 'electron'
import { randomUUID } from 'node:crypto'

export interface ProjectMeta {
  id: string
  name: string
  path: string
  createdAt: string
}

export function ensureDir(dir: string): void {
  fs.mkdirSync(dir, { recursive: true })
}

export function createProjectAt(rootPath: string, name: string): ProjectMeta {
  ensureDir(rootPath)
  ensureDir(path.join(rootPath, 'sessions'))
  ensureDir(path.join(rootPath, 'skills'))
  ensureDir(path.join(rootPath, 'memory'))
  const meta: ProjectMeta = {
    id: randomUUID(),
    name,
    path: rootPath,
    createdAt: new Date().toISOString(),
  }
  fs.writeFileSync(path.join(rootPath, 'project.json'), JSON.stringify(meta, null, 2), 'utf8')
  return meta
}

export function readProjectMeta(rootPath: string): ProjectMeta | null {
  const file = path.join(rootPath, 'project.json')
  if (!fs.existsSync(file)) return null
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as ProjectMeta
  } catch {
    return null
  }
}

export function bundledSkillsDir(): string {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'skills')
  }
  return path.join(app.getAppPath(), 'skills')
}
