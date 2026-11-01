import { Siren, Users } from 'lucide-react'
import { StatusBadge, Tag } from './ui'
import { dateTime, meters, taka } from '../utils/format'

/** Ride history list (sorted newest first by the backend merge sort). */
export default function RideList({ rides, showPassenger = false }) {
  return (
    <ul className="mt-6 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-white">
      {rides.map((r) => (
        <li key={r.id} className="flex items-start justify-between gap-4 px-4 py-4">
          <div className="min-w-0">
            <p className="truncate font-medium">{r.pickup.name} → {r.destination.name}</p>
            <p className="mt-0.5 text-sm text-muted">
              {dateTime(r.requestedAt) || `Ride #${r.id}`} · {meters(r.distanceMeters)} · {r.seats} seat{r.seats > 1 ? 's' : ''}
              {showPassenger && r.passenger ? ` · ${r.passenger.name}` : ''}
              {!showPassenger && r.driver ? ` · ${r.driver.name}` : ''}
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <StatusBadge status={r.status} />
              {r.shared && <Tag tone="accent"><Users size={12} /> Shared</Tag>}
              {r.emergency && <Tag tone="danger"><Siren size={12} /> Emergency</Tag>}
            </div>
          </div>
          <span className={`shrink-0 font-semibold ${r.status === 'COMPLETED' ? '' : 'text-faint line-through'}`}>{taka(r.fare)}</span>
        </li>
      ))}
    </ul>
  )
}
