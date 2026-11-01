import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { CheckCircle2, Info, TriangleAlert, X } from 'lucide-react'

const ToastContext = createContext(null)

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const push = useCallback(
    (message, tone = 'info') => {
      if (!message) return
      const id = nextId.current++
      setToasts((t) => [...t.slice(-2), { id, message, tone }])
      setTimeout(() => dismiss(id), tone === 'error' ? 6000 : 4000)
    },
    [dismiss],
  )

  const value = useMemo(() => ({ push, dismiss }), [push, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-3 z-[100] flex flex-col items-center gap-2 px-4" aria-live="polite">
        {toasts.map((t) => {
          const Icon = t.tone === 'error' ? TriangleAlert : t.tone === 'success' ? CheckCircle2 : Info
          return (
            <div key={t.id} role="status"
              className="fade-up pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-xl bg-ink px-4 py-3 text-sm text-white shadow-lg">
              <Icon size={18} className={t.tone === 'error' ? 'mt-0.5 text-red-300' : 'mt-0.5 text-emerald-300'} />
              <p className="flex-1 leading-snug">{t.message}</p>
              <button onClick={() => dismiss(t.id)} className="text-white/60 hover:text-white" aria-label="Dismiss">
                <X size={16} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}
