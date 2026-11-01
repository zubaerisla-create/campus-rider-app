import { useCallback, useEffect, useState } from 'react'
import { ArrowUpDown, Bike, ChevronDown, Siren, Sparkles, Users } from 'lucide-react'
import LocationInput from '../../components/LocationInput'
import { Button, ErrorBanner, Segmented, Switch, Tag } from '../../components/ui'
import { useAuth } from '../../context/AuthContext'
import { useInterval } from '../../hooks/useInterval'
import { errorMessage } from '../../services/api'
import { locationService } from '../../services/locationService'
import { rideService } from '../../services/rideService'
import { eta, meters, taka } from '../../utils/format'

const SORTS = [
  { value: 'best', label: 'Best match' },
  { value: 'nearest', label: 'Nearest' },
  { value: 'seats', label: 'Most seats' },
]

/**
 * Step 1-5 of booking: pickup, destination, seats -> route estimate (Dijkstra) and
 * available rickshaws (route compatibility + merge sort) -> confirm.
 */
export default function BookingPanel({ byId, draft, setDraft, onPreview, onRequested }) {
  const { user } = useAuth()
  const pickup = draft.pickupId ? byId.get(draft.pickupId) || null : null
  const destination = draft.destinationId ? byId.get(draft.destinationId) || null : null
  const [seats, setSeats] = useState(1)
  const [emergency, setEmergency] = useState(user?.passengerType === 'EMERGENCY')
  const [sort, setSort] = useState('best')
  const [route, setRoute] = useState(null)
  const [options, setOptions] = useState(null)
  const [choice, setChoice] = useState('') // '' = automatic best match
  const [loading, setLoading] = useState(false)
  const [showUnavailable, setShowUnavailable] = useState(false)
  const [error, setError] = useState('')
  const [requesting, setRequesting] = useState(false)

  const ready = pickup && destination && pickup.id !== destination.id

  const loadOptions = useCallback(
    async (quiet = false) => {
      if (!ready) return
      if (!quiet) setLoading(true)
      try {
        const list = await rideService.available(pickup.id, destination.id, seats, sort)
        setOptions(list)
        setError('')
      } catch (e) {
        if (!quiet) setError(errorMessage(e))
      } finally {
        if (!quiet) setLoading(false)
      }
    },
    [ready, pickup?.id, destination?.id, seats, sort],
  )

  // Route estimate whenever the trip changes.
  useEffect(() => {
    setRoute(null)
    setOptions(null)
    setChoice('')
    if (!ready) {
      onPreview([], pickup?.id, destination?.id)
      return
    }
    let alive = true
    locationService
      .route(pickup.id, destination.id)
      .then((r) => {
        if (!alive) return
        setRoute(r)
        onPreview(r.path, pickup.id, destination.id)
      })
      .catch((e) => alive && setError(errorMessage(e)))
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pickup?.id, destination?.id])

  useEffect(() => {
    loadOptions()
  }, [loadOptions])
  useInterval(() => loadOptions(true), ready ? 8000 : null)

  const compatible = (options || []).filter((o) => o.compatible)
  const unavailable = (options || []).filter((o) => !o.compatible)
  const chosen = compatible.find((o) => o.rickshawId === choice)
  const best = compatible[0]
  const fare = chosen?.fare ?? (sort === 'best' ? best?.fare : null) ?? (route ? route.fare * seats : 0)

  const request = async () => {
    setError('')
    setRequesting(true)
    try {
      const ride = await rideService.request({
        pickupId: pickup.id,
        destinationId: destination.id,
        seats,
        emergency,
        preferredRickshawId: choice || null,
      })
      onRequested(ride)
    } catch (e) {
      setError(errorMessage(e))
      loadOptions(true)
    } finally {
      setRequesting(false)
    }
  }

  return (
    <div className="flex min-h-full flex-col">
      <div className="space-y-3 px-5 pb-4 pt-4 md:pt-5">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold tracking-tight">Where to?</h1>
          {(pickup || destination) && (
            <button type="button" onClick={() => setDraft((d) => ({ pickupId: d.destinationId, destinationId: d.pickupId }))}
              className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-muted hover:bg-canvas hover:text-ink">
              <ArrowUpDown size={14} /> Swap
            </button>
          )}
        </div>
        <div className="space-y-2">
          <LocationInput value={pickup} placeholder="Pickup location" label="Pickup location" exclude={destination?.id}
            onChange={(l) => setDraft((d) => ({ ...d, pickupId: l?.id || null }))} />
          <LocationInput value={destination} placeholder="Destination" label="Destination" marker="square" exclude={pickup?.id}
            onChange={(l) => setDraft((d) => ({ ...d, destinationId: l?.id || null }))} />
        </div>
        {!pickup && !destination && (
          <p className="text-sm text-muted">Tip: tap places on the map to set the pickup, then the destination.</p>
        )}

        <div className="flex items-center gap-3">
          <Segmented label="Seats" className="flex-1" value={seats} onChange={setSeats}
            options={[1, 2, 3].map((n) => ({ value: n, label: `${n} seat${n > 1 ? 's' : ''}` }))} />
        </div>
        <label className="flex items-center justify-between gap-3 rounded-xl border border-line px-3.5 py-2.5">
          <span className="flex items-center gap-2.5 text-sm">
            <Siren size={18} className={emergency ? 'text-danger' : 'text-muted'} />
            <span>
              <span className="font-medium">Emergency ride</span>
              <span className="block text-xs text-muted">Medical or urgent: served before other requests</span>
            </span>
          </span>
          <Switch checked={emergency} onChange={setEmergency} label="Emergency ride" />
        </label>
      </div>

      {ready && (
        <div className="border-t border-line px-5 py-4">
          {route ? (
            <div className="flex items-baseline justify-between">
              <div>
                <p className="text-sm text-muted">Shortest route</p>
                <p className="text-lg font-semibold">{meters(route.distanceMeters)} · {route.estimatedMinutes} min</p>
              </div>
              <p className="text-right text-sm text-muted">
                from <span className="text-lg font-semibold text-ink">{taka(route.fare * seats)}</span>
              </p>
            </div>
          ) : (
            <div className="h-12 animate-pulse rounded-xl bg-canvas" />
          )}

          <div className="mt-5 flex items-center justify-between">
            <h2 className="text-sm font-semibold">Available rickshaws</h2>
            <span className="text-xs text-muted">{compatible.length} can take you</span>
          </div>
          <Segmented label="Sort rickshaws" className="mt-2" value={sort} onChange={setSort} options={SORTS} />

          <ErrorBanner className="mt-3">{error}</ErrorBanner>

          <div className="mt-3 space-y-2" role="radiogroup" aria-label="Choose a rickshaw">
            {loading && !options && [0, 1, 2].map((i) => <div key={i} className="h-[72px] animate-pulse rounded-xl bg-canvas" />)}
            {options && compatible.length > 0 && (
              <OptionRow selected={choice === ''} onSelect={() => setChoice('')}
                icon={<Sparkles size={18} />} title="Automatic best match"
                subtitle={best ? `Sent to the best driver first (${best.driverName} now)` : 'Sent to the best available driver'}
                right={best ? taka(best.fare) : null} />
            )}
            {compatible.map((o) => (
              <OptionRow key={o.rickshawId} selected={choice === o.rickshawId} onSelect={() => setChoice(o.rickshawId)}
                icon={<Bike size={18} />}
                title={
                  <span className="flex items-center gap-2">
                    {o.driverName}
                    {o.shared && <Tag tone="accent"><Users size={12} /> Shared</Tag>}
                  </span>
                }
                subtitle={`${eta(o.distanceToPickupMeters)} away · ${o.availableSeats}/${o.maxSeats} seats free${o.shared ? ` · +${meters(o.detourMeters)} detour` : ''}`}
                note={o.summary}
                right={taka(o.fare)} />
            ))}
            {options && compatible.length === 0 && (
              <div className="rounded-xl bg-canvas px-4 py-4 text-sm">
                <p className="font-medium">No rickshaw can take this trip right now</p>
                <p className="mt-1 text-muted">You can still request: you will be queued and matched as soon as a rickshaw is free.</p>
              </div>
            )}
            {unavailable.length > 0 && (
              <div>
                <button type="button" onClick={() => setShowUnavailable((s) => !s)}
                  className="flex items-center gap-1 py-1 text-xs font-medium text-muted hover:text-ink">
                  <ChevronDown size={14} className={showUnavailable ? 'rotate-180' : ''} />
                  {unavailable.length} not suitable for this trip
                </button>
                {showUnavailable && (
                  <ul className="mt-1 space-y-1">
                    {unavailable.map((o) => (
                      <li key={o.rickshawId} className="flex justify-between gap-3 rounded-lg px-3 py-2 text-xs text-muted">
                        <span>{o.driverName}</span>
                        <span className="text-right">{o.summary}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      <div className="sticky bottom-0 mt-auto border-t border-line bg-white px-5 py-4">
        <Button className="w-full" size="lg" disabled={!ready || !route} loading={requesting} onClick={request}>
          {ready ? `Request ${emergency ? 'emergency ' : ''}ride · ${taka(fare)}` : 'Choose pickup and destination'}
        </Button>
      </div>
    </div>
  )
}

function OptionRow({ selected, onSelect, icon, title, subtitle, note, right }) {
  return (
    <button type="button" role="radio" aria-checked={selected} onClick={onSelect}
      className={`flex w-full items-center gap-3 rounded-xl border px-3.5 py-3 text-left transition-colors ${
        selected ? 'border-ink bg-white ring-1 ring-ink' : 'border-line hover:bg-canvas'}`}>
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-canvas">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[15px] font-semibold">{title}</span>
        <span className="block truncate text-xs text-muted">{subtitle}</span>
        {note && <span className="block truncate text-xs text-faint">{note}</span>}
      </span>
      {right && <span className="shrink-0 text-[15px] font-semibold">{right}</span>}
    </button>
  )
}
