import type { SystemModelDto, ToolSettingsDto } from '../types/agent'
import type { AdminUserListItem, SystemConfigItem, UserContainerStatus } from '../types/admin'
import { http } from './httpClient'

export const adminApi = {
  listContainers: () =>
    http<UserContainerStatus[]>('/api/admin/containers'),

  /** 管理员：获取全部系统模型（含已关闭） */
  listAllSystemModels: () =>
    http<SystemModelDto[]>('/api/admin/system-models'),

  /** 管理员：更新系统模型启用状态 */
  updateSystemModelEnabled: (id: number, enabled: boolean) =>
    http<SystemModelDto>(`/api/admin/system-models/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取全部用户列表 */
  listUsers: () =>
    http<AdminUserListItem[]>('/api/admin/users'),

  /** 管理员：更新用户状态或角色 */
  updateUser: (id: number, body: { status?: string; role?: string }) =>
    http<AdminUserListItem>(`/api/admin/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取指定用户的系统工具设置 */
  getUserToolSettings: (userId: number) =>
    http<ToolSettingsDto>(`/api/admin/users/${userId}/tool-settings`),

  /** 管理员：更新指定用户的系统工具设置 */
  updateUserToolSettings: (userId: number, body: ToolSettingsDto) =>
    http<ToolSettingsDto>(`/api/admin/users/${userId}/tool-settings`, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),

  /** 管理员：获取全部系统配置项 */
  listSystemConfigs: () =>
    http<SystemConfigItem[]>('/api/admin/system-configs'),

  /** 管理员：更新系统配置项的值与说明 */
  updateSystemConfig: (id: number, body: { configValue?: string | null; description?: string | null }) =>
    http<SystemConfigItem>(`/api/admin/system-configs/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    }),
}
