import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import './admin.css'
import App from './App.tsx'
import AdminApp from './AdminApp.tsx'
import { AdminAuthProvider } from './auth/AdminAuthContext'
import { isAdminHost } from './lib/host'

const adminHost = isAdminHost()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter future={{ v7_relativeSplatPath: true }}>
      {adminHost ? (
        <AdminAuthProvider>
          <AdminApp />
        </AdminAuthProvider>
      ) : (
        <App />
      )}
    </BrowserRouter>
  </StrictMode>
)
