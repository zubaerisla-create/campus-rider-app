import { useCallback, useEffect, useMemo, useState } from 'react'
import { Power } from 'lucide-react'
import CampusMap from '../../components/CampusMap'
import MapPanel from '../../components/MapPanel'
import { Button, ErrorBanner, Spinner, Switch } from '../../components/ui'
import { useCampusMap } from '../../hooks/useCampusMap'
import { useInterval } from '../../hooks/useInterval'
import { useLivePositions } from '../../hooks/useLivePositions'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { useRealtimeEvent } from '../../context/RealtimeContext'
import { useToast } from '../../context/ToastContext'
import { errorMessage } from '../../services/api'
import { driverService } from '../../services/driverService'
import OfferCard from './OfferCard'
import ActiveRides from './ActiveRides'
import RickshawSettings from './RickshawSettings'

/** Driver home: requests, active-ride controls and the route on the map. */
export default function DriverDashboard() {
  const { graph } = useCampusMap()
  const positions = useLivePositions()
  const toast = useToast()
  const desktop = useMediaQuery('(min-width: 768px)')
  const [dash, setDash] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')

  const load = useCallback(async () => {
    try {
      setDash(await driverService.dashboard())
      setError('')
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])
  useRealtimeEvent('driver_update', (e) => {
    if (e.message) toast.push(e.message)
    load()
  })
  useRealtimeEvent('connected', () => load())
  useInterval(load, 10000)

  const me = dash?.rickshaw?.id
  const myPos = positions.find((p) => p.id === me)
  const nodeKey = myPos ? `${myPos.locationId}>${myPos.movingToId}|${myPos.autoDrive}` : ''
  useEffect(() => {
    if (nodeKey) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodeKey])

  /** Runs a driver action, then refreshes the dashboard. */
  const act = async (key, fn, success) => {
    setBusy(key)
    setError('')
    try {
      const result = await fn()
      if (result && result.rickshaw && result.offers) setDash(result)
      else await load()
      if (success) toast.push(typeof success === 'function' ? success(result) : success, 'success')
    } catch (e) {
      toast.push(errorMessage(e), 'error')
      await load()
    } finally {
      setBusy('')
    }
  }

  const livePositions = useMemo(() => {
    if (!dash) return positions
    return positions.some((p) => p.id === me) ? positions : [...positions, dash.rickshaw.position]
  }, [positions, dash, me])

  const firstOffer = dash?.offers?.[0]?.ride
  const hasRides = dash?.activeRides?.length > 0
  const mapProps = hasRides
    ? { tripPath: dash.plannedPath, stops: dash.stops, fitIds: dash.plannedPath, fitKey: `plan-${dash.stops.map((s) => s.rideId + s.type).join()}` }
    : firstOffer
      ? {
          approachPath: firstOffer.approachPath,
          tripPath: firstOffer.tripPath,
          pickupId: firstOffer.pickup.id,
          destinationId: firstOffer.destination.id,
          fitIds: [...firstOffer.approachPath, ...firstOffer.tripPath],
          fitKey: `offer-${firstOffer.id}`,
        }
      : dash?.rickshaw?.targetDestination
        ? { tripPath: dash.plannedPath, destinationId: dash.rickshaw.targetDestination.id, fitIds: dash.plannedPath, fitKey: `target-${dash.rickshaw.targetDestination.id}` }
        : { fitKey: 'idle' }

  const r = dash?.rickshaw

  return (
    <div className="absolute inset-0">
      <CampusMap graph={graph} positions={livePositions} focusRickshawId={me}
        padding={desktop ? { top: 64, right: 72, bottom: 40, left: 440 } : { top: 24, right: 72, bottom: Math.round(window.innerHeight * 0.5), left: 24 }}
        className="absolute inset-0" {...mapProps} />
      <div className="pointer-events-none absolute right-3 top-3 z-10 rounded-full bg-white/95 px-3 py-1.5 text-xs font-medium text-muted shadow-sm">
        Simulated live tracking
      </div>
      <MapPanel>
        {!dash ? (
          error ? <div className="p-5"><ErrorBanner>{error}</ErrorBanner></div> : <Spinner label="Loading dashboard" />
        ) : (
          <div className="pb-5">
            {/* Status header */}
            <div className="flex items-center justify-between gap-3 px-5 pb-4 pt-4 md:pt-5">
              <div className="min-w-0">
                <h1 className="text-xl font-bold tracking-tight">{r.online ? "You're online" : "You're offline"}</h1>
                <p className="truncate text-sm text-muted">
                  {r.plateNumber || r.id} · at {r.location?.name}
                  {r.position.movingToId ? ' (driving)' : ''}
                </p>
              </div>
              <Switch checked={r.online} label="Online" disabled={busy === 'online' || (r.online && hasRides)}
                onChange={(v) => act('online', () => driverService.setOnline(v), v ? 'You are online.' : 'You are offline.')} />
            </div>

            <div className="mx-5 grid grid-cols-3 divide-x divide-line rounded-xl bg-canvas py-2.5 text-center">
              <Stat label="Seats free" value={`${r.availableSeats}/${r.maxSeats}`} />
              <Stat label="On board" value={r.bookedSeats} />
              <Stat label="Requests" value={dash.offers.length} />
            </div>

            {!r.online && (
              <div className="mx-5 mt-4 rounded-2xl border border-line p-4">
                <p className="font-medium">Go online to receive ride requests</p>
                <p className="mt-1 text-sm text-muted">Requests near you will be sent here automatically.</p>
                <Button className="mt-3 w-full" loading={busy === 'online'}
                  onClick={() => act('online', () => driverService.setOnline(true), 'You are online.')}>
                  <Power size={18} /> Go online
                </Button>
              </div>
            )}

            {dash.offers.length > 0 && (
              <section className="mt-5 px-5">
                <h2 className="text-sm font-semibold">Incoming requests</h2>
                <div className="mt-2 space-y-3">
                  {dash.offers.map((o) => (
                    <OfferCard key={o.ride.id} offer={o} busy={busy}
                      onAccept={() => act(`accept-${o.ride.id}`, () => driverService.accept(o.ride.id), `Ride accepted. Pick up ${o.ride.passenger?.name} at ${o.ride.pickup.name}.`)}
                      onReject={() => act(`reject-${o.ride.id}`, () => driverService.reject(o.ride.id), 'Request passed to another driver.')} />
                  ))}
                </div>
              </section>
            )}

            {hasRides && <ActiveRides dash={dash} busy={busy} act={act} />}

            {r.online && !hasRides && dash.offers.length === 0 && (
              <div className="mx-5 mt-4 rounded-2xl border border-dashed border-line px-4 py-6 text-center">
                <p className="font-medium">Waiting for requests</p>
                <p className="mt-1 text-sm text-muted">Stay near a rickshaw stand. New requests appear here instantly.</p>
                {r.targetDestination && (
                  <Button variant="outline" size="sm" className="mt-3" loading={busy === 'auto'}
                    onClick={() => act('auto', () => driverService.autoDrive(!r.position.autoDrive))}>
                    {r.position.autoDrive ? 'Stop driving' : `Drive to ${r.targetDestination.name}`}
                  </Button>
                )}
              </div>
            )}

            <RickshawSettings dash={dash} graph={graph} busy={busy} act={act} />
          </div>
        )}
      </MapPanel>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div>
      <div className="text-lg font-semibold leading-tight">{value}</div>
      <div className="text-[11px] text-muted">{label}</div>
    </div>
  )
}
