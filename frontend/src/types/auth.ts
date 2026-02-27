// Backend uses SNAKE_CASE (Spring Jackson global config)
export interface AuthResponse {
  user_id: number
  username: string
  display_name: string
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
  token: string
}
