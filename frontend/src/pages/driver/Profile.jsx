import { useEffect, useState } from 'react'
import PasswordFields from '../../components/PasswordFields'
import { Avatar, Button, ErrorBanner, Field, Input, Segmented, Spinner } from '../../components/ui'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { errorMessage, fieldErrors } from '../../services/api'
import { driverService } from '../../services/driverService'
import { NAME, PHONE, PLATE } from '../auth/validation'

export default function DriverProfile() {
  const { refresh } = useAuth()
  const toast = useToast()
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const fill = (p) => {
    setProfile(p)
    setForm({ driverName: p.driverName, phone: p.phone, plateNumber: p.plateNumber, vehicleType: p.vehicleType,
      currentPassword: '', newPassword: '' })
  }

  useEffect(() => {
    driverService.profile().then(fill).catch((e) => setError(errorMessage(e)))
  }, [])

  const save = async (e) => {
    e.preventDefault()
    const v = {}
    if (!NAME.test(form.driverName.trim())) v.driverName = 'Enter your name (letters only).'
    if (!PHONE.test(form.phone)) v.phone = 'Enter an 11-digit mobile number.'
    if (!PLATE.test(form.plateNumber.trim())) v.plateNumber = 'Letters, digits, spaces and "-" (3-20 characters).'
    if (form.newPassword && form.newPassword.length < 6) v.newPassword = 'Use at least 6 characters.'
    setErrors(v)
    if (Object.keys(v).length) return
    setBusy(true)
    setError('')
    try {
      fill(await driverService.updateProfile({ ...form, driverName: form.driverName.trim(), plateNumber: form.plateNumber.trim() }))
      await refresh()
      toast.push('Profile saved.', 'success')
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-2xl font-bold tracking-tight">Account</h1>
        {!form && !error && <Spinner />}
        <ErrorBanner className="mt-4">{error}</ErrorBanner>
        {form && (
          <>
            <div className="mt-6 flex items-center gap-4">
              <Avatar name={profile.driverName} size={56} />
              <div>
                <p className="text-lg font-semibold">{profile.driverName}</p>
                <p className="text-sm text-muted">@{profile.username} · {profile.rickshawId} · {profile.completedRides} completed</p>
              </div>
            </div>
            <form onSubmit={save} className="card mt-6 space-y-4 p-5" noValidate>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Full name" id="driverName" error={errors.driverName}>
                  <Input id="driverName" value={form.driverName} error={errors.driverName}
                    onChange={(e) => setForm({ ...form, driverName: e.target.value })} />
                </Field>
                <Field label="Mobile number" id="phone" error={errors.phone}>
                  <Input id="phone" inputMode="tel" value={form.phone} error={errors.phone}
                    onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                </Field>
              </div>
              <div className="border-t border-line pt-5">
                <h2 className="text-sm font-semibold">Rickshaw</h2>
                <p className="text-sm text-muted">ID {profile.rickshawId} · up to 3 passengers</p>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Plate / registration no." id="plateNumber" error={errors.plateNumber}>
                  <Input id="plateNumber" value={form.plateNumber} error={errors.plateNumber}
                    onChange={(e) => setForm({ ...form, plateNumber: e.target.value })} />
                </Field>
                <div>
                  <span className="label">Type</span>
                  <Segmented label="Rickshaw type" value={form.vehicleType} onChange={(v) => setForm({ ...form, vehicleType: v })}
                    options={[{ value: 'ELECTRIC', label: 'Battery' }, { value: 'PEDAL', label: 'Pedal' }]} />
                </div>
              </div>
              <PasswordFields form={form} setForm={setForm} errors={errors} />
              <div className="flex justify-end">
                <Button type="submit" loading={busy}>Save changes</Button>
              </div>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
