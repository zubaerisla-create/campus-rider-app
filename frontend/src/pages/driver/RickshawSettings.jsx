import { useEffect, useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { Button, Field, Segmented, Select } from '../../components/ui'
import { driverService } from '../../services/driverService'

/** Current location (only while empty), own destination, and seats offered (0-3). */
export default function RickshawSettings({ dash, graph, busy, act }) {
  const r = dash.rickshaw
  const hasRides = dash.activeRides.length > 0
  const [open, setOpen] = useState(!r.online)
  const [form, setForm] = useState({ currentLocationId: '', targetDestinationId: '', availableSeats: 3 })

  useEffect(() => {
    setForm({
      currentLocationId: r.location?.id || '',
      targetDestinationId: r.targetDestination?.id || '',
      availableSeats: r.availableSeats,
    })
  }, [r.location?.id, r.targetDestination?.id, r.availableSeats])

  const maxOffer = r.maxSeats - r.bookedSeats
  const places = (graph?.locations || []).filter((l) => l.reachable)

  return (
    <section className="mt-5 border-t border-line px-5 pt-4">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="flex w-full items-center justify-between text-sm font-semibold">
        Rickshaw settings
        <ChevronDown size={18} className={`text-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <form className="mt-3 space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            act('settings', () => driverService.updateRickshaw(form), 'Rickshaw updated.')
          }}>
          <Field label="Current location" id="currentLocationId"
            hint={hasRides ? 'Locked while you have passengers: use Move to drive.' : undefined}>
            <Select id="currentLocationId" value={form.currentLocationId} disabled={hasRides || !!r.position.movingToId}
              onChange={(e) => setForm({ ...form, currentLocationId: e.target.value })}>
              {places.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
            </Select>
          </Field>
          <Field label="My destination (optional)" id="targetDestinationId"
            hint="Only rides that fit your way there will be offered when you are empty.">
            <Select id="targetDestinationId" value={form.targetDestinationId}
              onChange={(e) => setForm({ ...form, targetDestinationId: e.target.value })}>
              <option value="">Anywhere on campus</option>
              {places.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
            </Select>
          </Field>
          <div>
            <span className="label">Seats offered</span>
            <Segmented label="Seats offered" value={form.availableSeats}
              onChange={(v) => setForm({ ...form, availableSeats: v })}
              options={[0, 1, 2, 3].map((n) => ({ value: n, label: String(n), disabled: n > maxOffer }))} />
            <p className="mt-1.5 text-[13px] text-muted">{r.bookedSeats} of {r.maxSeats} seats are taken by passengers.</p>
          </div>
          <Button type="submit" variant="outline" className="w-full" loading={busy === 'settings'}>Save</Button>
        </form>
      )}
    </section>
  )
}
