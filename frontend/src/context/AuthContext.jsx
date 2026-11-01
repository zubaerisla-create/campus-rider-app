import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authService } from '../services/authService'
import { onUnauthorized } from '../services/api'

const AuthContext = createContext(null)

/** Holds the signed-in user (from the server session; nothing sensitive is stored in the browser). */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    authService
      .csrf()
      .catch(() => {})
      .then(() => authService.me())
      .then((u) => alive && setUser(u))
      .catch(() => alive && setUser(null))
      .finally(() => alive && setLoading(false))
    onUnauthorized(() => setUser(null))
    return () => {
      alive = false
    }
  }, [])

  const login = useCallback(async (role, username, password) => {
    const u = await authService.login(role, username, password)
    setUser(u)
    return u
  }, [])

  const register = useCallback(async (role, body) => {
    const u = role === 'driver' ? await authService.registerDriver(body) : await authService.registerPassenger(body)
    setUser(u)
    return u
  }, [])

  const logout = useCallback(async () => {
    try {
      await authService.logout()
    } finally {
      setUser(null)
    }
  }, [])

  const refresh = useCallback(async () => setUser(await authService.me()), [])

  const value = useMemo(() => ({ user, loading, login, register, logout, refresh }), [user, loading, login, register, logout, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}

export function homeFor(user) {
  if (!user) return '/'
  return user.role === 'DRIVER' ? '/drive' : '/ride'
}
