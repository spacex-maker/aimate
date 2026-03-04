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
