import { useEffect, useState } from 'react'
import { Siren, Users } from 'lucide-react'
import { Button, Tag } from '../../components/ui'
import { eta, firstName, meters, taka } from '../../utils/format'

/** One incoming request with its countdown, trip and Accept / Reject actions. */
export default function OfferCard({ offer, busy, onAccept, onReject }) {
  const { ride } = offer
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])
  const left = Math.max(0, Math.round((offer.expiresAt - now) / 1000))

  return (
    <div className={`fade-up rounded-2xl border p-4 ${ride.emergency ? 'border-danger' : 'border-ink'}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1.5">
            <p className="font-semibold">{firstName(ride.passenger?.name)}</p>
            <span className="text-sm text-muted">· {ride.seats} seat{ride.seats > 1 ? 's' : ''}</span>
            {ride.emergency && <Tag tone="danger"><Siren size={12} /> Emergency</Tag>}
            {offer.shared && <Tag tone="accent"><Users size={12} /> Shared</Tag>}
          </div>
          <p className="mt-0.5 text-sm text-muted">
            {offer.distanceToPickupMeters >= 0 ? `${meters(offer.distanceToPickupMeters)} to pickup (${eta(offer.distanceToPickupMeters)})` : offer.summary}
          </p>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-lg font-bold">{taka(ride.fare)}</p>
          <p className={`text-xs tabular-nums ${left <= 10 ? 'text-danger' : 'text-muted'}`}>{left}s</p>
        </div>
      </div>
      <div className="mt-3 space-y-1.5 text-sm">
        <p className="flex items-center gap-2"><span className="h-2 w-2 rounded-full border-2 border-ink" /> {ride.pickup.name}</p>
        <p className="flex items-center gap-2"><span className="h-2 w-2 bg-ink" /> {ride.destination.name}</p>
        <p className="text-xs text-muted">
          Trip {meters(ride.distanceMeters)}
          {offer.shared && offer.detourMeters >= 0 ? ` · adds ${meters(offer.detourMeters)} for current passengers` : ''}
        </p>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Button variant="outline" onClick={onReject} loading={busy === `reject-${ride.id}`} disabled={!!busy}>Reject</Button>
        <Button onClick={onAccept} loading={busy === `accept-${ride.id}`} disabled={!!busy}>Accept</Button>
      </div>
    </div>
  )
}
