import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { ModelSelectionProvider } from './state/modelSelection.ts'
import { ChatInputProvider } from './state/chatInput.ts'
import { Navbar } from './components/layout/Navbar'
import { RequireAuth } from './components/layout/RequireAuth'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { HomePage } from './pages/HomePage'
import { SessionPage } from './pages/SessionPage'
import { MemoryPage } from './pages/MemoryPage'
import { ApiKeyPage } from './pages/ApiKeyPage'
import { EmbeddingModelPage } from './pages/EmbeddingModelPage'
import { ToolsPage } from './pages/ToolsPage'
import { AdminContainerPage } from './pages/AdminContainerPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 10_000,
    },
  },
})

const SIDEBAR_EXPANDED = '13rem'
const SIDEBAR_COLLAPSED = '3.5rem'
const SIDEBAR_BREAKPOINT = 640
const STORAGE_KEY = 'ofx_sidebar_collapsed'

function useIsNarrow(breakpoint: number) {
  const [isNarrow, setIsNarrow] = React.useState(() => {
    if (typeof window === 'undefined') return false
    return window.matchMedia(`(max-width: ${breakpoint - 1}px)`).matches
  })
  React.useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${breakpoint - 1}px)`)
    const handler = () => setIsNarrow(mql.matches)
    mql.addEventListener('change', handler)
    return () => mql.removeEventListener('change', handler)
  }, [breakpoint])
  return isNarrow
}

/** Layout with sidebar — only shown for authenticated routes；侧栏可收起以适配移动端，窄屏时自动收起 */
function AppLayout({ children }: { children: React.ReactNode }) {
  const isNarrow = useIsNarrow(SIDEBAR_BREAKPOINT)
  const [savedCollapsed, setSavedCollapsed] = React.useState(() => {
    if (typeof window === 'undefined') return false
    try {
      return localStorage.getItem(STORAGE_KEY) === '1'
    } catch {
      return false
    }
  })
  const collapsed = isNarrow || savedCollapsed
  const width = collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED
  const toggle = () => {
    setSavedCollapsed((c) => {
      const next = !c
      try {
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
      } catch {}
      return next
    })
  }
  return (
    <div
      className="h-screen flex bg-[#0d0d0d] text-white overflow-hidden"
      style={{ ['--sidebar-width' as string]: width }}
    >
      <aside
        className="flex-shrink-0 flex flex-col transition-[width] duration-200 ease-out overflow-hidden border-r border-white/10 bg-[#111]"
        style={{ width }}
      >
        <Navbar collapsed={collapsed} onToggle={toggle} />
      </aside>
      <main className="flex-1 min-w-0 overflow-hidden">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ModelSelectionProvider>
        <ChatInputProvider>
        <BrowserRouter future={{ v7_relativeSplatPath: true }}>
        <Routes>
          {/* ── Public routes (no sidebar) ─────────────────────────────── */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* ── Protected routes (require login) ──────────────────────── */}
          <Route
            path="/"
            element={
              <RequireAuth>
                <AppLayout>
                  <HomePage />
                </AppLayout>
              </RequireAuth>
            }
          />
          <Route
            path="/session/:sessionId"
            element={
              <RequireAuth>
                <AppLayout>
                  <SessionPage />
                </AppLayout>
              </RequireAuth>
            }
          />
          <Route
            path="/memory"
            element={
              <RequireAuth>
                <AppLayout>
                  <MemoryPage />
                </AppLayout>
              </RequireAuth>
            }
          />

          <Route
            path="/api-keys"
            element={
              <RequireAuth>
                <AppLayout>
                  <ApiKeyPage />
                </AppLayout>
              </RequireAuth>
            }
          />

          <Route
            path="/embedding-models"
            element={
              <RequireAuth>
                <AppLayout>
                  <EmbeddingModelPage />
                </AppLayout>
              </RequireAuth>
            }
          />

          <Route
            path="/tools"
            element={
              <RequireAuth>
                <AppLayout>
                  <ToolsPage />
                </AppLayout>
              </RequireAuth>
            }
          />

          <Route
            path="/admin/containers"
            element={
              <RequireAuth>
                <AppLayout>
                  <AdminContainerPage />
                </AppLayout>
              </RequireAuth>
            }
          />

          {/* Fallback */}
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
      </BrowserRouter>
      </ChatInputProvider>
      </ModelSelectionProvider>
    </QueryClientProvider>
  )
}
