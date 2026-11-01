import { Field, Input } from './ui'

export default function PasswordFields({ form, setForm, errors }) {
  return (
    <div className="space-y-4 border-t border-line pt-5">
      <div>
        <h2 className="text-sm font-semibold">Change password</h2>
        <p className="text-sm text-muted">Leave blank to keep your current password.</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Current password" id="currentPassword" error={errors.currentPassword}>
          <Input id="currentPassword" type="password" autoComplete="current-password" value={form.currentPassword}
            onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
        </Field>
        <Field label="New password" id="newPassword" error={errors.newPassword}>
          <Input id="newPassword" type="password" autoComplete="new-password" value={form.newPassword}
            onChange={(e) => setForm({ ...form, newPassword: e.target.value })} error={errors.newPassword} />
        </Field>
      </div>
    </div>
  )
}
