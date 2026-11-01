import { CircleDot, Navigation, Phone, Square } from 'lucide-react'
import { Button, Switch } from '../../components/ui'
import { driverService } from '../../services/driverService'
import { meters } from '../../utils/format'

/** Active passengers, the next stop, and the Start / Complete / Move / Auto-drive controls. */
export default function ActiveRides({ dash, busy, act }) {
  const pos = dash.rickshaw.position
  const here = dash.rickshaw.location?.id
  // Rides that need an action at the current location.
  const actions = dash.activeRides.filter((r) => (r.status === 'ACCEPTED' && r.arrivedAtPickup) || (r.status === 'STARTED' && r.atDestination))
  const next = dash.nextStop

  return (
    <section className="mt-5 px-5">
      <div className="rounded-2xl bg-ink p-4 text-white">
        {actions.length > 0 ? (
          <div className="space-y-3">
            {actions.map((r) => {
              const start = r.status === 'ACCEPTED'
              return (
                <div key={r.id}>
                  <p className="text-sm text-white/70">{start ? 'At pickup' : 'At drop-off'} · {start ? r.pickup.name : r.destination.name}</p>
                  <p className="text-lg font-semibold">{start ? `Pick up ${r.passenger?.name}` : `Drop off ${r.passenger?.name}`}</p>
                  <Button variant="accent" className="mt-2 w-full" loading={busy === `ride-${r.id}`} disabled={!!busy}
                    onClick={() => act(`ride-${r.id}`, () => (start ? driverService.start(r.id) : driverService.complete(r.id)),
                      start ? `Trip started for ${r.passenger?.name}.` : `Ride completed. Collect ৳${r.fare}.`)}>
                    {start ? 'Start ride' : 'Complete ride'}
                  </Button>
                </div>
              )
            })}
          </div>
        ) : next ? (
          <>
            <p className="text-sm text-white/70">Next stop{pos.movingToId ? ' · driving' : ''}</p>
            <p className="text-lg font-semibold">
              {next.type === 'PICKUP' ? `Pick up ${next.passengerName}` : `Drop off ${next.passengerName}`}
            </p>
            <p className="text-sm text-white/70">{next.locationName}</p>
          </>
        ) : (
          <p className="text-sm">Route complete.</p>
        )}

        <div className="mt-4 flex items-center gap-2 border-t border-white/15 pt-4">
          <Button variant="secondary" className="flex-1" loading={busy === 'move'} disabled={!!busy || (actions.length > 0 && !pos.movingToId)}
            onClick={() => act('move', driverService.move, (r) => r?.message)}>
            <Navigation size={17} /> Move to next location
          </Button>
          <label className="flex items-center gap-2 pl-2 text-sm">
            <span className="text-white/80">Auto</span>
            <Switch checked={!!pos.autoDrive} label="Automatic simulated drive" disabled={busy === 'auto'}
              onChange={(v) => act('auto', () => driverService.autoDrive(v), v ? 'Auto drive on: the rickshaw follows the route.' : 'Auto drive off.')} />
          </label>
        </div>
      </div>

      {dash.stops.length > 0 && (
        <div className="mt-4">
          <h2 className="text-sm font-semibold">Route plan</h2>
          <ol className="mt-2 space-y-1.5">
            {dash.stops.map((s, i) => (
              <li key={`${s.rideId}-${s.type}`} className="flex items-center gap-3 rounded-xl bg-canvas px-3.5 py-2.5 text-sm">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white text-xs font-bold">{i + 1}</span>
                {s.type === 'PICKUP' ? <CircleDot size={16} className="shrink-0" /> : <Square size={14} className="shrink-0" />}
                <span className="min-w-0 flex-1 truncate">
                  <span className="font-medium">{s.type === 'PICKUP' ? 'Pick up' : 'Drop off'} {s.passengerName}</span>
                  <span className="text-muted"> · {s.locationName}{s.locationId === here && !pos.movingToId ? ' (here)' : ''}</span>
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}

      <div className="mt-4">
        <h2 className="text-sm font-semibold">Passengers</h2>
        <ul className="mt-2 divide-y divide-line rounded-xl border border-line">
          {dash.activeRides.map((r) => (
            <li key={r.id} className="flex items-center gap-3 px-3.5 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{r.passenger?.name} · {r.seats} seat{r.seats > 1 ? 's' : ''}</p>
                <p className="truncate text-xs text-muted">
                  {r.status === 'STARTED' ? 'On board' : `Waiting · ${meters(r.metersToPickup)} away`} · {r.pickup.name} → {r.destination.name}
                </p>
              </div>
              {r.passenger?.phone && (
                <a href={`tel:${r.passenger.phone}`} aria-label={`Call ${r.passenger.name}`}
                  className="flex h-9 w-9 items-center justify-center rounded-full bg-canvas hover:bg-line"><Phone size={16} /></a>
              )}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
