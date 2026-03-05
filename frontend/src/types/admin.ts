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
