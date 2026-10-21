import { Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { useAdminAuth } from './auth/AdminAuthContext'
import { AdminLayout } from './components/admin/AdminLayout'
import { AdminLoginPage } from './pages/admin/AdminLoginPage'
import { AdminSsoPage } from './pages/admin/AdminSsoPage'
import {
  AdminBillingPage,
  AdminHostPage,
  AdminModelsPage,
  AdminSystemPage,
  AdminUsersPage,
} from './pages/admin/AdminPages'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 10_000 },
  },
})

function RequireAdminSession({ children }: { children: React.ReactNode }) {
  const { state } = useAdminAuth()
  if (state.status === 'loading') {
    return (
      <div className="admin-shell flex min-h-screen items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    )
  }
  if (state.status !== 'authed') {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

function AdminHomeGate() {
  const { state } = useAdminAuth()
  if (state.status === 'loading') {
    return (
      <div className="admin-shell flex min-h-screen items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    )
  }
  if (state.status === 'authed') {
    return <Navigate to="/admin" replace />
  }
  return <Navigate to="/login" replace />
}

export default function AdminApp() {
  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route path="/" element={<AdminHomeGate />} />
        <Route path="/login" element={<AdminLoginPage />} />
        <Route path="/admin/sso" element={<AdminSsoPage />} />
        <Route
          element={
            <RequireAdminSession>
              <AdminLayout />
            </RequireAdminSession>
          }
        >
          <Route path="/admin" element={<AdminModelsPage />} />
          <Route path="/admin/host" element={<AdminHostPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/billing" element={<AdminBillingPage />} />
          <Route path="/admin/system" element={<AdminSystemPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: {
            background: '#1a1a1a',
            color: '#fff',
            border: '1px solid rgba(255,255,255,0.1)',
            fontSize: '13px',
          },
        }}
      />
    </QueryClientProvider>
  )
}
