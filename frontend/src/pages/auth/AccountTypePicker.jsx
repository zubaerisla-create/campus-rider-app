import { Bike, User } from 'lucide-react'

const TYPES = [
  { value: 'passenger', label: 'Passenger', hint: 'Book rides', icon: User },
  { value: 'driver', label: 'Driver', hint: 'Give rides', icon: Bike },
]

/** Passenger / Driver selector used on the signup and login forms. */
export default function AccountTypePicker({ value, onChange }) {
  return (
    <div role="radiogroup" aria-label="Account type" className="grid grid-cols-2 gap-2">
      {TYPES.map((t) => {
        const active = value === t.value
        return (
          <button key={t.value} type="button" role="radio" aria-checked={active} onClick={() => onChange(t.value)}
            className={`flex items-center gap-3 rounded-xl border px-3.5 py-3 text-left transition-colors ${
              active ? 'border-ink ring-1 ring-ink' : 'border-line hover:bg-canvas'}`}>
            <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${active ? 'bg-ink text-white' : 'bg-canvas text-muted'}`}>
              <t.icon size={18} />
            </span>
            <span className="min-w-0">
              <span className="block text-[15px] font-semibold">{t.label}</span>
              <span className="block truncate text-xs text-muted">{t.hint}</span>
            </span>
          </button>
        )
      })}
    </div>
  )
}

export function normalizeType(value) {
  return value === 'driver' ? 'driver' : 'passenger'
}
