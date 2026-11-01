import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import AuthCard from './AuthCard'
import { Button, ErrorBanner, Field, Input } from '../../components/ui'
import { homeFor, useAuth } from '../../context/AuthContext'
import { errorMessage } from '../../services/api'

const DEMO = {
  passenger: ['tanvir', 'nila', 'rafiq', 'emergency'],
  driver: ['karim', 'mokbul', 'salam', 'rahim', 'jamal'],
}

export default function Login({ role }) {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ username: '', password: '' })
  const [show, setShow] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const isDriver = role === 'driver'

  if (user) return <Navigate to={homeFor(user)} replace />

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.username.trim() || !form.password) {
      setError('Enter your username and password.')
      return
    }
    setBusy(true)
    try {
      const u = await login(role, form.username.trim(), form.password)
      const from = location.state?.from
      const expected = u.role === 'DRIVER' ? '/drive' : '/ride'
      navigate(from && from.startsWith(expected) ? from : homeFor(u), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard
      title={isDriver ? 'Driver log in' : 'Log in to ride'}
      subtitle={isDriver ? 'Go online and start receiving ride requests.' : 'Book a rickshaw anywhere on campus.'}
      footer={
        <>
          {isDriver ? 'New driver? ' : 'New to Campus Rider? '}
          <Link to={isDriver ? '/register/driver' : '/register/passenger'} className="font-semibold text-ink hover:underline">
            Create an account
          </Link>
          <div className="mt-3">
            <Link to={isDriver ? '/login/passenger' : '/login/driver'} className="hover:text-ink">
              {isDriver ? 'Log in as a passenger instead' : 'Are you a driver? Log in here'}
            </Link>
          </div>
        </>
      }>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorBanner>{error}</ErrorBanner>
        <Field label="Username" id="username">
          <Input id="username" autoComplete="username" autoFocus value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })} />
        </Field>
        <Field label="Password" id="password">
          <div className="relative">
            <Input id="password" type={show ? 'text' : 'password'} autoComplete="current-password" className="pr-11"
              value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted hover:text-ink">
              {show ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </Field>
        <Button type="submit" className="w-full" size="lg" loading={busy}>Log in</Button>
      </form>
      <div className="mt-6 rounded-xl bg-canvas px-4 py-3 text-sm text-muted">
        <span className="font-medium text-ink">Demo accounts</span> (password <code className="font-mono">1234</code>):{' '}
        {DEMO[role].map((u, i) => (
          <span key={u}>
            <button type="button" className="font-medium text-ink hover:underline"
              onClick={() => setForm({ username: u, password: '1234' })}>{u}</button>
            {i < DEMO[role].length - 1 ? ', ' : ''}
          </span>
        ))}
      </div>
    </AuthCard>
  )
}
