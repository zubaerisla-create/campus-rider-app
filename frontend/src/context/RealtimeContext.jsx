import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { useAuth } from './AuthContext'

const RealtimeContext = createContext(null)

/**
 * One WebSocket per signed-in tab to /ws/tracking (simulated live tracking).
 * Components subscribe with useRealtimeEvent(type, handler). Reconnects with backoff.
 */
export function RealtimeProvider({ children }) {
  const { user } = useAuth()
  const listeners = useRef(new Map())
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!user) return undefined
    let socket
    let closed = false
    let retry = 0
    let timer
    let ping

    const connect = () => {
      const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${proto}://${window.location.host}/ws/tracking`)
      socket.onopen = () => {
        retry = 0
        setConnected(true)
        ping = setInterval(() => socket.readyState === 1 && socket.send('ping'), 25000)
        emit({ type: 'connected' })
      }
      socket.onmessage = (msg) => {
        try {
          emit(JSON.parse(msg.data))
        } catch {
          /* ignore non-JSON frames */
        }
      }
      socket.onclose = () => {
        setConnected(false)
        clearInterval(ping)
        if (!closed) {
          retry += 1
          timer = setTimeout(connect, Math.min(10000, 500 * 2 ** retry))
        }
      }
      socket.onerror = () => socket.close()
    }

    const emit = (event) => {
      for (const fn of listeners.current.get(event.type) || []) fn(event)
      for (const fn of listeners.current.get('*') || []) fn(event)
    }

    connect()
    return () => {
      closed = true
      clearTimeout(timer)
      clearInterval(ping)
      socket?.close()
      setConnected(false)
    }
  }, [user])

  const api = useRef({
    subscribe(type, fn) {
      const set = listeners.current.get(type) || new Set()
      set.add(fn)
      listeners.current.set(type, set)
      return () => set.delete(fn)
    },
  }).current

  return <RealtimeContext.Provider value={{ ...api, connected }}>{children}</RealtimeContext.Provider>
}

export function useRealtime() {
  return useContext(RealtimeContext)
}

/** Calls handler(event) for every realtime event of the given type ('*' for all). */
export function useRealtimeEvent(type, handler) {
  const rt = useRealtime()
  const ref = useRef(handler)
  ref.current = handler
  useEffect(() => rt?.subscribe(type, (e) => ref.current(e)), [rt, type])
}
