import { Route, Routes } from 'react-router-dom'
import PublicLayout from '../layouts/PublicLayout'
import AppLayout from '../layouts/AppLayout'
import ProtectedRoute from './ProtectedRoute'
import Landing from '../pages/Landing'
import About from '../pages/About'
import NotFound from '../pages/NotFound'
import Login from '../pages/auth/Login'
import Register from '../pages/auth/Register'
import Book from '../pages/passenger/Book'
import PassengerHistory from '../pages/passenger/History'
import PassengerProfile from '../pages/passenger/Profile'
import DriverDashboard from '../pages/driver/Dashboard'
import DriverHistory from '../pages/driver/History'
import DriverProfile from '../pages/driver/Profile'

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<Landing />} />
        <Route path="about" element={<About />} />
        <Route path="login" element={<Login />} />
        <Route path="login/passenger" element={<Login key="p" role="passenger" />} />
        <Route path="login/driver" element={<Login key="d" role="driver" />} />
        <Route path="register" element={<Register />} />
        <Route path="register/:type" element={<Register />} />
      </Route>

      <Route element={<ProtectedRoute role="PASSENGER" />}>
        <Route path="ride" element={<AppLayout />}>
          <Route index element={<Book />} />
          <Route path="history" element={<PassengerHistory />} />
          <Route path="profile" element={<PassengerProfile />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute role="DRIVER" />}>
        <Route path="drive" element={<AppLayout />}>
          <Route index element={<DriverDashboard />} />
          <Route path="history" element={<DriverHistory />} />
          <Route path="profile" element={<DriverProfile />} />
        </Route>
      </Route>

      <Route element={<PublicLayout />}>
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
