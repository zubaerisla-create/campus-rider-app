import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homeFor, useAuth } from '../context/AuthContext'
import { Spinner } from '../components/ui'

/** Only lets the given role through; others are sent to their own home or to the right login page. */
export default function ProtectedRoute({ role }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner label="Loading Campus Rider" className="h-full" />
  if (!user) {
    return <Navigate to={role === 'DRIVER' ? '/login/driver' : '/login/passenger'} replace state={{ from: location.pathname }} />
  }
  if (user.role !== role) return <Navigate to={homeFor(user)} replace />
  return <Outlet />
}
