import { useState } from 'react'
import { Phone, Siren, Users } from 'lucide-react'
import { Avatar, Button, ErrorBanner, Tag } from '../../components/ui'
import { useToast } from '../../context/ToastContext'
import { errorMessage } from '../../services/api'
import { rideService } from '../../services/rideService'
import { eta, meters, taka } from '../../utils/format'

const STEPS = ['Requested', 'Driver assigned', 'On trip', 'Arrived']

function stepIndex(ride) {
  if (ride.status === 'PENDING') return 0
  if (ride.status === 'ACCEPTED') return 1
  if (ride.status === 'STARTED') return ride.atDestination ? 3 : 2
  return 3
}

function headline(ride) {
  switch (ride.status) {
    case 'PENDING':
      return { title: 'Finding your rickshaw', sub: ride.statusNote }
    case 'ACCEPTED':
      return ride.arrivedAtPickup
        ? { title: 'Your rickshaw is here', sub: `Meet ${ride.driver?.name} at ${ride.pickup.name}` }
        : { title: `Arriving in ${eta(ride.metersToPickup)}`, sub: `${meters(ride.metersToPickup)} away · ${ride.statusNote}` }
    case 'STARTED':
      return ride.atDestination
        ? { title: `You've reached ${ride.destination.name}`, sub: 'The driver will complete the trip.' }
        : { title: `${eta(ride.metersToDropoff)} to ${ride.destination.name}`, sub: `${meters(ride.metersToDropoff)} to go` }
    default:
      return { title: ride.statusLabel, sub: ride.statusNote }
  }
}

/** Live tracking of the passenger's open ride (updated from WebSocket events). */
export default function LiveRidePanel({ ride, onChanged }) {
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const h = headline(ride)
  const step = stepIndex(ride)

  const cancel = async () => {
    setBusy(true)
    setError('')
    try {
      await rideService.cancel(ride.id)
      toast.push('Your ride was cancelled.')
      onChanged()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
      setConfirmCancel(false)
    }
  }

  return (
    <div className="fade-up px-5 pb-5 pt-4 md:pt-5" aria-live="polite">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-bold leading-tight tracking-tight">{h.title}</h1>
          {h.sub && <p className="mt-1 text-sm text-muted">{h.sub}</p>}
        </div>
        {ride.emergency && <Tag tone="danger" className="shrink-0"><Siren size={12} /> Emergency</Tag>}
      </div>

      {/* Progress */}
      <ol className="mt-4 grid grid-cols-4 gap-1.5" aria-label="Ride progress">
        {STEPS.map((s, i) => (
          <li key={s} className="min-w-0">
            <div className={`h-1 rounded-full ${i < step ? 'bg-ink' : i === step ? 'bg-accent' : 'bg-line'} ${
              i === step && ride.status === 'PENDING' ? 'animate-pulse' : ''}`} />
            <span className={`mt-1.5 block truncate text-[11px] ${i <= step ? 'text-ink' : 'text-faint'}`}>{s}</span>
          </li>
        ))}
      </ol>

      {/* Driver */}
      {ride.driver ? (
        <div className="mt-5 flex items-center gap-3 rounded-2xl border border-line p-3.5">
          <Avatar name={ride.driver.name} size={44} />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold">{ride.driver.name}</p>
            <p className="truncate text-sm text-muted">
              {ride.driver.plateNumber || ride.driver.rickshawId} · {ride.driver.vehicleType === 'PEDAL' ? 'Pedal' : 'Battery'} rickshaw
            </p>
          </div>
          {ride.driver.phone && (
            <a href={`tel:${ride.driver.phone}`} aria-label={`Call ${ride.driver.name}`}
              className="flex h-10 w-10 items-center justify-center rounded-full bg-canvas hover:bg-line">
              <Phone size={18} />
            </a>
          )}
        </div>
      ) : (
        <div className="mt-5 rounded-2xl bg-canvas p-4 text-sm text-muted">
          {ride.offeredTo ? `Waiting for ${ride.offeredTo} to accept…` : 'Looking for a rickshaw with free seats on your route…'}
        </div>
      )}

      {/* Trip */}
      <div className="mt-4 space-y-3 rounded-2xl border border-line p-4">
        <TripLine marker="circle" label="Pickup" value={ride.pickup.name} />
        <TripLine marker="square" label="Drop-off" value={ride.destination.name} />
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line pt-3 text-sm">
          <span><span className="text-muted">Fare</span> <span className="font-semibold">{taka(ride.fare)}</span></span>
          <span><span className="text-muted">Seats</span> <span className="font-semibold">{ride.seats}</span></span>
          <span><span className="text-muted">Trip</span> <span className="font-semibold">{meters(ride.distanceMeters)}</span></span>
          {ride.shared && <Tag tone="accent"><Users size={12} /> Shared</Tag>}
        </div>
      </div>

      {ride.coPassengers?.length > 0 && (
        <div className="mt-4">
          <h2 className="text-sm font-semibold">Sharing with</h2>
          <ul className="mt-2 space-y-1.5">
            {ride.coPassengers.map((c, i) => (
              <li key={i} className="flex items-center justify-between rounded-xl bg-canvas px-3.5 py-2.5 text-sm">
                <span className="font-medium">{c.firstName} <span className="font-normal text-muted">· {c.seats} seat{c.seats > 1 ? 's' : ''}</span></span>
                <span className="truncate pl-3 text-xs text-muted">{c.pickupName} → {c.destinationName}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <ErrorBanner className="mt-4">{error}</ErrorBanner>

      {ride.cancellable && (
        <div className="mt-5">
          {confirmCancel ? (
            <div className="rounded-2xl border border-line p-4">
              <p className="text-sm font-medium">Cancel this ride?</p>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <Button variant="outline" onClick={() => setConfirmCancel(false)}>Keep ride</Button>
                <Button variant="danger" loading={busy} onClick={cancel}>Cancel ride</Button>
              </div>
            </div>
          ) : (
            <Button variant="secondary" className="w-full" onClick={() => setConfirmCancel(true)}>Cancel ride</Button>
          )}
        </div>
      )}
      <p className="mt-4 text-center text-xs text-faint">Rickshaw position is simulated along campus roads, not GPS.</p>
    </div>
  )
}

function TripLine({ marker, label, value }) {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-4 w-4 items-center justify-center" aria-hidden="true">
        {marker === 'square' ? <span className="h-2.5 w-2.5 bg-ink" /> : <span className="h-2.5 w-2.5 rounded-full border-[3px] border-ink" />}
      </span>
      <div className="min-w-0">
        <p className="text-xs text-muted">{label}</p>
        <p className="truncate text-[15px] font-medium">{value}</p>
      </div>
    </div>
  )
}
