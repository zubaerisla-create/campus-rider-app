import { forwardRef } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, Loader2 } from 'lucide-react'
import { initials, STATUS_META } from '../utils/format'

const VARIANTS = {
  primary: 'bg-ink text-white hover:bg-ink-soft disabled:bg-[#c9cac7]',
  secondary: 'bg-canvas text-ink hover:bg-[#ebebe8] disabled:text-faint',
  outline: 'border border-line bg-white text-ink hover:bg-canvas disabled:text-faint',
  accent: 'bg-accent text-white hover:bg-accent-strong disabled:bg-[#9fcdb9]',
  danger: 'bg-white text-danger border border-line hover:bg-danger-soft disabled:text-faint',
  ghost: 'text-ink hover:bg-canvas',
}
const SIZES = { sm: 'h-9 px-3 text-sm', md: 'h-11 px-4 text-[15px]', lg: 'h-13 px-5 text-base' }

export const Button = forwardRef(function Button(
  { variant = 'primary', size = 'md', loading = false, className = '', children, disabled, to, ...rest },
  ref,
) {
  const cls = `inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-colors ${VARIANTS[variant]} ${SIZES[size]} ${className}`
  if (to) {
    return <Link ref={ref} to={to} className={cls}>{children}</Link>
  }
  return (
    <button ref={ref} className={cls} disabled={disabled || loading} {...rest}>
      {loading && <Loader2 size={18} className="animate-spin" />}
      {children}
    </button>
  )
})

export function Field({ label, error, hint, id, children }) {
  return (
    <div>
      {label && <label htmlFor={id} className="label">{label}</label>}
      {children}
      {error ? (
        <p className="mt-1.5 text-[13px] text-danger" id={`${id}-error`}>{error}</p>
      ) : hint ? (
        <p className="mt-1.5 text-[13px] text-muted">{hint}</p>
      ) : null}
    </div>
  )
}

export const Input = forwardRef(function Input({ error, className = '', id, ...rest }, ref) {
  return (
    <input ref={ref} id={id} aria-invalid={error ? 'true' : undefined}
      aria-describedby={error ? `${id}-error` : undefined} className={`input ${className}`} {...rest} />
  )
})

export function Select({ error, className = '', children, ...rest }) {
  return (
    <select aria-invalid={error ? 'true' : undefined} className={`input appearance-none pr-9 ${className}`}
      style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='none' stroke='%236b6f76' stroke-width='2' viewBox='0 0 24 24'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 12px center' }}
      {...rest}>
      {children}
    </select>
  )
}

export function Spinner({ label = 'Loading', className = '' }) {
  return (
    <div className={`flex items-center justify-center gap-2 py-8 text-sm text-muted ${className}`} role="status">
      <Loader2 size={18} className="animate-spin" /> {label}
    </div>
  )
}

export function ErrorBanner({ children, className = '' }) {
  if (!children) return null
  return (
    <div role="alert" className={`flex items-start gap-2 rounded-xl bg-danger-soft px-3.5 py-3 text-sm text-danger ${className}`}>
      <AlertCircle size={18} className="mt-0.5 shrink-0" />
      <span>{children}</span>
    </div>
  )
}

export function EmptyState({ icon: Icon, title, children, action }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      {Icon && (
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-canvas text-muted">
          <Icon size={22} />
        </div>
      )}
      <h3 className="text-base font-semibold">{title}</h3>
      {children && <p className="mt-1 max-w-sm text-sm text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

const TONES = {
  accent: 'bg-accent-soft text-accent-strong',
  warn: 'bg-warn-soft text-warn',
  danger: 'bg-danger-soft text-danger',
  neutral: 'bg-canvas text-ink-soft',
}

export function StatusBadge({ status }) {
  const meta = STATUS_META[status] || { label: status, tone: 'neutral' }
  return <span className={`chip ${TONES[meta.tone]}`}>{meta.label}</span>
}

export function Tag({ tone = 'neutral', children, className = '' }) {
  return <span className={`chip ${TONES[tone]} ${className}`}>{children}</span>
}

export function Avatar({ name, size = 40 }) {
  return (
    <div className="flex shrink-0 items-center justify-center rounded-full bg-ink font-semibold text-white"
      style={{ width: size, height: size, fontSize: size * 0.36 }} aria-hidden="true">
      {initials(name)}
    </div>
  )
}

/** Segmented control (e.g. seats 1-3, sort order). */
export function Segmented({ options, value, onChange, label, className = '' }) {
  return (
    <div role="radiogroup" aria-label={label} className={`flex rounded-xl bg-canvas p-1 ${className}`}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <button key={o.value} type="button" role="radio" aria-checked={active} disabled={o.disabled}
            onClick={() => onChange(o.value)}
            className={`h-9 flex-1 rounded-lg px-2 text-sm font-medium transition-colors disabled:opacity-40 ${
              active ? 'bg-white text-ink shadow-sm' : 'text-muted hover:text-ink'}`}>
            {o.label}
          </button>
        )
      })}
    </div>
  )
}

export function Switch({ checked, onChange, label, disabled }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:opacity-50 ${checked ? 'bg-accent' : 'bg-[#d4d5d1]'}`}>
      <span className={`absolute left-0 top-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-5.5' : 'translate-x-0.5'}`} />
    </button>
  )
}
