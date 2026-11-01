import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import AuthCard from './AuthCard'
import { Button, ErrorBanner, Field, Input, Segmented, Select } from '../../components/ui'
import { homeFor, useAuth } from '../../context/AuthContext'
import { useCampusMap } from '../../hooks/useCampusMap'
import { errorMessage, fieldErrors } from '../../services/api'
import { PLATE, validateAccount } from './validation'

export default function DriverRegister() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const { graph } = useCampusMap()
  const [form, setForm] = useState({
    driverName: '', username: '', phone: '', password: '', startLocationId: '', plateNumber: '', vehicleType: 'ELECTRIC',
  })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homeFor(user)} replace />
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const stands = (graph?.locations || []).filter((l) => l.rickshawStand && l.reachable)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const v = validateAccount(form, 'driverName')
    if (!PLATE.test(form.plateNumber.trim())) v.plateNumber = 'Letters, digits, spaces and "-" (3-20 characters).'
    if (!form.startLocationId) v.startLocationId = 'Choose where you usually wait for passengers.'
    setErrors(v)
    if (Object.keys(v).length) return
    setBusy(true)
    try {
      await register('driver', { ...form, username: form.username.trim(), driverName: form.driverName.trim(), plateNumber: form.plateNumber.trim() })
      navigate('/drive', { replace: true })
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Drive with Campus Rider" subtitle="Register yourself and your rickshaw."
      footer={<>Already registered? <Link to="/login/driver" className="font-semibold text-ink hover:underline">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorBanner>{error}</ErrorBanner>
        <Field label="Full name" id="driverName" error={errors.driverName}>
          <Input id="driverName" autoComplete="name" value={form.driverName} onChange={set('driverName')} error={errors.driverName} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Username" id="username" error={errors.username}>
            <Input id="username" autoComplete="username" value={form.username} onChange={set('username')} error={errors.username} />
          </Field>
          <Field label="Mobile number" id="phone" error={errors.phone}>
            <Input id="phone" inputMode="tel" placeholder="01XXXXXXXXX" value={form.phone} onChange={set('phone')} error={errors.phone} />
          </Field>
        </div>
        <Field label="Password" id="password" error={errors.password} hint="At least 6 characters">
          <Input id="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} />
        </Field>

        <div className="pt-2">
          <h2 className="text-sm font-semibold">Rickshaw</h2>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Plate / registration no." id="plateNumber" error={errors.plateNumber}>
            <Input id="plateNumber" placeholder="DHAKA-SA-11-0106" value={form.plateNumber} onChange={set('plateNumber')} error={errors.plateNumber} />
          </Field>
          <Field label="Home stand" id="startLocationId" error={errors.startLocationId}>
            <Select id="startLocationId" value={form.startLocationId} onChange={set('startLocationId')} error={errors.startLocationId}>
              <option value="">Choose a stand</option>
              {stands.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
            </Select>
          </Field>
        </div>
        <div>
          <span className="label">Type</span>
          <Segmented label="Rickshaw type" value={form.vehicleType} onChange={(v) => setForm({ ...form, vehicleType: v })}
            options={[{ value: 'ELECTRIC', label: 'Battery (auto)' }, { value: 'PEDAL', label: 'Pedal' }]} />
        </div>
        <p className="text-sm text-muted">Every rickshaw carries up to 3 passengers. You start offline; go online from your dashboard.</p>
        <Button type="submit" className="w-full" size="lg" loading={busy}>Register</Button>
      </form>
    </AuthCard>
  )
}
