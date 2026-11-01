import { useEffect, useRef } from 'react'

export function useInterval(fn, ms) {
  const ref = useRef(fn)
  ref.current = fn
  useEffect(() => {
    if (!ms) return undefined
    const t = setInterval(() => ref.current(), ms)
    return () => clearInterval(t)
  }, [ms])
}
