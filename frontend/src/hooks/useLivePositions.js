import { useEffect, useState } from 'react'
import { locationService } from '../services/locationService'
import { useRealtimeEvent } from '../context/RealtimeContext'

/** Simulated rickshaw positions: initial REST fetch, then live WebSocket "positions" pushes. */
export function useLivePositions(enabled = true) {
  const [positions, setPositions] = useState([])

  useEffect(() => {
    if (!enabled) return undefined
    let alive = true
    locationService.positions().then((p) => alive && setPositions(p)).catch(() => {})
    return () => {
      alive = false
    }
  }, [enabled])

  useRealtimeEvent('positions', (e) => enabled && setPositions(e.rickshaws || []))
  return positions
}
