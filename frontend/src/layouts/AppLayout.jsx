import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { History, LogOut, Map as MapIcon, User, Wifi, WifiOff } from 'lucide-react'
import Logo from '../components/Logo'
import { Avatar } from '../components/ui'
import { homeFor, useAuth } from '../context/AuthContext'
import { useRealtime } from '../context/RealtimeContext'

/** Signed-in shell: slim top bar with app sections and the account menu. */
export default function AppLayout() {
  const { user, logout } = useAuth()
  const { connected } = useRealtime()
  const navigate = useNavigate()
  const [menu, setMenu] = useState(false)
  const base = user?.role === 'DRIVER' ? '/drive' : '/ride'
  const tabs = [
    { to: base, label: user?.role === 'DRIVER' ? 'Drive' : 'Ride', icon: MapIcon, end: true },
    { to: `${base}/history`, label: 'History', icon: History },
    { to: `${base}/profile`, label: 'Account', icon: User },
  ]

  const doLogout = async () => {
    await logout()
    navigate('/')
  }

  return (
    <div className="flex h-full flex-col">
      <header className="z-40 flex h-14 shrink-0 items-center justify-between border-b border-line bg-white px-4">
        <div className="flex items-center gap-6">
          <Logo to={homeFor(user)} />
          <nav className="hidden items-center gap-1 sm:flex">
            {tabs.map((t) => (
              <NavLink key={t.to} to={t.to} end={t.end}
                className={({ isActive }) => `rounded-lg px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-canvas text-ink' : 'text-muted hover:text-ink'}`}>
                {t.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <span className={`hidden items-center gap-1.5 text-xs sm:flex ${connected ? 'text-accent-strong' : 'text-faint'}`}
            title={connected ? 'Live updates connected' : 'Reconnecting live updates…'}>
            {connected ? <Wifi size={14} /> : <WifiOff size={14} />} {connected ? 'Live' : 'Offline'}
          </span>
          <div className="relative">
            <button onClick={() => setMenu((m) => !m)} className="flex items-center gap-2 rounded-full p-0.5 hover:bg-canvas"
              aria-haspopup="menu" aria-expanded={menu} aria-label="Account menu">
              <Avatar name={user?.name} size={34} />
            </button>
            {menu && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setMenu(false)} />
                <div role="menu" className="absolute right-0 z-50 mt-2 w-60 rounded-xl border border-line bg-white p-1.5 shadow-lg">
                  <div className="px-3 py-2">
                    <p className="truncate text-sm font-semibold">{user?.name}</p>
                    <p className="truncate text-xs text-muted">@{user?.username} · {user?.role === 'DRIVER' ? 'Driver' : 'Passenger'}</p>
                  </div>
                  <button role="menuitem" onClick={doLogout}
                    className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-canvas">
                    <LogOut size={16} /> Log out
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </header>
      <div className="relative min-h-0 flex-1">
        <Outlet />
      </div>
      {/* Mobile tab bar */}
      <nav className="z-40 grid shrink-0 grid-cols-3 border-t border-line bg-white sm:hidden" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end}
            className={({ isActive }) => `flex flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${isActive ? 'text-ink' : 'text-faint'}`}>
            <t.icon size={20} /> {t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
