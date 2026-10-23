// 与后端 AuthResponse 一致，Jackson 默认输出驼峰
export interface AuthResponse {
  userId: number
  username: string
  displayName: string
  /** 统一登录中心同步的头像 */
  avatarUrl?: string | null
  role: 'USER' | 'ADMIN'
  token: string
}

export interface RegisterRequest {
  username: string
  email?: string
  password: string
  displayName?: string
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface AuthUser {
  userId: number
  username: string
  displayName: string
  avatarUrl?: string | null
  role: 'USER' | 'ADMIN'
  token: string
}
