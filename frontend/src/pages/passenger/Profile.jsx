import { useEffect, useState } from 'react'
import PasswordFields from '../../components/PasswordFields'
import { Avatar, Button, ErrorBanner, Field, Input, Spinner, Tag } from '../../components/ui'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { errorMessage, fieldErrors } from '../../services/api'
import { rideService } from '../../services/rideService'
import { NAME, PHONE } from '../auth/validation'

export default function PassengerProfile() {
  const { refresh } = useAuth()
  const toast = useToast()
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    rideService.profile().then((p) => {
      setProfile(p)
      setForm({ fullName: p.fullName, phone: p.phone, currentPassword: '', newPassword: '' })
    }).catch((e) => setError(errorMessage(e)))
  }, [])

  const save = async (e) => {
    e.preventDefault()
    const v = {}
    if (!NAME.test(form.fullName.trim())) v.fullName = 'Enter your name (letters only).'
    if (!PHONE.test(form.phone)) v.phone = 'Enter an 11-digit mobile number.'
    if (form.newPassword && form.newPassword.length < 6) v.newPassword = 'Use at least 6 characters.'
    setErrors(v)
    if (Object.keys(v).length) return
    setBusy(true)
    setError('')
    try {
      const p = await rideService.updateProfile({ ...form, fullName: form.fullName.trim() })
      setProfile(p)
      setForm({ fullName: p.fullName, phone: p.phone, currentPassword: '', newPassword: '' })
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
              <Avatar name={profile.fullName} size={56} />
              <div>
                <p className="text-lg font-semibold">{profile.fullName}</p>
                <p className="text-sm text-muted">@{profile.username} · {profile.completedRides} completed ride{profile.completedRides === 1 ? '' : 's'}</p>
              </div>
              <Tag className="ml-auto">{profile.passengerType.charAt(0) + profile.passengerType.slice(1).toLowerCase()}</Tag>
            </div>
            <form onSubmit={save} className="card mt-6 space-y-4 p-5" noValidate>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Full name" id="fullName" error={errors.fullName}>
                  <Input id="fullName" value={form.fullName} error={errors.fullName}
                    onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
                </Field>
                <Field label="Mobile number" id="phone" error={errors.phone}>
                  <Input id="phone" inputMode="tel" value={form.phone} error={errors.phone}
                    onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                </Field>
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
