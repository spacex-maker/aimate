/** 工具列表项（系统 + 用户），与后端 UserToolDto 一致 */
export interface UserToolDto {
  id: number
  toolName: string
  toolDescription: string
  toolType: string
  isActive: boolean
  isSystem: boolean
  userId: number | null
  inputSchema?: string | null
  scriptContent?: string | null
  entryPoint?: string | null
}

/** 更新用户工具请求（字段均可选） */
export interface UpdateUserToolRequest {
  toolDescription?: string
  inputSchema?: string
  scriptContent?: string
  entryPoint?: string
  isActive?: boolean
}
