import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import Logo from '../components/Logo'
import { Button } from '../components/ui'
import { homeFor, useAuth } from '../context/AuthContext'

export default function PublicLayout() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const link = ({ isActive }) => `text-[15px] font-medium ${isActive ? 'text-ink' : 'text-muted hover:text-ink'}`

  return (
    <div className="flex min-h-full flex-col bg-white">
      <header className="sticky top-0 z-40 border-b border-line bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
          <Logo />
          <nav className="hidden items-center gap-7 md:flex">
            <NavLink to="/about" className={link}>About</NavLink>
            <NavLink to="/register/driver" className={link}>Drive with us</NavLink>
          </nav>
          <div className="hidden items-center gap-2 md:flex">
            {user ? (
              <Button to={homeFor(user)} size="sm">Open app</Button>
            ) : (
              <>
                <Button to="/login/passenger" variant="ghost" size="sm">Log in</Button>
                <Button to="/register/passenger" size="sm">Sign up</Button>
              </>
            )}
          </div>
          <button className="md:hidden" onClick={() => setOpen((o) => !o)} aria-label="Menu" aria-expanded={open}>
            {open ? <X /> : <Menu />}
          </button>
        </div>
        {open && (
          <div className="border-t border-line px-5 py-4 md:hidden">
            <div className="flex flex-col gap-4" onClick={() => setOpen(false)}>
              <Link to="/about" className="font-medium">About</Link>
              <Link to="/register/driver" className="font-medium">Drive with us</Link>
              <Link to="/login/driver" className="font-medium">Driver log in</Link>
              {user ? (
                <Button to={homeFor(user)}>Open app</Button>
              ) : (
                <div className="grid grid-cols-2 gap-2">
                  <Button to="/login/passenger" variant="outline">Log in</Button>
                  <Button to="/register/passenger">Sign up</Button>
                </div>
              )}
            </div>
          </div>
        )}
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-line bg-white">
        <div className="mx-auto grid max-w-6xl gap-8 px-5 py-10 md:grid-cols-4">
          <div className="md:col-span-2">
            <Logo />
            <p className="mt-3 max-w-sm text-sm text-muted">
              Shared rickshaw rides across the Jahangirnagar University campus. A Data Structures &amp; Algorithms
              course project.
            </p>
          </div>
          <div>
            <h4 className="text-sm font-semibold">Ride</h4>
            <ul className="mt-3 space-y-2 text-sm text-muted">
              <li><Link to="/login/passenger" className="hover:text-ink">Passenger log in</Link></li>
              <li><Link to="/register/passenger" className="hover:text-ink">Create account</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold">Drive</h4>
            <ul className="mt-3 space-y-2 text-sm text-muted">
              <li><Link to="/login/driver" className="hover:text-ink">Driver log in</Link></li>
              <li><Link to="/register/driver" className="hover:text-ink">Register your rickshaw</Link></li>
              <li><Link to="/about" className="hover:text-ink">How it works</Link></li>
            </ul>
          </div>
        </div>
        <div className="border-t border-line py-5 text-center text-xs text-faint">
          © {new Date().getFullYear()} Campus Rider · Jahangirnagar University, Savar · Live tracking is simulated
        </div>
      </footer>
    </div>
  )
}
