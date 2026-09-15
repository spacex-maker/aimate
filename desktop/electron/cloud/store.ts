import fs from 'node:fs'
import path from 'node:path'
import { app } from 'electron'

export interface AuthSession {
  userId: number
  username: string
  displayName: string
  role: string
  token: string
}

export interface AppConfig {
  serverUrl: string
  preferredModel?: string
  currentProjectId?: string
}

interface StoreSchema {
  config: AppConfig
  auth: AuthSession | null
  projects: Array<{ id: string; name: string; path: string; createdAt: string }>
  disabledSkills: string[]
}

const defaults: StoreSchema = {
  config: {
    serverUrl: 'http://localhost:9299',
  },
  auth: null,
  projects: [],
  disabledSkills: [],
}

export class AppStore {
  private file: string
  private data: StoreSchema

  constructor() {
    const dir = path.join(app.getPath('userData'), 'aimate')
    fs.mkdirSync(dir, { recursive: true })
    this.file = path.join(dir, 'store.json')
    this.data = this.load()
  }

  private load(): StoreSchema {
    try {
      if (fs.existsSync(this.file)) {
        return { ...defaults, ...JSON.parse(fs.readFileSync(this.file, 'utf8')) }
      }
    } catch {
      /* ignore */
    }
    return structuredClone(defaults)
  }

  private save(): void {
    fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2), 'utf8')
  }

  getConfig(): AppConfig {
    return { ...this.data.config }
  }

  setConfig(partial: Partial<AppConfig>): AppConfig {
    this.data.config = { ...this.data.config, ...partial }
    this.save()
    return this.getConfig()
  }

  getAuth(): AuthSession | null {
    return this.data.auth
  }

  setAuth(auth: AuthSession | null): void {
    this.data.auth = auth
    this.save()
  }

  getProjects() {
    return [...this.data.projects]
  }

  setProjects(projects: StoreSchema['projects']): void {
    this.data.projects = projects
    this.save()
  }

  getDisabledSkills(): string[] {
    return [...(this.data.disabledSkills ?? [])]
  }

  setDisabledSkills(names: string[]): void {
    this.data.disabledSkills = names
    this.save()
  }

  userDataRoot(): string {
    return path.join(app.getPath('userData'), 'aimate')
  }

  managedSkillsDir(): string {
    return path.join(this.userDataRoot(), 'skills')
  }
}
