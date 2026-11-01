import { CircleCheck, CircleSlash, SearchX } from 'lucide-react'
import { Button } from '../../components/ui'
import { meters, taka } from '../../utils/format'

/** Shown once the open ride has finished: completed, rejected or cancelled. */
export default function RideEndedPanel({ ride, onDone }) {
  const completed = ride.status === 'COMPLETED'
  const rejected = ride.status === 'REJECTED'
  const Icon = completed ? CircleCheck : rejected ? SearchX : CircleSlash
  return (
    <div className="fade-up px-5 pb-5 pt-5 text-center">
      <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full ${completed ? 'bg-accent-soft text-accent' : 'bg-canvas text-muted'}`}>
        <Icon size={28} />
      </div>
      <h1 className="mt-4 text-xl font-bold tracking-tight">
        {completed ? `You've arrived at ${ride.destination.name}` : rejected ? 'No rickshaw available' : 'Ride cancelled'}
      </h1>
      <p className="mt-1.5 text-sm text-muted">{ride.statusNote}</p>
      {completed && (
        <div className="mx-auto mt-5 max-w-xs rounded-2xl border border-line p-4 text-left text-sm">
          <Row label="Fare" value={taka(ride.fare)} strong />
          <Row label="Distance" value={meters(ride.distanceMeters)} />
          <Row label="Driver" value={ride.driver?.name || '—'} />
          {ride.shared && <Row label="Ride type" value="Shared (20% off)" />}
        </div>
      )}
      <div className="mt-6 grid gap-2">
        {!completed && <Button onClick={() => onDone(true)}>Try again</Button>}
        <Button variant={completed ? 'primary' : 'secondary'} onClick={() => onDone(false)}>Book another ride</Button>
      </div>
    </div>
  )
}

function Row({ label, value, strong }) {
  return (
    <div className="flex justify-between py-1">
      <span className="text-muted">{label}</span>
      <span className={strong ? 'font-semibold' : ''}>{value}</span>
    </div>
  )
}
