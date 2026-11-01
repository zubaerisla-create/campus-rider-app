import { useEffect, useMemo, useState } from 'react'
import { locationService } from '../services/locationService'

/** Loads the campus graph once and returns { graph, byId, loading, error }. */
export function useCampusMap() {
  const [graph, setGraph] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    locationService
      .map()
      .then((g) => alive && setGraph(g))
      .catch((e) => alive && setError(e))
    return () => {
      alive = false
    }
  }, [])

  const byId = useMemo(() => {
    const m = new Map()
    for (const l of graph?.locations || []) m.set(l.id, l)
    return m
  }, [graph])

  return { graph, byId, loading: !graph && !error, error }
}
