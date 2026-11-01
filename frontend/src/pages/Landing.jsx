import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Bell, Navigation, Route, ShieldCheck, Users, Wallet } from 'lucide-react'
import CampusMap from '../components/CampusMap'
import LocationInput from '../components/LocationInput'
import { Button } from '../components/ui'
import { homeFor, useAuth } from '../context/AuthContext'
import { useCampusMap } from '../hooks/useCampusMap'

export default function Landing() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { graph } = useCampusMap()
  const [pickup, setPickup] = useState(null)
  const [destination, setDestination] = useState(null)

  const book = (e) => {
    e.preventDefault()
    const params = new URLSearchParams()
    if (pickup) params.set('pickup', pickup.id)
    if (destination) params.set('destination', destination.id)
    const target = `/ride${params.toString() ? `?${params}` : ''}`
    if (user?.role === 'PASSENGER') navigate(target)
    else if (user?.role === 'DRIVER') navigate(homeFor(user))
    else navigate('/login/passenger', { state: { from: target } })
  }

  const stands = graph?.locations.filter((l) => l.rickshawStand) || []

  return (
    <>
      {/* Hero */}
      <section className="mx-auto grid max-w-6xl items-center gap-10 px-5 pb-16 pt-10 md:grid-cols-[1fr_1.1fr] md:pt-16">
        <div>
          <p className="text-sm font-semibold text-accent">Jahangirnagar University · Savar</p>
          <h1 className="mt-3 text-4xl font-bold leading-[1.08] tracking-tight md:text-[52px]">
            Rickshaw rides across campus, in minutes.
          </h1>
          <p className="mt-4 max-w-md text-lg text-muted">
            Book a seat, share the ride with up to two others going your way, and watch your rickshaw arrive.
          </p>

          <form onSubmit={book} className="card mt-8 max-w-md space-y-3 p-4">
            <LocationInput value={pickup} onChange={setPickup} placeholder="Pickup location" exclude={destination?.id} />
            <LocationInput value={destination} onChange={setDestination} placeholder="Where to?" marker="square" exclude={pickup?.id} />
            <Button type="submit" className="w-full" size="lg">
              Book a ride <ArrowRight size={18} />
            </Button>
          </form>
          <p className="mt-4 text-sm text-muted">
            Drive a campus rickshaw?{' '}
            <Link to="/register/driver" className="font-semibold text-ink underline-offset-4 hover:underline">Register as a driver</Link>
          </p>
        </div>

        <div className="relative h-[360px] overflow-hidden rounded-3xl border border-line md:h-[520px]">
          <CampusMap graph={graph} showRickshaws={false} pickupId={pickup?.id} destinationId={destination?.id}
            padding={{ top: 56, right: 150, bottom: 28, left: 28 }} className="h-full w-full" />
          <div className="pointer-events-none absolute left-4 top-4 rounded-full bg-white/95 px-3 py-1.5 text-xs font-medium shadow-sm">
            {graph ? `${graph.locations.length} places · ${graph.roads.length} roads` : 'Loading campus map…'}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="border-t border-line bg-canvas">
        <div className="mx-auto max-w-6xl px-5 py-16">
          <h2 className="text-2xl font-bold tracking-tight md:text-3xl">How it works</h2>
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            {[
              { n: '1', t: 'Set pickup and destination', d: 'Search any hall, gate, faculty or stand. We plan the shortest route on campus roads.' },
              { n: '2', t: 'Choose a rickshaw', d: 'See nearby rickshaws, free seats and the fare. Join a shared ride heading your way to pay less.' },
              { n: '3', t: 'Track and ride', d: 'Your driver accepts, and you watch the rickshaw move to your pickup on the live map.' },
            ].map((s) => (
              <div key={s.n} className="rounded-2xl bg-white p-6">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink text-sm font-bold text-white">{s.n}</span>
                <h3 className="mt-4 font-semibold">{s.t}</h3>
                <p className="mt-1.5 text-sm text-muted">{s.d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Passengers & drivers */}
      <section className="mx-auto grid max-w-6xl gap-12 px-5 py-16 md:grid-cols-2">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">For passengers</h2>
          <ul className="mt-6 space-y-5">
            <Feature icon={Users} title="Share and save">Up to three passengers per rickshaw. Shared rides are 20% cheaper.</Feature>
            <Feature icon={Route} title="Only rides that fit">A rickshaw is offered only if your trip fits its route without a long detour.</Feature>
            <Feature icon={ShieldCheck} title="Emergency priority">Medical emergency requests jump the queue.</Feature>
          </ul>
          <Button to="/register/passenger" className="mt-8">Create a passenger account</Button>
        </div>
        <div>
          <h2 className="text-2xl font-bold tracking-tight">For drivers</h2>
          <ul className="mt-6 space-y-5">
            <Feature icon={Bell} title="Requests come to you">The best-matched request is sent to you. Pass, and it goes to the next driver.</Feature>
            <Feature icon={Navigation} title="Turn-by-turn on campus">Every pickup and drop-off in the best order, on the shortest roads.</Feature>
            <Feature icon={Wallet} title="Go online when you like">Set your seats and your own destination, and switch off any time.</Feature>
          </ul>
          <Button to="/register/driver" variant="outline" className="mt-8">Register your rickshaw</Button>
        </div>
      </section>

      {/* Coverage */}
      <section className="border-t border-line">
        <div className="mx-auto max-w-6xl px-5 py-16">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="text-2xl font-bold tracking-tight md:text-3xl">Campus coverage</h2>
              <p className="mt-2 text-muted">From Dairy Gate to Bishmile Gate: halls, faculties, gates and food spots.</p>
            </div>
            {graph && (
              <div className="flex gap-8 text-sm">
                <Stat value={graph.locations.length} label="Places" />
                <Stat value={stands.length} label="Rickshaw stands" />
                <Stat value={graph.roads.length} label="Road segments" />
              </div>
            )}
          </div>
          <div className="mt-8 flex flex-wrap gap-2">
            {(graph?.locations || []).map((l) => (
              <span key={l.id} className={`rounded-full border px-3 py-1.5 text-sm ${l.rickshawStand ? 'border-ink font-medium' : 'border-line text-muted'}`}>
                {l.name}
              </span>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}

function Feature({ icon: Icon, title, children }) {
  return (
    <li className="flex gap-4">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-canvas"><Icon size={20} /></span>
      <div>
        <h3 className="font-semibold">{title}</h3>
        <p className="mt-0.5 text-sm text-muted">{children}</p>
      </div>
    </li>
  )
}

function Stat({ value, label }) {
  return (
    <div>
      <div className="text-2xl font-bold">{value}</div>
      <div className="text-muted">{label}</div>
    </div>
  )
}
