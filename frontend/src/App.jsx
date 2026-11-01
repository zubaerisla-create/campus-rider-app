import { AuthProvider } from './context/AuthContext'
import { RealtimeProvider } from './context/RealtimeContext'
import { ToastProvider } from './context/ToastContext'
import AppRoutes from './routes/AppRoutes'

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <RealtimeProvider>
          <AppRoutes />
        </RealtimeProvider>
      </AuthProvider>
    </ToastProvider>
  )
}
