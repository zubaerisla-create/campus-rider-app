import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import AuthCard from './AuthCard'
import AccountTypePicker, { normalizeType } from './AccountTypePicker'
import { Button, ErrorBanner, Field, Input, Segmented, Select } from '../../components/ui'
import { homeFor, useAuth } from '../../context/AuthContext'
import { useCampusMap } from '../../hooks/useCampusMap'
import { errorMessage, fieldErrors } from '../../services/api'
import { PLATE, validateAccount } from './validation'

/**
 * One signup form for both account types. The chosen type decides which
 * backend endpoint is called (passengers.csv or drivers.csv + rickshaws.csv)
 * and which dashboard the new user lands on.
 */
export default function Register() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const { type: pathType } = useParams()
  const [params, setParams] = useSearchParams()
  const { graph } = useCampusMap()
  const [type, setTypeState] = useState(normalizeType(pathType || params.get('type')))
  const [form, setForm] = useState({
    name: '', username: '', phone: '', password: '',
    passengerType: 'STUDENT', startLocationId: '', plateNumber: '', vehicleType: 'ELECTRIC',
  })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homeFor(user)} replace />
  const isDriver = type === 'driver'
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const stands = (graph?.locations || []).filter((l) => l.rickshawStand && l.reachable)

  const setType = (t) => {
    setTypeState(t)
    setErrors({})
    setError('')
    setParams({ type: t }, { replace: true })
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const v = validateAccount(form, 'name')
    if (isDriver) {
      if (!PLATE.test(form.plateNumber.trim())) v.plateNumber = 'Letters, digits, spaces and "-" (3-20 characters).'
      if (!form.startLocationId) v.startLocationId = 'Choose where you usually wait for passengers.'
    }
    setErrors(v)
    if (Object.keys(v).length) return
    const common = { username: form.username.trim(), password: form.password, phone: form.phone }
    const body = isDriver
      ? { ...common, driverName: form.name.trim(), startLocationId: form.startLocationId,
          plateNumber: form.plateNumber.trim(), vehicleType: form.vehicleType }
      : { ...common, fullName: form.name.trim(), passengerType: form.passengerType }
    setBusy(true)
    try {
      const created = await register(type, body)
      navigate(homeFor(created), { replace: true })
    } catch (err) {
      const fe = fieldErrors(err)
      // The backend names the name field fullName / driverName; show it on our single name input.
      setErrors({ ...fe, name: fe.fullName || fe.driverName })
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Create your account"
      subtitle={isDriver ? 'Register yourself and your rickshaw.' : 'Ride anywhere on the JU campus.'}
      footer={<>Already have an account? <Link to={`/login?type=${type}`} className="font-semibold text-ink hover:underline">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div>
          <span className="label">Account type</span>
          <AccountTypePicker value={type} onChange={setType} />
        </div>
        <ErrorBanner>{error}</ErrorBanner>
        <Field label="Full name" id="name" error={errors.name}>
          <Input id="name" autoComplete="name" value={form.name} onChange={set('name')} error={errors.name} />
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

        {isDriver ? (
          <div className="fade-up space-y-4 border-t border-line pt-4">
            <h2 className="text-sm font-semibold">Rickshaw</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Plate / registration no." id="plateNumber" error={errors.plateNumber}>
                <Input id="plateNumber" placeholder="DHAKA-SA-11-0106" value={form.plateNumber} onChange={set('plateNumber')}
                  error={errors.plateNumber} />
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
          </div>
        ) : (
          <div className="fade-up">
            <span className="label">I am a</span>
            <Segmented label="Passenger type" value={form.passengerType} onChange={(v) => setForm({ ...form, passengerType: v })}
              options={[{ value: 'STUDENT', label: 'Student' }, { value: 'STAFF', label: 'Teacher / staff' }]} />
          </div>
        )}
        <Button type="submit" className="w-full" size="lg" loading={busy}>
          {isDriver ? 'Create driver account' : 'Create passenger account'}
        </Button>
      </form>
    </AuthCard>
  )
}
