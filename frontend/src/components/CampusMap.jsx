import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Bike, LocateFixed, Minus, Plus } from 'lucide-react'

const SCALE = 1000 // location x/y (0..1) -> SVG user units

/**
 * The campus map, drawn from the backend's real graph (locations.csv vertices,
 * roads.csv edges). Routes are the vertex paths returned by the Java Dijkstra /
 * route planner, and rickshaw markers are the simulated positions pushed over
 * the WebSocket - nothing here is computed in the browser except drawing.
 *
 * Pan with drag, zoom with the wheel or the +/- buttons.
 */
export default function CampusMap({
  graph,
  positions = [],
  focusRickshawId,
  showRickshaws = true,
  approachPath = [],
  tripPath = [],
  pickupId,
  destinationId,
  stops = [],
  onLocationClick,
  fitIds,
  fitKey,
  padding = { top: 24, right: 24, bottom: 24, left: 24 },
  className = '',
  compact = false,
}) {
  const wrapRef = useRef(null)
  const [size, setSize] = useState({ w: 0, h: 0 })
  const [vb, setVb] = useState(null)
  const drag = useRef(null)

  const byId = useMemo(() => {
    const m = new Map()
    for (const l of graph?.locations || []) m.set(l.id, l)
    return m
  }, [graph])

  // Track container size.
  useLayoutEffect(() => {
    const el = wrapRef.current
    if (!el) return undefined
    const ro = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize({ w: Math.max(1, width), h: Math.max(1, height) })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const fit = useCallback(
    (ids) => {
      if (!graph || !size.w) return
      const pts = (ids && ids.length ? ids : graph.locations.map((l) => l.id))
        .map((id) => byId.get(id))
        .filter(Boolean)
      if (!pts.length) return
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      for (const p of pts) {
        minX = Math.min(minX, p.x * SCALE)
        minY = Math.min(minY, p.y * SCALE)
        maxX = Math.max(maxX, p.x * SCALE)
        maxY = Math.max(maxY, p.y * SCALE)
      }
      const minSpan = 220
      const bw = Math.max(maxX - minX, minSpan)
      const bh = Math.max(maxY - minY, minSpan)
      const cx = (minX + maxX) / 2
      const cy = (minY + maxY) / 2
      const pad = { top: 24, right: 24, bottom: 24, left: 24, ...padding }
      const innerW = Math.max(60, size.w - pad.left - pad.right)
      const innerH = Math.max(60, size.h - pad.top - pad.bottom)
      const s = Math.min(innerW / bw, innerH / bh) // px per unit
      const w = size.w / s
      const h = size.h / s
      const x = cx - (pad.left + innerW / 2) / s
      const y = cy - (pad.top + innerH / 2) / s
      setVb({ x, y, w, h })
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [graph, byId, size.w, size.h, padding.top, padding.right, padding.bottom, padding.left],
  )

  // Fit when the map loads, the container resizes, or the caller asks (fitKey).
  useEffect(() => {
    fit(fitIds)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fit, fitKey])

  const k = vb && size.w ? vb.w / size.w : 1 // user units per screen pixel

  const zoomAt = useCallback(
    (factor, px = size.w / 2, py = size.h / 2) => {
      setVb((v) => {
        if (!v) return v
        const w = Math.min(4000, Math.max(120, v.w * factor))
        const real = w / v.w
        const h = v.h * real
        const ux = v.x + (px / size.w) * v.w
        const uy = v.y + (py / size.h) * v.h
        return { x: ux - (ux - v.x) * real, y: uy - (uy - v.y) * real, w, h }
      })
    },
    [size.w, size.h],
  )

  // Wheel zoom (non-passive so the page does not scroll).
  useEffect(() => {
    const el = wrapRef.current
    if (!el) return undefined
    const onWheel = (e) => {
      e.preventDefault()
      const rect = el.getBoundingClientRect()
      zoomAt(e.deltaY > 0 ? 1.12 : 1 / 1.12, e.clientX - rect.left, e.clientY - rect.top)
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [zoomAt])

  const onPointerDown = (e) => {
    if (e.button !== 0) return
    drag.current = { x: e.clientX, y: e.clientY, vb, moved: false, id: e.pointerId }
  }
  const onPointerMove = (e) => {
    const d = drag.current
    if (!d || !d.vb) return
    const dx = e.clientX - d.x
    const dy = e.clientY - d.y
    if (!d.moved && Math.hypot(dx, dy) > 4) {
      d.moved = true
      e.currentTarget.setPointerCapture?.(d.id)
    }
    if (d.moved) {
      const s = size.w / d.vb.w
      setVb({ ...d.vb, x: d.vb.x - dx / s, y: d.vb.y - dy / s })
    }
  }
  const endDrag = () => {
    setTimeout(() => {
      drag.current = null
    }, 0)
  }
  const clickLocation = (id) => {
    if (drag.current?.moved) return
    onLocationClick?.(id)
  }

  const point = (id) => {
    const l = byId.get(id)
    return l ? [l.x * SCALE, l.y * SCALE] : null
  }

  const focus = positions.find((p) => p.id === focusRickshawId)
  const focusPt = focus ? [focus.x * SCALE, focus.y * SCALE] : null

  // Connect the rickshaw's exact (mid-road) position to the start of its remaining path.
  const toPoints = (path, prefix) => {
    const pts = path.map(point).filter(Boolean)
    if (prefix && pts.length) pts.unshift(prefix)
    return pts.map((p) => p.join(',')).join(' ')
  }
  const approachPts = toPoints(approachPath, approachPath.length ? focusPt : null)
  const tripPts = toPoints(tripPath, !approachPath.length && tripPath.length ? focusPt : null)

  const important = new Set([pickupId, destinationId, ...stops.map((s) => s.locationId)].filter(Boolean))
  const showAllLabels = k < 0.75

  // The caller may position the map itself (e.g. "absolute inset-0"); otherwise it is relative.
  const position = /\b(absolute|fixed)\b/.test(className) ? '' : 'relative'

  if (!graph) {
    return (
      <div ref={wrapRef} className={`${position} overflow-hidden bg-[#eceeea] ${className}`}>
        <div className="absolute inset-0 animate-pulse bg-[#e6e8e3]" />
      </div>
    )
  }

  return (
    <div ref={wrapRef} className={`map-surface ${position} select-none overflow-hidden bg-[#eceeea] ${className}`}>
      {vb && (
        <svg
          className="absolute inset-0 h-full w-full"
          viewBox={`${vb.x} ${vb.y} ${vb.w} ${vb.h}`}
          preserveAspectRatio="none"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerLeave={endDrag}
          role="img"
          aria-label="Jahangirnagar University campus map"
        >
          {/* Roads: casing + fill, like a street map. */}
          <g strokeLinecap="round">
            {graph.roads.map((r) => {
              const a = point(r.from)
              const b = point(r.to)
              if (!a || !b) return null
              return (
                <line key={`c-${r.from}-${r.to}`} x1={a[0]} y1={a[1]} x2={b[0]} y2={b[1]}
                  stroke="#d6d9d3" strokeWidth={11 * k} />
              )
            })}
            {graph.roads.map((r) => {
              const a = point(r.from)
              const b = point(r.to)
              if (!a || !b) return null
              return (
                <line key={`r-${r.from}-${r.to}`} x1={a[0]} y1={a[1]} x2={b[0]} y2={b[1]}
                  stroke="#ffffff" strokeWidth={7.5 * k}>
                  <title>{`${r.name} · ${Math.round(r.distanceMeters)} m`}</title>
                </line>
              )
            })}
          </g>

          {/* Routes from the backend (Dijkstra / shared-ride planner). */}
          {tripPts && (
            <g fill="none" strokeLinecap="round" strokeLinejoin="round">
              <polyline points={tripPts} stroke="#ffffff" strokeWidth={9 * k} />
              <polyline points={tripPts} stroke="var(--color-accent)" strokeWidth={5 * k} />
            </g>
          )}
          {approachPts && (
            <polyline points={approachPts} fill="none" stroke="var(--color-ink)" strokeWidth={3.5 * k}
              strokeDasharray={`${0.1 * k} ${8 * k}`} strokeLinecap="round" strokeLinejoin="round" />
          )}

          {/* Locations. */}
          {graph.locations.map((l) => {
            const x = l.x * SCALE
            const y = l.y * SCALE
            const isImportant = important.has(l.id)
            const showLabel = !compact && (showAllLabels || l.rickshawStand || isImportant)
            return (
              <g key={l.id} onClick={() => clickLocation(l.id)}
                className={onLocationClick ? 'cursor-pointer' : undefined}>
                <circle cx={x} cy={y} r={14 * k} fill="transparent" />
                <circle cx={x} cy={y} r={(l.rickshawStand ? 4.5 : 3.5) * k}
                  fill={l.reachable ? '#ffffff' : '#f3d4d4'} stroke={l.rickshawStand ? '#7c8178' : '#a9ada6'}
                  strokeWidth={1.6 * k} />
                {showLabel && (
                  <text x={x + 14 * k} y={y + 4 * k} fontSize={11.5 * k} fontWeight={isImportant ? 600 : 500}
                    fill={isImportant ? '#0b0b0c' : '#4a4e48'} stroke="#eceeea" strokeWidth={3.5 * k}
                    paintOrder="stroke" style={{ pointerEvents: 'none' }}>
                    {l.name}
                  </text>
                )}
                <title>{l.name}</title>
              </g>
            )
          })}

          {/* Driver stops (numbered). */}
          {stops.map((s, i) => {
            const p = point(s.locationId)
            if (!p) return null
            return (
              <g key={`${s.rideId}-${s.type}`} transform={`translate(${p[0]},${p[1]})`}>
                <circle r={10 * k} fill={s.type === 'PICKUP' ? '#0b0b0c' : '#ffffff'} stroke="#0b0b0c" strokeWidth={2 * k} />
                <text y={4 * k} textAnchor="middle" fontSize={11 * k} fontWeight="700"
                  fill={s.type === 'PICKUP' ? '#ffffff' : '#0b0b0c'}>{i + 1}</text>
              </g>
            )
          })}

          {/* Pickup (circle) and destination (square) markers. */}
          {pickupId && point(pickupId) && (
            <g transform={`translate(${point(pickupId)[0]},${point(pickupId)[1]})`}>
              <circle r={10 * k} fill="#0b0b0c" />
              <circle r={3.6 * k} fill="#ffffff" />
            </g>
          )}
          {destinationId && point(destinationId) && (
            <g transform={`translate(${point(destinationId)[0]},${point(destinationId)[1]})`}>
              <rect x={-9 * k} y={-9 * k} width={18 * k} height={18 * k} rx={2 * k} fill="#0b0b0c" />
              <rect x={-3.4 * k} y={-3.4 * k} width={6.8 * k} height={6.8 * k} fill="#ffffff" />
            </g>
          )}

          {/* Rickshaws (simulated positions). */}
          {showRickshaws &&
            positions
              .filter((p) => p.online || p.id === focusRickshawId)
              .map((p) => {
                const isFocus = p.id === focusRickshawId
                const r = (isFocus ? 15 : 11) * k
                return (
                  <g key={p.id} className="rickshaw-marker"
                    style={{ transform: `translate(${p.x * SCALE}px, ${p.y * SCALE}px)` }}>
                    {isFocus && <circle r={r} fill="var(--color-accent)" className="pulse-ring" />}
                    <circle r={r} fill={isFocus ? '#0b0b0c' : '#ffffff'} stroke={isFocus ? '#ffffff' : '#0b0b0c'}
                      strokeWidth={(isFocus ? 2.5 : 1.8) * k} />
                    <Bike x={-r * 0.62} y={-r * 0.62} width={r * 1.24} height={r * 1.24}
                      color={isFocus ? '#ffffff' : '#0b0b0c'} strokeWidth={2.2} />
                    <title>{`${p.id} · ${p.availableSeats}/${p.maxSeats} seats free`}</title>
                  </g>
                )
              })}
        </svg>
      )}

      {/* Controls */}
      <div className="absolute right-3 flex flex-col gap-2" style={{ bottom: (padding.bottom || 0) + 12 }}>
        <MapButton label="Zoom in" onClick={() => zoomAt(1 / 1.35)}><Plus size={18} /></MapButton>
        <MapButton label="Zoom out" onClick={() => zoomAt(1.35)}><Minus size={18} /></MapButton>
        <MapButton label="Recenter" onClick={() => fit(fitIds)}><LocateFixed size={18} /></MapButton>
      </div>
    </div>
  )
}

function MapButton({ children, label, onClick }) {
  return (
    <button type="button" onClick={onClick} aria-label={label} title={label}
      className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-ink shadow-md hover:bg-canvas">
      {children}
    </button>
  )
}
