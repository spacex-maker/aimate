import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { Navbar } from './components/layout/Navbar'
import { RequireAuth } from './components/layout/RequireAuth'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { HomePage } from './pages/HomePage'
import { SessionPage } from './pages/SessionPage'
import { MemoryPage } from './pages/MemoryPage'
import { ApiKeyPage } from './pages/ApiKeyPage'
import { EmbeddingModelPage } from './pages/EmbeddingModelPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 10_000,
    },
  },
})

/** Layout with sidebar — only shown for authenticated routes */
function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="h-screen flex bg-[#0d0d0d] text-white overflow-hidden">
      <div className="w-52 flex-shrink-0">
        <Navbar />
      </div>
      <main className="flex-1 overflow-hidden">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
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
    </QueryClientProvider>
  )
}
