import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import CampusMap from '../../components/CampusMap'
import MapPanel from '../../components/MapPanel'
import { Spinner } from '../../components/ui'
import { useCampusMap } from '../../hooks/useCampusMap'
import { useLivePositions } from '../../hooks/useLivePositions'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { useInterval } from '../../hooks/useInterval'
import { useRealtimeEvent } from '../../context/RealtimeContext'
import { useToast } from '../../context/ToastContext'
import { rideService } from '../../services/rideService'
import BookingPanel from './BookingPanel'
import LiveRidePanel from './LiveRidePanel'
import RideEndedPanel from './RideEndedPanel'

/** Passenger home: full map + booking panel, then live tracking of the open ride. */
export default function Book() {
  const { graph, byId } = useCampusMap()
  const positions = useLivePositions()
  const toast = useToast()
  const desktop = useMediaQuery('(min-width: 768px)')
  const [params] = useSearchParams()

  const [ride, setRide] = useState(null)
  const [ended, setEnded] = useState(null)
  const [loading, setLoading] = useState(true)
  const [draft, setDraft] = useState({ pickupId: params.get('pickup'), destinationId: params.get('destination') })
  const [preview, setPreview] = useState({ tripPath: [], pickupId: null, destinationId: null, fitKey: 0 })
  // Id of the open ride we are showing, so we can show its summary once it ends.
  const lastRideId = useRef(null)

  const loadCurrent = useCallback(async () => {
    try {
      const current = await rideService.current()
      if (current) {
        lastRideId.current = current.id
        setEnded(null)
        setRide(current)
        return
      }
      const finishedId = lastRideId.current
      lastRideId.current = null
      setRide(null)
      if (finishedId) {
        // The open ride just finished (completed / rejected / cancelled): show its summary.
        const r = await rideService.get(finishedId)
        if (r.status !== 'PENDING') setEnded(r)
      }
    } catch {
      /* keep the last known state; errors surface on actions */
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadCurrent()
  }, [loadCurrent])

  // Live updates: status changes come as ride_update events over the WebSocket.
  useRealtimeEvent('ride_update', (e) => {
    if (e.message) toast.push(e.message, e.status === 'REJECTED' ? 'error' : 'info')
    loadCurrent()
  })
  useRealtimeEvent('connected', () => loadCurrent())
  useInterval(() => ride && loadCurrent(), 15000)

  // When the assigned rickshaw reaches a new campus location, refresh the remaining route.
  const myRickshaw = ride?.rickshaw?.id
  const myPos = positions.find((p) => p.id === myRickshaw)
  const nodeKey = myPos ? `${myPos.locationId}>${myPos.movingToId}` : ''
  useEffect(() => {
    if (ride && nodeKey) loadCurrent()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodeKey])

  const livePositions = useMemo(() => {
    if (!ride?.rickshaw) return positions
    // Prefer the live pushed position for our rickshaw; fall back to the ride snapshot.
    return positions.some((p) => p.id === ride.rickshaw.id) ? positions : [...positions, ride.rickshaw]
  }, [positions, ride])

  const mapProps = ride
    ? {
        approachPath: ride.approachPath,
        tripPath: ride.tripPath,
        pickupId: ride.pickup.id,
        destinationId: ride.destination.id,
        focusRickshawId: ride.rickshaw?.id,
        fitIds: [...new Set([...(ride.approachPath || []), ...(ride.tripPath || []), ride.pickup.id, ride.destination.id])],
        fitKey: `ride-${ride.id}-${ride.status}`,
      }
    : {
        tripPath: preview.tripPath,
        pickupId: preview.pickupId,
        destinationId: preview.destinationId,
        fitIds: preview.tripPath.length ? preview.tripPath : undefined,
        fitKey: `preview-${preview.fitKey}`,
      }

  const onMapClick = (id) => {
    if (ride) return
    const loc = byId.get(id)
    if (!loc?.reachable) return
    setDraft((d) => {
      if (!d.pickupId || (d.pickupId && d.destinationId)) return { pickupId: id, destinationId: d.pickupId && d.destinationId ? null : d.destinationId }
      if (id === d.pickupId) return d
      return { ...d, destinationId: id }
    })
  }

  return (
    <div className="absolute inset-0">
      <CampusMap
        graph={graph}
        positions={livePositions}
        onLocationClick={onMapClick}
        padding={desktop ? { top: 64, right: 72, bottom: 40, left: 440 } : { top: 24, right: 72, bottom: Math.round(window.innerHeight * 0.5), left: 24 }}
        className="absolute inset-0"
        {...mapProps}
      />
      <div className="pointer-events-none absolute right-3 top-3 z-10 rounded-full bg-white/95 px-3 py-1.5 text-xs font-medium text-muted shadow-sm">
        Simulated live tracking
      </div>
      <MapPanel>
        {loading ? (
          <Spinner label="Loading your ride" />
        ) : ride ? (
          <LiveRidePanel ride={ride} onChanged={loadCurrent} />
        ) : ended ? (
          <RideEndedPanel ride={ended} onDone={(again) => {
            setEnded(null)
            if (again) setDraft({ pickupId: ended.pickup.id, destinationId: ended.destination.id })
          }} />
        ) : (
          <BookingPanel
            byId={byId}
            draft={draft}
            setDraft={setDraft}
            onPreview={(tripPath, pickupId, destinationId) =>
              setPreview((p) => ({ tripPath, pickupId, destinationId, fitKey: p.fitKey + 1 }))}
            onRequested={(r) => {
              lastRideId.current = r.id
              setRide(r)
            }}
          />
        )}
      </MapPanel>
    </div>
  )
}
