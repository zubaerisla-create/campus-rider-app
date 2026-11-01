export function meters(m) {
  if (m == null || m < 0) return '—'
  if (m < 1000) return `${Math.round(m / 10) * 10} m`
  return `${(m / 1000).toFixed(1)} km`
}

/** Realistic campus rickshaw speed (~12 km/h) for displayed time estimates. */
export function minutesFor(m) {
  if (m == null || m < 0) return null
  if (m === 0) return 0
  return Math.max(1, Math.round(m / 3.3 / 60))
}

export function eta(m) {
  const min = minutesFor(m)
  if (min == null) return '—'
  if (min === 0) return 'Here now'
  return `${min} min`
}

export function taka(n) {
  return `৳${n ?? 0}`
}

export function dateTime(ms) {
  if (!ms) return ''
  const d = new Date(ms)
  return d.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

export function firstName(name = '') {
  const parts = name.trim().split(/\s+/)
  if (parts.length > 1 && parts[0].endsWith('.')) return parts[1]
  return parts[0] || ''
}

export function initials(name = '') {
  const parts = name.replace(/^\S+\.\s+/, '').trim().split(/\s+/)
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase()
}

export const STATUS_META = {
  PENDING: { label: 'Finding rickshaw', tone: 'warn' },
  ACCEPTED: { label: 'Driver on the way', tone: 'accent' },
  STARTED: { label: 'On trip', tone: 'accent' },
  COMPLETED: { label: 'Completed', tone: 'neutral' },
  REJECTED: { label: 'No rickshaw', tone: 'danger' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
}
