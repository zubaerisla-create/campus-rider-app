import { useEffect, useId, useRef, useState } from 'react'
import { MapPin, X } from 'lucide-react'
import { locationService } from '../services/locationService'
import { useDebounced } from '../hooks/useDebounced'

/**
 * Pickup / destination autocomplete. Suggestions come from the backend Trie
 * (GET /api/locations/search) - the browser does no searching of its own.
 */
export default function LocationInput({ value, onChange, placeholder, marker = 'circle', autoFocus, exclude, label }) {
  const [text, setText] = useState(value?.name || '')
  const [open, setOpen] = useState(false)
  const [results, setResults] = useState([])
  const [active, setActive] = useState(0)
  const [loading, setLoading] = useState(false)
  const query = useDebounced(text, 120)
  const listId = useId()
  const inputRef = useRef(null)

  useEffect(() => {
    setText(value?.name || '')
  }, [value?.id, value?.name])

  useEffect(() => {
    if (!open) return
    const q = query.trim()
    if (!q || q === value?.name) {
      setResults([])
      return
    }
    let alive = true
    setLoading(true)
    locationService
      .search(q)
      .then((r) => alive && (setResults(r.filter((l) => l.id !== exclude)), setActive(0)))
      .catch(() => alive && setResults([]))
      .finally(() => alive && setLoading(false))
    return () => {
      alive = false
    }
  }, [query, open, exclude, value?.name])

  const choose = (loc) => {
    onChange(loc)
    setText(loc.name)
    setOpen(false)
    inputRef.current?.blur()
  }

  const onKeyDown = (e) => {
    if (!results.length) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((a) => (a + 1) % results.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => (a - 1 + results.length) % results.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      choose(results[active])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="relative">
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-3.5 flex h-4 w-4 items-center justify-center" aria-hidden="true">
          {marker === 'square' ? (
            <span className="h-2.5 w-2.5 bg-ink" />
          ) : (
            <span className="h-2.5 w-2.5 rounded-full border-[3px] border-ink" />
          )}
        </span>
        <input
          ref={inputRef}
          className="input pl-10 pr-9"
          value={text}
          placeholder={placeholder}
          aria-label={label || placeholder}
          autoFocus={autoFocus}
          role="combobox"
          aria-expanded={open && results.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          onChange={(e) => {
            setText(e.target.value)
            setOpen(true)
            if (value) onChange(null)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKeyDown}
        />
        {text && (
          <button type="button" aria-label="Clear" className="absolute right-2.5 text-faint hover:text-ink"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => {
              setText('')
              onChange(null)
              inputRef.current?.focus()
            }}>
            <X size={16} />
          </button>
        )}
      </div>
      {open && (results.length > 0 || (loading && query)) && (
        <ul id={listId} role="listbox"
          className="absolute z-30 mt-1.5 max-h-72 w-full overflow-auto rounded-xl border border-line bg-white py-1 shadow-lg">
          {results.map((l, i) => (
            <li key={l.id} role="option" aria-selected={i === active}>
              <button type="button" disabled={!l.reachable}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(l)}
                onMouseEnter={() => setActive(i)}
                className={`flex w-full items-center gap-3 px-3.5 py-2.5 text-left disabled:opacity-40 ${i === active ? 'bg-canvas' : ''}`}>
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-canvas text-muted">
                  <MapPin size={16} />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-[15px] font-medium">{l.name}</span>
                  <span className="block truncate text-xs text-muted">
                    {l.category.charAt(0) + l.category.slice(1).toLowerCase()}
                    {l.rickshawStand ? ' · Rickshaw stand' : ''}
                    {!l.reachable ? ' · Not connected' : ''}
                  </span>
                </span>
              </button>
            </li>
          ))}
          {loading && !results.length && <li className="px-3.5 py-2.5 text-sm text-muted">Searching…</li>}
        </ul>
      )}
    </div>
  )
}
