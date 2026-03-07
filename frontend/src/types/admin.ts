export interface UserContainerStatus {
  userId: number
  username: string
  containerName: string
  status: string
  lastUsedAt?: number | null
  cpuPercent?: string | null
  memUsage?: string | null
  memPercent?: string | null
}

/** 管理员用户列表项（不含密码） */
export interface AdminUserListItem {
  id: number
  username: string
  email: string | null
  displayName: string | null
  status: string
  role: string
  createTime: string | null
  lastLoginTime: string | null
}

/** 系统配置项（供管理员修改） */
export interface SystemConfigItem {
  id: number
  configKey: string
  configValue: string | null
  description: string | null
}

/** 宿主机负载历史记录，用于时间轴图表 */
export interface HostLoadMetric {
  hostName: string
  timestamp: string
  cpuLoadPercent: number | null
  memAvailablePercent: number | null
  rootFsUsedPercent: number | null
}

/** 各组件连接状态（与启动摘要一致） */
export interface ComponentStatusDto {
  server: { port: string; javaVersion: string; virtualThreadEnabled: boolean }
  mysql: { ok: boolean; message: string }
  milvus: { ok: boolean; host: string; port: number; collectionName: string; dimensions: number; message: string | null }
  llm: {
    primary: { name: string; model: string; keyOk: boolean }
    fallback: { name: string; model: string; keyOk: boolean }
  }
  embedding: { ok: boolean; model: string; dimensions: number; baseUrl: string }
  docker: { ok: boolean; version: string | null; enabled: boolean; image: string | null; message: string | null }
}
