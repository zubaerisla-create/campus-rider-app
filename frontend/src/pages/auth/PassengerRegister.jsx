import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import AuthCard from './AuthCard'
import { Button, ErrorBanner, Field, Input, Segmented } from '../../components/ui'
import { homeFor, useAuth } from '../../context/AuthContext'
import { errorMessage, fieldErrors } from '../../services/api'
import { validateAccount } from './validation'

export default function PassengerRegister() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', username: '', phone: '', password: '', passengerType: 'STUDENT' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homeFor(user)} replace />
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const v = validateAccount(form, 'fullName')
    setErrors(v)
    if (Object.keys(v).length) return
    setBusy(true)
    try {
      await register('passenger', { ...form, username: form.username.trim(), fullName: form.fullName.trim() })
      navigate('/ride', { replace: true })
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Create your account" subtitle="Ride anywhere on the JU campus."
      footer={<>Already have an account? <Link to="/login/passenger" className="font-semibold text-ink hover:underline">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorBanner>{error}</ErrorBanner>
        <Field label="Full name" id="fullName" error={errors.fullName}>
          <Input id="fullName" autoComplete="name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Username" id="username" error={errors.username}>
            <Input id="username" autoComplete="username" value={form.username} onChange={set('username')} error={errors.username} />
          </Field>
          <Field label="Mobile number" id="phone" error={errors.phone}>
            <Input id="phone" inputMode="tel" autoComplete="tel" placeholder="01XXXXXXXXX" value={form.phone}
              onChange={set('phone')} error={errors.phone} />
          </Field>
        </div>
        <Field label="Password" id="password" error={errors.password} hint="At least 6 characters">
          <Input id="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')}
            error={errors.password} />
        </Field>
        <div>
          <span className="label">I am a</span>
          <Segmented label="Passenger type" value={form.passengerType} onChange={(v) => setForm({ ...form, passengerType: v })}
            options={[{ value: 'STUDENT', label: 'Student' }, { value: 'STAFF', label: 'Teacher / staff' }]} />
        </div>
        <Button type="submit" className="w-full" size="lg" loading={busy}>Create account</Button>
      </form>
    </AuthCard>
  )
}
