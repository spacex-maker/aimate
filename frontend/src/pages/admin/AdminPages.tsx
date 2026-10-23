import {
  BillingManageContent,
  ContainerMonitorContent,
  ModelManageContent,
  SystemConfigContent,
  UserManageContent,
} from '../../components/admin/AdminModal'
import { AdminAgentsPanel } from '../../components/admin/AdminAgentsPanel'
export { AdminKnowledgePage } from './AdminKnowledgePage'

export function AdminModelsPage() {
  return (
    <div>
      <h1 className="admin-page__title">模型管理</h1>
      <p className="admin-page__lead">系统模型与 Key 配置</p>
      <div className="mt-4">
        <ModelManageContent />
      </div>
    </div>
  )
}

export function AdminHostPage() {
  return (
    <div>
      <h1 className="admin-page__title">主机状态</h1>
      <p className="admin-page__lead">负载与组件健康</p>
      <div className="mt-4">
        <ContainerMonitorContent />
      </div>
    </div>
  )
}

export function AdminUsersPage() {
  return (
    <div>
      <h1 className="admin-page__title">用户管理</h1>
      <p className="admin-page__lead">用户列表、角色与工具设置</p>
      <div className="mt-4">
        <UserManageContent />
      </div>
    </div>
  )
}

export function AdminBillingPage() {
  return (
    <div>
      <h1 className="admin-page__title">计费管理</h1>
      <p className="admin-page__lead">用量、套餐、订单与调账</p>
      <div className="mt-4">
        <BillingManageContent />
      </div>
    </div>
  )
}

export function AdminSystemPage() {
  return (
    <div>
      <h1 className="admin-page__title">系统配置</h1>
      <p className="admin-page__lead">功能开关与系统参数</p>
      <div className="mt-4">
        <SystemConfigContent />
      </div>
    </div>
  )
}

export function AdminAgentsPage() {
  return (
    <div>
      <h1 className="admin-page__title">官方 Agent</h1>
      <p className="admin-page__lead">招聘目录、等级模型，以及与官方知识库的绑定</p>
      <div className="mt-4">
        <AdminAgentsPanel />
      </div>
    </div>
  )
}
